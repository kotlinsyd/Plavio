package com.pulsestream.app.ui.screens.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerStartupTest {
    @Test
    fun playerStartsPreparingBeforeSavedPositionLookupCanSuspend() = runBlocking {
        val events = mutableListOf<String>()
        val savedPosition = CompletableDeferred<Long>()
        val startup = async(start = CoroutineStart.UNDISPATCHED) {
            preparePlayerBeforeSavedPositionLookup(
                preparePlayer = { events += "prepare" },
                loadSavedPosition = {
                    events += "load-position"
                    savedPosition.await()
                }
            )
        }

        assertEquals(listOf("prepare", "load-position"), events)
        assertFalse(startup.isCompleted)
        savedPosition.complete(42_000L)
        assertEquals(42_000L, startup.await())
    }

    @Test
    fun staleResumeUrlDoesNotPreloadWhileFreshSourceResolutionMayBeNeeded() = runBlocking {
        val events = mutableListOf<String>()
        val saved = preparePlayerBeforeSavedPositionLookup(
            preparePlayer = { events += "prepare-old-url" },
            loadSavedPosition = { events += "load-position"; 42_000L },
            prepareEarly = false
        )
        assertEquals(listOf("load-position"), events)
        assertEquals(42_000L, saved)
    }

    @Test
    fun mobileLiveStartupWatchdogOnlyFiresAfterTimeoutWhileWaitingToPlay() {
        assertFalse(shouldFailoverStalledLiveStartup(
            isLive = true, isTv = false, playWhenReady = true, hasEverPlayed = false,
            isBuffering = true, elapsedMs = MOBILE_LIVE_STARTUP_TIMEOUT_MS - 1
        ))
        assertTrue(shouldFailoverStalledLiveStartup(
            isLive = true, isTv = false, playWhenReady = true, hasEverPlayed = false,
            isBuffering = true, elapsedMs = MOBILE_LIVE_STARTUP_TIMEOUT_MS
        ))
    }

    @Test
    fun mobileLiveStartupWatchdogNeverInterruptsVODTVPausedOrPreviouslyHealthyPlayback() {
        val elapsed = MOBILE_LIVE_STARTUP_TIMEOUT_MS * 2
        fun check(
            isLive: Boolean = true,
            isTv: Boolean = false,
            playWhenReady: Boolean = true,
            hasEverPlayed: Boolean = false,
            isBuffering: Boolean = true
        ) = shouldFailoverStalledLiveStartup(
            isLive, isTv, playWhenReady, hasEverPlayed, isBuffering, elapsed
        )

        assertFalse(check(isLive = false))
        assertFalse(check(isTv = true))
        assertFalse(check(playWhenReady = false))
        assertFalse(check(hasEverPlayed = true))
        assertFalse(check(isBuffering = false))
    }

    @Test
    fun mobileLiveRebufferWatchdogRecoversOnlyAfterSustainedBufferingAfterPlayback() {
        assertFalse(shouldFailoverStalledLiveRebuffer(
            isLive = true, isTv = false, playWhenReady = true, hasEverPlayed = true,
            isBuffering = true, elapsedMs = MOBILE_LIVE_REBUFFER_TIMEOUT_MS - 1
        ))
        assertTrue(shouldFailoverStalledLiveRebuffer(
            isLive = true, isTv = false, playWhenReady = true, hasEverPlayed = true,
            isBuffering = true, elapsedMs = MOBILE_LIVE_REBUFFER_TIMEOUT_MS
        ))
    }

    @Test
    fun mobileLiveRebufferWatchdogDoesNotInterruptPausedVodTvOrStartup() {
        val elapsed = MOBILE_LIVE_REBUFFER_TIMEOUT_MS * 2
        fun check(
            isLive: Boolean = true,
            isTv: Boolean = false,
            playWhenReady: Boolean = true,
            hasEverPlayed: Boolean = true,
            isBuffering: Boolean = true
        ) = shouldFailoverStalledLiveRebuffer(
            isLive, isTv, playWhenReady, hasEverPlayed, isBuffering, elapsed
        )
        assertFalse(check(isLive = false))
        assertFalse(check(isTv = true))
        assertFalse(check(playWhenReady = false))
        assertFalse(check(hasEverPlayed = false))
        assertFalse(check(isBuffering = false))
    }

    @Test
    fun mobileLiveUsesDefaultLiveEdgeInsteadOfOldCandidateOffset() {
        val events = mutableListOf<String>()
        seekToPlaybackStart(true, 120_000L,
            seekToPosition = { events += "position:$it" },
            seekToDefaultPosition = { events += "live-edge" })
        assertEquals(listOf("live-edge"), events)
    }

    @Test
    fun nonMobileLivePlaybackPreservesExistingResumeOffset() {
        val events = mutableListOf<String>()
        seekToPlaybackStart(false, 120_000L,
            seekToPosition = { events += "position:$it" },
            seekToDefaultPosition = { events += "live-edge" })
        assertEquals(listOf("position:120000"), events)
    }

    @Test
    fun pausingOrBecomingReadyResetsTheActiveStartupWait() {
        assertEquals(1_000L, liveStartupWaitingSince(null, 1_000L, true, true))
        assertEquals(1_000L, liveStartupWaitingSince(1_000L, 2_000L, true, true))
        assertEquals(null, liveStartupWaitingSince(1_000L, 50_000L, false, true))
        assertEquals(51_000L, liveStartupWaitingSince(null, 51_000L, true, true))
        assertEquals(null, liveStartupWaitingSince(51_000L, 52_000L, true, false))
    }
}
