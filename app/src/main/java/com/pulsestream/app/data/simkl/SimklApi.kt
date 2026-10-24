package com.pulsestream.app.data.simkl

import com.pulsestream.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Raw HTTP client for the Simkl API (https://api.simkl.com).
 * Uses the OAuth 2.0 Device Authorization Grant (RFC 8628):
 * POST /oauth2/device -> user approves at simkl.com/pin -> POST /oauth2/token.
 */
class SimklApi {

    companion object {
        const val CLIENT_ID = "c1cbe532ad5f4224a0ffe1da154d321c7656ab23bd7f8bf45d77844fca42ad38"
        private const val BASE = "https://api.simkl.com"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    private val userAgent = "PulseStream/${BuildConfig.VERSION_NAME}"

    data class DeviceData(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val verificationUriComplete: String,
        val intervalSec: Int,
        val expiresInSec: Int
    )

    sealed interface PollResult {
        data class Approved(val accessToken: String) : PollResult
        data object Pending : PollResult
        data object SlowDown : PollResult
        data object Expired : PollResult
        data object Failed : PollResult
    }

    private val client = OkHttpClient.Builder()
        .callTimeout(25, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Step 1 of device auth: get a user code to show in the app. */
    suspend fun requestDeviceCode(): DeviceData = withContext(Dispatchers.IO) {
        val form = FormBody.Builder().add("client_id", CLIENT_ID).build()
        val request = Request.Builder()
            .url("$BASE/oauth2/device")
            .header("User-Agent", userAgent)
            .post(form)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Simkl device request failed (HTTP ${response.code})")
            val bodyStr = response.body?.string() ?: throw IllegalStateException("Empty response")
            val json = JSONObject(bodyStr)
            DeviceData(
                deviceCode = json.getString("device_code"),
                userCode = json.getString("user_code"),
                verificationUri = json.optString("verification_uri", "https://simkl.com/pin"),
                verificationUriComplete = json.optString(
                    "verification_uri_complete",
                    "https://simkl.com/pin"
                ),
                intervalSec = json.optInt("interval", 5).coerceAtLeast(3),
                expiresInSec = json.optInt("expires_in", 900)
            )
        }
    }

    /**
     * Step 2 of device auth: poll until the user approves the code.
     */
    suspend fun pollDeviceToken(deviceCode: String): PollResult = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
            .add("client_id", CLIENT_ID)
            .add("device_code", deviceCode)
            .build()
        val request = Request.Builder()
            .url("$BASE/oauth2/token")
            .header("User-Agent", userAgent)
            .post(form)
            .build()
        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string() ?: return@withContext PollResult.Failed
            val json = JSONObject(bodyStr)
            if (response.isSuccessful) {
                val token = json.optString("access_token", "")
                return@withContext if (token.isNotEmpty()) PollResult.Approved(token)
                else PollResult.Failed
            }
            when (json.optString("error")) {
                "authorization_pending" -> PollResult.Pending
                "slow_down" -> PollResult.SlowDown
                "expired_token", "access_denied" -> PollResult.Expired
                else -> PollResult.Failed
            }
        }
    }

    /** Fetch the linked account's display name. */
    suspend fun getUsername(token: String): String? = withContext(Dispatchers.IO) {
        val request = authedRequest(token, "$BASE/users/settings").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val bodyStr = response.body?.string() ?: return@withContext null
            val json = JSONObject(bodyStr)
            json.optJSONObject("user")?.optString("name", null)
        }
    }

    /**
     * Send a scrobble event. [action] is one of "start", "pause", "stop".
     * @return true if the server accepted it.
     */
    suspend fun scrobble(token: String, action: String, body: JSONObject): Boolean =
        withContext(Dispatchers.IO) {
            val request = authedRequest(token, "$BASE/scrobble/$action")
                .post(body.toString().toRequestBody(JSON))
                .build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        }

    // ---- Library lists (library sync, issue #3) ----

    /** One Simkl library entry, IMDb-keyed. [type] is "movie" or "series". */
    data class ListEntry(val type: String, val imdbId: String, val title: String)

    /**
     * The user's Simkl library — Plan to Watch + Watching only (Completed /
     * On Hold / Dropped are not "library" in Plavio's sense). Throws on
     * failure so the sync can tell "empty" from "unreachable".
     */
    suspend fun getAllItems(token: String): List<ListEntry> = withContext(Dispatchers.IO) {
        val request = authedRequest(token, "$BASE/sync/all-items").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Simkl library failed (HTTP ${response.code})")
            }
            val root = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
            val out = mutableListOf<ListEntry>()
            fun collect(section: String, type: String, nodeName: String) {
                val arr = root.optJSONArray(section) ?: return
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val status = item.optString("status")
                    if (status != "plantowatch" && status != "watching") continue
                    val node = item.optJSONObject(nodeName) ?: continue
                    val imdb = node.optJSONObject("ids")?.optString("imdb")
                        ?.takeIf { it.startsWith("tt") } ?: continue
                    out += ListEntry(type, imdb, node.optString("title"))
                }
            }
            collect("movies", "movie", "movie")
            collect("shows", "series", "show")
            collect("anime", "series", "anime")
            out
        }
    }

    /** One show the user is currently watching on Simkl. */
    data class WatchingEntry(
        val imdbId: String,
        val title: String,
        val watchedEpisodes: Int?,
        val totalEpisodes: Int?,
        val lastWatchedAt: String?
    )

    /** Shows with Simkl status "watching" (the Library screen's Up Next input). */
    suspend fun getWatching(token: String): List<WatchingEntry> = withContext(Dispatchers.IO) {
        val request = authedRequest(token, "$BASE/sync/all-items").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Simkl library failed (HTTP ${response.code})")
            }
            val root = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
            val out = mutableListOf<WatchingEntry>()
            fun collect(section: String, nodeName: String) {
                val arr = root.optJSONArray(section) ?: return
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    if (item.optString("status") != "watching") continue
                    val node = item.optJSONObject(nodeName) ?: continue
                    val imdb = node.optJSONObject("ids")?.optString("imdb")
                        ?.takeIf { it.startsWith("tt") } ?: continue
                    out += WatchingEntry(
                        imdbId = imdb,
                        title = node.optString("title"),
                        watchedEpisodes = item.optInt("watched_episodes_count", -1).takeIf { it >= 0 },
                        totalEpisodes = item.optInt("total_episodes_count", -1).takeIf { it > 0 },
                        lastWatchedAt = item.optString("last_watched_at").takeIf { it.isNotBlank() }
                    )
                }
            }
            collect("shows", "show")
            collect("anime", "anime")
            out
        }
    }

    /** Plan-to-Watch items only (the Library screen's Simkl watchlist row). */
    suspend fun getPlanToWatch(token: String): List<ListEntry> = withContext(Dispatchers.IO) {
        val request = authedRequest(token, "$BASE/sync/all-items").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Simkl library failed (HTTP ${response.code})")
            }
            val root = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
            val out = mutableListOf<ListEntry>()
            fun collect(section: String, type: String, nodeName: String) {
                val arr = root.optJSONArray(section) ?: return
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    if (item.optString("status") != "plantowatch") continue
                    val node = item.optJSONObject(nodeName) ?: continue
                    val imdb = node.optJSONObject("ids")?.optString("imdb")
                        ?.takeIf { it.startsWith("tt") } ?: continue
                    out += ListEntry(type, imdb, node.optString("title"))
                }
            }
            collect("movies", "movie", "movie")
            collect("shows", "series", "show")
            collect("anime", "series", "anime")
            out
        }
    }

    /** Adds entries to the Simkl library as Plan to Watch (= tracked on Simkl). Best-effort. */
    suspend fun addToList(token: String, entries: List<ListEntry>): Boolean =
        postList(token, "$BASE/sync/add-to-list", entries, withStatus = true)

    /** Removes entries from the Simkl library. Best-effort. */
    suspend fun removeFromList(token: String, entries: List<ListEntry>): Boolean =
        postList(token, "$BASE/sync/remove-from-list", entries, withStatus = false)

    private suspend fun postList(
        token: String,
        url: String,
        entries: List<ListEntry>,
        withStatus: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        if (entries.isEmpty()) return@withContext true
        val movies = org.json.JSONArray()
        val shows = org.json.JSONArray()
        entries.forEach { e ->
            val obj = JSONObject().put("ids", JSONObject().put("imdb", e.imdbId))
            if (withStatus) obj.put("to", "plantowatch")
            if (e.type == "movie") movies.put(obj) else shows.put(obj)
        }
        val body = JSONObject()
        if (movies.length() > 0) body.put("movies", movies)
        if (shows.length() > 0) body.put("shows", shows)
        val request = authedRequest(token, url)
            .post(body.toString().toRequestBody(JSON))
            .build()
        try {
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    private fun authedRequest(token: String, url: String): Request.Builder =
        Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
            .header("simkl-api-key", CLIENT_ID)
}
