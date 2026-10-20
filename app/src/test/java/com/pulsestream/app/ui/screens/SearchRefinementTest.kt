package com.pulsestream.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRefinementTest {
    private data class Item(val title: String, val release: String?, val rating: String?)

    @Test fun parsesYearFromYearAndDateReleaseInfo() {
        assertEquals(2024, parseSearchReleaseYear("2024"))
        assertEquals(1998, parseSearchReleaseYear("1998-12-25"))
        assertEquals(2015, parseSearchReleaseYear("Released in 2015"))
        assertNull(parseSearchReleaseYear(null))
        assertNull(parseSearchReleaseYear("unknown"))
    }

    @Test fun parsesValidImdbValuesAndRejectsUnknownOrOutOfRangeValues() {
        assertEquals(8.7, parseSearchImdbRating("8.7")!!, 0.001)
        assertEquals(7.5, parseSearchImdbRating("IMDb 7,5/10")!!, 0.001)
        assertNull(parseSearchImdbRating(null))
        assertNull(parseSearchImdbRating("N/A"))
        assertNull(parseSearchImdbRating("11.2"))
    }

    @Test fun unknownMetadataPassesOnlyWhenItsFilterIsAny() {
        val unknown = Item("Unknown", null, "N/A")
        assertTrue(matchesSearchRefinement(unknown.release, unknown.rating))
        assertFalse(matchesSearchRefinement(unknown.release, unknown.rating, SearchYearFilter.FROM_2020))
        assertFalse(matchesSearchRefinement(unknown.release, unknown.rating, ratingFilter = SearchRatingFilter.AT_LEAST_7))
    }

    @Test fun yearAndRatingFiltersUseInclusiveBoundsAndCombine() {
        assertTrue(matchesSearchRefinement("2020-01-01", "8.0", SearchYearFilter.FROM_2020, SearchRatingFilter.AT_LEAST_8))
        assertTrue(matchesSearchRefinement("2019", "7.0", SearchYearFilter.YEARS_2010S, SearchRatingFilter.AT_LEAST_7))
        assertTrue(matchesSearchRefinement("2009", "9.0", SearchYearFilter.BEFORE_2010))
        assertFalse(matchesSearchRefinement("2010", "6.9", SearchYearFilter.BEFORE_2010, SearchRatingFilter.AT_LEAST_7))
    }

    @Test fun genericFilterPreservesOrderAndLeavesResultsUnchangedForAny() {
        val items = listOf(
            Item("new", "2022", "8.1"),
            Item("old", "1999", "7.1"),
            Item("unknown", null, null)
        )
        assertEquals(items, filterSearchRefinements(items, releaseInfo = { it.release }, imdbRating = { it.rating }))
        assertEquals(listOf("new"), filterSearchRefinements(
            items,
            SearchYearFilter.FROM_2020,
            SearchRatingFilter.AT_LEAST_8,
            releaseInfo = { it.release },
            imdbRating = { it.rating }
        ).map { it.title })
    }
}
