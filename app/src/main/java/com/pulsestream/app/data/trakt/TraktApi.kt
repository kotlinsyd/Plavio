package com.pulsestream.app.data.trakt

import com.pulsestream.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * Raw HTTP client for the Trakt API (https://api.trakt.tv).
 * Uses OAuth 2.0 Authorization Code flow with PKCE (no client secret):
 * app builds an authorize URL -> user approves in the browser ->
 * our callback page shows the code -> user pastes it into the app ->
 * POST /oauth/token exchanges it for tokens.
 */
class TraktApi {

    companion object {
        const val CLIENT_ID = "2j7KBLNBFVZG5qs6afdPGxbWyb23_93tX0aopgiDGSY"
        const val REDIRECT_URI = "https://plaviomedia.vercel.app/trakt-callback.html"
        private const val AUTHORIZE_URL = "https://trakt.tv/oauth/authorize"
        private const val TOKEN_URL = "https://api.trakt.tv/oauth/token"
        private const val API_BASE = "https://api.trakt.tv"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    private val userAgent = "Plavio/${BuildConfig.VERSION_NAME}"

    data class Pkce(val verifier: String, val challenge: String, val state: String)

    data class Tokens(
        val accessToken: String,
        val refreshToken: String,
        val expiresInSec: Long
    )

    private val client = OkHttpClient.Builder()
        .callTimeout(25, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Generates a PKCE verifier/challenge pair plus a state token. */
    fun newPkce(): Pkce {
        val random = SecureRandom()
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        val verifier = buildString {
            repeat(64) { append(chars[random.nextInt(chars.length)]) }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        val challenge = base64Url(digest)
        val state = buildString {
            repeat(16) { append(chars[random.nextInt(62)]) }
        }
        return Pkce(verifier, challenge, state)
    }

    /** The URL the user opens in a browser to approve Plavio. */
    fun authorizeUrl(pkce: Pkce): String {
        val params = listOf(
            "response_type" to "code",
            "client_id" to CLIENT_ID,
            "redirect_uri" to REDIRECT_URI,
            "state" to pkce.state,
            "code_challenge" to pkce.challenge,
            "code_challenge_method" to "S256"
        ).joinToString("&") { (k, v) -> "$k=${java.net.URLEncoder.encode(v, "UTF-8")}" }
        return "$AUTHORIZE_URL?$params"
    }

    /** Exchanges the pasted authorization code for tokens. */
    suspend fun exchangeCode(code: String, verifier: String): Tokens = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("code", code.trim())
            .put("client_id", CLIENT_ID)
            .put("redirect_uri", REDIRECT_URI)
            .put("grant_type", "authorization_code")
            .put("code_verifier", verifier)
            .toString()
            .toRequestBody(JSON)
        val request = Request.Builder()
            .url(TOKEN_URL)
            .header("User-Agent", userAgent)
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException("Trakt code exchange failed (HTTP ${response.code})")
            val json = JSONObject(text)
            Tokens(
                accessToken = json.getString("access_token"),
                refreshToken = json.getString("refresh_token"),
                expiresInSec = json.optLong("expires_in", 7_776_000L)
            )
        }
    }

    /** Refreshes an expired access token. */
    suspend fun refreshToken(refreshToken: String): Tokens = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("refresh_token", refreshToken)
            .put("client_id", CLIENT_ID)
            .put("redirect_uri", REDIRECT_URI)
            .put("grant_type", "refresh_token")
            .toString()
            .toRequestBody(JSON)
        val request = Request.Builder()
            .url(TOKEN_URL)
            .header("User-Agent", userAgent)
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException("Trakt refresh failed (HTTP ${response.code})")
            val json = JSONObject(text)
            Tokens(
                accessToken = json.getString("access_token"),
                refreshToken = json.optString("refresh_token", refreshToken),
                expiresInSec = json.optLong("expires_in", 7_776_000L)
            )
        }
    }

    /** Returns the Trakt username for the linked account, or null. */
    suspend fun getUsername(accessToken: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$API_BASE/users/settings")
            .header("User-Agent", userAgent)
            .header("trakt-api-version", "2")
            .header("trakt-api-key", CLIENT_ID)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val text = response.body?.string().orEmpty()
            JSONObject(text).optJSONObject("user")?.optString("username")?.takeIf { it.isNotBlank() }
        }
    }

    /**
     * Sends a scrobble event (start/pause/stop).
     * Returns true on success; false on auth failure (caller should refresh/unlink).
     */
    suspend fun scrobble(accessToken: String, action: String, body: JSONObject): ScrobbleResult =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$API_BASE/scrobble/$action")
                .header("User-Agent", userAgent)
                .header("trakt-api-version", "2")
                .header("trakt-api-key", CLIENT_ID)
                .header("Authorization", "Bearer $accessToken")
                .post(body.toString().toRequestBody(JSON))
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    when {
                        response.isSuccessful -> ScrobbleResult.Ok
                        response.code == 401 -> ScrobbleResult.Unauthorized
                        else -> ScrobbleResult.Failed
                    }
                }
            } catch (_: Exception) {
                ScrobbleResult.Failed
            }
        }

    enum class ScrobbleResult { Ok, Unauthorized, Failed }

    // ---- Watchlist (library sync, issue #3) ----

    /** One Trakt watchlist entry, IMDb-keyed. [type] is "movie" or "series". */
    data class WatchlistEntry(val type: String, val imdbId: String, val title: String)

    private fun authed(accessToken: String): Request.Builder = Request.Builder()
        .header("User-Agent", userAgent)
        .header("trakt-api-version", "2")
        .header("trakt-api-key", CLIENT_ID)
        .header("Authorization", "Bearer $accessToken")

    /** The user's whole Trakt watchlist (movies + shows). Throws on failure. */
    suspend fun getWatchlist(accessToken: String): List<WatchlistEntry> = withContext(Dispatchers.IO) {
        val out = mutableListOf<WatchlistEntry>()
        val sections = listOf(
            Triple("movies", "movie", "movie"),
            Triple("shows", "series", "show")
        )
        for ((path, type, nodeName) in sections) {
            val request = authed(accessToken).url("$API_BASE/users/me/watchlist/$path").get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Trakt watchlist failed (HTTP ${response.code})")
                }
                val arr = org.json.JSONArray(response.body?.string().orEmpty().ifBlank { "[]" })
                for (i in 0 until arr.length()) {
                    val node = arr.optJSONObject(i)?.optJSONObject(nodeName) ?: continue
                    val imdb = node.optJSONObject("ids")?.optString("imdb")
                        ?.takeIf { it.startsWith("tt") } ?: continue
                    out += WatchlistEntry(type, imdb, node.optString("title"))
                }
            }
        }
        out
    }

    /** Adds entries to the Trakt watchlist. Best-effort. */
    suspend fun addToWatchlist(accessToken: String, entries: List<WatchlistEntry>): Boolean =
        syncWatchlist(accessToken, "watchlist", entries)

    /** Removes entries from the Trakt watchlist. Best-effort. */
    suspend fun removeFromWatchlist(accessToken: String, entries: List<WatchlistEntry>): Boolean =
        syncWatchlist(accessToken, "watchlist/remove", entries)

    private suspend fun syncWatchlist(
        accessToken: String,
        path: String,
        entries: List<WatchlistEntry>
    ): Boolean = withContext(Dispatchers.IO) {
        if (entries.isEmpty()) return@withContext true
        val movies = org.json.JSONArray()
        val shows = org.json.JSONArray()
        entries.forEach { e ->
            val obj = JSONObject().put("ids", JSONObject().put("imdb", e.imdbId))
            if (e.type == "movie") movies.put(obj) else shows.put(obj)
        }
        val body = JSONObject()
        if (movies.length() > 0) body.put("movies", movies)
        if (shows.length() > 0) body.put("shows", shows)
        val request = authed(accessToken).url("$API_BASE/sync/$path")
            .post(body.toString().toRequestBody(JSON)).build()
        try {
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    /** One show the user is mid-way through, with the next episode to watch. */
    data class UpNextEntry(
        val imdbId: String,
        val title: String,
        val lastSeason: Int,
        val lastEpisode: Int,
        val nextSeason: Int,
        val nextEpisode: Int,
        val nextTitle: String?,
        val watchedCount: Int,
        val airedCount: Int,
        val lastWatchedAtMs: Long
    )

    private data class NextEp(val season: Int, val episode: Int, val title: String?, val airedTotal: Int)

    /**
     * "Up next" from Trakt watched progress: shows with at least one
     * watched episode and a later aired episode still unwatched.
     * sync/watched/shows gives the watched sets; each candidate show's
     * episode list is then checked for the first aired unwatched one.
     * Per-show failures are skipped — one bad show never sinks the list.
     */
    suspend fun getUpNext(accessToken: String, limit: Int = 12): List<UpNextEntry> =
        withContext(Dispatchers.IO) {
            val request = authed(accessToken).url("$API_BASE/sync/watched/shows").get().build()
            data class Candidate(
                val imdb: String, val title: String,
                val watched: Set<Pair<Int, Int>>, val lastMs: Long
            )
            val candidates = mutableListOf<Candidate>()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Trakt watched failed (HTTP ${response.code})")
                }
                val arr = org.json.JSONArray(response.body?.string().orEmpty().ifBlank { "[]" })
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val show = item.optJSONObject("show") ?: continue
                    val imdb = show.optJSONObject("ids")?.optString("imdb")
                        ?.takeIf { it.startsWith("tt") } ?: continue
                    val lastMs = runCatching {
                        java.time.Instant.parse(item.optString("last_watched_at")).toEpochMilli()
                    }.getOrDefault(0L)
                    val watched = mutableSetOf<Pair<Int, Int>>()
                    val seasons = item.optJSONArray("seasons")
                    if (seasons != null) for (s in 0 until seasons.length()) {
                        val season = seasons.optJSONObject(s) ?: continue
                        val sNum = season.optInt("number")
                        if (sNum == 0) continue
                        val eps = season.optJSONArray("episodes")
                        if (eps != null) for (e in 0 until eps.length()) {
                            val ep = eps.optJSONObject(e) ?: continue
                            watched += sNum to ep.optInt("number")
                        }
                    }
                    if (watched.isNotEmpty()) {
                        candidates += Candidate(imdb, show.optString("title"), watched, lastMs)
                    }
                }
            }
            val out = mutableListOf<UpNextEntry>()
            for (c in candidates.sortedByDescending { it.lastMs }.take(limit)) {
                val next = runCatching { nextAiredEpisode(accessToken, c.imdb, c.watched) }
                    .getOrNull() ?: continue
                val last = c.watched.maxWith(compareBy({ it.first }, { it.second }))
                out += UpNextEntry(
                    imdbId = c.imdb, title = c.title,
                    lastSeason = last.first, lastEpisode = last.second,
                    nextSeason = next.season, nextEpisode = next.episode,
                    nextTitle = next.title,
                    watchedCount = c.watched.size, airedCount = next.airedTotal,
                    lastWatchedAtMs = c.lastMs
                )
            }
            out
        }

    /** First aired episode (season >= 1) not in [watched], plus the aired total. */
    private fun nextAiredEpisode(
        accessToken: String, imdb: String, watched: Set<Pair<Int, Int>>
    ): NextEp? {
        val request = authed(accessToken)
            .url("$API_BASE/shows/$imdb/seasons?extended=episodes").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val arr = org.json.JSONArray(response.body?.string().orEmpty().ifBlank { "[]" })
            var aired = 0
            var found: NextEp? = null
            for (s in 0 until arr.length()) {
                val season = arr.optJSONObject(s) ?: continue
                val sNum = season.optInt("number")
                if (sNum == 0) continue
                val eps = season.optJSONArray("episodes") ?: continue
                for (e in 0 until eps.length()) {
                    val ep = eps.optJSONObject(e) ?: continue
                    if (ep.optString("first_aired").isBlank()) continue
                    aired++
                    val key = sNum to ep.optInt("number")
                    if (found == null && key !in watched) {
                        found = NextEp(
                            key.first, key.second,
                            ep.optString("title").takeIf { it.isNotBlank() }, 0
                        )
                    }
                }
            }
            val f = found ?: return null
            return NextEp(f.season, f.episode, f.title, aired)
        }
    }

    private fun base64Url(bytes: ByteArray): String =
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
