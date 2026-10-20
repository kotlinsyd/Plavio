package com.pulsestream.app.data

import com.pulsestream.app.data.download.DownloadProtocol
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class BoundedDownloadProtocolTest {
    @Test fun `storage checks do not wrap when payload and reserve exceed Long`() {
        assertFalse(DownloadProtocol.fitsWithReserve(Long.MAX_VALUE, Long.MAX_VALUE, 1L))
        assertTrue(DownloadProtocol.fitsWithReserve(Long.MAX_VALUE, Long.MAX_VALUE - 1L, 1L))
        assertFalse(DownloadProtocol.fitsWithReserve(100L, 80L, 21L))
        assertTrue(DownloadProtocol.fitsWithReserve(100L, 80L, 20L))
        assertFalse(DownloadProtocol.fitsWithReserve(Long.MAX_VALUE, -1L, 0L))
    }

    @Test fun `range and storage checks preserve sizes beyond four GiB`() {
        val start = 5L * 1024 * 1024 * 1024
        val total = 10L * 1024 * 1024 * 1024
        val range = DownloadProtocol.parseContentRange("bytes $start-${total - 1}/$total", start)
        assertNotNull(range)
        assertEquals(start, range!!.start)
        assertTrue(DownloadProtocol.fitsWithReserve(total + 1024L, total, 1024L))
        assertFalse(DownloadProtocol.fitsWithReserve(total, total, 1024L))
    }

    @Test fun `range must begin exactly at requested byte and be internally valid`() {
        assertEquals(100L, DownloadProtocol.parseContentRange("bytes 100-199/500", 100)?.start)
        assertNull(DownloadProtocol.parseContentRange("bytes 99-199/500", 100))
        assertNull(DownloadProtocol.parseContentRange("bytes 100-500/500", 100))
        assertNull(DownloadProtocol.parseContentRange("nonsense", 100))
        assertNotNull(DownloadProtocol.parseContentRange("bytes 100-199/*", 100))
    }

    @Test fun `adaptive HLS and DASH manifests are not accepted as offline files`() {
        assertTrue(DownloadProtocol.isAdaptive("https://x.test/live.m3u8"))
        assertTrue(DownloadProtocol.isAdaptive("https://x.test/play?id=1", "application/dash+xml"))
        assertFalse(DownloadProtocol.isAdaptive("https://x.test/movie.mp4", "video/mp4"))
    }

    @Test fun `validator mismatch and weak etag behavior are explicit`() {
        assertTrue(DownloadProtocol.changedValidator("v1", null, "v2", null))
        assertFalse(DownloadProtocol.changedValidator("v1", null, "v1", null))
        assertEquals("Wed, 21 Oct 2015 07:28:00 GMT", DownloadProtocol.ifRange("W/weak", "Wed, 21 Oct 2015 07:28:00 GMT"))
        assertEquals("strong", DownloadProtocol.ifRange("strong", "date"))
    }

    @Test fun `decoded response body reader enforces a hard byte cap`() {
        assertEquals("1234", BoundedHttp.readBoundedUtf8(Buffer().writeUtf8("1234"), 4))
        try {
            BoundedHttp.readBoundedUtf8(Buffer().writeUtf8("12345"), 4)
            throw AssertionError("Expected bounded reader to reject excess bytes")
        } catch (_: IOException) {
            // Expected: one over-limit byte is enough to reject the response.
        }
    }
    @Test fun `disguised playlists and error pages never count as offline media`() {
        listOf("#EXTM3U\n#EXT-X-VERSION:3", "<MPD xmlns='x'>", "<?xml version='1.0'?><MPD>",
            "<!doctype html><html>Expired", "{\"error\":\"expired\"}").forEach {
            assertTrue(DownloadProtocol.isUnsupportedPayload(it.toByteArray()))
        }
        assertFalse(DownloadProtocol.isUnsupportedPayload(byteArrayOf(0, 0, 0, 24, 102, 116, 121, 112)))
        assertNull(DownloadProtocol.parseContentRange("bytes 0-9223372036854775807/*", 0))
    }

}
