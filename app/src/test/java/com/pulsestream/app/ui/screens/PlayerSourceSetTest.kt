package com.pulsestream.app.ui.screens

import com.pulsestream.app.data.StreamItem
import com.pulsestream.app.ui.screens.player.mergePlayerSourceSet
import org.junit.Assert.*
import org.junit.Test

class PlayerSourceSetTest {
    private fun source(index: Int) = StreamItem("https://fixture.invalid/$index.mkv", title = "Source $index", sourceName = "Addon $index")

    @Test fun earlyPartialRefreshDoesNotDiscardFullLoadedHandoff() {
        val loaded = (1..12).map(::source)
        assertEquals(loaded, mergePlayerSourceSet(loaded, listOf(source(1))))
    }

    @Test fun lateAddonResultsAreAddedWithoutDuplicateRows() {
        val initial = listOf(source(1), source(2))
        assertEquals((1..4).map(::source), mergePlayerSourceSet(initial, listOf(source(1), source(3), source(4), source(3))))
    }

    @Test fun sameCountUpdatedResultRefreshesLabelsAndHeaders() {
        val changed = source(1).copy(name = "Fresh label", headers = mapOf("Referer" to "https://fixture.invalid/"))
        assertEquals(listOf(changed), mergePlayerSourceSet(listOf(source(1)), listOf(changed)))
    }

    @Test fun cachedResolutionReplacesPendingRowAndLatePlaceholderCannotUndoIt() {
        val pending = StreamItem("infohash:abc", infoHash = "abc", fileIdx = 2, filePath = "Season/E03.mkv", title = "Episode 3")
        val resolved = pending.copy(url = "https://fixture.invalid/episode3.mkv", infoHash = null, resolvedInfoHash = "abc", sourceName = "TorBox")
        assertEquals(listOf(resolved), mergePlayerSourceSet(listOf(pending), listOf(resolved)))
        assertEquals(listOf(resolved), mergePlayerSourceSet(listOf(resolved), listOf(pending)))
    }

    @Test fun distinctFilesWithinSameTorrentRemainSeparate() {
        val first = StreamItem("infohash:abc", infoHash = "abc", fileIdx = 0)
        val second = first.copy(fileIdx = 1)
        assertEquals(listOf(first, second), mergePlayerSourceSet(listOf(first), listOf(second)))
    }

    @Test fun sameLiveUrlWithDifferentHeadersRemainsAnIndependentCandidateOnlyInLiveMode() {
        val first = source(1).copy(headers = mapOf("Referer" to "https://one.fixture.invalid"))
        val second = first.copy(headers = mapOf("Referer" to "https://two.fixture.invalid"))
        assertEquals(listOf(second), mergePlayerSourceSet(listOf(first), listOf(second)))
        assertEquals(listOf(first, second), mergePlayerSourceSet(
            listOf(first), listOf(second), includeHeadersInIdentity = true
        ))
    }
}
