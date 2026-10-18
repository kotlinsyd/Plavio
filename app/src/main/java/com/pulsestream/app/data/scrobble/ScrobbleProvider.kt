package com.pulsestream.app.data.scrobble

import com.pulsestream.app.data.PlaybackRequest

/**
 * A single scrobbling backend (Trakt, Simkl, …).
 *
 * Implementations own their auth state and network calls; failures inside
 * [scrobbleStart]/[scrobblePause]/[scrobbleStop] must never throw to the
 * caller — but the fan-out in [ScrobbleManager] isolates providers anyway,
 * so one backend can never break another.
 */
interface ScrobbleProvider {
    /** Human-readable name for logs, e.g. "Trakt". */
    val name: String

    /**
     * Whether this backend should receive scrobbles right now.
     * For Trakt/Simkl this is simply "user is logged in".
     */
    val isEnabled: Boolean

    suspend fun scrobbleStart(req: PlaybackRequest, posMs: Long, durMs: Long)
    suspend fun scrobblePause(req: PlaybackRequest, posMs: Long, durMs: Long)
    suspend fun scrobbleStop(req: PlaybackRequest, posMs: Long, durMs: Long)
}
