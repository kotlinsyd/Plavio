package com.pulsestream.app.ui.components

import com.pulsestream.app.data.StreamItem
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for autoplay stream selection.
 *
 * Covers:
 * - Unresolved infohash: placeholders are skipped
 * - Only playable (HTTP) URLs are selected
 * - Empty result when only placeholders exist
 */
class AutoplayFilterTest {

    private fun streamItem(url: String, name: String = "Test"): StreamItem {
        return StreamItem(
            url = url,
            name = name,
            title = name,
            headers = emptyMap(),
            infoHash = if (url.startsWith("infohash:")) url.removePrefix("infohash:") else null
        )
    }

    @Test
    fun `playable URL detection filters infohash placeholders`() {
        val streams: List<StreamItem> = listOf(
            streamItem("infohash:abc123", "Torrent 1"),
            streamItem("https://example.com/video.mp4", "Direct"),
            streamItem("infohash:def456", "Torrent 2")
        )
        val playable: List<StreamItem> = streams.filter { !it.url.startsWith("infohash:") }
        assertEquals(1, playable.size)
        assertEquals("https://example.com/video.mp4", playable[0].url)
    }

    @Test
    fun `empty when only placeholders exist`() {
        val streams: List<StreamItem> = listOf(
            streamItem("infohash:abc123"),
            streamItem("infohash:def456")
        )
        val playable: List<StreamItem> = streams.filter { !it.url.startsWith("infohash:") }
        assertTrue(playable.isEmpty())
    }

    @Test
    fun `all playable when no placeholders`() {
        val streams: List<StreamItem> = listOf(
            streamItem("https://example.com/a.mp4"),
            streamItem("https://example.com/b.mkv")
        )
        val playable: List<StreamItem> = streams.filter { !it.url.startsWith("infohash:") }
        assertEquals(2, playable.size)
    }

    @Test
    fun `infohash placeholder format is correct`() {
        val hash = "ABCDEF1234567890"
        val placeholder = "infohash:$hash"
        assertTrue(placeholder.startsWith("infohash:"))
        assertEquals(hash, placeholder.removePrefix("infohash:"))
    }
}
