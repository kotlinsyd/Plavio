package com.pulsestream.app

import android.app.Application
import android.util.Log
import androidx.room.Room
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
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PulseApp : Application() {

    lateinit var repository: StreamRepository
        private set

    lateinit var settings: SettingsStore
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
    }

    /**
     * Opens the database, self-healing from corruption: if the file is
     * unreadable the app recreates it empty instead of crash-looping.
     * The in-app updater keeps working, so a fix can always reach the phone.
     */
    private fun openDatabase(): AppDatabase {
        return try {
            buildDatabase().also {
                // Force the open now — throws here if the file is corrupt.
                it.openHelper.writableDatabase
            }
        } catch (e: Exception) {
            Log.e("PulseApp", "Database unreadable, recreating fresh", e)
            try {
                deleteDatabase(DB_NAME)
            } catch (_: Exception) {
            }
            // The fresh DB is empty but the preinstall_done_* flags survived
            // in SharedPreferences — without clearing them the bundled
            // addons (PlavioMeta/Cinemeta/…) would never be reinstalled and
            // the app would look broken with no explanation.
            runCatching { SettingsStore(this).clearPreinstallFlags() }
            buildDatabase()
        }
    }

    private fun buildDatabase(): AppDatabase =
        Room.databaseBuilder(this, AppDatabase::class.java, DB_NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
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
