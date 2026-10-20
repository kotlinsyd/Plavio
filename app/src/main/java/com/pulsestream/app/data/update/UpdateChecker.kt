package com.pulsestream.app.data.update

import android.content.Context
import android.content.SharedPreferences
import com.pulsestream.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/** Info about a newer release found via the stable pointer. */
data class UpdateInfo(
    val version: String,
    val notes: String,
    val apkUrl: String,
    /** True when Najam marked this as a "major update" — auto-popup shows. False = silent, only visible on manual check. */
    val isMajor: Boolean = false
)

/**
 * Checks the stable pointer (stable.json in the repo) for a version newer
 * than the installed one. Only versions Najam marks "stable" notify users —
 * dev releases pushed to GitLab stay silent. Silent on any failure.
 */
object UpdateChecker {
    private const val STABLE_URL = "https://gitlab.com/KotlinJS5/plavio/-/raw/main/stable.json"
    private const val PREFS = "pulse_update"
    private const val KEY_LAST_CHECK = "last_check_ms"
    private const val KEY_SKIPPED = "skipped_version"
    private const val KEY_AUTO_CHECK = "auto_check"
    /** Automatic launch checks are throttled — at most one per day. */
    private const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

    /** Outcome of a manual update check, so the UI can tell "offline" from "up to date". */
    sealed interface UpdateCheckResult {
        data class Update(val info: UpdateInfo) : UpdateCheckResult
        data object UpToDate : UpdateCheckResult
        data object Offline : UpdateCheckResult
        data class Error(val message: String) : UpdateCheckResult
    }

    /** A check that failed for a classifiable reason (vs. "no update"). */
    private class UpdateCheckFailure(message: String, val offline: Boolean) : Exception(message)

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** User toggle: show the update popup automatically (Settings > About > App Updater). */
    fun isAutoCheckEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_CHECK, true)

    fun setAutoCheckEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_CHECK, enabled).apply()
    }

    /**
     * Automatic check used on app launch: throttled to once a day (the
     * pointer fetch is a few KB, but there's no reason to hit it on every
     * launch). Respects the user toggle and skips versions the user
     * dismissed. Silent when there is nothing new.
     *
     * MAJOR-ONLY: Only pops up for updates marked "major" in stable.json.
     * Regular updates are silent — users see them only via manual check.
     */
    suspend fun check(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val p = prefs(context)
        if (!p.getBoolean(KEY_AUTO_CHECK, true)) return@withContext null
        val last = p.getLong(KEY_LAST_CHECK, 0L)
        if (last > 0 && System.currentTimeMillis() - last < CHECK_INTERVAL_MS) {
            return@withContext null
        }
        p.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
        val info = runCatching { fetchLatestOrThrow(p, respectSkip = true) }.getOrNull()
        // Only auto-notify for major updates
        if (info?.isMajor == true) info else null
    }

    /**
     * Manual check (Settings > About > App Updater > Check for update now):
     * ignores the throttle, the auto-check toggle and dismissed versions.
     */
    suspend fun checkNow(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        (checkNowDetailed(context) as? UpdateCheckResult.Update)?.info
    }

    /**
     * Manual check with the outcome classified: Update / UpToDate / Offline /
     * Error. A null from [checkNow] used to mean all of "up to date",
     * "offline" and "server hiccup" at once.
     */
    suspend fun checkNowDetailed(context: Context): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            val p = prefs(context)
            try {
                val info = fetchLatestOrThrow(p, respectSkip = false)
                p.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
                if (info != null) UpdateCheckResult.Update(info)
                else UpdateCheckResult.UpToDate
            } catch (e: UpdateCheckFailure) {
                if (e.offline) UpdateCheckResult.Offline
                else UpdateCheckResult.Error(e.message ?: "Couldn't check for updates")
            } catch (e: Exception) {
                UpdateCheckResult.Error(e.message ?: "Couldn't check for updates")
            }
        }

    private fun fetchLatestOrThrow(
        p: SharedPreferences,
        respectSkip: Boolean
    ): UpdateInfo? {
        val conn = (URL(STABLE_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("User-Agent", "Plavio/${BuildConfig.VERSION_NAME}")
        }
        val info = try {
            if (conn.responseCode != 200) throw UpdateCheckFailure(
                "The update server answered ${conn.responseCode}", offline = false)
            if (conn.contentLengthLong > 65536) throw UpdateCheckFailure("Update manifest is too large", false)
            val bytes = conn.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (output.size() + n > 65536) throw UpdateCheckFailure("Update manifest is too large", false)
                    output.write(buffer, 0, n)
                }
                output.toByteArray()
            }
            try { MobileReleaseManifest.parse(JSONObject(bytes.toString(Charsets.UTF_8))) }
            catch (_: Exception) { throw UpdateCheckFailure("Invalid stable mobile release metadata", false) }
        } catch (e: UpdateCheckFailure) {
            throw e
        } catch (e: Exception) {
            throw UpdateCheckFailure("Couldn't reach the update server", offline = isOffline(e))
        } finally {
            conn.disconnect()
        }
        if (!isNewer(info.version, BuildConfig.VERSION_NAME)) return null
        if (respectSkip && p.getString(KEY_SKIPPED, null) == info.version) return null
        return info
    }

    private fun isOffline(e: Throwable): Boolean =
        e is UnknownHostException ||
            e is ConnectException ||
            e is NoRouteToHostException ||
            e is SocketTimeoutException

    /** Remember that the user dismissed this version ("Later"). */
    fun skip(context: Context, version: String) {
        prefs(context).edit().putString(KEY_SKIPPED, version).apply()
    }

    internal fun isNewer(remote: String, local: String): Boolean {
        // Leading-digit compare per component: "0.8.22-beta" compares as
        // 0.8.22 (the old toIntOrNull ?: 0 read "22-beta" as 0), and a
        // stray "V" prefix is stripped.
        fun parts(v: String) = v.trim().trimStart { it == 'v' || it == 'V' }
            .split(".").map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val r = parts(remote)
        val l = parts(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
