package com.pulsestream.app.data.simkl

import android.os.SystemClock
import com.pulsestream.app.data.PlaybackRequest
import com.pulsestream.app.data.SettingsStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * Owns the Simkl link: token storage, PIN login, and scrobble calls.
 * All network work happens on the caller's thread (call from IO).
 * Failures are swallowed — sync must never break playback.
 */
class SimklManager(private val settings: SettingsStore) {

    private val api = SimklApi()
    private val scrobbleMutex = Mutex()
    private var lastScrobbleAt = 0L

    val isLoggedIn: Boolean get() = settings.simklToken != null
    val username: String? get() = settings.simklUsername

    // ---- Auth (OAuth 2.0 device flow) ----

    suspend fun requestDeviceCode(): SimklApi.DeviceData? =
        try { api.requestDeviceCode() } catch (_: Exception) { null }

    suspend fun pollDeviceToken(deviceCode: String): SimklApi.PollResult =
        try { api.pollDeviceToken(deviceCode) } catch (_: Exception) { SimklApi.PollResult.Failed }

    fun saveLogin(token: String, username: String?) {
        settings.simklToken = token
        settings.simklUsername = username
    }

    fun logout() {
        settings.simklToken = null
        settings.simklUsername = null
    }

    suspend fun fetchUsername(token: String): String? =
        try { api.getUsername(token) } catch (_: Exception) { null }

    // ---- Scrobble ----

    suspend fun scrobbleStart(req: PlaybackRequest, posMs: Long, durMs: Long) =
        throttled("start", req, posMs, durMs)

    suspend fun scrobblePause(req: PlaybackRequest, posMs: Long, durMs: Long) =
        throttled("pause", req, posMs, durMs)

    suspend fun scrobbleStop(req: PlaybackRequest, posMs: Long, durMs: Long) =
        throttled("stop", req, posMs, durMs)

    /**
     * Simkl enforces one scrobble per user per ~20s (400 RATE_LIMIT on collision).
     * Non-critical events inside the window are dropped; "stop" waits it out so
     * the watch is never lost.
     */
    private suspend fun throttled(action: String, req: PlaybackRequest, posMs: Long, durMs: Long) {
        val token = settings.simklToken ?: return
        if (durMs <= 0) return
        scrobbleMutex.withLock {
            val waitMs = 21_000L - (SystemClock.elapsedRealtime() - lastScrobbleAt)
            if (waitMs > 0) {
                if (action != "stop") return
                delay(waitMs)
            }
            try {
                if (api.scrobble(token, action, scrobbleBody(req, posMs, durMs))) {
                    lastScrobbleAt = SystemClock.elapsedRealtime()
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
