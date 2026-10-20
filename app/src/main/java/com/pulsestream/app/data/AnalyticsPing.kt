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
 * Optional first-launch install ping for the PlavioHD stats backend.
 *
 * When optional telemetry is enabled, sends at most one fire-and-forget HTTPS GET per install:
 *   /app-ping?uuid={random UUID}&ver={versionName}&flavor={mobile|tv}
 * The random install UUID is a pseudonymous identifier, not a guarantee of
 * anonymity. No request is made unless the user enabled optional telemetry.
 * Never blocks startup; failures are silent (retried on a later launch).
 */
object AnalyticsPing {
    private const val ENDPOINT = "https://plaviohd.edon.workers.dev/app-ping"
    private const val PREFS = "pulse_analytics"
    private const val KEY_UUID = "install_uuid"
    private const val KEY_PINGED = "ping_sent"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Sends the one-time install ping. Safe to call on every launch. */
    suspend fun trackInstallOnce(context: Context) = withContext(Dispatchers.IO) {
        if (!TelemetryConsent.enabled(context)) return@withContext
        val p = prefs(context.applicationContext)
        if (p.getBoolean(KEY_PINGED, false)) return@withContext
        val uuid = p.getString(KEY_UUID, null) ?: UUID.randomUUID().toString().also {
            p.edit().putString(KEY_UUID, it).apply()
        }
        try {
            val query = "uuid=${URLEncoder.encode(uuid, "UTF-8")}" +
                "&ver=${URLEncoder.encode(BuildConfig.VERSION_NAME, "UTF-8")}" +
                "&flavor=${URLEncoder.encode(BuildConfig.FLAVOR, "UTF-8")}"
            val conn = URL("$ENDPOINT?$query").openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            // Drain the response so the hit registers server-side.
            conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            p.edit().putBoolean(KEY_PINGED, true).apply()
        } catch (_: Exception) {
            // Offline or backend hiccup — try again on next launch.
        }
    }
}
