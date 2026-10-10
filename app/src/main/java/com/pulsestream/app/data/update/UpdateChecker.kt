package com.pulsestream.app.data.update

import android.content.Context
import android.content.SharedPreferences
import com.pulsestream.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/** Info about a newer release found on GitLab. */
data class UpdateInfo(val version: String, val notes: String, val apkUrl: String)

/**
 * Checks the GitLab releases feed for a version newer than the installed one.
 * Silent on any failure (no update popup then).
 */
object UpdateChecker {
    private const val RELEASES_URL = "https://gitlab.com/api/v4/projects/87396069/releases"
    private const val PREFS = "pulse_update"
    private const val KEY_LAST_CHECK = "last_check_ms"
    private const val KEY_SKIPPED = "skipped_version"
    private const val KEY_AUTO_CHECK = "auto_check"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** User toggle: show the update popup automatically (Settings > About > App Updater). */
    fun isAutoCheckEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_CHECK, true)

    fun setAutoCheckEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_CHECK, enabled).apply()
    }

    /**
     * Automatic check used on app launch: runs every launch (the check is a
     * single tiny JSON fetch, a few KB). Respects the user toggle and skips
     * versions the user dismissed. Silent when there is nothing new.
     */
    suspend fun check(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val p = prefs(context)
        if (!p.getBoolean(KEY_AUTO_CHECK, true)) return@withContext null
        p.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
        fetchLatest(p, respectSkip = true)
    }

    /**
     * Manual check (Settings > About > App Updater > Check for update now):
     * ignores the throttle, the auto-check toggle and dismissed versions.
     */
    suspend fun checkNow(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val info = fetchLatest(prefs(context), respectSkip = false)
        prefs(context).edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
        info
    }

    private fun fetchLatest(p: SharedPreferences, respectSkip: Boolean): UpdateInfo? {
        return try {
            val conn = (URL(RELEASES_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("User-Agent", "PulseStream/${BuildConfig.VERSION_NAME}")
            }
            if (conn.responseCode != 200) return null
            val arr = JSONArray(conn.inputStream.bufferedReader().readText())
            if (arr.length() == 0) return null
            val latest = arr.getJSONObject(0)
            val version = latest.optString("tag_name", "").removePrefix("v")
            if (version.isBlank() || !isNewer(version, BuildConfig.VERSION_NAME)) return null
            if (respectSkip && p.getString(KEY_SKIPPED, null) == version) return null
            val apkUrl = latest.optJSONObject("assets")
                ?.optJSONArray("links")
                ?.let { links ->
                    (0 until links.length())
                        .map { links.getJSONObject(it) }
                        .firstOrNull { it.optString("url").endsWith(".apk", ignoreCase = true) }
                        ?.optString("url")
                }
                ?.takeIf { it.isNotBlank() }
                ?: "https://gitlab.com/api/v4/projects/87396069/packages/generic/plavio/$version/Plavio-v$version.apk"
            UpdateInfo(
                version = version,
                notes = latest.optString("description", "").trim(),
                apkUrl = apkUrl
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Remember that the user dismissed this version ("Later"). */
    fun skip(context: Context, version: String) {
        prefs(context).edit().putString(KEY_SKIPPED, version).apply()
    }

    internal fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val l = local.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
