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

    private fun base64Url(bytes: ByteArray): String =
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
