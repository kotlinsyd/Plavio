package com.pulsestream.app.data.scrobble

import android.os.SystemClock
import android.util.Log
import com.pulsestream.app.data.PlaybackRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Single scrobbling pipeline for the player.
 *
 * Replaces the per-service TraktScrobbler/SimklScrobbler pair: the player
 * calls [tick] once per poll and [shutdown] on dispose, and this fans every
 * scrobble event out to all [providers] that are currently enabled.
 *
 * Behavior (unchanged from the old per-service drivers):
 * - stream/episode switch -> stop old, start new
 * - pause/resume -> pause/start
 * - playing -> progress refresh every 2 minutes
 * - playback ended -> stop at 100% (marks watched server-side)
 * - [shutdown] (player dispose) -> final stop
 *
 * All [tick] calls must happen on the main thread; network goes to [io].
 * Each provider gets its own coroutine per event, so a slow backend
 * (e.g. waiting out its rate-limit window on "stop") never delays another.
 * A throwing provider is caught and logged — it can never break playback
 * or another provider.
 */
class ScrobbleManager(
    private val providers: List<ScrobbleProvider>,
    private val io: CoroutineScope
) {
    private var activeUrl: String? = null
    private var activeReq: PlaybackRequest? = null
    private var lastPosMs = 0L
    private var lastDurMs = 0L
    private var lastProgressPushAt = 0L
    private var wasPlaying = false

    /** Providers currently eligible to receive events. Snapshot per event. */
    private fun activeProviders(): List<ScrobbleProvider> =
        providers.filter {
            try {
                it.isEnabled
            } catch (e: Exception) {
                Log.w("ScrobbleManager", "isEnabled threw for a provider", e)
                false
            }
        }

    private fun fanOut(action: String, req: PlaybackRequest, posMs: Long, durMs: Long) {
        val targets = activeProviders()
        if (targets.isEmpty()) return
        for (p in targets) {
            io.launch {
                try {
                    when (action) {
                        "start" -> p.scrobbleStart(req, posMs, durMs)
                        "pause" -> p.scrobblePause(req, posMs, durMs)
                        "stop" -> p.scrobbleStop(req, posMs, durMs)
                    }
                } catch (e: Exception) {
                    // One backend must never break another (or playback).
                    Log.w("ScrobbleManager", "scrobble $action failed for ${p.name}", e)
                }
            }
        }
    }

    fun tick(req: PlaybackRequest, posMs: Long, durMs: Long, playing: Boolean, ended: Boolean) {
        if (durMs <= 0) return
        if (activeProviders().isEmpty()) return
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
            if (prev != null && prevDur > 0) {
                val targets = activeProviders()
                for (p in targets) {
                    io.launch {
                        try {
                            p.scrobbleStop(prev, prevPos, prevDur)
                            p.scrobbleStart(req, posMs, durMs)
                        } catch (e: Exception) {
                            Log.w("ScrobbleManager", "stream-switch scrobble failed for ${p.name}", e)
                        }
                    }
                }
            } else {
                fanOut("start", req, posMs, durMs)
            }
            return
        }
        if (ended) {
            fanOut("stop", req, durMs, durMs)
            reset()
            return
        }
        lastPosMs = posMs
        lastDurMs = durMs
        when {
            playing && !wasPlaying -> {
                lastProgressPushAt = SystemClock.elapsedRealtime()
                fanOut("start", req, posMs, durMs)
            }
            !playing && wasPlaying -> {
                fanOut("pause", req, posMs, durMs)
            }
            playing && SystemClock.elapsedRealtime() - lastProgressPushAt > 120_000 -> {
                lastProgressPushAt = SystemClock.elapsedRealtime()
                fanOut("start", req, posMs, durMs)
            }
        }
        wasPlaying = playing
    }

    fun shutdown() {
        val req = activeReq ?: return
        val pos = lastPosMs
        val dur = lastDurMs
        reset()
        if (dur > 0) fanOut("stop", req, pos, dur)
    }

    private fun reset() {
        activeUrl = null
        activeReq = null
        lastPosMs = 0L
        lastDurMs = 0L
        wasPlaying = false
    }
}
