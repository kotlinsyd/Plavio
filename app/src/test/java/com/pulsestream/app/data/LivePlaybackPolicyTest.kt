package com.pulsestream.app.data

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePlaybackPolicyTest {
    @Test
    fun `mobile Live retries native IO failures beyond the old narrow error list`() {
        assertTrue(LivePlaybackPolicy.shouldRetry(isLive = true, isTv = false, errorCode = 2_000, attemptsSoFar = 0))
        assertTrue(LivePlaybackPolicy.shouldRetry(isLive = true, isTv = false, errorCode = 2_005, attemptsSoFar = 4))
    }

    @Test
    fun `mobile Live retries malformed and decoder errors`() {
        assertTrue(LivePlaybackPolicy.shouldRetry(
            isLive = true, isTv = false,
            errorCode = PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            attemptsSoFar = 0
        ))
        assertTrue(LivePlaybackPolicy.shouldRetry(
            isLive = true, isTv = false,
            errorCode = PlaybackException.ERROR_CODE_DECODING_FAILED,
            attemptsSoFar = 0
        ))
    }

    @Test
    fun `retry policy is capped and does not expand TV or non-Live behavior`() {
        assertEquals(8, LivePlaybackPolicy.MAX_MOBILE_LIVE_FAILOVERS)
        assertFalse(LivePlaybackPolicy.shouldRetry(
            isLive = true, isTv = false, errorCode = 2_000,
            attemptsSoFar = LivePlaybackPolicy.MAX_MOBILE_LIVE_FAILOVERS
        ))
        assertFalse(LivePlaybackPolicy.shouldRetry(isLive = true, isTv = true, errorCode = 2_000, attemptsSoFar = 0))
        assertFalse(LivePlaybackPolicy.shouldRetry(isLive = false, isTv = false, errorCode = 2_000, attemptsSoFar = 0))
        assertFalse(LivePlaybackPolicy.shouldRetry(isLive = true, isTv = false, errorCode = 4_000, attemptsSoFar = 0))
    }

    @Test
    fun `policy permits a fresh consecutive retry budget after playback resets the count`() {
        val cap = LivePlaybackPolicy.MAX_MOBILE_LIVE_FAILOVERS
        assertFalse(LivePlaybackPolicy.shouldRetry(
            isLive = true, isTv = false, errorCode = 2_000, attemptsSoFar = cap
        ))

        // The player resets its consecutive-failure count once playback succeeds.
        assertTrue(LivePlaybackPolicy.shouldRetry(
            isLive = true, isTv = false, errorCode = 2_000, attemptsSoFar = 0
        ))
    }

    @Test
    fun `candidate selection handles more than six rows without wrapping or repeating tried variants`() {
        val streams = (0..8).map { StreamItem("https://fixture.invalid/$it.m3u8") }
        assertEquals(streams[1], LivePlaybackPolicy.nextUntriedCandidate(streams, streams[0], emptySet()))
        assertEquals(streams[7], LivePlaybackPolicy.nextUntriedCandidate(
            streams, streams[5], setOf(LivePlaybackPolicy.candidateIdentity(streams[6]))
        ))
        assertNull(LivePlaybackPolicy.nextUntriedCandidate(streams, streams.last(), emptySet()))
        assertNull(LivePlaybackPolicy.nextUntriedCandidate(
            streams, streams[0], (1..8).map { LivePlaybackPolicy.candidateIdentity(streams[it]) }.toSet()
        ))
    }

    @Test
    fun `six failed sources still allow forward selection of the seventh source`() {
        val streams = (0..8).map { StreamItem("https://fixture.invalid/$it.m3u8") }
        var current = streams.first()
        var attempted = emptySet<String>()
        repeat(6) {
            attempted = attempted + LivePlaybackPolicy.candidateIdentity(current)
            current = requireNotNull(LivePlaybackPolicy.nextUntriedCandidate(streams, current, attempted))
        }
        assertEquals(streams[6], current)
        assertTrue(LivePlaybackPolicy.shouldRetry(
            isLive = true, isTv = false, errorCode = 2_000,
            attemptsSoFar = LivePlaybackPolicy.MAX_MOBILE_LIVE_FAILOVERS - 1
        ))
    }

    @Test
    fun `same URL with distinct headers is a distinct fallback candidate`() {
        val current = StreamItem("https://fixture.invalid/live.m3u8", headers = mapOf("Referer" to "https://one.invalid"))
        val variant = current.copy(headers = mapOf("referer" to "https://two.invalid"))
        val equivalent = current.copy(headers = linkedMapOf("referer" to "https://one.invalid"))
        assertEquals(LivePlaybackPolicy.candidateIdentity(current), LivePlaybackPolicy.candidateIdentity(equivalent))
        assertTrue(LivePlaybackPolicy.candidateIdentity(current) != LivePlaybackPolicy.candidateIdentity(variant))
        assertFalse(LivePlaybackPolicy.isSameMobileLiveCandidate(
            current.url, current.headers, variant.url, variant.headers
        ))
        assertTrue(LivePlaybackPolicy.isSameMobileLiveCandidate(
            current.url, current.headers, equivalent.url, equivalent.headers
        ))
        assertEquals(variant, LivePlaybackPolicy.nextUntriedCandidate(listOf(current, variant), current, emptySet()))
        assertNull(LivePlaybackPolicy.nextUntriedCandidate(
            listOf(current, variant), current, setOf(LivePlaybackPolicy.candidateIdentity(variant))
        ))
    }
}
