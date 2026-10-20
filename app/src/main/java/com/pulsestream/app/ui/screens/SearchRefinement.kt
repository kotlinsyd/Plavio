package com.pulsestream.app.ui.screens

/** Optional release-year bounds for search results. Unknown years match only [ANY]. */
enum class SearchYearFilter(val label: String) {
    ANY("Any year"),
    FROM_2020("2020+"),
    YEARS_2010S("2010s"),
    BEFORE_2010("Before 2010")
}

/** Optional IMDb rating threshold. Unknown ratings match only [ANY]. */
enum class SearchRatingFilter(val label: String) {
    ANY("Any rating"),
    AT_LEAST_7("7+"),
    AT_LEAST_8("8+")
}

private val releaseYearPattern = Regex("(?<!\\d)(18\\d{2}|19\\d{2}|20\\d{2}|2100)(?!\\d)")
private val ratingPattern = Regex("(?<!\\d)(\\d{1,2}(?:[.,]\\d+)?)(?!\\d)")

/** Extract a plausible four-digit year from common releaseInfo forms (year or ISO date). */
fun parseSearchReleaseYear(releaseInfo: String?): Int? {
    val raw = releaseInfo?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return releaseYearPattern.find(raw)?.value?.toIntOrNull()
}

/** Parse a 0–10 IMDb value, allowing labels and decimal-comma metadata; invalid values are unknown. */
fun parseSearchImdbRating(imdbRating: String?): Double? {
    val raw = imdbRating?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val value = ratingPattern.find(raw)?.groupValues?.getOrNull(1)
        ?.replace(',', '.')?.toDoubleOrNull() ?: return null
    return value.takeIf { it in 0.0..10.0 }
}

fun matchesSearchRefinement(
    releaseInfo: String?,
    imdbRating: String?,
    yearFilter: SearchYearFilter = SearchYearFilter.ANY,
    ratingFilter: SearchRatingFilter = SearchRatingFilter.ANY
): Boolean {
    val yearMatches = when (yearFilter) {
        SearchYearFilter.ANY -> true
        SearchYearFilter.FROM_2020 -> parseSearchReleaseYear(releaseInfo)?.let { it >= 2020 } ?: false
        SearchYearFilter.YEARS_2010S -> parseSearchReleaseYear(releaseInfo)?.let { it in 2010..2019 } ?: false
        SearchYearFilter.BEFORE_2010 -> parseSearchReleaseYear(releaseInfo)?.let { it < 2010 } ?: false
    }
    val ratingMatches = when (ratingFilter) {
        SearchRatingFilter.ANY -> true
        SearchRatingFilter.AT_LEAST_7 -> parseSearchImdbRating(imdbRating)?.let { it >= 7.0 } ?: false
        SearchRatingFilter.AT_LEAST_8 -> parseSearchImdbRating(imdbRating)?.let { it >= 8.0 } ?: false
    }
    return yearMatches && ratingMatches
}

/** Filter arbitrary result wrappers without changing their order or requiring UI/framework dependencies. */
fun <T> filterSearchRefinements(
    items: List<T>,
    yearFilter: SearchYearFilter = SearchYearFilter.ANY,
    ratingFilter: SearchRatingFilter = SearchRatingFilter.ANY,
    releaseInfo: (T) -> String?,
    imdbRating: (T) -> String?
): List<T> = items.filter {
    matchesSearchRefinement(releaseInfo(it), imdbRating(it), yearFilter, ratingFilter)
}
