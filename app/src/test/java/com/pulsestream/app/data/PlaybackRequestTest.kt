package com.pulsestream.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackRequestTest {
    private fun request() = PlaybackRequest(
        streamUrl = "https://example.invalid/video.mp4",
        title = "Fixture",
        poster = null,
        videoId = "fixture"
    )

    @Test
    fun `normal playback does not request a separate audio source`() {
        assertNull(request().mergeAudioUrl)
    }

    @Test
    fun `separate audio survives subtitle and source request copies`() {
        val audio = "https://example.invalid/audio.m4a"
        val merged = request().copy(mergeAudioUrl = audio)
        assertEquals(audio, merged.copy(subtitles = listOf(Subtitle("https://example.invalid/sub.vtt", "en"))).mergeAudioUrl)
        assertEquals(audio, merged.copy(streamUrl = "https://example.invalid/video-720.mp4").mergeAudioUrl)
    }
}
