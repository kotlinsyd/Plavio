package com.pulsestream.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pulsestream.app.ui.components.SafePosterImage
import com.pulsestream.app.ui.tv.tvFocusRing
import com.pulsestream.app.data.CachedMeta
import com.pulsestream.app.data.SearchHistory
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.components.liquidGlass
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.GlassBorder
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary

@Composable
fun SearchScreen(
    repository: StreamRepository,
    onOpenDetail: (addonId: String, type: String, metaId: String) -> Unit
) {
    // Survive rotation: query/type/genre are saveable, and the debounced
    // search effect below re-runs on restore (results aren't parcelable,
    // so they re-fetch instead of being saved).
    var query by rememberSaveable { mutableStateOf("") }
    var selectedType by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedGenre by rememberSaveable { mutableStateOf<String?>(null) }
    var yearFilter by rememberSaveable { mutableStateOf(SearchYearFilter.ANY) }
    var ratingFilter by rememberSaveable { mutableStateOf(SearchRatingFilter.ANY) }
    var refinementsExpanded by rememberSaveable { mutableStateOf(false) }
    // The query+type combo the saved genre belongs to — resets the genre
    // when the search moves on (replaces the old remember(query) reset).
    var genreOwner by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<CachedMeta>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var resultOwner by remember { mutableStateOf("") }
    val queryOwner = "${query.trim()}|$selectedType"
    val queryResults = if (queryOwner == resultOwner) results else emptyList()
    val genres = remember(queryResults) {
        queryResults.flatMap { it.meta.genres }.distinct().sorted().take(14)
    }
    val refinedResults = remember(queryResults, yearFilter, ratingFilter) {
        filterSearchRefinements(
            queryResults,
            yearFilter = yearFilter,
            ratingFilter = ratingFilter,
            releaseInfo = { it.meta.releaseInfo },
            imdbRating = { it.meta.imdbRating }
        )
    }
    val visibleResults = remember(refinedResults, selectedGenre) {
        if (selectedGenre == null) refinedResults
        else refinedResults.filter { selectedGenre in it.meta.genres }
    }
    val activeRefinementCount =
        (if (selectedGenre != null) 1 else 0) +
            (if (yearFilter != SearchYearFilter.ANY) 1 else 0) +
            (if (ratingFilter != SearchRatingFilter.ANY) 1 else 0)
    val metadataRefinementActive =
        yearFilter != SearchYearFilter.ANY || ratingFilter != SearchRatingFilter.ANY
    val clearRefinements = {
        selectedGenre = null
        yearFilter = SearchYearFilter.ANY
        ratingFilter = SearchRatingFilter.ANY
    }
    val appContext = LocalContext.current.applicationContext
    var history by remember { mutableStateOf(SearchHistory.get(appContext)) }

    // Debounced live search: instant local-cache hits, then addon results.
    LaunchedEffect(query, selectedType) {
        val q = query.trim()
        if (q.length < 2) {
            results = emptyList()
            resultOwner = ""
            searching = false
            return@LaunchedEffect
        }
        // Genre filter resets with each new query (and stays put on
        // rotation, where owner still matches).
        val owner = "$q|$selectedType"
        if (owner != genreOwner) {
            selectedGenre = null
            genreOwner = owner
        }
        searching = true
        delay(500)
        val fetchedResults = repository.searchAll(q, selectedType)
        results = fetchedResults
        resultOwner = owner
        searching = false
        SearchHistory.add(appContext, q)
        history = SearchHistory.get(appContext)
    }

    GlowBackground {
        Column(Modifier.fillMaxSize()) {
            Text(
                text = "Search",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(16.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .liquidGlass(RoundedCornerShape(50))
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Movies, shows…", color = TextSecondary) },
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = TextSecondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = LocalPalette.current.accent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(8.dp))
            // Category chips — filter the search by content type.
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(SEARCH_TYPES, key = { it.second }) { (type, label) ->
                    FilterChip(
                        modifier = Modifier.tvFocusRing(RoundedCornerShape(50)),
                        // Re-tapping a selected chip toggles it back off.
                        selected = selectedType == type,
                        onClick = { selectedType = if (selectedType == type) null else type },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = LocalPalette.current.accent.copy(alpha = 0.25f),
                            selectedLabelColor = TextPrimary,
                            containerColor = Color.White.copy(alpha = 0.06f),
                            labelColor = TextSecondary
                        )
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            // Keep the added metadata filters tucked away until requested, so
            // search results remain visible on smaller phone screens.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = { refinementsExpanded = !refinementsExpanded }) {
                    Text(
                        if (activeRefinementCount > 0) "Refine ($activeRefinementCount)" else "Refine",
                        color = LocalPalette.current.accent
                    )
                }
                if (activeRefinementCount > 0) {
                    TextButton(onClick = clearRefinements) {
                        Text("Clear filters", color = TextSecondary)
                    }
                }
            }
            if (refinementsExpanded) {
                Text(
                    "Year",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 2.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(SearchYearFilter.entries, key = { it.name }) { filter ->
                        FilterChip(
                            modifier = Modifier.tvFocusRing(RoundedCornerShape(50)),
                            selected = yearFilter == filter,
                            onClick = { yearFilter = filter },
                            label = { Text(filter.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LocalPalette.current.accent.copy(alpha = 0.25f),
                                selectedLabelColor = TextPrimary,
                                containerColor = Color.White.copy(alpha = 0.06f),
                                labelColor = TextSecondary
                            )
                        )
                    }
                }
                Text(
                    "IMDb rating",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 2.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(SearchRatingFilter.entries, key = { it.name }) { filter ->
                        FilterChip(
                            modifier = Modifier.tvFocusRing(RoundedCornerShape(50)),
                            selected = ratingFilter == filter,
                            onClick = { ratingFilter = filter },
                            label = { Text(filter.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LocalPalette.current.accent.copy(alpha = 0.25f),
                                selectedLabelColor = TextPrimary,
                                containerColor = Color.White.copy(alpha = 0.06f),
                                labelColor = TextSecondary
                            )
                        )
                    }
                }
                if (yearFilter != SearchYearFilter.ANY || ratingFilter != SearchRatingFilter.ANY) {
                    Text(
                        "Titles with unknown year or rating are hidden while that filter is active.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
            // Discover: genre chips derived from the results, filtering locally.
            if (genres.isNotEmpty() && query.trim().length >= 2) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item(key = "genre_all") {
                        FilterChip(
                            modifier = Modifier.tvFocusRing(RoundedCornerShape(50)),
                            selected = selectedGenre == null,
                            onClick = { selectedGenre = null },
                            label = { Text("All genres") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LocalPalette.current.accent.copy(alpha = 0.25f),
                                selectedLabelColor = TextPrimary,
                                containerColor = Color.White.copy(alpha = 0.06f),
                                labelColor = TextSecondary
                            )
                        )
                    }
                    items(genres, key = { "genre_$it" }) { genre ->
                        FilterChip(
                            modifier = Modifier.tvFocusRing(RoundedCornerShape(50)),
                            selected = selectedGenre == genre,
                            onClick = { selectedGenre = if (selectedGenre == genre) null else genre },
                            label = { Text(genre) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LocalPalette.current.accent.copy(alpha = 0.25f),
                                selectedLabelColor = TextPrimary,
                                containerColor = Color.White.copy(alpha = 0.06f),
                                labelColor = TextSecondary
                            )
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            when {
                query.trim().length < 2 -> {
                    if (history.isEmpty()) {
                        Box(
                            Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Type at least 2 characters to search\nacross your installed addons.",
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(32.dp)
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Recent searches",
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.labelLarge
                                )
                                TextButton(
                                    onClick = {
                                        SearchHistory.clear(appContext)
                                        history = emptyList()
                                    }
                                ) {
                                    Text("Clear", color = TextSecondary)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(history, key = { it }) { past ->
                                    FilterChip(
                            modifier = Modifier.tvFocusRing(RoundedCornerShape(50)),
                                        selected = false,
                                        onClick = { query = past },
                                        label = { Text(past) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = Color.White.copy(alpha = 0.06f),
                                            labelColor = TextSecondary
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                searching -> Box(
                    Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = LocalPalette.current.accent)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Searching for \"${query.trim()}\"…",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                results.isEmpty() -> Box(
                    Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No results for \"$query\"",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(110.dp),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (visibleResults.isEmpty()) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    if (activeRefinementCount > 0) "No titles match these filters."
                                    else "No $selectedGenre titles in these results.",
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center
                                )
                                if (metadataRefinementActive) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "Some titles may have no release year or IMDb rating; unknown values are excluded by active filters.",
                                        color = TextSecondary,
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center
                                    )
                                    TextButton(onClick = clearRefinements) {
                                        Text("Clear filters", color = LocalPalette.current.accent)
                                    }
                                }
                            }
                        }
                    }
                    itemsIndexed(
                        visibleResults,
                        key = { index, item -> "${item.addonId}_${item.meta.id}_$index" }
                    ) { _, item ->
                        GridPoster(item) {
                            onOpenDetail(item.addonId, item.meta.type, item.meta.id)
                        }
                    }
                }
            }
        }
    }
}

/** Search category chips. Types come from addon data ("movie", "series", "tv"). */
private val SEARCH_TYPES: List<Pair<String?, String>> = listOf(
    null to "All",
    "movie" to "Movies",
    "series" to "Series",
    "tv" to "Live TV"
)

@Composable
private fun GridPoster(item: CachedMeta, onClick: () -> Unit) {
    Column(
        Modifier
            .tvFocusRing(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
        ) {
            SafePosterImage(
                model = item.meta.poster,
                contentDescription = item.meta.name,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = item.meta.name,
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = item.addonName,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
