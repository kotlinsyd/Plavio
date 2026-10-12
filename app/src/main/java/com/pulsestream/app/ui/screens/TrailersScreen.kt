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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulsestream.app.data.PlaybackRequest
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.data.TrailerItem
import com.pulsestream.app.data.TrailerResolver
import com.pulsestream.app.ui.components.EmptyState
import com.pulsestream.app.ui.components.ErrorState
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.components.LoadingState
import com.pulsestream.app.ui.components.SafePosterImage
import com.pulsestream.app.ui.components.SectionTitle
import com.pulsestream.app.ui.theme.GlassBorder
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Debounce for the trailer search field. */
private const val SEARCH_DEBOUNCE_MS = 400L

private sealed interface TrailersUiState {
    data object Loading : TrailersUiState
    data class Content(val movies: List<TrailerItem>, val series: List<TrailerItem>) : TrailersUiState
    data class Error(val message: String) : TrailersUiState
    data object Empty : TrailersUiState
}

private class TrailersViewModel(
    private val repository: StreamRepository
) : ViewModel() {
    var uiState: TrailersUiState by mutableStateOf(TrailersUiState.Loading)
        private set
    var refreshing: Boolean by mutableStateOf(false)
        private set

    /** Trailer search state (not cached — fresh on every search). */
    var searchQuery: String by mutableStateOf("")
    var searchResults: List<TrailerItem>? by mutableStateOf(null)
    var searching: Boolean by mutableStateOf(false)

    /** Session cache: "type|metaId" -> YouTube id. */
    private val ytCache = mutableMapOf<String, String>()

    init {
        load(forceRefresh = false)
    }

    fun load(forceRefresh: Boolean) {
        val hadContent = uiState is TrailersUiState.Content
        if (!hadContent) uiState = TrailersUiState.Loading
        if (forceRefresh) refreshing = true
        viewModelScope.launch {
            try {
                val (movies, series) = withTimeoutOrNull(30_000) {
                    repository.trendingTrailersCached(forceRefresh)
                } ?: (emptyList<TrailerItem>() to emptyList())
                uiState = if (movies.isEmpty() && series.isEmpty()) TrailersUiState.Empty
                else TrailersUiState.Content(movies, series)
            } catch (e: Exception) {
                if (!hadContent) uiState = TrailersUiState.Error("Couldn't load trailers: ${e.message}")
            } finally {
                refreshing = false
            }
        }
    }

    /** YouTube id for an item — cached, else one meta fetch. Null when the addon has none. */
    suspend fun youtubeId(item: TrailerItem): String? {
        val key = "${item.type}|${item.meta.id}"
        ytCache[key]?.let { return it }
        val meta = withTimeoutOrNull(15_000) {
            repository.getMetaFromAny(item.baseUrl, item.type, item.meta.id)
        }
        val ytId = meta?.trailerStreams?.firstOrNull()?.ytId
        if (ytId != null) ytCache[key] = ytId
        return ytId
    }
}

private fun trailerKey(item: TrailerItem) = "${item.type}|${item.meta.id}"

/**
 * The Trailers tab, mirroring Home: hero carousel + 2-column Movie/Series
 * trailer grids, played in-app via NewPipeExtractor into the mini floating
 * player. Data is TTL-cached so revisits are instant; pull-to-refresh forces
 * reload. The search icon opens trailer search (PlavioMeta keyless search
 * catalogs), replacing the old region filter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrailersScreen(
    repository: StreamRepository,
    onPlay: (PlaybackRequest) -> Unit,
    onGoToAddons: () -> Unit
) {
    val vm: TrailersViewModel = viewModel(factory = remember {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TrailersViewModel(repository) as T
        }
    })
    val scope = rememberCoroutineScope()
    var resolvingKey by remember { mutableStateOf<String?>(null) }
    var resolveError by remember { mutableStateOf<String?>(null) }
    var searchOpen by remember { mutableStateOf(false) }

    fun playTrailer(item: TrailerItem) {
        val key = trailerKey(item)
        if (resolvingKey != null) return
        resolvingKey = key
        resolveError = null
        scope.launch {
            try {
                val ytId = vm.youtubeId(item)
                if (ytId == null) {
                    resolveError = "No trailer found for \"${item.meta.name}\"."
                    return@launch
                }
                val url = withTimeoutOrNull(30_000) { TrailerResolver.resolveUrl(ytId) }
                if (url == null) {
                    resolveError = "Couldn't load the trailer for \"${item.meta.name}\"."
                    return@launch
                }
                onPlay(
                    PlaybackRequest(
                        streamUrl = url,
                        title = "${item.meta.name} — Trailer",
                        poster = item.meta.background ?: item.meta.poster,
                        videoId = "trailer_$ytId",
                        isMovie = true,
                        type = item.type,
                        streamLabel = "TRAILER",
                        sourceName = null,
                        isTrailer = true
                    )
                )
            } catch (e: Exception) {
                resolveError = "Couldn't load the trailer: ${e.message}"
            } finally {
                resolvingKey = null
            }
        }
    }

    // Debounced trailer search: fires on the PlavioMeta keyless search
    // catalogs (same searchAll the Search tab uses). Cancelled + restarted
    // on every keystroke; results are never cached.
    LaunchedEffect(vm.searchQuery, searchOpen) {
        if (!searchOpen) return@LaunchedEffect
        val q = vm.searchQuery.trim()
        if (q.length < 2) {
            vm.searchResults = null
            vm.searching = false
            return@LaunchedEffect
        }
        delay(SEARCH_DEBOUNCE_MS)
        if (vm.searchQuery.trim() != q) return@LaunchedEffect
        vm.searching = true
        try {
            vm.searchResults = repository.searchAll(q)
                .filter { it.meta.type == "movie" || it.meta.type == "series" }
                .distinctBy { it.meta.type + "|" + it.meta.id }
                .take(50)
                .map { TrailerItem(it.addonId, it.baseUrl, it.meta.type, it.meta) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            vm.searchResults = emptyList()
        } finally {
            vm.searching = false
        }
    }

    GlowBackground {
        Column(Modifier.fillMaxSize()) {
            TrailersHeader(
                searchOpen = searchOpen,
                query = vm.searchQuery,
                onQueryChange = { vm.searchQuery = it },
                onOpenSearch = { searchOpen = true },
                onCloseSearch = {
                    searchOpen = false
                    vm.searchQuery = ""
                    vm.searchResults = null
                    vm.searching = false
                }
            )

            if (searchOpen) {
                TrailerSearchContent(
                    query = vm.searchQuery,
                    searching = vm.searching,
                    results = vm.searchResults,
                    resolvingKey = resolvingKey,
                    onPlay = ::playTrailer
                )
            } else {
                PullToRefreshBox(
                    isRefreshing = vm.refreshing,
                    onRefresh = { vm.load(forceRefresh = true) },
                    modifier = Modifier.fillMaxSize()
                ) {
                    when (val state = vm.uiState) {
                        is TrailersUiState.Loading -> LoadingState("Finding trailers…")
                        is TrailersUiState.Error -> ErrorState(state.message, onRetry = { vm.load(false) })
                        is TrailersUiState.Empty -> EmptyState(
                            title = "No trailers yet",
                            message = "Trailers come from your catalog addons' trending lists. " +
                                "Install a catalog addon and they'll appear here.",
                            actionLabel = "Add an addon",
                            onAction = onGoToAddons
                        )
                        is TrailersUiState.Content -> {
                            val heroItems = remember(state.movies, state.series) {
                                (state.movies + state.series).take(5)
                            }
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 16.dp, end = 16.dp, bottom = 24.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(20.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                if (heroItems.isNotEmpty()) {
                                    item(
                                        span = { GridItemSpan(maxLineSpan) },
                                        key = "trailer|hero"
                                    ) {
                                        TrailerHeroCarousel(
                                            items = heroItems,
                                            resolvingKey = resolvingKey,
                                            onPlay = ::playTrailer
                                        )
                                        Spacer(Modifier.height(4.dp))
                                    }
                                }
                                resolveError?.let { err ->
                                    item(
                                        span = { GridItemSpan(maxLineSpan) },
                                        key = "trailer|error"
                                    ) {
                                        Text(
                                            err,
                                            color = Color(0xFFFF6B6B),
                                            style = MaterialTheme.typography.bodySmall,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                                if (state.movies.isNotEmpty()) {
                                    item(
                                        span = { GridItemSpan(maxLineSpan) },
                                        key = "trailer|sec_movies"
                                    ) {
                                        SectionTitle(title = "Movie Trailers")
                                    }
                                    items(state.movies, key = { trailerKey(it) }) { item ->
                                        TrailerGridCard(
                                            item = item,
                                            showType = false,
                                            busy = resolvingKey == trailerKey(item),
                                            onPlay = { playTrailer(item) }
                                        )
                                    }
                                }
                                if (state.series.isNotEmpty()) {
                                    item(
                                        span = { GridItemSpan(maxLineSpan) },
                                        key = "trailer|sec_series"
                                    ) {
                                        SectionTitle(title = "Series Trailers")
                                    }
                                    items(state.series, key = { trailerKey(it) }) { item ->
                                        TrailerGridCard(
                                            item = item,
                                            showType = false,
                                            busy = resolvingKey == trailerKey(item),
                                            onPlay = { playTrailer(item) }
                                        )
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
 * Fixed header: "Trailers" title + search icon, or an expanding search field
 * with autofocus when search is open.
 */
@Composable
private fun TrailersHeader(
    searchOpen: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (searchOpen) {
            val focusRequester = remember { FocusRequester() }
            IconButton(onClick = onCloseSearch) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Close search",
                    tint = TextSecondary
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search movies, series…", color = TextSecondary) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = TextSecondary) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear", tint = TextSecondary)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LocalPalette.current.accent,
                    unfocusedBorderColor = GlassBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = LocalPalette.current.accent
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
                    .focusRequester(focusRequester)
            )
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
        } else {
            Text(
                text = "Trailers",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            IconButton(onClick = onOpenSearch) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = "Search trailers",
                    tint = TextSecondary
                )
            }
        }
    }
}

/** Search results: same 2-column card design, empty/loading states included. */
@Composable
private fun TrailerSearchContent(
    query: String,
    searching: Boolean,
    results: List<TrailerItem>?,
    resolvingKey: String?,
    onPlay: (TrailerItem) -> Unit
) {
    val q = query.trim()
    Column(Modifier.fillMaxSize()) {
        // Thin progress bar while a refine-search is in flight over old results.
        if (searching && results != null) {
            LinearProgressIndicator(
                color = LocalPalette.current.accent,
                trackColor = Color.White.copy(alpha = 0.08f),
                modifier = Modifier.fillMaxWidth()
            )
        }
        when {
            q.length < 2 -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Type at least 2 characters to search.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 48.dp)
                )
            }
            searching && results == null -> LoadingState("Searching trailers…")
            results.isNullOrEmpty() -> EmptyState(
                title = "No trailers found",
                message = "No trailers found for \"$q\". Try another title."
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(results, key = { trailerKey(it) }) { item ->
                    TrailerGridCard(
                        item = item,
                        showType = true,
                        busy = resolvingKey == trailerKey(item),
                        onPlay = { onPlay(item) }
                    )
                }
            }
        }
    }
}

/** Hero carousel mirroring Home's: auto-advancing pager with dots. */
@Composable
private fun TrailerHeroCarousel(
    items: List<TrailerItem>,
    resolvingKey: String?,
    onPlay: (TrailerItem) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { items.size })

    LaunchedEffect(pagerState, items.size) {
        while (items.size > 1) {
            delay(5000)
            if (!pagerState.isScrollInProgress) {
                val next = (pagerState.currentPage + 1) % items.size
                runCatching { pagerState.animateScrollToPage(next) }
            }
        }
    }

    Column {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(232.dp)
                .clip(RoundedCornerShape(22.dp))
                .border(1.dp, GlassBorder, RoundedCornerShape(22.dp)),
            key = { index -> "trailer_hero_${items[index].meta.id}_$index" }
        ) { index ->
            val item = items[index]
            val busy = resolvingKey == trailerKey(item)
            val palette = LocalPalette.current
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onPlay(item) }
            ) {
                SafePosterImage(
                    model = (item.meta.background?.takeIf { it.isNotBlank() }
                        ?: item.meta.poster),
                    contentDescription = item.meta.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.35f),
                                Color.Black.copy(alpha = 0.88f)
                            )
                        )
                    )
                )
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "TRAILER",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.5.sp,
                        color = palette.accent
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = item.meta.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val info = listOfNotNull(
                        item.meta.releaseInfo?.take(4),
                        if (item.type == "movie") "Movie" else "Series"
                    ).joinToString("  •  ")
                    if (info.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = info,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(palette.accent)
                            .padding(horizontal = 18.dp, vertical = 8.dp)
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                color = Color(0xFF06251F),
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Filled.PlayArrow, null,
                                tint = Color(0xFF06251F),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (busy) "Loading…" else "Watch Trailer",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF06251F)
                        )
                    }
                }
            }
        }
        // Position dots.
        if (items.size > 1) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(items.size) { i ->
                    val active = i == pagerState.currentPage
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .width(if (active) 18.dp else 6.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (active) LocalPalette.current.accent
                                else Color.White.copy(alpha = 0.28f)
                            )
                    )
                }
            }
        }
    }
}

/**
 * 16:9 trailer card: backdrop art with rounded corners and a subtle glass
 * play affordance, then a centered "Title · Year" line (2 lines max with
 * ellipsis for long titles) and an optional Movie/Series caption.
 */
@Composable
private fun TrailerGridCard(
    item: TrailerItem,
    showType: Boolean,
    busy: Boolean,
    onPlay: () -> Unit
) {
    val year = item.meta.releaseInfo
        ?.take(4)
        ?.takeIf { it.length == 4 && it.all(Char::isDigit) }
    val titleLine = if (year != null) "${item.meta.name} · $year" else item.meta.name

    Column(
        modifier = Modifier.clickable(onClick = onPlay),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, GlassBorder, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            SafePosterImage(
                model = item.meta.background ?: item.meta.poster,
                contentDescription = item.meta.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // Soft bottom shade so the play button sits cleanly on any art.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.25f)
                        )
                    )
                )
            )
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Play trailer",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = titleLine,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 18.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        )
        if (showType) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (item.type == "movie") "Movie" else "Series",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}
