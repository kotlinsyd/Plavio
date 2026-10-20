package com.pulsestream.app.data

import org.junit.Assert.*
import org.junit.Test

class TorrentStreamHashTest {
    private val hash = "0123456789abcdef0123456789abcdef01234567"

    @Test fun extractsEncodedAndUppercaseMagnetHashes() {
        assertEquals(hash, torrentHashFromMagnet("magnet:?dn=Fixture&xt=urn%3Abtih%3A${hash.uppercase()}&tr=https%3A%2F%2Ftracker.invalid"))
    }

    @Test fun base32HashIsConvertedToHex() {
        assertEquals("0".repeat(40), torrentHashFromMagnet("magnet:?xt=urn:btih:${"A".repeat(32)}"))
    }

    @Test fun invalidOrAmbiguousMagnetIdentityIsRejected() {
        assertNull(torrentHashFromMagnet("https://example.invalid/magnet:?xt=urn:btih:$hash"))
        assertNull(torrentHashFromMagnet("magnet:?xt=urn:btih:not-a-hash"))
        assertNull(torrentHashFromMagnet("magnet:?xt=urn:btih:$hash&xt=urn:btih:${"f".repeat(40)}"))
        assertNull(torrentHashFromMagnet("magnet:?xt=%zz"))
    }

    @Test fun parserNormalizesMagnetAddonStreamAndRetainsFileSelector() {
        val stream = StremioParser.parseStreams("""{"streams":[{"url":"magnet:?xt=urn:btih:$hash","fileIdx":2,"filePath":"Season 1/E03.mkv"}]}""").streams.single()
        assertEquals(hash, stream.infoHash)
        assertEquals("infohash:$hash", stream.url)
        assertEquals(2, stream.fileIdx)
        assertEquals("Season 1/E03.mkv", stream.filePath)
    }

    @Test fun directHttpAddonLinkIsNeverReplacedByMagnetResolution() {
        val stream = StremioParser.parseStreams("""{"streams":[{"url":"https://example.invalid/file.mp4","infoHash":"$hash"}]}""").streams.single()
        assertEquals("https://example.invalid/file.mp4", stream.url)
    }
}
