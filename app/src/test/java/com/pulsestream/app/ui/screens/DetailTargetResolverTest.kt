package com.pulsestream.app.ui.screens

import com.pulsestream.app.data.db.WatchlistItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetailTargetResolverTest {
    @Test fun parsesTmdbMovieCredits() {
        assertEquals(TmdbReference("movie", "603"), parseTmdbReference("tmdb:movie:603", "movie"))
    }

    @Test fun normalizesTvCreditsToSeries() {
        assertEquals(TmdbReference("tv", "1399"), parseTmdbReference("tmdb:tv:1399", "tv"))
        assertEquals("series", normalizeDetailType("tv"))
        assertEquals("movie", normalizeDetailType("movie"))
    }

    @Test fun rejectsMalformedOrNonNumericTmdbIds() {
        assertNull(parseTmdbReference("tmdb:person:42", "movie"))
        assertNull(parseTmdbReference("tmdb:tv:abc", "tv"))
    }

    @Test fun librarySearchAndSortAreCaseInsensitiveAndStableByTimestamp() {
        val items = listOf(
            WatchlistItem("1", "a", "movie", "1", "zebra", null, 1L),
            WatchlistItem("2", "a", "movie", "2", "Alpha", null, 2L),
            WatchlistItem("3", "a", "movie", "3", "alpha sequel", null, 3L)
        )
        assertEquals(listOf("Alpha", "alpha sequel"), filterAndSortLibrary(items, "ALPHA", { it.name }, { it.addedAt }, LibrarySort.Title).map { it.name })
        assertEquals(listOf("alpha sequel", "Alpha", "zebra"), filterAndSortLibrary(items, "", { it.name }, { it.addedAt }, LibrarySort.RecentlyAdded).map { it.name })
    }
}
