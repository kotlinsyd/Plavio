package com.pulsestream.app.data.notifications

import android.content.Context
import android.content.SharedPreferences

/**
 * Remembers the newest episode the app has already seen (or notified about)
 * for each followed show, so the background worker can detect releases.
 *
 * Deliberately backed by SharedPreferences rather than Room: the episode
 * check worker runs without the app's database helper, and this avoids a
 * schema migration for what is effectively ephemeral sync state. Entries
 * are keyed "addonId|metaId" and pruned when a show leaves the watchlist.
 */
class EpisodeTracker(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Newest known (season, episode) for a show, or null when never checked. */
    fun lastKnown(addonId: String, metaId: String): Pair<Int, Int>? {
        val raw = prefs.getString(key(addonId, metaId), null) ?: return null
        val parts = raw.split(":")
        val season = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val episode = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return season to episode
    }

    /** Record the newest episode seen for a show. */
    fun update(addonId: String, metaId: String, season: Int, episode: Int) {
        prefs.edit()
            .putString(key(addonId, metaId), "$season:$episode:${System.currentTimeMillis()}")
            .apply()
    }

    /** Drop tracking state for shows no longer followed. */
    fun prune(keepKeys: Set<String>) {
        val stale = prefs.all.keys.filter { it.startsWith(PREFIX) && it !in keepKeys }
        if (stale.isEmpty()) return
        prefs.edit().apply {
            stale.forEach { remove(it) }
        }.apply()
    }

    companion object {
        private const val PREFS = "pulse_episode_tracker"
        private const val PREFIX = "ep_"

        /** Stable storage key for a followed show. */
        fun key(addonId: String, metaId: String): String = "$PREFIX$addonId|$metaId"
    }
}
