package com.pulsestream.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class StremioParserPlaybackTest {
    @Test
    fun `parses file index and path selectors without collapsing same-hash files`() {
        val result = StremioParser.parseStreams(
            """{"streams":[
              {"infoHash":"abc123","fileIdx":1,"filePath":"Season 1/E01.mkv","name":"E01"},
              {"infoHash":"abc123","fileIdx":2,"filePath":"Season 1/E02.mkv","name":"E02"}
            ]}"""
        )
        assertEquals(2, result.streams.size)
        assertEquals(1, result.streams[0].fileIdx)
        assertEquals("Season 1/E01.mkv", result.streams[0].filePath)
        assertEquals(2, result.streams[1].fileIdx)
        assertEquals("Season 1/E02.mkv", result.streams[1].filePath)
        assertNotSame(result.streams[0], result.streams[1])
    }

    @Test
    fun `parses file selector hints from behavior hints`() {
        val stream = StremioParser.parseStreams(
            """{"streams":[{"infoHash":"abc123","behaviorHints":{"fileIdx":7,"filePath":"pack/e07.mkv"}}]}"""
        ).streams.single()
        assertEquals(7, stream.fileIdx)
        assertEquals("pack/e07.mkv", stream.filePath)
    }

    @Test
    fun `retains HTTP URL root headers and behavior hint playback metadata`() {
        val stream = StremioParser.parseStreams(
            """{"streams":[{"url":"http://iptv.example/live.m3u8","headers":{"Referer":"https://root.example/","X-Addon":"root"},"behaviorHints":{"proxyHeaders":{"request":{"User-Agent":"FixtureAgent","Referer":"https://proxy.example/"}},"mimeType":"application/vnd.apple.mpegurl","videoSize":4096,"filename":"news.m3u8"}}]}"""
        ).streams.single()

        assertEquals("http://iptv.example/live.m3u8", stream.url)
        assertEquals("FixtureAgent", stream.headers["User-Agent"])
        // The explicit root request header takes precedence over proxy metadata.
        assertEquals("https://root.example/", stream.headers["Referer"])
        assertEquals("root", stream.headers["X-Addon"])
        assertEquals("application/vnd.apple.mpegurl", stream.mimeType)
        assertEquals(4096L, stream.videoSize)
        assertEquals("news.m3u8", stream.filename)
    }

    @Test
    fun `splits URL embedded IPTV headers and preserves direct stream URL`() {
        val stream = StremioParser.parseStreams(
            """{"streams":[{"url":"https://cdn.example/sports.m3u8?token=abc|User-Agent=Fixture%20Agent&Referer=https%3A%2F%2Fportal.example%2F"}]}"""
        ).streams.single()

        assertEquals("https://cdn.example/sports.m3u8?token=abc", stream.url)
        assertEquals("Fixture Agent", stream.headers["User-Agent"])
        assertEquals("https://portal.example/", stream.headers["Referer"])
    }

    @Test
    fun `explicit header objects override URL embedded values and malformed suffix is retained`() {
        val stream = StremioParser.parseStreams(
            """{"streams":[{"url":"https://cdn.example/live.m3u8|Referer=https%3A%2F%2Fembedded.example%2F&User-Agent=URLAgent","behaviorHints":{"proxyHeaders":{"request":{"User-Agent":"ProxyAgent"}}},"headers":{"User-Agent":"ExplicitAgent"}}]}"""
        ).streams.single()
        assertEquals("https://cdn.example/live.m3u8", stream.url)
        assertEquals("ExplicitAgent", stream.headers["User-Agent"])
        assertEquals("https://embedded.example/", stream.headers["Referer"])

        val malformed = StremioParser.parseStreams(
            """{"streams":[{"url":"https://cdn.example/live.m3u8|not-a-header"}]}"""
        ).streams.single()
        assertEquals("https://cdn.example/live.m3u8|not-a-header", malformed.url)
        assertEquals(emptyMap<String, String>(), malformed.headers)
    }

    @Test
    fun `explicit header precedence is case insensitive through playback handoff`() {
        val stream = StremioParser.parseStreams(
            """{"streams":[{"url":"https://cdn.example/live.m3u8|Referer=https%3A%2F%2Fembedded.example%2F","behaviorHints":{"proxyHeaders":{"request":{"referer":"https://proxy.example/"}}},"headers":{"Referer":"https://explicit.example/"}}]}"""
        ).streams.single()
        assertEquals(1, stream.headers.keys.count { it.equals("referer", ignoreCase = true) })
        assertEquals("https://explicit.example/", PlaybackHeaders.buildPlaybackHeaders(stream.headers, false)["Referer"])
    }

    @Test
    fun `catalog and meta responses without item type inherit the requested live type`() {
        val catalogItem = StremioParser.parseCatalog(
            """{"metas":[{"id":"channel-7","name":"Sports 7"}]}""",
            "tv"
        ).single()
        val detail = StremioParser.parseMeta(
            """{"meta":{"id":"channel-7","name":"Sports 7"}}""",
            "tv"
        )

        assertEquals("tv", catalogItem.type)
        assertEquals("tv", detail?.type)
    }
}
