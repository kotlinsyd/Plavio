package com.pulsestream.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveResolutionTest {
    @Test
    fun `recognizes standard live types and explicitly named custom channel catalogs`() {
        assertTrue(isLiveCatalog("tv", "popular", "Popular"))
        assertTrue(isLiveCatalog("station", "sports-live", "Sports"))
        assertTrue(isLiveCatalog("event", "news", "IPTV Channels"))
        assertTrue(isLiveCatalog("customChannel", "news", "News"))
        assertFalse(isLiveCatalog("movie", "popular", "Popular movies"))
        assertFalse(isLiveCatalog("series", "top", "Top series"))
    }

    @Test
    fun `uses item metadata resource type when it differs from catalog type`() {
        assertEquals("customChannel", liveResourceType("customChannel", "tv"))
        assertEquals("tv", liveResourceType("", "tv"))
    }

    @Test
    fun `channel zap reference carries origin addon catalog and actual resource type`() {
        val channel = TvChannelRef(
            addonId = "catalog-addon",
            metaId = "station-42",
            name = "News",
            poster = null,
            type = liveResourceType("station", "tv"),
            catalogId = "world-news"
        )
        assertEquals("catalog-addon", channel.addonId)
        assertEquals("world-news", channel.catalogId)
        assertEquals("station", channel.type)
        assertEquals("station-42", channel.metaId)
    }

    @Test
    fun `Live requests recognize custom types and only accept direct HTTP URLs`() {
        val channel = TvChannelRef("catalog-addon", "station-42", "News", null,
            type = "station", catalogId = "world-news")
        val addonRequest = PlaybackRequest("https://cdn.example/live.m3u8", "News", null,
            "station-42", type = channel.type, liveChannel = channel)
        val m3uRequest = PlaybackRequest("http://iptv.example/channel", "News", null,
            "http://iptv.example/channel", type = "tv")

        assertTrue(addonRequest.isLive)
        assertTrue(m3uRequest.isLive)
        assertTrue(isPlayableLiveStreamUrl("http://iptv.example/live.m3u8"))
        assertTrue(isPlayableLiveStreamUrl("https://cdn.example/live.m3u8"))
        assertFalse(isPlayableLiveStreamUrl("infohash:abc123"))
        assertFalse(isPlayableLiveStreamUrl("magnet:?xt=urn:btih:abc123"))
    }

    @Test
    fun `Live source deadline accommodates bounded addon fetch and cached debrid resolution`() {
        assertEquals(65_000L, LIVE_SOURCE_RESOLUTION_TIMEOUT_MS)
    }

    @Test
    fun `Live resolution failure text does not include provider exception details`() {
        val message = liveResolutionFailureMessage("World News")

        assertEquals("Couldn't load \"World News\". The source may be unavailable; please retry.", message)
        assertFalse(message.contains("https://provider.example/playlist?token=secret"))
    }
}
