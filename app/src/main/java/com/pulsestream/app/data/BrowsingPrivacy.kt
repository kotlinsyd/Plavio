package com.pulsestream.app.data

import android.content.Context
import java.nio.charset.StandardCharsets

/**
 * Local-only state for spoiler-safe episode browsing. Kept in its own preference
 * file so it does not share the general settings/export surface.
 */
class BrowsingPrivacy(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hideUnrevealedEpisodes(): Boolean = prefs.getBoolean(KEY_HIDE_UNREVEALED, false)

    fun setHideUnrevealedEpisodes(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_UNREVEALED, enabled).apply()
    }

    fun revealedEpisodes(showKey: String): Set<String> =
        prefs.getStringSet(showPreferenceKey(showKey), emptySet()).orEmpty().toSet()

    /** Returns the updated set so the UI can update without waiting for disk I/O. */
    fun setEpisodeRevealed(showKey: String, episodeId: String, revealed: Boolean): Set<String> {
        val key = showPreferenceKey(showKey)
        val updated = prefs.getStringSet(key, emptySet()).orEmpty().toMutableSet().apply {
            if (revealed) add(episodeId) else remove(episodeId)
        }
        prefs.edit().apply {
            if (updated.isEmpty()) remove(key) else putStringSet(key, updated)
        }.apply()
        return updated.toSet()
    }

    companion object {
        private const val PREFS_NAME = "pulse_browsing_privacy"
        private const val KEY_HIDE_UNREVEALED = "hide_unrevealed_episode_details"
        private const val SHOW_PREFIX = "revealed_episodes_"

        /** A deterministic encoded key that cannot collide on delimiter-containing IDs. */
        fun showPreferenceKey(showKey: String): String =
            SHOW_PREFIX + showKey.toByteArray(StandardCharsets.UTF_8)
                .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

        /** Pure display rule, intentionally independent of playback selection. */
        fun shouldHideEpisodeDetails(hidingEnabled: Boolean, episodeRevealed: Boolean): Boolean =
            hidingEnabled && !episodeRevealed
    }
}
