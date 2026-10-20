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
    fun `encoded playlist punctuation is detected without changing the URL`() {
        assertEquals("application/x-mpegURL", PlaybackHeaders.inferMimeType(
            "https://proxy.example/manifest?url=https%3A%2F%2Fmedia.example%2Flive%2Em3u8%3Ftoken%3Dfixture"
        ))
        assertEquals("application/dash+xml", PlaybackHeaders.inferMimeType(
            "https://proxy.example/manifest?url=https%3A%2F%2Fmedia.example%2Flive%2Empd%3Ftoken%3Dfixture"
        ))
        assertNull(PlaybackHeaders.inferMimeType("https://proxy.example/manifest?bad=%ZZ"))
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

    @Test
    fun `playback headers reject unsafe and hop by hop headers and normalize names`() {
        val headers = PlaybackHeaders.buildPlaybackHeaders(mapOf(
            "User-Agent" to "   ",
            "Cookie" to "session=abc",
            "Range" to "bytes=0-5",
            "Connection" to "close",
            "X-Bad\r\nInjected" to "yes",
            "X-Okay" to "fine"
        ), isTv = false)
        assertEquals("fine", headers["X-Okay"])
        assertEquals(PlaybackHeaders.DEFAULT_MOBILE_UA, headers["User-Agent"])
        assertEquals("session=abc", headers["Cookie"])
        assertFalse(headers.keys.any { it.equals("Range", true) || it.equals("Connection", true) || it.contains("Bad") })
    }

    @Test
    fun `external player headers use a strict transport allowlist`() {
        val headers = PlaybackHeaders.externalPlayerHeaders(mapOf(
            "Authorization" to "Bearer secret",
            "Cookie" to "sid=secret",
            "Referer" to "https://example.test/",
            "X-Source" to "addon",
            "User-Agent" to "PhonePlayer"
        ))
        assertFalse(headers.keys.any { it.equals("Authorization", true) || it.equals("Cookie", true) })
        assertNull(headers["Referer"])
        assertNull(headers["X-Source"])
        assertEquals("PhonePlayer", headers["User-Agent"])
    }

}
