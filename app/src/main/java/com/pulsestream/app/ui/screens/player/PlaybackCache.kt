@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.pulsestream.app.ui.screens.player

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Singleton on-disk playback cache (Settings > Playback > Buffer & Cache).
 * Caches media so re-watches don't re-download. 0 = off (caller skips cache).
 * Recreated if the configured max size changes.
 */
object PlaybackCache {

    @Volatile
    private var instance: Cache? = null

    @Volatile
    private var maxBytes: Long = 0

    @Synchronized
    fun get(context: Context, maxBytes: Long): Cache {
        val current = instance
        if (current != null && this.maxBytes == maxBytes) return current
        try {
            current?.release()
        } catch (_: Exception) {
        }
        val cacheDir = File(context.cacheDir, "playback")
        val evictor = LeastRecentlyUsedCacheEvictor(maxBytes)
        val dbProvider = StandaloneDatabaseProvider(context)
        return SimpleCache(cacheDir, evictor, dbProvider).also {
            instance = it
            this.maxBytes = maxBytes
        }
    }
}
