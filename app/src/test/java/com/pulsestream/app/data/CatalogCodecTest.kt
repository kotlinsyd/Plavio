package com.pulsestream.app.data

import org.junit.Assert.*
import org.junit.Test

class CatalogCodecTest {

    @Test
    fun `encodeMetas and decodeMetas roundtrip`() {
        val metas = listOf(
            MetaPreview(
                id = "tt123",
                type = "movie",
                name = "Test Movie",
                poster = "http://example.com/poster.jpg",
                description = "A test movie",
                genres = listOf("Action", "Drama")
            ),
            MetaPreview(
                id = "tt456",
                type = "series",
                name = "Test Series",
                releaseInfo = "2024",
                imdbRating = "8.5"
            )
        )

        val encoded = CatalogCodec.encodeMetas(metas)
        val decoded = CatalogCodec.decodeMetas(encoded)

        assertEquals(2, decoded.size)
        assertEquals("tt123", decoded[0].id)
        assertEquals("Test Movie", decoded[0].name)
        assertEquals("http://example.com/poster.jpg", decoded[0].poster)
        assertEquals(listOf("Action", "Drama"), decoded[0].genres)
        assertEquals("tt456", decoded[1].id)
        assertEquals("8.5", decoded[1].imdbRating)
    }

    @Test
    fun `encodeMetas handles empty list`() {
        val encoded = CatalogCodec.encodeMetas(emptyList())
        val decoded = CatalogCodec.decodeMetas(encoded)
        assertTrue(decoded.isEmpty())
    }

    @Test
    fun `decodeMetas handles invalid input gracefully`() {
        val decoded = CatalogCodec.decodeMetas("not valid json {{{")
        assertTrue(decoded.isEmpty())
    }

    @Test
    fun `decodeMetas handles empty string`() {
        assertTrue(CatalogCodec.decodeMetas("").isEmpty())
    }

    @Test
    fun `encodeSubtitles and decodeSubtitles roundtrip`() {
        val subs = listOf(
            Subtitle(url = "http://example.com/en.srt", lang = "en"),
            Subtitle(url = "http://example.com/es.srt", lang = "es")
        )

        val encoded = CatalogCodec.encodeSubtitles(subs)
        val decoded = CatalogCodec.decodeSubtitles(encoded)

        assertEquals(2, decoded.size)
        assertEquals("http://example.com/en.srt", decoded[0].url)
        assertEquals("en", decoded[0].lang)
        assertEquals("es", decoded[1].lang)
    }

    @Test
    fun `encodeSubtitles handles empty list`() {
        val encoded = CatalogCodec.encodeSubtitles(emptyList())
        assertTrue(CatalogCodec.decodeSubtitles(encoded).isEmpty())
    }

    @Test
    fun `metas preserve special characters`() {
        val metas = listOf(
            MetaPreview(
                id = "tt789",
                type = "movie",
                name = "Test: A \"Quoted\" Movie — with émojis 🎬",
                description = "Line1\nLine2\ttab"
            )
        )

        val decoded = CatalogCodec.decodeMetas(CatalogCodec.encodeMetas(metas))

        assertEquals(1, decoded.size)
        assertEquals("Test: A \"Quoted\" Movie — with émojis 🎬", decoded[0].name)
        assertEquals("Line1\nLine2\ttab", decoded[0].description)
    }
}
