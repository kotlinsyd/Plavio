package com.pulsestream.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import com.pulsestream.app.ui.components.OrbitDots
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.pulsestream.app.ui.components.SafePosterImage
import com.pulsestream.app.ui.components.ShimmerPoster
import com.pulsestream.app.ui.tv.tvFocusRing
import com.pulsestream.app.data.CachedMeta
import com.pulsestream.app.data.CastMember
import com.pulsestream.app.data.SearchHistory
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.components.liquidGlass
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.GlassBorder
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun SearchScreen(
    repository: StreamRepository,
    onOpenDetail: (addonId: String, type: String, metaId: String) -> Unit,
    onOpenActor: (Int, String) -> Unit = { _, _ -> }
) {
    // Survive rotation: query/type/genre are saveable, and the debounced
    // search effect below re-runs on restore (results aren't parcelable,
    // so they re-fetch instead of being saved).
    var query by rememberSaveable { mutableStateOf("") }
    var selectedType by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedGenre by rememberSaveable { mutableStateOf<String?>(null) }
    // Actors filter chip (owner's order 2026-10-26): while on, the
    // results area shows ONLY actors — actor matches never mix into
    // the title searches any more.
    var actorsOnly by rememberSaveable { mutableStateOf(false) }
    // The query+type combo the saved genre belongs to — resets the genre
    // when the search moves on (replaces the old remember(query) reset).
    var genreOwner by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<CachedMeta>>(emptyList()) }
    // Actors matching the query (owner's order 2026-10-26 — search by
    // actor): name + photo, tap opens the actor page.
    var people by remember { mutableStateOf<List<CastMember>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var resultOwner by remember { mutableStateOf("") }
    val queryOwner = "${query.trim()}|$selectedType"
    val queryResults = if (queryOwner == resultOwner) results else emptyList()
    val genres = remember(queryResults) {
        queryResults.flatMap { it.meta.genres }.distinct().sorted().take(14)
    }
    // "Refine" (year / IMDb-rating filters) removed in v0.12.0 — user's
    // call: useless. Only the genre chips below still filter the results.
    val visibleResults = remember(queryResults, selectedGenre) {
        if (selectedGenre == null) queryResults
        else queryResults.filter { selectedGenre in it.meta.genres }
    }
    val clearRefinements = {
        selectedGenre = null
    }
    val appContext = LocalContext.current.applicationContext
    var history by remember { mutableStateOf(SearchHistory.get(appContext)) }
    // "Popular right now" for the landing state (v0.12.4, Sample B):
    // TMDB trending with a key, in-app chart catalogs without one;
    // empty when no source can supply titles (recents then stand alone).
    var popular by remember { mutableStateOf<List<CachedMeta>>(emptyList()) }
    LaunchedEffect(Unit) { popular = repository.loadPopularTitles(10) }

    // Opening a result (title or actor) is what earns a history entry
    // (owner's spec 2026-10-26): with first-letter searching, recording
    // per keystroke would fill recents with fragments ("a", "av", "ava").
    fun recordSettledSearch() {
        val q = query.trim()
        if (q.isNotEmpty()) {
            SearchHistory.add(appContext, q)
            history = SearchHistory.get(appContext)
        }
    }

    // Debounced live search: starts on the FIRST letter (owner's order
    // 2026-10-26) — instant local-cache hits, then addon results.
    LaunchedEffect(query, selectedType, actorsOnly) {
        val q = query.trim()
        if (q.isEmpty()) {
            results = emptyList()
            people = emptyList()
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
        // People ride the same settled query; skipped for 1-letter
        // queries and type-filtered searches (unless the Actors chip
        // is the active filter — then they ARE the results).
        people = if (q.length >= 2 && (selectedType == null || actorsOnly)) {
            runCatching { repository.searchPeople(q) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
    }

    // ---- Scroll-overlap (owner's approved sample 2026-10-26) ----
    // The header (title, bar, chips, genres) and the Actors strip stay
    // put; the "Movies & Series" layer starts below them and, as the
    // grid scrolls, slides up OVER everything — actors, genres, chips,
    // even the search bar — until the posters own the full screen.
    // Scrolling back to the grid's top lowers it again.
    val headerHeightState = remember { mutableIntStateOf(0) }
    val actorsHeightState = remember { mutableIntStateOf(0) }
    val rootHeightState = remember { mutableIntStateOf(0) }
    var layerLiftPx by remember(resultOwner, selectedType, actorsOnly) {
        mutableFloatStateOf(0f)
    }
    val resultsGridState = remember(resultOwner, selectedType, actorsOnly) {
        LazyGridState()
    }
    val overlapConnection = remember(resultsGridState) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val dy = available.y
                val maxLift =
                    (headerHeightState.intValue + actorsHeightState.intValue).toFloat()
                return when {
                    // Scrolling down: lift the layer over the header
                    // first; the grid itself starts moving once the
                    // layer is docked at the top.
                    dy < 0f && layerLiftPx < maxLift -> {
                        val consume = min(-dy, maxLift - layerLiftPx)
                        layerLiftPx += consume
                        Offset(0f, -consume)
                    }
                    // Scrolling back: the grid returns to its top
                    // first, then the layer lowers, restoring the
                    // header underneath.
                    dy > 0f && layerLiftPx > 0f &&
                        resultsGridState.firstVisibleItemIndex == 0 &&
                        resultsGridState.firstVisibleItemScrollOffset == 0 -> {
                        val consume = min(dy, layerLiftPx)
                        layerLiftPx -= consume
                        Offset(0f, consume)
                    }
                    else -> Offset.Zero
                }
            }
        }
    }
    val density = LocalDensity.current
    val headerDp = with(density) { headerHeightState.intValue.toDp() }

    // Results-state shape: the titles layer exists when there are
    // titles (or a genre verdict to show); the Actors strip alone
    // (All mode, actors but no titles) is also a results state.
    val hasTitlesLayer = visibleResults.isNotEmpty() || selectedGenre != null
    val hasActorsStrip = !actorsOnly && selectedType == null && people.isNotEmpty()
    val hasResultsState = hasTitlesLayer || hasActorsStrip

    GlowBackground {
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { rootHeightState.intValue = it.height }
        ) {
            // Header zone: stays put and gets COVERED by the sliding
            // titles layer (never scrolls itself).
            Column(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { headerHeightState.intValue = it.height }
            ) {
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
                // Filter chips — All · Movies · Series · Live TV ·
                // Actors (owner's order 2026-10-26). The type chips
                // filter the title search; the Actors chip switches
                // the results area to actors only.
                if (query.trim().length >= 1) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(SEARCH_TYPES, key = { it.second }) { (type, label) ->
                            FilterChip(
                                modifier = Modifier.tvFocusRing(RoundedCornerShape(50)),
                                // Re-tapping a selected chip toggles it back off.
                                selected = !actorsOnly && selectedType == type,
                                onClick = {
                                    actorsOnly = false
                                    selectedType = if (selectedType == type) null else type
                                },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = LocalPalette.current.accent.copy(alpha = 0.25f),
                                    selectedLabelColor = TextPrimary,
                                    containerColor = Color.White.copy(alpha = 0.06f),
                                    labelColor = TextSecondary
                                )
                            )
                        }
                        item(key = "actors") {
                            FilterChip(
                                modifier = Modifier.tvFocusRing(RoundedCornerShape(50)),
                                selected = actorsOnly,
                                onClick = { actorsOnly = true },
                                label = { Text("Actors") },
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
                // Discover: genre chips derived from the results, filtering locally.
                if (genres.isNotEmpty() && query.trim().length >= 1 && !actorsOnly) {
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
            }
            when {
                query.trim().isEmpty() -> {
                    // Sample B landing (v0.12.4): capsule bar (above) →
                    // recent searches as capsule chips → "Popular right
                    // now" numbered list. One scrolling column so both
                    // sections fit small screens; bottom padding keeps
                    // the last row clear of the floating dock.
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = headerDp),
                        contentPadding = PaddingValues(bottom = 104.dp)
                    ) {
                        if (history.isNotEmpty()) {
                            item(key = "recent_header") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp, top = 8.dp),
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
                            }
                            item(key = "recent_chips") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
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
                        if (popular.isNotEmpty()) {
                            item(key = "popular_header") {
                                Text(
                                    "Popular right now",
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(
                                        start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp
                                    )
                                )
                            }
                            itemsIndexed(
                                popular,
                                key = { index, item -> "pop_${item.addonId}_${item.meta.id}_$index" }
                            ) { index, item ->
                                PopularRow(rank = index + 1, item = item) {
                                    onOpenDetail(item.addonId, item.meta.type, item.meta.id)
                                }
                            }
                        }
                        if (history.isEmpty() && popular.isEmpty()) {
                            item(key = "landing_hint") {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Start typing to search\nacross your installed addons.",
                                        color = TextSecondary,
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
                searching && !(actorsOnly && people.isNotEmpty()) -> {
                    // Shimmer skeleton grid while results land (owner's
                    // order 2026-10-26) — no orbit screen, no blank wait.
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(110.dp),
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp, top = 16.dp, bottom = 104.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = headerDp),
                        userScrollEnabled = false
                    ) {
                        items(12) {
                            Column {
                                ShimmerPoster(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(2f / 3f)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    " ",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                actorsOnly -> {
                    // Actors only (the Actors chip): heading + a grid
                    // of actor cells — photo, name, best-known titles.
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(top = headerDp)
                    ) {
                        Text(
                            "Actors",
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp)
                        )
                        Text(
                            "People matching \"${query.trim()}\"",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 4.dp)
                        )
                        if (people.isEmpty()) {
                            Box(
                                Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No actors for \"${query.trim()}\"",
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(110.dp),
                                // Bottom 104dp: last row scrolls clear of the floating dock.
                                contentPadding = PaddingValues(
                                    start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(people, key = { "actor_${it.id}_${it.name}" }) { person ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .tvFocusRing(RoundedCornerShape(12.dp))
                                            .clickable {
                                                recordSettledSearch()
                                                onOpenActor(person.id, person.name)
                                            }
                                    ) {
                                        SafePosterImage(
                                            model = person.photo,
                                            contentDescription = person.name,
                                            modifier = Modifier
                                                .size(88.dp)
                                                .clip(CircleShape)
                                                .liquidGlass(CircleShape)
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = person.name,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = TextPrimary,
                                            maxLines = 2,
                                            textAlign = TextAlign.Center
                                        )
                                        if (!person.knownFor.isNullOrBlank()) {
                                            Text(
                                                text = person.knownFor!!,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                !hasResultsState -> Box(
                    Modifier
                        .fillMaxSize()
                        .padding(top = headerDp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No results for \"$query\"",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                else -> {
                    // Covered zone: the Actors strip (All mode) — it
                    // stays put and the titles layer slides over it.
                    if (hasActorsStrip) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = headerDp)
                                .onSizeChanged { actorsHeightState.intValue = it.height }
                        ) {
                            Text(
                                "Actors",
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(people, key = { "person_${it.id}_${it.name}" }) { person ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .width(72.dp)
                                            .tvFocusRing(RoundedCornerShape(12.dp))
                                            .clickable {
                                                recordSettledSearch()
                                                onOpenActor(person.id, person.name)
                                            }
                                    ) {
                                        SafePosterImage(
                                            model = person.photo,
                                            contentDescription = person.name,
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(CircleShape)
                                                .liquidGlass(CircleShape)
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = person.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextPrimary,
                                            maxLines = 2,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    } else {
                        // No strip in this filter: the layer's travel
                        // is just the header's height.
                        LaunchedEffect(Unit) { actorsHeightState.intValue = 0 }
                    }
                    // The sliding titles layer: opaque, starts under
                    // the strip and rises over the whole header as
                    // the grid scrolls (see overlapConnection).
                    if (hasTitlesLayer) {
                        val layerTopPx = (
                            headerHeightState.intValue +
                                actorsHeightState.intValue -
                                layerLiftPx.roundToInt()
                            ).coerceAtLeast(0)
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .height(with(density) { rootHeightState.intValue.toDp() })
                                .offset { IntOffset(0, layerTopPx) }
                                .background(LocalPalette.current.background)
                                .zIndex(1f)
                        ) {
                            Text(
                                "Movies & Series",
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
                            )
                            LazyVerticalGrid(
                                state = resultsGridState,
                                columns = GridCells.Adaptive(110.dp),
                                // Bottom 104dp: last row scrolls clear of the floating dock.
                                contentPadding = PaddingValues(
                                    start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .nestedScroll(overlapConnection)
                            ) {
                                if (visibleResults.isEmpty()) {
                                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                "No $selectedGenre titles in these results.",
                                                color = TextSecondary,
                                                style = MaterialTheme.typography.bodyMedium,
                                                textAlign = TextAlign.Center
                                            )
                                            TextButton(onClick = clearRefinements) {
                                                Text("Show all genres", color = LocalPalette.current.accent)
                                            }
                                        }
                                    }
                                }
                                itemsIndexed(
                                    visibleResults,
                                    key = { index, item -> "${item.addonId}_${item.meta.id}_$index" }
                                ) { _, item ->
                                    GridPoster(item) {
                                        recordSettledSearch()
                                        onOpenDetail(item.addonId, item.meta.type, item.meta.id)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One "Popular right now" row (Sample B): mint rank number, title, and a
 * right-aligned "Movie · 2024" / "Series · 2023" tag (year omitted when
 * the source didn't provide one).
 */
@Composable
private fun PopularRow(rank: Int, item: CachedMeta, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusRing(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$rank",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = LocalPalette.current.accent,
            modifier = Modifier.width(30.dp)
        )
        Text(
            item.meta.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        val typeLabel = when (item.meta.type) {
            "movie" -> "Movie"
            "series" -> "Series"
            else -> item.meta.type.replaceFirstChar { it.uppercase() }
        }
        val year = item.meta.releaseInfo
            ?.filter { it.isDigit() }
            ?.take(4)
            ?.takeIf { it.length == 4 }
        Text(
            if (year != null) "$typeLabel · $year" else typeLabel,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
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
