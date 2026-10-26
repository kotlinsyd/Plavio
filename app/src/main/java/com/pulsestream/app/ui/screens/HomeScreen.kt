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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulsestream.app.data.CatalogRow
import com.pulsestream.app.data.MetaPreview
import com.pulsestream.app.data.PlaybackRequest
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.data.db.PlaybackState
import com.pulsestream.app.ui.components.ErrorState
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.components.OrbitDots
import com.pulsestream.app.data.CustomListsRepository
import com.pulsestream.app.ui.components.AddToListSheet
import com.pulsestream.app.ui.components.LoadingState
import com.pulsestream.app.ui.components.PosterCard
import com.pulsestream.app.ui.components.SafePosterImage
import com.pulsestream.app.ui.components.SectionTitle
import com.pulsestream.app.ui.components.liquidGlass
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.GlassBorder
import com.pulsestream.app.ui.theme.GlassWhite
import com.pulsestream.app.ui.components.tmdbSizedImage
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Dock → Home scroll-to-top signal (v0.12.4): the bottom dock's Home tab
 * bumps [requests] whenever it's tapped while unfolded; HomeScreen
 * collects it and smooth-scrolls its list to the very top. A plain
 * counter (not an event bus) so a tap that lands while Home is being
 * restored from the back stack still takes effect on arrival.
 */
object HomeScrollSignal {
    val requests = MutableStateFlow(0)
    fun requestScrollToTop() {
        requests.value += 1
    }
}

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
                val cached = repo.loadCatalogRowsCached().filter { it.type != "tv" }
                if (cached.isNotEmpty()) {
                    val stale = !repo.isCatalogCacheFresh()
                    uiState = HomeUiState.Content(cached, refreshing = stale)
                    if (stale) {
                        runCatching { repo.refreshCatalogRows().filter { it.type != "tv" } }
                            .onSuccess { uiState = HomeUiState.Content(it) }
                            .onFailure { uiState = HomeUiState.Content(cached) }
                    }
                    return@launch
                }
            }
            uiState = HomeUiState.Loading
            try {
                // Live TV has its own tab — never show tv-type rows on Home
                // (hero or cards).
                val rows = repo.refreshCatalogRows().filter { it.type != "tv" }
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
            runCatching { repo.refreshCatalogRows().filter { it.type != "tv" } }
                .onSuccess { uiState = HomeUiState.Content(it) }
                .onFailure { uiState = HomeUiState.Content(current) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: StreamRepository,
    listsRepository: CustomListsRepository,
    onOpenDetail: (addonId: String, type: String, metaId: String) -> Unit,
    onResumePlayback: () -> Unit,
    onGoToAddons: () -> Unit,
    onGoToWatchlist: () -> Unit,
    onGoToLists: () -> Unit,
    onOpenCatalog: (row: CatalogRow) -> Unit,
    onGoToDownloads: () -> Unit = {},
    onGoToLive: () -> Unit = {},
) {
    val vm: HomeViewModel = viewModel(factory = vmFactory { HomeViewModel(repository) })
    val watching by repository.continueWatching.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    // Long-press context menu target: (addonId, meta) for the pressed poster.
    var contextMenuTarget by remember { mutableStateOf<Pair<String, MetaPreview>?>(null) }
    // Add-to-list sheet target: (addonId, meta).
    var addToListTarget by remember { mutableStateOf<Pair<String, MetaPreview>?>(null) }
    // Watchlist lives ONLY in the Watchlist tab (top-right bookmark) — it is
    // deliberately not shown on Home (v0.8.21 behavior fix).
    // Reload when Home catalog visibility changes in Settings.
    val catalogVersion by repository.catalogVisibility.collectAsState(initial = 0)

    LaunchedEffect(catalogVersion) { vm.load() }

    // The status-bar inset the Scaffold already padded away above this
    // screen: the Content list shifts up by exactly this amount so the
    // immersive hero reaches the very top of the screen, under the
    // status bar (v0.12.4, Sample C).
    val density = LocalDensity.current
    val statusBarDp = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
    val statusBarPx = with(density) { statusBarDp.roundToPx() }

    // Android TV: initial D-pad focus target (the hero card).
    val isTv = rememberIsTv()
    val heroFocus = remember { FocusRequester() }
    // Land focus on the hero ONCE per Home visit — never yank it back on
    // every catalog refresh while the user is browsing the rows.
    var heroFocusDone by remember { mutableStateOf(false) }

    GlowBackground {
        // No header (v0.12.3, owner's order): the "Plavio" wordmark +
        // tagline row is gone, so the screen starts clear and the hero
        // poster owns the full top of the screen. The one functional
        // control the header held — the Saved (Watchlist / My Lists)
        // button — floats overlaid at the top end (below, after the
        // content states).
        Box(Modifier.fillMaxSize()) {
            when (val state = vm.uiState) {
                is HomeUiState.Loading -> LoadingState("Loading your catalogs…")
                is HomeUiState.Error -> ErrorState(state.message, onRetry = { vm.load() })
                is HomeUiState.Empty -> NoAddonsEmptyState(
                    repository = repository,
                    onGoToAddons = onGoToAddons,
                    onReload = { vm.load() }
                )
                is HomeUiState.Content -> {
                    // Hero carousel data: computed once per row-set, outside the
                    // LazyColumn DSL (remember can't run inside the list scope).
                    val featured = remember(state.rows) { featuredTitles(state.rows) }
                    // Dock Home-tap → scroll to the very top (v0.12.4).
                    // handledScrollRequest is saveable so a request fired
                    // while Home sat in the back stack is honoured once,
                    // on return, and never replayed on later visits.
                    val homeListState = rememberLazyListState()
                    var handledScrollRequest by rememberSaveable {
                        mutableStateOf(HomeScrollSignal.requests.value)
                    }
                    val scrollRequests by HomeScrollSignal.requests.collectAsState()
                    LaunchedEffect(scrollRequests) {
                        if (scrollRequests != handledScrollRequest) {
                            handledScrollRequest = scrollRequests
                            runCatching { homeListState.animateScrollToItem(0) }
                        }
                    }
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
                            // Refresh indicator: the signature orbit
                            // (owner's order 2026-10-26 — supersedes the
                            // Wave dots pick of 2026-10-25; the orbit is
                            // the app's one loading circle everywhere).
                            if (state.refreshing || pullRefreshState.distanceFraction > 0f) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = 18.dp)
                                ) {
                                    OrbitDots(modifier = Modifier.size(38.dp))
                                }
                            }
                        }
                    ) {
                    LazyColumn(
                        state = homeListState,
                        modifier = Modifier
                            .fillMaxSize()
                            // Full-bleed BOTH ends (v0.12.12): the column
                            // is measured statusBarPx TALLER than its
                            // parent and placed shifted up by the same
                            // amount, so the hero reaches the screen's
                            // top edge AND the viewport reaches the
                            // physical bottom edge — cards scroll under
                            // and below the floating dock to the very
                            // edge. The old plain .offset() shifted the
                            // column up WITHOUT the extra height, ending
                            // the viewport short of the bottom and
                            // leaving a solid strip under the dock.
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(
                                    constraints.copy(
                                        minHeight = constraints.maxHeight + statusBarPx,
                                        maxHeight = constraints.maxHeight + statusBarPx
                                    )
                                )
                                layout(placeable.width, placeable.height) {
                                    placeable.place(0, -statusBarPx)
                                }
                            },
                        // Extra bottom room so the last row scrolls clear
                        // of the floating dock (content now runs under it).
                        contentPadding = PaddingValues(bottom = 104.dp)
                    ) {
                        // Hero carousel: featured titles with backdrop art, first item
                        // so it's the first thing on screen. Explicit key keeps it
                        // stable while rows refresh underneath.
                        if (featured.isNotEmpty()) {
                            item(key = "hero_carousel") {
                                // Pinned hero (v0.12.5, owner's order): the
                                // hero stays at the top while the catalog
                                // sheet scrolls UP AND OVER it. The hero is
                                // still a normal list item (pager swipe,
                                // tap and TV focus untouched), but its draw
                                // position is counter-translated by exactly
                                // its scrolled-off amount, so it visually
                                // never leaves the top; the sections below
                                // are drawn after it, hence over it. At rest
                                // the translation is 0 and the first section
                                // starts exactly at the hero's bottom
                                // border — the round-5 -22dp static overlap
                                // (which also left a 22dp dead band in the
                                // layout under the first row) is gone.
                                Box(
                                    Modifier.graphicsLayer {
                                        val heroInfo = homeListState.layoutInfo
                                            .visibleItemsInfo
                                            .firstOrNull { it.key == "hero_carousel" }
                                        translationY = if (heroInfo != null && heroInfo.offset < 0)
                                            -heroInfo.offset.toFloat() else 0f
                                    }
                                ) {
                                    HeroCarousel(featured, onOpenDetail, heroFocus)
                                }
                            }
                        }
                        if (watching.isNotEmpty()) {
                            // Explicit key: this section appears/disappears as the
                            // Flow emits, so an index-based default key would
                            // reshuffle every row below it on each emission.
                            item(key = "continue_watching") {
                              Column(
                                // Scroll-over sheet (v0.12.5): sections
                                // carry the solid app background so the
                                // sheet reads cleanly (headers stay
                                // legible) while it slides over the
                                // pinned hero.
                                Modifier.background(LocalPalette.current.background)
                              ) {
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
                                Spacer(Modifier.height(4.dp))
                              }
                            }
                        }
                        // Watchlist intentionally NOT shown here (v0.8.21):
                        // bookmarked titles live only in the Watchlist tab.
                        state.rows.forEachIndexed { rowIndex, row ->
                            // THE v0.3.0 scroll-crash fix: the key MUST include the
                            // catalog TYPE. Cinemeta declares the same catalog ids
                            // ("top", "year", "imdbRating") for both movie and
                            // series — without the type, two rows shared one key
                            // and LazyColumn threw, closing the app on scroll.
                            item(key = "${row.addonId}|${row.type}|${row.catalogId}") {
                              Column(
                                // Scroll-over sheet (v0.12.5): same solid
                                // background as Continue Watching — the
                                // whole sheet slides over the pinned hero
                                // as one readable surface.
                                Modifier.background(LocalPalette.current.background)
                              ) {
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
                                    ) { index, meta ->
                                        PosterCard(
                                            meta = meta,
                                            showRatingChip = true,
                                            onClick = { onOpenDetail(row.addonId, meta.type, meta.id) },
                                            onLongClick = { contextMenuTarget = row.addonId to meta }
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                              }
                            }
                        }
                    }
                    }
                }
            }

            // Top-right of Home: the Live pill alone now (the old
            // floating Saved/bookmark button was removed 2026-10-24 —
            // Watchlist + My Lists live in the Library tab).
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Live pill (owner's order 2026-10-24): Live left the
                // dock for the Library tab — it lives here now, at
                // Home's top-right. Real glass (owner's device verdict
                // 2026-10-26: the plain liquidGlass call read as a solid
                // pill) — the dock's exact treatment: a fitted backing
                // pill behind glass with explicit frost/darken.
                Box(Modifier.height(42.dp)) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (LocalPalette.current.light) {
                                    Color.White.copy(alpha = 0.82f)
                                } else {
                                    Color.Black.copy(alpha = 0.48f)
                                },
                                RoundedCornerShape(50)
                            )
                    )
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .liquidGlass(
                                RoundedCornerShape(50),
                                frost = 0.12f,
                                darken = 0.03f
                            )
                            .clickable { onGoToLive() }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .background(Color(0xFFFF4D5E), CircleShape)
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Filled.LiveTv,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Live",
                            color = TextPrimary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    }
                }
            }
        }
    }

    // Long-press context menu: Add to Watchlist / Add to My List.
    contextMenuTarget?.let { (addonId, meta) ->
        Dialog(onDismissRequest = { contextMenuTarget = null }) {
            Column(
                modifier = Modifier
                    .liquidGlass(RoundedCornerShape(20.dp))
                    .padding(vertical = 8.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                Text(
                    text = meta.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                TextButton(
                    onClick = {
                        scope.launch {
                            repository.toggleWatchlist(addonId, meta.type, meta.id, meta.name, meta.poster)
                        }
                        contextMenuTarget = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Filled.Bookmark,
                        contentDescription = null,
                        tint = LocalPalette.current.accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Add to Watchlist", color = TextPrimary)
                }
                TextButton(
                    onClick = {
                        addToListTarget = addonId to meta
                        contextMenuTarget = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Filled.FormatListBulleted,
                        contentDescription = null,
                        tint = LocalPalette.current.accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Add to My List", color = TextPrimary)
                }
            }
        }
    }

    // Add-to-list bottom sheet.
    addToListTarget?.let { (addonId, meta) ->
        AddToListSheet(
            listsRepository = listsRepository,
            addonId = addonId,
            type = meta.type,
            metaId = meta.id,
            name = meta.name,
            poster = meta.poster,
            onDismiss = { addToListTarget = null }
        )
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
 * Home. Auto-advances every 5s on mobile (pausing while the user is touching
 * it); never auto-advances on TV to avoid stealing frames from D-pad input.
 * Tap opens the title's detail screen.
 *
 * v0.12.4 (owner's Sample C — "immersive"): the pager is now truly
 * full-bleed — edge-to-edge with no side padding, no rounded frame —
 * the list itself is shifted up under the status bar so the art owns
 * the screen's top edge; the swipe dots moved INSIDE the poster
 * (animated, active = elongated mint pill).
 *
 * v0.12.5 (owner's order): ~20% taller again — 380dp (was 316dp) — and
 * the hero is PINNED: it stays at the top while the catalog sheet
 * scrolls up and over it (the pin lives on the hero's list item in
 * [HomeScreen]; the pager itself is unchanged).
 */
@Composable
private fun HeroCarousel(
    featured: List<FeaturedTitle>,
    onOpenDetail: (addonId: String, type: String, metaId: String) -> Unit,
    heroFocus: FocusRequester? = null
) {
    val pagerState = rememberPagerState(pageCount = { featured.size })
    val isTv = rememberIsTv()
    // Hero height: TV keeps the 380.dp backdrop hero. On mobile the hero
    // takes 45% of the screen height with the poster filling it
    // edge-to-edge (owner's device verdict 2026-10-24 — 45% chosen after
    // the same-day 40% fix, itself superseding the 60% full-poster
    // version, which stood narrow with blurred side bars and was
    // rejected on sight).
    val heroHeight = if (isTv) 380.dp
        else (LocalConfiguration.current.screenHeightDp * 0.45f).dp + 40.dp

    val reducedMotion = com.pulsestream.app.data.rememberReducedMotion()
    LaunchedEffect(pagerState, featured.size, reducedMotion) {
        // No auto-advance on TV: the 5s full-bleed pager animation steals
        // frames from D-pad browsing on weak TV GPUs/CPUs and its backdrop
        // re-decodes cause jank spikes that coincide with user input.
        if (isTv || reducedMotion) return@LaunchedEffect
        while (featured.size > 1) {
            delay(5000)
            if (!pagerState.isScrollInProgress) {
                val next = (pagerState.currentPage + 1) % featured.size
                runCatching { pagerState.animateScrollToPage(next) }
            }
        }
    }

    Box {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight),
            key = { index -> "hero_${featured[index].meta.id}_$index" }
        ) { index ->
            val item = featured[index]
            val meta = item.meta
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .tvFocusRing(RoundedCornerShape(0.dp))
                    .clickable { onOpenDetail(item.addonId, meta.type, meta.id) }
                    .then(if (heroFocus != null && index == 0) Modifier.focusRequester(heroFocus) else Modifier)
                    .focusable()
            ) {
                if (isTv || meta.poster.isNullOrBlank()) {
                    SafePosterImage(
                        model = meta.background?.takeIf { it.isNotBlank() } ?: meta.poster,
                        contentDescription = meta.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // Mobile hero: the poster fills the hero edge-to-edge
                    // — full width, ContentScale.Crop, the same treatment
                    // as the detail-page hero (the top/bottom crop of the
                    // 2:3 poster is intended).
                    SafePosterImage(
                        model = meta.poster,
                        contentDescription = meta.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
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
                            text = com.pulsestream.app.ui.components.accentStarText(info, LocalPalette.current.accent),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        // Swipe dots INSIDE the poster's lower edge (v0.12.4): the active
        // dot stretches into an elongated mint pill, animated.
        if (featured.size > 1) {
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(featured.size) { i ->
                    val active = i == pagerState.currentPage
                    val dotWidth by animateDpAsState(
                        targetValue = if (active) 18.dp else 6.dp,
                        animationSpec = tween(320),
                        label = "heroDot$i"
                    )
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .width(dotWidth)
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (active) LocalPalette.current.accent
                                else Color.White.copy(alpha = 0.35f)
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
            .tvFocusRing(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .focusable()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .liquidGlass(RoundedCornerShape(14.dp))
        ) {
            SafePosterImage(
                model = tmdbSizedImage(item.poster, "w780"),
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
        // H-A (owner's pick 2026-10-24): the slot under a Continue
        // Watching title carries the time left as a quiet micro label —
        // the same slot catalog cards use for the popularity meter, and
        // no meter here. (PlaybackState has no rating, so no rating
        // chip on these cards.)
        val slotStyle = MaterialTheme.typography.labelSmall.copy(
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )
        if (remainingMs > 60_000) {
            Text(
                text = "${formatRemaining(remainingMs)} LEFT".uppercase(),
                style = slotStyle,
                color = TextSecondary,
                maxLines = 1
            )
        } else {
            Text(
                text = "RESUME",
                style = slotStyle,
                color = TextSecondary,
                maxLines = 1
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

/** Starter addons for the guided empty-state steps (Sample C). */
private const val META_ADDON_URL = "https://plaviometa.vercel.app/manifest.json"
private const val STREAMS_ADDON_URL = "https://hd.plavio.site/manifest.json"

private enum class SetupStepState { Idle, Adding, Added, Failed }

/**
 * "No addons yet" — Sample C (owner's pick 2026-10-24): two guided
 * steps instead of one vague button. Metadata first, streams second,
 * each with its own one-tap Add (the same install path as onboarding);
 * the ghost button below opens the full Addons screen for any URL.
 */
@Composable
private fun NoAddonsEmptyState(
    repository: StreamRepository,
    onGoToAddons: () -> Unit,
    onReload: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var metaStep by remember { mutableStateOf(SetupStepState.Idle) }
    var streamsStep by remember { mutableStateOf(SetupStepState.Idle) }

    fun add(url: String, set: (SetupStepState) -> Unit) {
        set(SetupStepState.Adding)
        scope.launch {
            val result = repository.addAddonFromUrl(url)
            if (result.isSuccess) {
                set(SetupStepState.Added)
                onReload()
            } else {
                set(SetupStepState.Failed)
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 58.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "Set up your home",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            "Two kinds of addons fill this screen. Add one of each, or any addon by URL.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Spacer(Modifier.height(2.dp))
        SetupStepRow(
            number = 1,
            title = "Metadata",
            subtitle = "Posters, cast and episode guides \u2014 e.g. PlavioMeta",
            state = metaStep,
            onAdd = { add(META_ADDON_URL) { metaStep = it } }
        )
        SetupStepRow(
            number = 2,
            title = "Streams",
            subtitle = "Movies and shows to actually play \u2014 e.g. PlavioHD",
            state = streamsStep,
            onAdd = { add(STREAMS_ADDON_URL) { streamsStep = it } }
        )
        Text(
            "Or tap \u201CAdd an addon\u201D below to paste any addon URL.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Box(
            Modifier
                .fillMaxWidth()
                .background(GlassWhite, RoundedCornerShape(50))
                .border(1.dp, GlassBorder, RoundedCornerShape(50))
                .clickable { onGoToAddons() }
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Add an addon",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
    }
}

@Composable
private fun SetupStepRow(
    number: Int,
    title: String,
    subtitle: String,
    state: SetupStepState,
    onAdd: () -> Unit
) {
    val palette = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .liquidGlass(RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(26.dp)
                .background(palette.accent.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$number",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = palette.accentText
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            if (state == SetupStepState.Failed) {
                Text(
                    "Couldn't add it \u2014 check your connection and try again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        when (state) {
            SetupStepState.Adding -> OrbitDots(modifier = Modifier.size(22.dp))
            SetupStepState.Added -> Icon(
                Icons.Filled.Check,
                contentDescription = "Added",
                tint = palette.accent,
                modifier = Modifier.size(22.dp)
            )
            else -> Box(
                Modifier
                    .background(palette.accent, RoundedCornerShape(50))
                    .clickable { onAdd() }
                    .padding(horizontal = 13.dp, vertical = 7.dp)
            ) {
                Text(
                    if (state == SetupStepState.Failed) "Retry" else "Add",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF04120B)
                )
            }
        }
    }
}

