package com.pulsestream.app.data

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SubtitleShifterTest {

    @Test
    fun `formatOffsetMs formats zero`() {
        assertEquals("0 ms", formatOffsetMs(0L))
    }

    @Test
    fun `formatOffsetMs formats whole seconds`() {
        assertEquals("+2 s", formatOffsetMs(2000L))
        assertEquals("-1 s", formatOffsetMs(-1000L))
        assertEquals("+1 s", formatOffsetMs(1000L))
    }

    @Test
    fun `formatOffsetMs formats milliseconds`() {
        assertEquals("+500 ms", formatOffsetMs(500L))
        assertEquals("-250 ms", formatOffsetMs(-250L))
    }

    @Test
    fun `shiftSubtitles returns original when offset is zero`() {
        val file = File.createTempFile("sub", ".srt").apply { deleteOnExit() }
        val resolved = listOf(
            ResolvedSubtitle(
                sub = Subtitle(url = "http://x", lang = "en"), file = file, mimeType = "text/srt"
            )
        )
        val cacheDir = createTempDir().apply { deleteOnExit() }

        val result = shiftSubtitles(resolved, 0L, cacheDir)

        assertSame(resolved, result)
    }

    @Test
    fun `shiftSubtitles returns original when list is empty`() {
        val cacheDir = createTempDir().apply { deleteOnExit() }
        assertTrue(shiftSubtitles(emptyList(), 500L, cacheDir).isEmpty())
    }

    @Test
    fun `shiftSubtitles shifts SRT timestamps forward`() {
        val srt = """
            1
            00:00:01,000 --> 00:00:04,000
            Hello world

            2
            00:00:05,500 --> 00:00:08,000
            Second line
        """.trimIndent()
        val file = File.createTempFile("sub", ".srt").apply {
            writeText(srt); deleteOnExit()
        }
        val resolved = listOf(
            ResolvedSubtitle(
                sub = Subtitle(url = "http://x", lang = "en"), file = file, mimeType = "text/srt"
            )
        )
        val cacheDir = File(createTempDir().apply { deleteOnExit() }, "shift-test").apply { mkdirs() }

        val result = shiftSubtitles(resolved, 2000L, cacheDir)

        assertEquals(1, result.size)
        val shiftedText = result[0].file.readText()
        // 1s + 2s = 3s, 5.5s + 2s = 7.5s
        assertTrue(shiftedText.contains("00:00:03,000 --> 00:00:06,000"))
        assertTrue(shiftedText.contains("00:00:07,500 --> 00:00:10,000"))
        // Original content preserved
        assertTrue(shiftedText.contains("Hello world"))
    }

    @Test
    fun `shiftSubtitles shifts SRT timestamps backward and clamps at zero`() {
        val srt = """
            1
            00:00:01,000 --> 00:00:04,000
            Hello
        """.trimIndent()
        val file = File.createTempFile("sub", ".srt").apply {
            writeText(srt); deleteOnExit()
        }
        val resolved = listOf(
            ResolvedSubtitle(
                sub = Subtitle(url = "http://x", lang = "en"), file = file, mimeType = "text/srt"
            )
        )
        val cacheDir = File(createTempDir().apply { deleteOnExit() }, "shift-test2").apply { mkdirs() }

        val result = shiftSubtitles(resolved, -2000L, cacheDir)

        val shiftedText = result[0].file.readText()
        // 1s - 2s would be negative → clamped to 0
        assertTrue(shiftedText.contains("00:00:00,000 --> 00:00:02,000"))
    }

    @Test
    fun `shiftSubtitles shifts VTT timestamps`() {
        val vtt = """
            WEBVTT

            00:00:01.000 --> 00:00:04.000
            Hello world
        """.trimIndent()
        val file = File.createTempFile("sub", ".vtt").apply {
            writeText(vtt); deleteOnExit()
        }
        val resolved = listOf(
            ResolvedSubtitle(
                sub = Subtitle(url = "http://x", lang = "en"), file = file, mimeType = "text/vtt"
            )
        )
        val cacheDir = File(createTempDir().apply { deleteOnExit() }, "shift-test3").apply { mkdirs() }

        val result = shiftSubtitles(resolved, 1000L, cacheDir)

        val shiftedText = result[0].file.readText()
        assertTrue(shiftedText.contains("00:00:02.000 --> 00:00:05.000"))
    }
}
