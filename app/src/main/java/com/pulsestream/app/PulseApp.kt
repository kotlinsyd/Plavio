package com.pulsestream.app

import android.app.Application
import android.util.Log
import androidx.room.Room
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.pulsestream.app.data.AnalyticsPing
import com.pulsestream.app.data.InstallCounter
import com.pulsestream.app.data.SettingsStore
import com.pulsestream.app.data.StremioApi
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.data.db.AppDatabase
import com.pulsestream.app.data.db.MIGRATION_1_2
import com.pulsestream.app.data.db.MIGRATION_2_3
import com.pulsestream.app.data.db.MIGRATION_3_4
import com.pulsestream.app.data.db.MIGRATION_4_5
import com.pulsestream.app.data.db.MIGRATION_5_6
import com.pulsestream.app.data.db.MIGRATION_6_7
import com.pulsestream.app.data.db.MIGRATION_7_8
import com.pulsestream.app.data.db.MIGRATION_8_9
import com.pulsestream.app.data.db.MIGRATION_9_10
import com.pulsestream.app.ui.tv.isTelevision
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PulseApp : Application(), ImageLoaderFactory {

    /**
     * TV-tuned image pipeline: on memory-constrained TVs (48MB heap class
     * seen in the field) Coil's default ~25% memory cache plus hundreds of
     * posters causes lag and OOMs. Cap it to 8% on TV; phones keep default.
     */
    override fun newImageLoader(): ImageLoader {
        val lowMem = isTelevision()
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(if (lowMem) 0.08 else 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            // Downscale posters on TV: cards are ~128dp wide, no need for full-res.
            .respectCacheHeaders(false)
            .build()
    }

    lateinit var repository: StreamRepository
        private set

    lateinit var settings: SettingsStore
        private set

    lateinit var database: AppDatabase
        private set

    /**
     * A crashing background task must never kill the app (previously an
     * uncaught exception here meant an instant force-close loop that also
     * bricked the in-app updater). Failures are logged instead.
     */
    private val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            Log.e("PulseApp", "Background startup task failed", e)
        }
    )

    override fun onCreate() {
        installCrashCatcher()
        super.onCreate()
        initCrashReporting()
        val db = openDatabase()
        database = db
        settings = SettingsStore(this)
        repository = StreamRepository(db, StremioApi(), settings)
        // Quietly install the bundled addons (PlavioMeta + PlavioHD + Cinemeta + subtitles) on first launch.
        appScope.launch { repository.ensurePreinstalledAddons() }
        // Fill in addon logos for installs from before logos were stored.
        appScope.launch { repository.backfillAddonLogos() }
        // Give pre-ordering addons distinct positions so rearrange works.
        appScope.launch { repository.normalizeAddonSortOrder() }
        // Drop catalog rows too big for SQLite's CursorWindow (reading one
        // would crash Home with SQLiteBlobTooBigException). Surgical — the
        // watchlist, addons and settings survive.
        appScope.launch { repository.pruneOversizedCache() }
        // Count this install once for the About screen counter.
        appScope.launch { InstallCounter.trackInstallOnce(this@PulseApp) }
        // One-time install ping for the PlavioHD app-stats backend.
        appScope.launch { AnalyticsPing.trackInstallOnce(this@PulseApp) }
    }

    /**
     * Opens the database, self-healing ONLY from genuine file corruption.
     *
     * A corrupt database file is renamed to a .corrupt.bak backup (never
     * silently deleted) before recreating empty, so user data can still be
     * recovered. Any other failure — failed migration, transient I/O error,
     * programming bug — is rethrown to crash loudly and reach Crashlytics,
     * instead of silently wiping the user's add-ons, watchlist and history.
     * The in-app updater keeps working, so a fix can always reach the phone.
     */
    private fun openDatabase(): AppDatabase {
        return try {
            buildDatabase().also {
                // Force the open now — throws here if the file is corrupt.
                it.openHelper.writableDatabase
            }
        } catch (e: Exception) {
            if (!isCorruption(e)) {
                // Not corruption — do NOT delete user data. Crash loudly so
                // the real bug is visible in Crashlytics instead of masking
                // it as an empty database.
                throw e
            }
            Log.e("PulseApp", "Database file corrupt, backing up and recreating", e)
            // Rename the corrupt file as a backup instead of deleting it.
            runCatching {
                val dbFile = getDatabasePath(DB_NAME)
                if (dbFile.exists()) {
                    val bak = java.io.File(dbFile.parent, "$DB_NAME.corrupt.bak")
                    runCatching { bak.delete() }
                    dbFile.renameTo(bak)
                    // Also move the -wal / -shm sidecars so they don't confuse reopen.
                    for (suffix in listOf("-wal", "-shm")) {
                        runCatching {
                            java.io.File(dbFile.parent, "$DB_NAME$suffix").renameTo(
                                java.io.File(dbFile.parent, "$DB_NAME$suffix.corrupt.bak")
                            )
                        }
                    }
                } else {
                    deleteDatabase(DB_NAME)
                }
            }
            // The fresh DB is empty but the preinstall_done_* flags survived
            // in SharedPreferences — without clearing them the bundled
            // addons (PlavioMeta/Cinemeta/…) would never be reinstalled and
            // the app would look broken with no explanation.
            runCatching { SettingsStore(this).clearPreinstallFlags() }
            buildDatabase()
        }
    }

    /** True only for genuine SQLite file-corruption failures. */
    private fun isCorruption(e: Throwable): Boolean {
        var t: Throwable? = e
        while (t != null) {
            if (t is android.database.sqlite.SQLiteDatabaseCorruptException) return true
            val msg = (t.message ?: "").lowercase()
            // SQLite corruption signatures — deliberately narrow so a failed
            // migration or I/O error never counts as "corrupt".
            if ("database disk image is malformed" in msg ||
                "file is not a database" in msg ||
                "database is corrupt" in msg ||
                "notadb" in msg
            ) return true
            t = t.cause
        }
        return false
    }

    private fun buildDatabase(): AppDatabase =
        Room.databaseBuilder(this, AppDatabase::class.java, DB_NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
            .build()

    /**
     * Saves any uncaught crash to internal storage. On the next launch
     * MainActivity shows it with a Copy button, so the user can send it
     * for diagnosis.
     */
    private fun installCrashCatcher() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                openFileOutput("last_crash.txt", MODE_PRIVATE).bufferedWriter().use { out ->
                    out.write(Log.getStackTraceString(throwable))
                }
            } catch (_: Exception) {
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Firebase Crashlytics: automatic crash reports (stack trace, device info)
     * sent silently in the background. No extra permissions needed — it uses
     * the internet permission the app already has. Coexists with the local
     * crash catcher above (that one chains to Crashlytics' handler).
     */
    private fun initCrashReporting() {
        try {
            val crashlytics = com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance()
            crashlytics.setCrashlyticsCollectionEnabled(true)
            crashlytics.setCustomKey("app_version", com.pulsestream.app.BuildConfig.VERSION_NAME)
        } catch (_: Exception) {
            // Crash reporting must never break app startup (e.g. no network
            // on first launch, Firebase not configured in dev builds).
        }
    }

    /** Crash log from the previous run, if the app died with an exception. */
    fun readCrashReport(): String? {        val f = java.io.File(filesDir, "last_crash.txt")
        return if (f.exists()) f.readText().take(6000) else null
    }

    fun clearCrashReport() {
        try {
            java.io.File(filesDir, "last_crash.txt").delete()
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val DB_NAME = "pulse-stream.db"
    }
}
