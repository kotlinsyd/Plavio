package com.pulsestream.app.data

import org.junit.Assert.*
import org.junit.Test

class M3uParserTest {

    @Test
    fun `parse extracts channels from valid playlist`() {
        val text = """
            #EXTM3U
            #EXTINF:-1 tvg-name="News 24" tvg-logo="http://example.com/logo.png" group-title="News",News 24
            http://example.com/stream1.m3u8
            #EXTINF:-1 tvg-name="Sports HD" group-title="Sports",Sports HD
            https://example.com/stream2.m3u8
        """.trimIndent()

        val channels = M3uParser.parse(text)

        assertEquals(2, channels.size)
        assertEquals("News 24", channels[0].name)
        assertEquals("http://example.com/stream1.m3u8", channels[0].url)
        assertEquals("http://example.com/logo.png", channels[0].logo)
        assertEquals("News", channels[0].group)
        assertEquals("Sports HD", channels[1].name)
        assertNull(channels[1].logo)
        assertEquals("Sports", channels[1].group)
    }

    @Test
    fun `parse skips malformed entries without failing`() {
        val text = """
            #EXTM3U
            #EXTINF:-1 tvg-name="Good Channel",Good
            http://example.com/good.m3u8
            #EXTINF:-1 tvg-name="Bad Channel",Bad
            not-a-url
            #EXTINF:-1 tvg-name="Another Good",Another
            https://example.com/another.m3u8
        """.trimIndent()

        val channels = M3uParser.parse(text)

        assertEquals(2, channels.size)
        assertEquals("Good Channel", channels[0].name)
        // Like the first channel, tvg-name takes precedence over the display name.
        assertEquals("Another Good", channels[1].name)
        assertEquals("https://example.com/another.m3u8", channels[1].url)
    }

    @Test
    fun `parse preserves slash-sensitive URLs as distinct channels`() {
        val text = """
            #EXTM3U
            #EXTINF:-1 tvg-name="Channel",Channel
            http://example.com/stream
            #EXTINF:-1 tvg-name="Channel Dup",Channel Dup
            http://example.com/stream/
        """.trimIndent()

        val channels = M3uParser.parse(text)
        assertEquals(2, channels.size)
        assertEquals("http://example.com/stream/", channels[1].url)
    }

    @Test
    fun `comparison key preserves authentication IPv6 and request-sensitive path`() {
        assertEquals(
            "http://user:pass@[::1]:8080/live/",
            M3uParser.comparisonKey("HTTP://user:pass@[::1]:8080/live/")
        )
        assertEquals(
            "https://example.com/playlist/",
            M3uParser.comparisonKey("https://EXAMPLE.com:443/playlist/")
        )
    }

    @Test
    fun `parse retains exact playlist stream URL for the request`() {
        val url = "http://user:pass@[::1]:8080/live/"
        val channels = M3uParser.parse("#EXTM3U\n#EXTINF:-1,Channel\n$url")
        assertEquals(url, channels.single().url)
    }

    @Test
    fun `parse handles empty and comment-only input`() {
        assertTrue(M3uParser.parse("").isEmpty())
        assertTrue(M3uParser.parse("#EXTM3U\n# Just a comment\n").isEmpty())
    }

    @Test
    fun `parse uses display name when tvg-name missing`() {
        val text = """
            #EXTM3U
            #EXTINF:-1,My Display Name
            http://example.com/stream.m3u8
        """.trimIndent()

        val channels = M3uParser.parse(text)

        assertEquals(1, channels.size)
        assertEquals("My Display Name", channels[0].name)
    }

    @Test
    fun `normalizeUrl lowercases scheme and host`() {
        assertEquals(
            "http://example.com/stream",
            M3uParser.normalizeUrl("HTTP://EXAMPLE.COM/stream")
        )
    }

    @Test
    fun `normalizeUrl drops default ports`() {
        assertEquals(
            "http://example.com/stream",
            M3uParser.normalizeUrl("http://example.com:80/stream")
        )
        assertEquals(
            "https://example.com/stream",
            M3uParser.normalizeUrl("https://example.com:443/stream")
        )
    }

    @Test
    fun `normalizeUrl keeps non-default ports`() {
        assertEquals(
            "http://example.com:8080/stream",
            M3uParser.normalizeUrl("http://example.com:8080/stream")
        )
    }

    @Test
    fun `normalizeUrl keeps trailing slash and path case`() {
        assertEquals(
            "http://example.com/Stream/Path/",
            M3uParser.normalizeUrl("http://example.com/Stream/Path/")
        )
    }

    @Test
    fun `normalizeUrl preserves query string`() {
        assertEquals(
            "http://example.com/stream?token=abc&x=1",
            M3uParser.normalizeUrl("http://example.com/stream?token=abc&x=1")
        )
    }
}
