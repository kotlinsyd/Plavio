package com.pulsestream.app.data

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleResolverTest {
    @Test fun `recognizes WebVTT with BOM and leading whitespace`() {
        assertEquals(MimeTypes.TEXT_VTT, detectSubtitleMimeType("\uFEFF  WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nHello"))
    }

    @Test fun `recognizes SRT timestamps`() {
        assertEquals(MimeTypes.APPLICATION_SUBRIP, detectSubtitleMimeType("1\n00:00:01,000 --> 00:00:02,000\nHello"))
    }

    @Test fun `rejects html json and arbitrary text instead of treating them as SRT`() {
        assertNull(detectSubtitleMimeType("<html><body>not found</body></html>"))
        assertNull(detectSubtitleMimeType("{\"error\":\"unauthorized\"}"))
        assertNull(detectSubtitleMimeType("server temporarily unavailable"))
    }
}
