package com.pulsestream.app.data

import android.content.Context
import com.pulsestream.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

/**
 * Anonymous install + activity counting on the owner's OWN stats
 * server (Cloudflare worker + Supabase — an open-source backend.
 * No Google, no Firebase, no third-party analytics: those were
 * removed on the owner's order, 2026-10-22).
 *
 * What is sent: a random install UUID (generated on the device;
 * it identifies no person), the app version and the flavor —
 * nothing else. Three tiny GETs to /app-ping:
 *   kind=install   — once per install (first launch)
 *   kind=session   — every launch
 *   kind=heartbeat — every 5 minutes while the app is on screen
 * The server turns these into total installs, daily active users
 * and "online now" (seen within the last 15 minutes), which the
 * owner reads through his Stats view. Nothing is shown in the
 * app UI.
 *
 * Counting is ON by default (the owner wants true numbers) and
 * can be turned off in Settings → "Anonymous usage counting".
 * Failures are silent and never block the app; a failed install
 * ping is retried on a later launch.
 */
object AppStats {
    private const val ENDPOINT = "https://plaviohd.edon.workers.dev/app-ping"
    private const val PREFS = "plavio_stats"
    private const val KEY_UUID = "install_uuid"
    private const val KEY_COUNTED = "install_counted"
    private const val PRIVACY_PREFS = "plavio_privacy"
    private const val KEY_ENABLED = "anonymous_stats"

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PRIVACY_PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PRIVACY_PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, value).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun uuid(context: Context): String {
        val p = prefs(context)
        return p.getString(KEY_UUID, null) ?: UUID.randomUUID().toString().also {
            p.edit().putString(KEY_UUID, it).apply()
        }
    }

    private fun ping(context: Context, kind: String): Boolean {
        return try {
            val query = "uuid=${URLEncoder.encode(uuid(context), "UTF-8")}" +
                "&ver=${URLEncoder.encode(BuildConfig.VERSION_NAME, "UTF-8")}" +
                "&flavor=${URLEncoder.encode(BuildConfig.FLAVOR, "UTF-8")}" +
                "&kind=$kind"
            val conn = URL("$ENDPOINT?$query").openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            // Drain the response so the hit registers server-side.
            conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Launch ping: the one-time install count, plus a session tick. */
    suspend fun trackLaunch(context: Context) = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        if (!enabled(appCtx)) return@withContext
        val p = prefs(appCtx)
        if (!p.getBoolean(KEY_COUNTED, false)) {
            if (ping(appCtx, "install")) {
                p.edit().putBoolean(KEY_COUNTED, true).apply()
            }
        }
        ping(appCtx, "session")
    }

    /** Foreground heartbeat: keeps "online now" honest. */
    suspend fun heartbeat(context: Context) = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        if (!enabled(appCtx)) return@withContext
        ping(appCtx, "heartbeat")
    }
}
