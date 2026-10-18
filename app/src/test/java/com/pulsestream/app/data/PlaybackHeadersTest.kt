package com.pulsestream.app.data

import org.junit.Assert.*
import org.junit.Test

class PlaybackHeadersTest {

    @Test
    fun `inferMimeType detects HLS streams`() {
        assertEquals(
            "application/x-mpegURL",
            PlaybackHeaders.inferMimeType("https://example.com/stream.m3u8")
        )
        assertEquals(
            "application/x-mpegURL",
            PlaybackHeaders.inferMimeType("https://example.com/path/to/index.m3u8?token=abc")
        )
    }

    @Test
    fun `inferMimeType detects DASH streams`() {
        assertEquals(
            "application/dash+xml",
            PlaybackHeaders.inferMimeType("https://example.com/stream.mpd")
        )
    }

    @Test
    fun `inferMimeType returns null for unknown types`() {
        assertNull(PlaybackHeaders.inferMimeType("https://example.com/video.mp4"))
        assertNull(PlaybackHeaders.inferMimeType("https://example.com/video.mkv"))
        assertNull(PlaybackHeaders.inferMimeType("not a url"))
    }

    @Test
    fun `inferMimeType is case insensitive`() {
        assertEquals(
            "application/x-mpegURL",
            PlaybackHeaders.inferMimeType("https://example.com/STREAM.M3U8")
        )
    }
}
