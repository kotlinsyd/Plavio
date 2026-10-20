package com.pulsestream.app.data.debrid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure local fixtures only: these tests never call TorBox or use an account key. */
class TorBoxFileSelectionTest {
    private fun file(index: Int, id: Long, name: String, size: Long = 1L) =
        TorBoxApi.TorrentFile(id = id, name = name, size = size, sourceIndex = index)

    @Test
    fun `honors requested index and path exactly in season pack`() {
        val files = listOf(
            file(0, 31, "Show/Season 1/Episode 01.mkv", 900),
            file(1, 44, "Show/Season 1/Episode 02.mkv", 1200),
            file(2, 99, "Show/Season 1/subtitles.srt", 30)
        )

        assertEquals(files[1], TorBoxFileSelection.select(files, 1, null))
        assertEquals(files[1], TorBoxFileSelection.select(files, null, "\\Show\\Season 1\\Episode 02.mkv"))
        assertEquals(files[1], TorBoxFileSelection.select(files, 1, "Show/Season 1/Episode 02.mkv"))
    }

    @Test
    fun `rejects index path disagreement and unknown exact hints`() {
        val files = listOf(file(0, 7, "A.mkv"), file(1, 8, "B.mkv"))
        assertNull(TorBoxFileSelection.select(files, 0, "B.mkv"))
        assertNull(TorBoxFileSelection.select(files, 7, null))
        assertNull(TorBoxFileSelection.select(files, null, "missing.mkv"))
    }

    @Test
    fun `source array index does not shift when an invalid API entry was skipped`() {
        // Fixture models API array positions 0, 1 (malformed/skipped), and 2.
        val files = listOf(file(0, 3, "readme.txt"), file(2, 5, "Episode 03.mkv"))
        assertEquals(files[1], TorBoxFileSelection.select(files, 2, null))
        assertNull(TorBoxFileSelection.select(files, 1, null))
    }

    @Test
    fun `no hint resolves only a single video and fails closed for packs`() {
        val one = listOf(file(0, 21, "Movie.mkv", 1000), file(1, 22, "Movie.nfo", 10))
        val pack = one + file(2, 23, "Bonus/Extra.mp4", 500)
        assertEquals(one[0], TorBoxFileSelection.select(one, null, null))
        assertNull(TorBoxFileSelection.select(pack, null, null))
        assertNull(TorBoxFileSelection.select(listOf(file(0, 1, "readme.txt")), null, null))
    }

    @Test
    fun `duplicate paths are ambiguous unless an index disambiguates`() {
        val files = listOf(file(0, 31, "Episode.mkv"), file(1, 32, "episode.mkv"))
        assertNull(TorBoxFileSelection.select(files, null, "Episode.mkv"))
        assertEquals(files[1], TorBoxFileSelection.select(files, 1, "episode.mkv"))
    }
}
