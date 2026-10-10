package com.pulsestream.app.data.simkl

import android.os.SystemClock
import com.pulsestream.app.data.PlaybackRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Drives Simkl scrobbling from the player's 500ms poll loop.
 * All [tick] calls must happen on the main thread; network goes to [io].
 *
 * Behavior:
 * - stream/episode switch -> stop old, start new
 * - pause/resume -> pause/start
 * - playing -> progress refresh every 2 minutes
 * - playback ended -> stop at 100% (marks watched server-side)
 * - [shutdown] (player dispose) -> final stop
 */
class SimklScrobbler(
    private val simkl: SimklManager,
    private val io: CoroutineScope
) {
    private var activeUrl: String? = null
    private var activeReq: PlaybackRequest? = null
    private var lastPosMs = 0L
    private var lastDurMs = 0L
    private var lastProgressPushAt = 0L
    private var wasPlaying = false

    fun tick(req: PlaybackRequest, posMs: Long, durMs: Long, playing: Boolean, ended: Boolean) {
        if (!simkl.isLoggedIn || durMs <= 0) return
        if (req.streamUrl != activeUrl) {
            // New stream: finalize the previous one, start the new session.
            val prev = activeReq
            val prevPos = lastPosMs
            val prevDur = lastDurMs
            activeUrl = req.streamUrl
            activeReq = req
            lastPosMs = posMs
            lastDurMs = durMs
            wasPlaying = playing
            lastProgressPushAt = SystemClock.elapsedRealtime()
            io.launch {
                if (prev != null && prevDur > 0) simkl.scrobbleStop(prev, prevPos, prevDur)
                simkl.scrobbleStart(req, posMs, durMs)
            }
            return
        }
        if (ended) {
            io.launch { simkl.scrobbleStop(req, durMs, durMs) }
            reset()
            return
        }
        lastPosMs = posMs
        lastDurMs = durMs
        when {
            playing && !wasPlaying -> {
                lastProgressPushAt = SystemClock.elapsedRealtime()
                io.launch { simkl.scrobbleStart(req, posMs, durMs) }
            }
            !playing && wasPlaying -> {
                io.launch { simkl.scrobblePause(req, posMs, durMs) }
            }
            playing && SystemClock.elapsedRealtime() - lastProgressPushAt > 120_000 -> {
                lastProgressPushAt = SystemClock.elapsedRealtime()
                io.launch { simkl.scrobbleStart(req, posMs, durMs) }
            }
        }
        wasPlaying = playing
    }

    fun shutdown() {
        val req = activeReq ?: return
        val pos = lastPosMs
        val dur = lastDurMs
        reset()
        if (dur > 0) io.launch { simkl.scrobbleStop(req, pos, dur) }
    }

    private fun reset() {
        activeUrl = null
        activeReq = null
        lastPosMs = 0L
        lastDurMs = 0L
        wasPlaying = false
    }
}
