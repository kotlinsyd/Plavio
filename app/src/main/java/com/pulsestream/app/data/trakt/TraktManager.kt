package com.pulsestream.app.data.trakt

import android.os.SystemClock
import com.pulsestream.app.data.PlaybackRequest
import com.pulsestream.app.data.SettingsStore
import com.pulsestream.app.data.scrobble.ScrobbleProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * Owns the Trakt link: PKCE login, token storage/refresh, and scrobble calls.
 * All network work happens on the caller's thread (call from IO).
 * Failures are swallowed — sync must never break playback.
 *
 * Implements [ScrobbleProvider] so the player's unified [com.pulsestream.app.data.scrobble.ScrobbleManager]
 * can fan scrobble events out to Trakt alongside other backends.
 */
class TraktManager(private val settings: SettingsStore) : ScrobbleProvider {

    override val name: String = "Trakt"
    override val isEnabled: Boolean get() = isLoggedIn

    private val api = TraktApi()
    private val scrobbleMutex = Mutex()
    private var lastScrobbleAt = 0L
    private val refreshMutex = Mutex()

    val isLoggedIn: Boolean get() = settings.traktAccessToken != null
    val username: String? get() = settings.traktUsername

    fun newPkce(): TraktApi.Pkce = api.newPkce()
    fun authorizeUrl(pkce: TraktApi.Pkce): String = api.authorizeUrl(pkce)

    /** Exchanges the user-pasted code for tokens and stores them. Returns false on failure. */
    suspend fun loginWithCode(code: String, verifier: String): Boolean {
        return try {
            val tokens = api.exchangeCode(code, verifier)
            val name = api.getUsername(tokens.accessToken)
            saveTokens(tokens)
            settings.traktUsername = name
            true
        } catch (_: Exception) {
            false
        }
    }

    fun logout() {
        settings.traktAccessToken = null
        settings.traktRefreshToken = null
        settings.traktTokenExpiry = 0L
        settings.traktUsername = null
    }

    private fun saveTokens(tokens: TraktApi.Tokens) {
        settings.traktAccessToken = tokens.accessToken
        settings.traktRefreshToken = tokens.refreshToken
        // Refresh a minute before actual expiry.
        settings.traktTokenExpiry =
            System.currentTimeMillis() + (tokens.expiresInSec - 60) * 1000L
    }

    /** Returns a valid access token, refreshing first if needed. Null when logged out. */
    private suspend fun validToken(): String? {
        val token = settings.traktAccessToken ?: return null
        if (System.currentTimeMillis() < settings.traktTokenExpiry) return token
        return refreshMutex.withLock {
            // Re-check inside the lock; another call may have refreshed already.
            val current = settings.traktAccessToken ?: return@withLock null
            if (System.currentTimeMillis() < settings.traktTokenExpiry) return@withLock current
            val refresh = settings.traktRefreshToken ?: run {
                logout()
                return@withLock null
            }
            try {
                val tokens = api.refreshToken(refresh)
                saveTokens(tokens)
                tokens.accessToken
            } catch (_: Exception) {
                logout()
                null
            }
        }
    }

    /** Valid access token for non-scrobble features (library sync). Null when logged out. */
    suspend fun validAccessToken(): String? = validToken()

    // ---- Scrobble ----

    override suspend fun scrobbleStart(req: PlaybackRequest, posMs: Long, durMs: Long) =
        throttled("start", req, posMs, durMs)

    override suspend fun scrobblePause(req: PlaybackRequest, posMs: Long, durMs: Long) =
        throttled("pause", req, posMs, durMs)

    override suspend fun scrobbleStop(req: PlaybackRequest, posMs: Long, durMs: Long) =
        throttled("stop", req, posMs, durMs)

    /**
     * Trakt rate-limits scrobbles; keep at least ~10s between calls.
     * Non-critical events inside the window are dropped; "stop" waits it out
     * so the watch is never lost.
     */
    private suspend fun throttled(action: String, req: PlaybackRequest, posMs: Long, durMs: Long) {
        if (durMs <= 0) return
        scrobbleMutex.withLock {
            val waitMs = 10_000L - (SystemClock.elapsedRealtime() - lastScrobbleAt)
            if (waitMs > 0) {
                if (action != "stop") return
                kotlinx.coroutines.delay(waitMs)
            }
            val token = validToken() ?: return
            try {
                when (api.scrobble(token, action, scrobbleBody(req, posMs, durMs))) {
                    TraktApi.ScrobbleResult.Ok -> lastScrobbleAt = SystemClock.elapsedRealtime()
                    TraktApi.ScrobbleResult.Unauthorized -> logout()
                    TraktApi.ScrobbleResult.Failed -> { /* try again next time */ }
                }
            } catch (_: Exception) {
                // Telemetry must never crash playback.
            }
        }
    }

    private fun scrobbleBody(req: PlaybackRequest, posMs: Long, durMs: Long): JSONObject {
        val progress = (posMs.toDouble() / durMs.toDouble() * 100.0).coerceIn(0.0, 100.0)
        val ids = JSONObject().apply {
            req.imdbId?.takeIf { it.isNotBlank() }?.let { put("imdb", it) }
        }
        return JSONObject().apply {
            put("progress", progress)
            if (req.isMovie) {
                put("movie", JSONObject().put("title", req.title).put("ids", ids))
            } else {
                put("show", JSONObject().put("title", req.seriesName ?: req.title).put("ids", ids))
                put("episode", JSONObject().put("season", req.season).put("number", req.episode))
            }
        }
    }
}
