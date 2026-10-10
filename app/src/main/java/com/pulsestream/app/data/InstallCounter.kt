package com.pulsestream.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Global install counter shown in Settings > About ("N installs").
 *
 * Backend: Abacus (free counter API, no signup). One [trackInstallOnce] hit per
 * install; About reads the total via [getInstallCount].
 *
 * Abacus keys have an absolute ~168-day TTL, so the app self-heals: it remembers
 * the highest count it has ever seen locally, and if the backend key ever
 * vanishes it re-seeds the counter from that local max instead of restarting at 0.
 */
object InstallCounter {
    private const val BASE = "https://abacus.jasoncameron.dev"
    private const val NAMESPACE = "pulse-stream-app"
    private const val KEY = "downloads"
    private const val PREFS = "install_counter"
    private const val KEY_COUNTED = "counted_this_install"
    private const val KEY_MAX_SEEN = "max_seen"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Increments the global counter once per install. Safe to call on every launch. */
    suspend fun trackInstallOnce(context: Context) = withContext(Dispatchers.IO) {
        val p = prefs(context)
        if (p.getBoolean(KEY_COUNTED, false)) return@withContext
        try {
            val conn = URL("$BASE/hit/$NAMESPACE/$KEY").openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            p.edit().putBoolean(KEY_COUNTED, true).apply()
        } catch (_: Exception) {
            // Offline or backend hiccup — try again on next launch.
        }
    }

    /**
     * Returns the install count for display, or null if it can't be determined
     * right now (offline and nothing cached yet).
     */
    suspend fun getInstallCount(context: Context): Int? = withContext(Dispatchers.IO) {
        val p = prefs(context)
        val localMax = p.getInt(KEY_MAX_SEEN, 0)
        try {
            val conn = URL("$BASE/get/$NAMESPACE/$KEY").openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            if (conn.responseCode == 404) {
                conn.disconnect()
                reseed(context, localMax)
                return@withContext localMax.takeIf { it > 0 }
            }
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val remote = Regex("\"value\"\\s*:\\s*(\\d+)").find(body)
                ?.groupValues?.get(1)?.toIntOrNull() ?: return@withContext null
            val shown = maxOf(remote, localMax)
            p.edit().putInt(KEY_MAX_SEEN, shown).apply()
            shown
        } catch (_: Exception) {
            localMax.takeIf { it > 0 }
        }
    }

    /** Backend key expired — recreate it seeded from the local max so history survives. */
    private fun reseed(context: Context, localMax: Int) {
        try {
            val conn =
                URL("$BASE/create/$NAMESPACE/$KEY?initializer=$localMax").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.inputStream.bufferedReader().readText()
            conn.disconnect()
        } catch (_: Exception) {
            // Another device probably re-seeded first; the next read will pick it up.
        }
    }
}
