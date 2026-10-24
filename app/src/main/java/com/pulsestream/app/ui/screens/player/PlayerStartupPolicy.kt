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


/** VOD startup: a source that never becomes playable within the window fails over. */
internal const val VOD_STARTUP_STALL_TIMEOUT_MS = 25_000L

/** VOD mid-play: buffering with no position progress for this long fails over. */
internal const val VOD_REBUFFER_STALL_TIMEOUT_MS = 30_000L

/**
 * VOD stall rule (owner's player round, 2026-10-23): a movie/episode source
 * that never starts, or freezes with NO position progress — hosts that
 * ignore range requests behave exactly like this — fails over to the next
 * source instead of spinning forever. Progress, not buffering alone, is
 * the signal, so a slow-but-alive connection is never interrupted.
 */
internal fun shouldFailoverStalledVod(
    isLive: Boolean,
    playWhenReady: Boolean,
    hasEverPlayed: Boolean,
    isBuffering: Boolean,
    positionProgressed: Boolean,
    elapsedMs: Long,
    startupTimeoutMs: Long = VOD_STARTUP_STALL_TIMEOUT_MS,
    rebufferTimeoutMs: Long = VOD_REBUFFER_STALL_TIMEOUT_MS
): Boolean {
    if (isLive || !playWhenReady || !isBuffering || positionProgressed) return false
    val budget = if (hasEverPlayed) rebufferTimeoutMs else startupTimeoutMs
    return elapsedMs >= budget
}
