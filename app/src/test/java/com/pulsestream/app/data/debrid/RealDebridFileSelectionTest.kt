package com.pulsestream.app.data.debrid

import org.junit.Assert.*
import org.junit.Test

class RealDebridFileSelectionTest {
    @Test fun selectedEpisodeMapsToItsOwnLinkInAnAlreadyDownloadedPack() {
        val api = RealDebridApi("unused-test-key")
        val first = RealDebridApi.RdFile(1, "/Show/S01E01.mkv", 900, true)
        val second = RealDebridApi.RdFile(2, "/Show/S01E02.mkv", 1100, true)
        val status = RealDebridApi.TorrentStatus("test", "downloaded", listOf(first, second), listOf("episode1", "episode2"))
        val chosen = api.selectRequestedFile(status.files, 1, null)
        assertEquals(second, chosen)
        assertEquals("episode2", api.linkForRequestedFile(status, chosen!!))
        assertNull(api.selectRequestedFile(status.files, 10, null))
        assertNull(api.selectRequestedFile(status.files, 0, "/Show/S01E02.mkv"))
    }
    @Test fun ambiguousOrUnselectedFileMappingsFailClosed() {
        val api = RealDebridApi("unused-test-key")
        val first = RealDebridApi.RdFile(1, "/first.mkv", 900, true)
        val second = RealDebridApi.RdFile(2, "/second.mkv", 1100, false)
        val status = RealDebridApi.TorrentStatus("test", "downloaded", listOf(first, second), listOf("first"))
        assertNull(api.linkForRequestedFile(status, second))
        assertNull(api.linkForRequestedFile(status.copy(links = listOf("split1", "split2")), first))
    }
}
