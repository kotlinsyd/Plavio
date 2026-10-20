package com.pulsestream.app.ui.screens.player

/** Only recover a mobile Live source that never became playable after a bounded startup window. */
internal const val MOBILE_LIVE_STARTUP_TIMEOUT_MS = 30_000L

internal fun shouldFailoverStalledLiveStartup(
    isLive: Boolean,
    isTv: Boolean,
    playWhenReady: Boolean,
    hasEverPlayed: Boolean,
    isBuffering: Boolean,
    elapsedMs: Long,
    timeoutMs: Long = MOBILE_LIVE_STARTUP_TIMEOUT_MS
): Boolean = isLive && !isTv && playWhenReady && !hasEverPlayed && isBuffering &&
    elapsedMs >= timeoutMs

/** Paused or ready time cannot exhaust the active startup-buffering budget. */
internal fun liveStartupWaitingSince(
    previousMs: Long?,
    nowMs: Long,
    playWhenReady: Boolean,
    isBuffering: Boolean
): Long? = if (playWhenReady && isBuffering) previousMs ?: nowMs else null

/** Different Live candidates have independent sliding windows, not VOD offsets. */
internal fun seekToPlaybackStart(
    isMobileLive: Boolean,
    startAtMs: Long,
    seekToPosition: (Long) -> Unit,
    seekToDefaultPosition: () -> Unit
) {
    if (isMobileLive) seekToDefaultPosition() else seekToPosition(startAtMs)
}


/** A separate bounded timeout for a Live stream that played successfully, then stalled. */
internal const val MOBILE_LIVE_REBUFFER_TIMEOUT_MS = 20_000L

internal fun shouldFailoverStalledLiveRebuffer(
    isLive: Boolean,
    isTv: Boolean,
    playWhenReady: Boolean,
    hasEverPlayed: Boolean,
    isBuffering: Boolean,
    elapsedMs: Long,
    timeoutMs: Long = MOBILE_LIVE_REBUFFER_TIMEOUT_MS
): Boolean = isLive && !isTv && playWhenReady && hasEverPlayed && isBuffering && elapsedMs >= timeoutMs
