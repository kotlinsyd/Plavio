package com.pulsestream.app.ui.components

import com.pulsestream.app.data.StreamItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamBrowserFilterTest {
    private fun stream(
        label: String,
        addon: String = "PlavioHD"
    ) = StreamItem(
        url = "https://example.test/${label.hashCode()}.mkv",
        name = label,
        title = label,
        sourceName = addon
    )

    @Test
    fun `normalizes 4K aliases case insensitively`() {
        assertEquals("4K", normalizeStreamQuality("4K"))
        assertEquals("4K", normalizeStreamQuality("4k"))
        assertEquals("4K", normalizeStreamQuality("UHD"))
        assertEquals("4K", normalizeStreamQuality("uhd"))
        assertEquals("4K", normalizeStreamQuality("2160p"))
        assertEquals("4K", normalizeStreamQuality("2160P"))

        // Parsing itself also recognizes lowercase aliases in stream titles.
        assertEquals("4K", parseStreamInfo(stream("4k HDR release")).quality)
        assertEquals("UHD", parseStreamInfo(stream("uhd HDR release")).quality)
    }

    @Test
    fun `combined availability canonicalizes aliases and retains 1440p and 360p`() {
        val results = listOf(
            stream("4K release", "PlavioHD"),
            stream("2160p release", "TMAddon"),
            stream("1440p release", "StAddon"),
            stream("360p release", "Other")
        )

        assertEquals(listOf("4K", "1440p", "360p"), availableStreamQualities(results))
    }

    @Test
    fun `quality filtering intersects combined results across all addons`() {
        val results = listOf(
            stream("2160p source A", "PlavioHD"),
            stream("4K source B", "TMAddon"),
            stream("1080p source C", "StAddon"),
            stream("UHD source D", "Other")
        )

        val filtered = filterStreamsByQuality(results, "uhd")
        assertEquals(listOf("PlavioHD", "TMAddon", "Other"), filtered.map { it.sourceName })
        assertTrue(filtered.all { normalizeStreamQuality(parseStreamInfo(it).quality) == "4K" })
    }

    @Test
    fun `unknown quality labels have no bucket and do not create availability pills`() {
        assertNull(normalizeStreamQuality("CAM"))
        assertNull(normalizeStreamQuality(""))
        val results = listOf(stream("CAM rip", "Unknown"), stream("720p rip", "Known"))
        assertEquals(listOf("720p"), availableStreamQualities(results))
        assertEquals(results, filterStreamsByQuality(results, "CAM"))
    }

    @Test
    fun `available qualities include async results added after initial response`() {
        val initialResponse = listOf(stream("1080p first result"))
        assertEquals(listOf("1080p"), availableStreamQualities(initialResponse))

        val refreshedCombinedResults = initialResponse + stream("360p added later", "TMAddon")
        assertEquals(listOf("1080p", "360p"), availableStreamQualities(refreshedCombinedResults))
        assertEquals(listOf("360p added later"), filterStreamsByQuality(refreshedCombinedResults, "360p").map { it.title })
    }

    @Test
    fun `display normalization removes contained case and punctuation duplicates`() {
        assertEquals("Example.Release.1080p.mkv", streamDisplayName("Example.Release.1080p.mkv", " example release 1080P MKV "))
        assertEquals("Great Film 1080p.mkv", streamDisplayName("Great Film 1080p.mkv", "GREAT-FILM"))
    }

    @Test
    fun `distinct display labels remain distinct and missing labels use url host`() {
        assertEquals("Episode One\n1080p Episode Two", streamDisplayName("Episode One", "1080p Episode Two"))
        assertEquals("cdn.example.test", streamDisplayName(null, null, "https://cdn.example.test/video.mkv"))
    }

    @Test
    fun `display normalization deduplicates overlapping multiline metadata and repeated filenames`() {
        val title = "Film.mkv\n💾 5.4 GB\n1080p\nFilm.mkv"
        val name = "Provider\n💾 5.4GB\n1080P\nFilm.mkv"

        assertEquals("Film.mkv\n💾 5.4 GB\n1080p\nProvider", streamDisplayName(title, name))
    }

    @Test
    fun `compact display model omits equivalent quality chip already represented in headline`() {
        val item = StreamItem(
            url = "https://cdn.example.test/film.mkv",
            title = "Film UHD",
            name = "Provider 2160p",
            sourceName = "PlavioHD"
        )

        val display = streamDisplayModel(item)
        assertEquals("Film UHD\nProvider 2160p", display.headline)
        assertTrue(display.metadata.none { normalizeStreamQuality(it) == "4K" })
    }

    @Test
    fun `compact display model omits metadata already present in the filename and keeps provider`() {
        val filename = "Film.2160p.WEB-DL.x265.DTS-HD.5.4GB.mkv"
        val item = StreamItem(
            url = "https://cdn.example.test/film.mkv",
            title = filename,
            name = filename,
            sourceName = "PlavioHD"
        )

        val display = streamDisplayModel(item)
        assertEquals(filename, display.headline)
        assertEquals("PlavioHD", display.provider)
        assertTrue(display.metadata.isEmpty())
    }

    @Test
    fun `compact display model does not repeat provider already visible in headline`() {
        val item = StreamItem(
            url = "https://cdn.example.test/film.mkv",
            title = "PlavioHD 1080p WEB-DL",
            name = "PlavioHD 1080p WEB-DL",
            sourceName = "PlavioHD"
        )

        val display = streamDisplayModel(item)
        assertEquals("PlavioHD 1080p WEB-DL", display.headline)
        assertNull(display.provider)
        assertTrue(display.metadata.isEmpty())
    }

}
