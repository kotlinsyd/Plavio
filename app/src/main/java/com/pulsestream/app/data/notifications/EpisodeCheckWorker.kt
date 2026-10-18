package com.pulsestream.app.data.notifications

import android.content.Context
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pulsestream.app.data.SettingsStore
import com.pulsestream.app.data.StremioApi
import com.pulsestream.app.data.db.AppDatabase
import com.pulsestream.app.data.db.MIGRATION_1_2
import com.pulsestream.app.data.db.MIGRATION_2_3
import com.pulsestream.app.data.db.MIGRATION_3_4
import com.pulsestream.app.data.db.MIGRATION_4_5
import com.pulsestream.app.data.db.MIGRATION_5_6
import com.pulsestream.app.data.db.MIGRATION_6_7
import com.pulsestream.app.data.db.MIGRATION_7_8
import java.time.LocalDate

/**
 * Periodic background check for new episodes of watchlisted series.
 *
 * Runs every [EpisodeNotifications.CHECK_INTERVAL_HOURS] hours when the
 * network is connected and the battery isn't low. For each followed show it
 * fetches the series meta from the addon's own server, finds the newest
 * released episode, and notifies when it's newer than the last seen one.
 *
 * The first check for a show only records a baseline — no notification —
 * so installing the feature (or re-adding a show) never spams.
 *
 * All failures are contained per show and never crash the worker: an
 * unreachable addon simply means "try again next cycle".
 */
class EpisodeCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val settings = SettingsStore(ctx)
        if (!settings.episodeNotifications) {
            EpisodeNotifications.cancel(ctx)
            return Result.success()
        }

        // Don't resurrect an empty database if the app's own open failed.
        val dbFile = ctx.getDatabasePath(DB_NAME)
        if (!dbFile.exists()) return Result.success()

        val db = runCatching {
            Room.databaseBuilder(ctx, AppDatabase::class.java, DB_NAME)
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                    MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8
                )
                .build()
        }.getOrNull() ?: return Result.success()

        try {
            val tracker = EpisodeTracker(ctx)
            val api = StremioApi()

            val addons = runCatching { db.addonDao().getAll() }.getOrNull().orEmpty()
            val metaAddons = addons
                .filter { it.enabled && "meta" in it.resourceSet }
                .associateBy { it.id }

            val series = runCatching { db.watchlistDao().getAll() }
                .getOrNull().orEmpty()
                .filter { it.type == "series" }
            if (series.isEmpty()) {
                tracker.prune(emptySet())
                return Result.success()
            }

            val keepKeys = mutableSetOf<String>()
            for (item in series) {
                val key = EpisodeTracker.key(item.addonId, item.metaId)
                keepKeys.add(key)
                val addon = metaAddons[item.addonId] ?: continue
                runCatching {
                    val meta = api.fetchMeta(addon.baseUrl, "series", item.metaId)
                        ?: return@runCatching
                    val newest = newestReleased(meta.videos)
                        ?: return@runCatching

                    val last = tracker.lastKnown(item.addonId, item.metaId)
                    if (last == null) {
                        // Baseline: record without notifying.
                        tracker.update(item.addonId, item.metaId, newest.first, newest.second)
                    } else if (isNewer(newest.first, newest.second, last.first, last.second)) {
                        val epTitle = meta.videos
                            .firstOrNull { it.season == newest.first && it.episode == newest.second }
                            ?.title?.takeIf { it.isNotBlank() }
                        EpisodeNotifications.postNewEpisode(
                            ctx, item.addonId, item.metaId, item.name,
                            newest.first, newest.second, epTitle
                        )
                        tracker.update(item.addonId, item.metaId, newest.first, newest.second)
                    }
                }
            }
            tracker.prune(keepKeys)
        } finally {
            runCatching { db.close() }
        }
        return Result.success()
    }

    private fun isNewer(s: Int, e: Int, lastS: Int, lastE: Int): Boolean =
        s > lastS || (s == lastS && e > lastE)

    /**
     * Newest episode that has actually aired. Videos without a parseable
     * release date are treated as released (metadata sources usually omit
     * the date rather than lie about it).
     */
    private fun newestReleased(
        videos: List<com.pulsestream.app.data.Video>
    ): Pair<Int, Int>? {
        val today = runCatching { LocalDate.now() }.getOrNull()
        return videos
            .filter { v ->
                today == null || v.released == null || !isFutureDate(v.released, today)
            }
            .maxWithOrNull(compareBy({ it.season }, { it.episode }))
            ?.let { it.season to it.episode }
    }

    private fun isFutureDate(raw: String, today: LocalDate): Boolean {
        // Expected "yyyy-MM-dd"; anything else is treated as not-future.
        val parts = raw.take(10).split("-")
        if (parts.size != 3) return false
        val y = parts[0].toIntOrNull() ?: return false
        val m = parts[1].toIntOrNull() ?: return false
        val d = parts[2].toIntOrNull() ?: return false
        return runCatching { LocalDate.of(y, m, d).isAfter(today) }.getOrDefault(false)
    }

    companion object {
        private const val DB_NAME = "pulse-stream.db"
    }
}
