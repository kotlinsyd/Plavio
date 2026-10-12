package com.pulsestream.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.pulsestream.app.ui.tv.rememberIsTv
import com.pulsestream.app.ui.tv.tvFocusRing
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulsestream.app.data.CatalogRow
import com.pulsestream.app.data.MetaPreview
import com.pulsestream.app.data.PlaybackRequest
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.data.db.PlaybackState
import com.pulsestream.app.ui.components.EmptyState
import com.pulsestream.app.ui.components.ErrorState
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.components.LoadingState
import com.pulsestream.app.ui.components.PosterCard
import com.pulsestream.app.ui.components.DotsLoader
import com.pulsestream.app.ui.components.SafePosterImage
import com.pulsestream.app.ui.components.SectionTitle
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.GlassBorder
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object Empty : HomeUiState
    data class Content(val rows: List<CatalogRow>, val refreshing: Boolean = false) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

class HomeViewModel(private val repo: StreamRepository) : ViewModel() {
    var uiState by mutableStateOf<HomeUiState>(HomeUiState.Loading)
        private set

    /**
     * Cache-first loading: show cached catalogs instantly, then quietly
     * refresh in the background — but only when the cache is stale (the
     * splash warm-up may have just refreshed it). [forceRefresh] skips
     * the cache entirely.
     */
    fun load(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (!forceRefresh) {
                val cached = repo.loadCatalogRowsCached()
                if (cached.isNotEmpty()) {
                    val stale = !repo.isCatalogCacheFresh()
                    uiState = HomeUiState.Content(cached, refreshing = stale)
                    if (stale) {
                        runCatching { repo.refreshCatalogRows() }
                            .onSuccess { uiState = HomeUiState.Content(it) }
                            .onFailure { uiState = HomeUiState.Content(cached) }
                    }
                    return@launch
                }
            }
            uiState = HomeUiState.Loading
            try {
                val rows = repo.refreshCatalogRows()
                uiState = if (rows.isEmpty()) HomeUiState.Empty else HomeUiState.Content(rows)
            } catch (e: Exception) {
                uiState = HomeUiState.Error(e.message ?: "Couldn't load catalogs")
            }
        }
    }

    /**
     * Pull-to-refresh: reload catalogs from the network while keeping the
     * current rows on screen with the refresh indicator.
     */
    fun refresh() {
        val current = (uiState as? HomeUiState.Content)?.rows
        if (current == null) {
            load(forceRefresh = true)
            return
        }
        // Don't stack refreshes.
        if ((uiState as? HomeUiState.Content)?.refreshing == true) return
        viewModelScope.launch {
            uiState = HomeUiState.Content(current, refreshing = true)
            runCatching { repo.refreshCatalogRows() }
                .onSuccess { uiState = HomeUiState.Content(it) }
                .onFailure { uiState = HomeUiState.Content(current) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: StreamRepository,
    onOpenDetail: (addonId: String, type: String, metaId: String) -> Unit,
    onResumePlayback: () -> Unit,
    onGoToAddons: () -> Unit,
    onGoToWatchlist: () -> Unit,
    onOpenCatalog: (row: CatalogRow) -> Unit
) {
    val vm: HomeViewModel = viewModel(factory = vmFactory { HomeViewModel(repository) })
    val watching by repository.continueWatching.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    // Watchlist lives ONLY in the Watchlist tab (top-right bookmark) — it is
    // deliberately not shown on Home (v0.8.21 behavior fix).
    // Reload when Home catalog visibility changes in Settings.
    val catalogVersion by repository.catalogVisibility.collectAsState(initial = 0)

    LaunchedEffect(catalogVersion) { vm.load() }

    // Android TV: initial D-pad focus target (the hero card).
    val isTv = rememberIsTv()
    val heroFocus = remember { FocusRequester() }
    // Land focus on the hero ONCE per Home visit — never yank it back on
    // every catalog refresh while the user is browsing the rows.
    var heroFocusDone by remember { mutableStateOf(false) }

    GlowBackground {
        Column(Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Plavio",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Black,
                            brush = LocalPalette.current.gradient
                        )
                    )
                    Text(
                        text = "Your streaming universe",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                IconButton(onClick = onGoToWatchlist) {
                    Icon(
                        Icons.Filled.Bookmark,
                        contentDescription = "Watchlist",
                        tint = TextSecondary
                    )
                }
            }

            when (val state = vm.uiState) {
                is HomeUiState.Loading -> LoadingState("Loading your catalogs…")
                is HomeUiState.Error -> ErrorState(state.message, onRetry = { vm.load() })
                is HomeUiState.Empty -> EmptyState(
                    title = "No addons yet",
                    message = "Add an addon to start browsing movies and shows.",
                    actionLabel = "Add an addon",
                    onAction = onGoToAddons
                )
                is HomeUiState.Content -> {
                    // Hero carousel data: computed once per row-set, outside the
                    // LazyColumn DSL (remember can't run inside the list scope).
                    val featured = remember(state.rows) { featuredTitles(state.rows) }
                    // TV: land D-pad focus on the hero once content is in.
                    LaunchedEffect(isTv, featured.size) {
                        if (isTv && featured.isNotEmpty() && !heroFocusDone) {
                            heroFocusDone = true
                            delay(600)
                            runCatching { heroFocus.requestFocus() }
                        }
                    }
                    val pullRefreshState = rememberPullToRefreshState()
                    PullToRefreshBox(
                        isRefreshing = state.refreshing,
                        onRefresh = { vm.refresh() },
                        modifier = Modifier.fillMaxSize(),
                        state = pullRefreshState,
                        indicator = {
                            // The signature pulse line replaces the default refresh circle.
                            if (state.refreshing || pullRefreshState.distanceFraction > 0f) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = 18.dp)
                                ) {
                                    DotsLoader()
                                }
                            }
                        }
                    ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        // Hero carousel: featured titles with backdrop art, first item
                        // so it's the first thing on screen. Explicit key keeps it
                        // stable while rows refresh underneath.
                        if (featured.isNotEmpty()) {
                            item(key = "hero_carousel") {
                                HeroCarousel(featured, onOpenDetail, heroFocus)
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                        if (watching.isNotEmpty()) {
                            // Explicit key: this section appears/disappears as the
                            // Flow emits, so an index-based default key would
                            // reshuffle every row below it on each emission.
                            item(key = "continue_watching") {
                                SectionTitle("Continue Watching")
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    itemsIndexed(
                                        watching,
                                        key = { index, item -> "${item.key}_$index" }
                                    ) { _, item ->
                                        ContinueWatchingCard(
                                            item,
                                            onRemove = {
                                                scope.launch {
                                                    repository.deleteSavedPosition(item.key)
                                                }
                                            }
                                        ) {
                                            repository.pendingPlayback = PlaybackRequest(
                                                streamUrl = item.streamUrl,
                                                title = item.title,
                                                poster = item.poster,
                                                videoId = item.videoId,
                                                imdbId = item.imdbId,
                                                season = item.season,
                                                episode = item.episode,
                                                isMovie = item.season == 0 && item.episode == 0,
                                                type = if (item.season == 0 && item.episode == 0) "movie" else "series"
                                            )
                                            onResumePlayback()
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                        // Watchlist intentionally NOT shown here (v0.8.21):
                        // bookmarked titles live only in the Watchlist tab.
                        state.rows.forEach { row ->
                            // THE v0.3.0 scroll-crash fix: the key MUST include the
                            // catalog TYPE. Cinemeta declares the same catalog ids
                            // ("top", "year", "imdbRating") for both movie and
                            // series — without the type, two rows shared one key
                            // and LazyColumn threw, closing the app on scroll.
                            item(key = "${row.addonId}|${row.type}|${row.catalogId}") {
                                SectionTitle(
                                    row.catalogName,
                                    // Don't repeat the addon name when the catalog name
                                    // already starts with it ("PlavioMeta: Trending
                                    // Movies" + "PlavioMeta" looks broken).
                                    row.addonName.takeIf {
                                        !row.catalogName.startsWith(it, ignoreCase = true)
                                    },
                                    onMoreClick = { onOpenCatalog(row) }
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    itemsIndexed(
                                        row.items,
                                        key = { index, meta -> "${row.addonId}_${meta.id}_$index" }
                                    ) { _, meta ->
                                        PosterCard(
                                            meta = meta,
                                            onClick = { onOpenDetail(row.addonId, meta.type, meta.id) }
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                    }
                }
            }
        }
    }
}

/** One featured title for the hero carousel: its source addon plus metadata. */
private data class FeaturedTitle(val addonId: String, val meta: MetaPreview)

/**
 * Picks up to 6 featured titles for the hero carousel: titles with real
 * backdrop art first (falling back to poster-only titles so the carousel
 * never comes up empty when data is thin).
 */
private fun featuredTitles(rows: List<CatalogRow>, max: Int = 6): List<FeaturedTitle> {
    val all = rows.flatMap { row -> row.items.map { FeaturedTitle(row.addonId, it) } }
        .distinctBy { it.meta.id }
    if (all.isEmpty()) return emptyList()
    val withBackdrop = all.filter { !it.meta.background.isNullOrBlank() }
    return (withBackdrop + all).distinctBy { it.meta.id }.take(max)
}

/**
 * Netflix-style hero carousel: full-bleed backdrop pager at the top of
 * Home. Auto-advances every 5s, pausing while the user is touching it.
 * Tap opens the title's detail screen.
 */
@Composable
private fun HeroCarousel(
    featured: List<FeaturedTitle>,
    onOpenDetail: (addonId: String, type: String, metaId: String) -> Unit,
    heroFocus: FocusRequester? = null
) {
    val pagerState = rememberPagerState(pageCount = { featured.size })

    LaunchedEffect(pagerState, featured.size) {
        while (featured.size > 1) {
            delay(5000)
            if (!pagerState.isScrollInProgress) {
                val next = (pagerState.currentPage + 1) % featured.size
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
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(22.dp))
                .border(1.dp, GlassBorder, RoundedCornerShape(22.dp)),
            key = { index -> "hero_${featured[index].meta.id}_$index" }
        ) { index ->
            val item = featured[index]
            val meta = item.meta
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onOpenDetail(item.addonId, meta.type, meta.id) }
                    .then(if (heroFocus != null && index == 0) Modifier.focusRequester(heroFocus) else Modifier)
                    .focusable()
                    .tvFocusRing(RoundedCornerShape(22.dp))
            ) {
                SafePosterImage(
                    model = meta.background?.takeIf { it.isNotBlank() } ?: meta.poster,
                    contentDescription = meta.name,
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
                        text = meta.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val info = listOfNotNull(
                        meta.releaseInfo?.take(4),
                        meta.imdbRating?.let { "★ $it" },
                        meta.genres.firstOrNull()
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
                }
            }
        }
        // Position dots.
        if (featured.size > 1) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(featured.size) { i ->
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

@Composable
private fun ContinueWatchingCard(
    item: PlaybackState,
    onRemove: () -> Unit,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(200.dp)
            .clickable(onClick = onClick)
            .focusable()
            .tvFocusRing(RoundedCornerShape(14.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.05f))
        ) {
            SafePosterImage(
                model = item.poster,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize()
            )
            // Visible remove button: live channels can leak into Continue
            // Watching with no expiry, so the user must be able to clear
            // any entry by hand.
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(28.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.65f))
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Remove from Continue Watching",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
            if (item.durationMs > 0) {
                LinearProgressIndicator(
                    progress = { (item.positionMs.toFloat() / item.durationMs).coerceIn(0f, 1f) },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
                    color = LocalPalette.current.accent,
                    trackColor = Color.Black.copy(alpha = 0.5f)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val remainingMs = item.durationMs - item.positionMs
        if (remainingMs > 60_000) {
            Text(
                text = "${formatRemaining(remainingMs)} left",
                style = MaterialTheme.typography.labelSmall.copy(
                    brush = LocalPalette.current.gradient
                )
            )
        } else {
            Text(
                text = "Resume",
                style = MaterialTheme.typography.labelSmall.copy(
                    brush = LocalPalette.current.gradient
                )
            )
        }
    }
}

/** "50m left" / "1h 20m left" for Continue Watching cards. */
private fun formatRemaining(ms: Long): String {
    val totalMin = (ms / 60_000).toInt().coerceAtLeast(1)
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
