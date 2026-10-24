package com.pulsestream.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.res.painterResource
import com.pulsestream.app.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Card
import com.pulsestream.app.ui.components.OrbitDots
import com.pulsestream.app.ui.components.ShimmerPoster
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulsestream.app.ui.components.AddToListSheet
import com.pulsestream.app.ui.components.PlayButton
import com.pulsestream.app.ui.components.WatchedPill
import com.pulsestream.app.ui.components.SafePosterImage
import com.pulsestream.app.data.MetaDetail
import com.pulsestream.app.data.PlaybackRequest
import com.pulsestream.app.data.BrowsingPrivacy
import com.pulsestream.app.data.TrailerResolver
import com.pulsestream.app.data.TmdbClient
import com.pulsestream.app.data.RelatedSection
import com.pulsestream.app.data.StreamItem
import com.pulsestream.app.data.StreamProber
import com.pulsestream.app.data.CustomListsRepository
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.data.Subtitle
import com.pulsestream.app.data.Video
import com.pulsestream.app.data.streamQualityLabel
import com.pulsestream.app.data.SUBTITLE_LANGUAGE_OPTIONS
import com.pulsestream.app.ui.components.availableStreamQualities
import com.pulsestream.app.ui.components.parseStreamInfo
import com.pulsestream.app.ui.components.ErrorState
import com.pulsestream.app.ui.components.GlassCard
import com.pulsestream.app.ui.components.liquidGlass
import androidx.compose.ui.draw.alpha
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.components.LoadingState
import com.pulsestream.app.ui.components.AddonTabs
import com.pulsestream.app.ui.components.pickAutoplayStream
import com.pulsestream.app.ui.components.rememberStreamBrowserState
import com.pulsestream.app.ui.components.StreamCard
import com.pulsestream.app.ui.components.StreamList
import com.pulsestream.app.ui.components.streamListItems
import com.pulsestream.app.ui.components.DownloadConfirmDialog
import com.pulsestream.app.ui.components.StreamTab
import com.pulsestream.app.ui.components.addonKey
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.GlassBorder
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import androidx.compose.ui.focus.focusProperties
import com.pulsestream.app.ui.tv.rememberIsTv
import com.pulsestream.app.ui.tv.tvFocusRing
import com.pulsestream.app.ui.tv.tvSheetFocus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed interface MetaUiState {
    data object Loading : MetaUiState
    data class Content(val meta: MetaDetail) : MetaUiState
    data class Error(val message: String) : MetaUiState
}

sealed interface StreamsUiState {
    data object Idle : StreamsUiState
    data object Loading : StreamsUiState
    data class Content(val streams: List<StreamItem>, val subtitles: List<Subtitle>, val loadingMore: Boolean = false) : StreamsUiState
    data class Error(val message: String) : StreamsUiState
}

/** Fired when the user pressed Play: play this stream immediately. */
data class AutoPlay(val stream: StreamItem, val subtitles: List<Subtitle>)

/** State of the "Plavio Pick" background stream check. */
sealed interface PickState {
    data object Idle : PickState
    data object Probing : PickState
    data class Found(val stream: StreamItem) : PickState
    data object None : PickState
}

class DetailViewModel(private val repo: StreamRepository) : ViewModel() {
    /** Set from the composable; used for TV-aware probing headers. */
    var isTv: Boolean = false
    var metaState by mutableStateOf<MetaUiState>(MetaUiState.Loading)
        private set
    var streamsState by mutableStateOf<StreamsUiState>(StreamsUiState.Idle)
        private set
    var selectedSeason by mutableStateOf<Int?>(null)
        private set
    var selectedVideo by mutableStateOf<Video?>(null)
        private set
    /** Shuffle episodes (v0.9.0+): random pick instead of sequential. Backed by SettingsStore. */
    var shuffleEpisodes by mutableStateOf(repo.settings.shuffleEpisodes)
        private set
    var autoPlayEvent by mutableStateOf<AutoPlay?>(null)
        private set
    /** "Plavio Pick": best verified stream, probed in the background. */
    var pickState by mutableStateOf<PickState>(PickState.Idle)
        private set
    /** "More like this" + "More from <actor>" rows from PlavioMeta. */
    var relatedState by mutableStateOf<List<RelatedSection>>(emptyList())
        private set

    /** Set when the user pressed Play while streams were still loading. */
    private var pendingAutoPlay = false
    /** In-flight stream collection — cancelled when a new one starts. */
    private var streamsJob: Job? = null
    /** In-flight Plavio Pick probe — cancelled when streams reload. */
    private var probeJob: Job? = null
    /** Base URL of the addon the title was opened from — tried first for metadata. */
    private var hintBaseUrl: String? = null

    fun load(hintBaseUrl: String?, type: String, id: String) {
        this.hintBaseUrl = hintBaseUrl
        // Drop stale related rows immediately — the new fetch is async and the
        // old title's rows would flash until it returns.
        relatedState = emptyList()
        viewModelScope.launch {
            metaState = MetaUiState.Loading
            try {
                // Metadata may live in a different addon than the catalog:
                // try every meta-capable addon until one succeeds.
                val meta = repo.getMetaFromAny(hintBaseUrl, type, id)
                if (meta == null) {
                    metaState = MetaUiState.Error("Couldn't find details for this title in any installed addon.")
                } else {
                    metaState = MetaUiState.Content(meta)
                    loadRelated(meta.type, meta.id)
                    if (meta.type == "series" && meta.videos.isNotEmpty()) {
                        val seasons = meta.videos.map { it.season }.distinct().sorted()
                        selectSeason(seasons.first(), meta)
                    } else {
                        loadStreams(meta.type, meta.id)
                    }
                }
            } catch (e: Exception) {
                metaState = MetaUiState.Error(e.message ?: "Couldn't load details")
            }
        }
    }

    fun selectSeason(season: Int, meta: MetaDetail) {
        selectedSeason = season
        val episodes = meta.videos.filter { it.season == season }
            .sortedWith(compareBy({ it.episode }, { it.title }))
        val pick = if (shuffleEpisodes) episodes.randomOrNull() else episodes.firstOrNull()
        if (pick != null) selectEpisode(pick)
    }

    /** Toggle shuffle mode; enabling it immediately jumps to a random episode. */
    fun updateShuffle(enabled: Boolean) {
        shuffleEpisodes = enabled
        repo.settings.shuffleEpisodes = enabled
        if (enabled) selectRandomEpisode()
    }

    /** Picks a random episode from the current season and selects it. */
    fun selectRandomEpisode() {
        val meta = (metaState as? MetaUiState.Content)?.meta ?: return
        val episodes = meta.videos.filter { it.season == selectedSeason }
        val pick = episodes.randomOrNull() ?: return
        selectEpisode(pick)
    }

    fun selectEpisode(video: Video) {
        selectedVideo = video
        val meta = (metaState as? MetaUiState.Content)?.meta ?: return
        loadStreams(meta.type, video.id)
    }

    fun retryLoad(repo: StreamRepository, addonId: String, type: String, metaId: String) {
        viewModelScope.launch {
            val baseUrl = repo.getAddonBaseUrl(addonId)
            load(baseUrl, type, metaId)
        }
    }

    /**
     * Streams may live in different addons than catalogs/metadata.
     * Collects the progressive merged flow: sources appear (and autoplay
     * fires) as soon as the first addon answers instead of waiting for
     * every addon — the fix for slow stream loading.
     */
    fun loadStreams(type: String, videoId: String) {
        streamsJob?.cancel()
        probeJob?.cancel()
        pickState = PickState.Idle
        streamsJob = viewModelScope.launch {
            streamsState = StreamsUiState.Loading
            var gotAny = false
            try {
                repo.streamResultsFlow(type, videoId).collect { result ->
                    if (result.streams.isNotEmpty()) {
                        gotAny = true
                        streamsState = StreamsUiState.Content(result.streams, result.subtitles, loadingMore = true)
                        maybeProbePick(result.streams)
                        if (pendingAutoPlay) {
                            pendingAutoPlay = false
                            playBestOrFirst()
                        }
                    }
                    // Empty intermediate emissions: stay on Loading, don't flash an error.
                }
                // v0.8.29 fix: transient failures (cold-start timeouts, dispatcher
                // contention) could empty the first pass while a manual retry
                // succeeded. Do automatic retries with backoff before showing
                // "no sources" so users never see a false empty state.
                var attempt = 0
                while (!gotAny && attempt < 3) {
                    attempt++
                    kotlinx.coroutines.delay(1500L * attempt)
                    repo.streamResultsFlow(type, videoId).collect { result ->
                        if (result.streams.isNotEmpty()) {
                            gotAny = true
                            streamsState = StreamsUiState.Content(result.streams, result.subtitles, loadingMore = true)
                            maybeProbePick(result.streams)
                            if (pendingAutoPlay) {
                                pendingAutoPlay = false
                                playBestOrFirst()
                            }
                        }
                    }
                }
                if (gotAny) {
                    // Collection is over: the Watch Now side-spinner stops,
                    // whatever is on screen is the final list.
                    val settled = streamsState as? StreamsUiState.Content
                    if (settled != null && settled.loadingMore) {
                        streamsState = settled.copy(loadingMore = false)
                    }
                }
                if (!gotAny) {
                    streamsState = StreamsUiState.Error("No playable sources found in any installed addon.")
                }
            } catch (e: Exception) {
                if (!gotAny) streamsState = StreamsUiState.Error(e.message ?: "Couldn't load sources")
            }
        }
    }

    /**
     * Starts (or restarts, when the merged list grew) the background Plavio Pick
     * probe. Keeps an already-found winner — no churn while sources stream in.
     */
    private fun maybeProbePick(streams: List<StreamItem>) {
        // Adaptive quality cap (user's order 2026-10-20): only when the user
        // left quality on "Auto".
        val cap = if (repo.settings.autoplayQuality == "Auto")
            repo.settings.adaptiveQualityCap() else null
        val found = pickState as? PickState.Found
        if (found != null) {
            // Keep the winner — UNLESS it violates the cap. A 4K pick could have
            // locked in from the first addon's early emission, before a 1080p
            // option from a slower addon ever arrived; then re-probe.
            val capRank = cap?.let { com.pulsestream.app.data.StreamProber.qualityRank(it) }
            if (capRank == null ||
                com.pulsestream.app.data.StreamProber.qualityRank(found.stream) <= capRank) return
        }
        probeJob?.cancel()
        probeJob = viewModelScope.launch {
            pickState = PickState.Probing
            val best = StreamProber.findBest(streams.filter { com.pulsestream.app.data.isPlayableLiveStreamUrl(it.url) }, isTv, cap)
            pickState = if (best != null) PickState.Found(best) else PickState.None
            // Warm the winner's connection so Play starts without the
            // DNS + TLS cost (shared pool — see PlaybackHttp).
            if (best != null) com.pulsestream.app.data.PlaybackHttp.warmUp(best.url)
        }
    }

    /**
     * Play button: always tries the fastest verified stream first.
     * If the background probe already found a winner, play it. If the probe
     * is still running, give it a brief moment, then play the winner or fall
     * back to the first available stream.
     */
    fun onPlayPressed() {
        when (val s = streamsState) {
            is StreamsUiState.Content -> playBestOrFirst()
            is StreamsUiState.Error -> { /* nothing playable — leave the error visible */ }
            else -> {
                pendingAutoPlay = true
                // Make sure a load is in flight for the current selection.
                val meta = (metaState as? MetaUiState.Content)?.meta
                if (meta != null && s is StreamsUiState.Idle) {
                    val videoId = if (meta.type == "series") selectedVideo?.id ?: meta.id else meta.id
                    loadStreams(meta.type, videoId)
                }
            }
        }
    }

    private fun playBestOrFirst() {
        val s = streamsState as? StreamsUiState.Content ?: return
        // Only consider playable streams - skip unresolved infohash: placeholders.
        // Debrid resolution replaces these with real HTTP links asynchronously.
        val playableStreams = s.streams.filter { com.pulsestream.app.data.isPlayableLiveStreamUrl(it.url) }
        if (playableStreams.isEmpty()) { pendingAutoPlay = true; return }
        // Settings > Streams autoplay: pick by the user's quality/size rule
        // instead of the probed fastest stream.
        if (repo.settings.streamAutoplay) {
            val ruled = pickAutoplayStream(playableStreams, repo.settings)
            if (ruled != null) {
                autoPlayEvent = AutoPlay(ruled, s.subtitles)
                return
            }
            // No playable streams yet - wait for debrid resolution instead of
            // playing an unresolvable placeholder.
            return
        }
        val pick = pickState as? PickState.Found
        if (pick != null && !pick.stream.url.startsWith("infohash:")) {
            autoPlayEvent = AutoPlay(pick.stream, s.subtitles)
            return
        }
        if (pickState is PickState.Probing) {
            viewModelScope.launch {
                withTimeoutOrNull(2500) { probeJob?.join() }
                val best = pickState as? PickState.Found
                val cur = streamsState as? StreamsUiState.Content ?: return@launch
                val curPlayable = cur.streams.filter { com.pulsestream.app.data.isPlayableLiveStreamUrl(it.url) }
                if (best != null && !best.stream.url.startsWith("infohash:")) autoPlayEvent = AutoPlay(best.stream, cur.subtitles)
                else if (curPlayable.isNotEmpty()) autoPlayEvent = AutoPlay(curPlayable.first(), cur.subtitles)
            }
            return
        }
        if (playableStreams.isNotEmpty()) autoPlayEvent = AutoPlay(playableStreams.first(), s.subtitles)
    }

    fun consumeAutoPlay(): AutoPlay? {
        val e = autoPlayEvent
        autoPlayEvent = null
        return e
    }

    /**
     * "More like this" + "More from <actor>" rows. Needs an IMDb tt id
     * (Cinemeta/PlavioMeta ids); quietly empty for anything else.
     */
    fun loadRelated(type: String, metaId: String) {
        viewModelScope.launch {
            // Uses the user's configured PlavioMeta (TMDB key when set);
            // the old hardcoded keyless URL always returned empty sections.
            relatedState = repo.getRelated(type, metaId.substringBefore(":"))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    repository: StreamRepository,
    listsRepository: CustomListsRepository,
    addonId: String,
    type: String,
    metaId: String,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onPlayTrailer: (PlaybackRequest) -> Unit = {},
    onOpenActor: (Int) -> Unit = {},
    onOpenDetail: (addonId: String, type: String, metaId: String) -> Unit = { _, _, _ -> }
) {
    val vm: DetailViewModel = viewModel(factory = vmFactory { DetailViewModel(repository) })
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // TV-aware probing: the prober must send the same UA the player will.
    vm.isTv = rememberIsTv()

    // Resolve the addon base URL (from search cache or the database) as a hint,
    // then load. Metadata/streams may come from *other* installed addons.
    LaunchedEffect(addonId, type, metaId) {
        val baseUrl = repository.getAddonBaseUrl(addonId)
        vm.load(baseUrl, type, metaId)
    }

    // Play button: autoplay the first available stream as soon as it's ready.
    LaunchedEffect(vm.autoPlayEvent) {
        vm.consumeAutoPlay()?.let { autoplay ->
            // #12: streams may finish loading after the user already backed
            // out — never navigate to the player from a dead screen.
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@LaunchedEffect
            val allStreams = (vm.streamsState as? StreamsUiState.Content)?.streams ?: emptyList()
            startPlayback(vm, repository, autoplay.stream, autoplay.subtitles, allStreams, onPlay)
        }
    }

    fun retry() {
        vm.retryLoad(repository, addonId, type, metaId)
    }

    GlowBackground {
        when (val state = vm.metaState) {
            is MetaUiState.Loading -> {
                BackBar(onBack)
                LoadingState("Loading details…")
            }
            is MetaUiState.Error -> {
                BackBar(onBack)
                ErrorState(state.message) { retry() }
            }
            is MetaUiState.Content -> DetailContent(
                vm = vm,
                meta = state.meta,
                addonId = addonId,
                repository = repository,
                listsRepository = listsRepository,
                onBack = onBack,
                onPlay = onPlay,
                onPlayTrailer = onPlayTrailer,
                onOpenActor = onOpenActor,
                onOpenDetail = onOpenDetail
            )
        }
    }
}

/**
 * Builds a [PlaybackRequest] for a stream and hands it to the player.
 * Shared by autoplay, the Play button and tapping a stream card.
 */
private fun startPlayback(
    vm: DetailViewModel,
    repository: StreamRepository,
    stream: StreamItem,
    subtitles: List<Subtitle>,
    allStreams: List<StreamItem>,
    onPlay: () -> Unit
) {
    if (stream.url.startsWith("infohash:", true) || stream.url.startsWith("magnet:", true)) return
    val meta = (vm.metaState as? MetaUiState.Content)?.meta ?: return
    val isSeries = meta.type == "series" && vm.selectedVideo != null
    val title = if (isSeries) {
        val v = vm.selectedVideo!!
        "${meta.name} S${v.season}E${v.episode}"
    } else meta.name
    val ids = resolvePlaybackIds(meta, vm.selectedVideo)
    repository.pendingPlayback = PlaybackRequest(
        streamUrl = stream.url,
        headers = stream.headers,
        mimeType = stream.mimeType,
        title = title,
        poster = vm.selectedVideo?.thumbnail ?: meta.poster,
        videoId = vm.selectedVideo?.id ?: meta.id,
        subtitles = subtitles,
        imdbId = ids.imdbId,
        season = ids.season,
        episode = ids.episode,
        isMovie = ids.isMovie,
        type = meta.type,
        streamLabel = streamQualityLabel(stream),
        sourceName = stream.sourceName,
        streams = allStreams,
        episodes = meta.videos,
        metaId = meta.id,
        seriesName = meta.name.takeIf { !ids.isMovie },
        episodeTitle = vm.selectedVideo?.title
    )
    onPlay()
}

@Composable
private fun BackBar(onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, "Back", tint = TextPrimary)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun DetailContent(
    vm: DetailViewModel,
    meta: MetaDetail,
    addonId: String,
    repository: StreamRepository,
    listsRepository: CustomListsRepository,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onPlayTrailer: (PlaybackRequest) -> Unit = {},
    onOpenActor: (Int) -> Unit = {},
    onOpenDetail: (addonId: String, type: String, metaId: String) -> Unit
) {
    val isSeries = meta.type == "series" && meta.videos.isNotEmpty()
    val seasons = rememberSeasonList(meta)
    // Shared scope and watchlist state for hero buttons and play section
    val scope = rememberCoroutineScope()
    val watchKey = "$addonId|${meta.type}|${meta.id}"
    val watchItem by repository.observeWatchlistItem(watchKey).collectAsState(initial = null)
    // "Add to list" bottom sheet state
    var showAddToList by remember { mutableStateOf(false) }
    // Shared stream-browser state: addon tabs + filtered list stay in sync as
    // sources stream in progressively.
    val allStreams = (vm.streamsState as? StreamsUiState.Content)?.streams ?: emptyList()
    // Hero fact stack (v0.12.5, owner's Sample E "Right-Side Stack" —
    // replaces the v0.12.4 stat-tile grid): best quality, top audio,
    // languages, subtitles and stream/addon counts computed across the
    // loaded streams (see heroFactRows), drawn INSIDE the hero at its
    // lower-right. Recomputed as streams stream in; any row whose data
    // is absent is simply not shown.
    val allSubtitles = (vm.streamsState as? StreamsUiState.Content)?.subtitles ?: emptyList()
    val heroFacts = remember(allStreams, allSubtitles) {
        heroFactRows(allStreams, allSubtitles)
    }
    val browserState = rememberStreamBrowserState(allStreams, repository.settings)
    // Mini floating source-picker card (the button next to Play opens this
    // instead of scrolling to the sources section).
    var sourcePickerOpen by remember { mutableStateOf(false) }
    // Full-page sources (long-press Play): title header + addon tabs + stream list.
    var fullPageSourceOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // On TV, land D-pad focus on the Play button when the detail screen opens.
    val isTvDetail = rememberIsTv()
    val playButtonFocus = remember { FocusRequester() }
    var playFocusLanded by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val browsingPrivacy = remember(context) { BrowsingPrivacy(context) }
    var hideEpisodeDetails by remember { mutableStateOf(browsingPrivacy.hideUnrevealedEpisodes()) }
    val episodeShowKey = "$addonId|${meta.type}|${meta.id}"
    var revealedEpisodeIds by remember(episodeShowKey) {
        mutableStateOf(browsingPrivacy.revealedEpisodes(episodeShowKey))
    }
    LaunchedEffect(isTvDetail, playFocusLanded) {
        if (isTvDetail && !playFocusLanded) {
            playFocusLanded = true
            delay(400)
            runCatching { playButtonFocus.requestFocus() }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            // Poster Duo (Sample G; owner's pick v0.12.14): no
            // backdrop hero — actions on a plain top row, poster
            // card on the left, title + stacked facts on the right.
            // Replaces Sample D's Poster Credits hero.
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))
                    ) {
                        Icon(Icons.Filled.ArrowBack, "Back", tint = TextPrimary)
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = {
                            scope.launch {
                                repository.toggleWatchlist(addonId, meta.type, meta.id, meta.name, meta.poster)
                            }
                        },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))
                    ) {
                        Icon(
                            if (watchItem != null) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = if (watchItem != null) "Remove from watchlist" else "Add to watchlist",
                            tint = TextPrimary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { showAddToList = true },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))
                    ) {
                        Icon(
                            Icons.Filled.PlaylistAdd,
                            contentDescription = "Add to list",
                            tint = TextPrimary
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth()) {
                    SafePosterImage(
                        model = meta.poster ?: meta.background,
                        contentDescription = null,
                        modifier = Modifier
                            .width(132.dp)
                            .height(198.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            meta.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        val duoMetaLine = buildList {
                            meta.imdbRating?.takeIf { it.isNotBlank() }?.let { add("\u2605 $it IMDb") }
                            meta.releaseInfo?.takeIf { it.isNotBlank() }?.let { add(it) }
                            if (meta.genres.isNotEmpty()) add(meta.genres.take(2).joinToString(" / "))
                        }.joinToString("  \u00b7  ")
                        if (duoMetaLine.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                duoMetaLine,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        heroFacts.forEach { fact ->
                            val duoLabel = when {
                                fact.label == "Best quality" -> "Quality"
                                fact.label.startsWith("Streams") -> "Streams"
                                else -> fact.label
                            }
                            val duoValue = if (fact.label.startsWith("Streams")) {
                                val addons = fact.label.substringAfter("\u00b7 ", "").trim()
                                if (addons.isNotBlank()) "${fact.value} \u00b7 $addons" else fact.value
                            } else fact.value
                            Row(Modifier.padding(vertical = 3.dp)) {
                                Text(
                                    duoLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    modifier = Modifier.width(84.dp)
                                )
                                Text(
                                    duoValue,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
          Column {
            val canPlay = vm.streamsState !is StreamsUiState.Error
            // Resume: check if there's playback progress for this title
            val continueWatching by repository.continueWatching.collectAsState(initial = emptyList())
            val resumeState = continueWatching.find { it.videoId == meta.id || it.key.contains(meta.id) }
            val hasResume = resumeState != null && resumeState.positionMs > 0 && resumeState.durationMs > 0
            val resumeProgress = if (hasResume) resumeState!!.positionMs.toFloat() / resumeState!!.durationMs else 0f
            val playText = if (hasResume) "Continue Watching" else "Watch Now"
            // Play button: full width capsule. Tap = play, long-press = open sources.
            PlayButton(
                onClick = { vm.onPlayPressed() },
                onLongClick = { fullPageSourceOpen = true },
                enabled = canPlay,
                isLoading = vm.streamsState is StreamsUiState.Loading,
                loadingMore = (vm.streamsState as? StreamsUiState.Content)?.loadingMore == true,
                text = playText,
                resumeProgress = if (hasResume) resumeProgress else null,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).focusRequester(playButtonFocus)
            )
            Text(
                "Press & hold Watch Now for sources",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
            )
          }
        }
        if (!meta.description.isNullOrBlank()) {
            item {
                Column {
                    Text(
                        meta.description!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (meta.genres.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            meta.genres.take(4).forEach { genre ->
                                Text(
                                    genre,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextPrimary,
                                    modifier = Modifier
                                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
                                        .padding(horizontal = 11.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        // Trailers section: horizontal cards like "You May Like"
        if (meta.trailerStreams.isNotEmpty()) {
            item(key = "trailers") {
                Text(
                    "Trailers",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(meta.trailerStreams, key = { it.ytId }) { trailer ->
                        var resolving by remember { mutableStateOf(false) }
                        var trailerUnavailable by remember { mutableStateOf(false) }
                        Card(
                            modifier = Modifier
                                .width(160.dp)
                                .clickable(enabled = !resolving) {
                                    if (resolving) return@clickable
                                    resolving = true
                                    trailerUnavailable = false
                                    scope.launch {
                                        try {
                                            val playback = withTimeoutOrNull(30_000) {
                                                TrailerResolver.resolvePlayback(trailer.ytId)
                                            }
                                            if (playback != null) {
                                                onPlayTrailer(
                                                    PlaybackRequest(
                                                        streamUrl = playback.videoUrl,
                                                        title = "${meta.name} — Trailer",
                                                        poster = meta.background ?: meta.poster,
                                                        videoId = "trailer_${trailer.ytId}",
                                                        isMovie = meta.type == "movie",
                                                        type = meta.type,
                                                        streamLabel = "TRAILER",
                                                        metaId = meta.id,
                                                        isTrailer = true,
                                                        mergeAudioUrl = playback.audioUrl
                                                    )
                                                )
                                            } else {
                                                trailerUnavailable = true
                                            }
                                        } finally {
                                            resolving = false
                                        }
                                    }
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A24))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(90.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                // YouTube thumbnail as poster
                                SafePosterImage(
                                    model = "https://i.ytimg.com/vi/${trailer.ytId}/hqdefault.jpg",
                                    contentDescription = trailer.title,
                                    modifier = Modifier.fillMaxSize()
                                )
                                // Dark overlay for play button visibility
                                Box(
                                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                if (resolving) {
                                    OrbitDots(modifier = Modifier.size(26.dp))
                                } else {
                                    Icon(
                                        Icons.Filled.PlayArrow,
                                        contentDescription = "Play trailer",
                                        tint = Color.White,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                                }
                            }
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = trailer.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary,
                                    maxLines = 1
                                )
                                if (trailerUnavailable) {
                                    Text(
                                        "Trailer stream unavailable",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        // Only actors with a TMDB person id navigate (keyless mode has names only).
        if (meta.cast.isNotEmpty()) {
            item(key = "cast") {
                val tmdbKey = remember { repository.settings.tmdbApiKey.takeIf { it.isNotBlank() } }
                // Enrich cast with TMDB photos/IDs when key is available
                var enrichedCast by remember { mutableStateOf(meta.cast) }
                LaunchedEffect(meta.id, tmdbKey) {
                    if (!tmdbKey.isNullOrBlank() && meta.cast.any { it.id == 0 || it.photo == null }) {
                        val enriched = TmdbClient.enrichCast(tmdbKey, meta.id, meta.type, meta.cast)
                        if (enriched.isNotEmpty()) enrichedCast = enriched
                    }
                }
                Text(
                    "Cast",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(enrichedCast, key = { it.id.toString() + "_" + it.name }) { actor ->
                        val clickable = actor.id > 0
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(72.dp)
                                .tvFocusRing(RoundedCornerShape(12.dp))
                                .then(
                                    if (clickable) Modifier.clickable { onOpenActor(actor.id) }
                                    else Modifier
                                )
                        ) {
                            SafePosterImage(
                                model = actor.photo,
                                contentDescription = actor.name,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .liquidGlass(CircleShape)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = actor.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (clickable) TextPrimary else TextSecondary,
                                maxLines = 2,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        // "You May Like" — TMDB recommendations (requires TMDB API key in Settings).
        item(key = "you_may_like") {
            val tmdbKey = remember { repository.settings.tmdbApiKey.takeIf { it.isNotBlank() } }
            if (!tmdbKey.isNullOrBlank()) {
                var recommendations by remember { mutableStateOf<List<TmdbRecommendation>>(emptyList()) }
                var loadingRecs by remember { mutableStateOf(false) }
                LaunchedEffect(meta.id, tmdbKey) {
                    loadingRecs = true
                    try {
                        recommendations = fetchTmdbRecommendations(meta.id, meta.type, tmdbKey)
                    } catch (e: Exception) {
                        // Silent fail — section just won't show
                    } finally {
                        loadingRecs = false
                    }
                }
                if (loadingRecs || recommendations.isNotEmpty()) {
                    Text(
                        "You May Like",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    if (loadingRecs) {
                        // Sample C (owner's pick 2026-10-24): shimmer
                        // tiles while recommendations load — no spinner.
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(4) {
                                ShimmerPoster(modifier = Modifier.size(94.dp, 140.dp))
                            }
                        }
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(recommendations, key = { it.id }) { rec ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .width(110.dp)
                                        .liquidGlass(RoundedCornerShape(16.dp))
                                        .clickable {
                                            val key = tmdbKey ?: return@clickable
                                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                                try {
                                                    val client = okhttp3.OkHttpClient()
                                                    val url = "https://api.themoviedb.org/3/${rec.mediaType}/${rec.id}/external_ids?api_key=$key"
                                                    val req = okhttp3.Request.Builder().url(url).get().build()
                                                    val resp = client.newCall(req).execute()
                                                    if (resp.isSuccessful) {
                                                        val json = org.json.JSONObject(resp.body?.string() ?: "")
                                                        val imdbId = json.optString("imdb_id", "")
                                                        if (imdbId.isNotBlank()) {
                                                            val detailType = if (rec.mediaType == "movie") "movie" else "series"
                                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                                onOpenDetail(addonId, detailType, imdbId)
                                                            }
                                                        }
                                                    }
                                                } catch (e: Exception) { }
                                            }
                                        }
                                        .padding(8.dp)
                                ) {
                                    SafePosterImage(
                                        model = rec.posterPath?.let { "https://image.tmdb.org/t/p/w342$it" },
                                        contentDescription = rec.title,
                                        modifier = Modifier
                                            .size(94.dp, 140.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = rec.title,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextPrimary,
                                        maxLines = 2,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        // (Available Streams button removed — long-press Play opens the source picker.)
        item {
            if (vm.streamsState is StreamsUiState.Error) {
                Text(
                    (vm.streamsState as StreamsUiState.Error).message,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
        if (isSeries) {
            item(key = "seasons") {
                Text("Seasons", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(seasons, key = { it }) { season ->
                        val isSel = vm.selectedSeason == season
                        val palette = LocalPalette.current
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(92.dp).clickable { vm.selectSeason(season, meta) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(2f / 3f)
                                    .shadow(
                                        if (isSel) 12.dp else 0.dp,
                                        RoundedCornerShape(14.dp),
                                        spotColor = palette.accent.copy(alpha = 0.55f),
                                        ambientColor = palette.accent.copy(alpha = 0.55f)
                                    )
                                    .then(
                                        if (isSel) Modifier.border(
                                            2.dp,
                                            Brush.linearGradient(listOf(palette.accent, palette.accent2)),
                                            RoundedCornerShape(14.dp)
                                        ) else Modifier.border(
                                            1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp)
                                        )
                                    )
                                    .clip(RoundedCornerShape(14.dp))
                            ) {
                                SafePosterImage(
                                    model = meta.poster ?: meta.background,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    Modifier.fillMaxSize().background(
                                        Brush.verticalGradient(
                                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                                        )
                                    )
                                )
                                Text(
                                    if (season == 0) "SPECIALS" else "SEASON $season",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 7.dp)
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .then(
                                        if (isSel) Modifier.background(
                                            Brush.horizontalGradient(listOf(palette.accent, palette.accent2)),
                                            RoundedCornerShape(50)
                                        ) else Modifier.background(
                                            Color.White.copy(alpha = 0.06f), RoundedCornerShape(50)
                                        )
                                    )
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    if (season == 0) "Specials" else "Season $season",
                                    color = if (isSel) Color(0xFF04140B) else TextSecondary,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
            val episodes = meta.videos.filter { it.season == vm.selectedSeason }
                .sortedWith(compareBy({ it.episode }, { it.title }))
            item(key = "episodes_header") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(
                        "Episodes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { vm.updateShuffle(!vm.shuffleEpisodes) }) {
                        Icon(
                            Icons.Filled.Shuffle,
                            contentDescription = if (vm.shuffleEpisodes) "Shuffle on" else "Shuffle off",
                            tint = if (vm.shuffleEpisodes) LocalPalette.current.accent else TextSecondary
                        )
                    }
                    if (!isTvDetail) {
                        FilterChip(
                            selected = hideEpisodeDetails,
                            onClick = {
                                val enabled = !hideEpisodeDetails
                                hideEpisodeDetails = enabled
                                browsingPrivacy.setHideUnrevealedEpisodes(enabled)
                            },
                            label = { Text("Hide spoilers") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LocalPalette.current.accent.copy(alpha = 0.25f),
                                selectedLabelColor = TextPrimary,
                                containerColor = Color.White.copy(alpha = 0.06f),
                                labelColor = TextSecondary
                            )
                        )
                    }
                }
            }
            item(key = "episodes_rail") {
                val watchedKeys by com.pulsestream.app.data.WatchedMarks.keys.collectAsState()
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(episodes, key = { index, video -> "${video.id}_$index" }) { _, video ->
                        val selected = vm.selectedVideo?.id == video.id
                        val episodeRevealed = video.id in revealedEpisodeIds
                        val hideDetails = BrowsingPrivacy.shouldHideEpisodeDetails(hideEpisodeDetails, episodeRevealed)
                        EpisodeCardA(
                            video = video,
                            selected = selected,
                            hideDetails = hideDetails,
                            isWatched = com.pulsestream.app.data.WatchedMarks.episodeKey(meta.id, video.season, video.episode) in watchedKeys,
                            spoilerMode = hideEpisodeDetails,
                            isTv = isTvDetail,
                            onClick = { vm.selectEpisode(video) },
                            onReveal = { revealedEpisodeIds = browsingPrivacy.setEpisodeRevealed(episodeShowKey, video.id, true) },
                            onHide = { revealedEpisodeIds = browsingPrivacy.setEpisodeRevealed(episodeShowKey, video.id, false) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        // Show Details — glass fact tiles (owner's Sample A pick, 2026-10-24).
        // Only facts the meta actually carries are shown.
        val detailTiles = buildDetailTiles(meta)
        if (detailTiles.size >= 2) {
            item(key = "show_details") {
                Column {
                    Text(
                        "Show Details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    Column(
                        Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        detailTiles.chunked(3).forEach { rowTiles ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                rowTiles.forEach { (label, value) ->
                                    Column(
                                        modifier = Modifier.weight(1f)
                                            .liquidGlass(RoundedCornerShape(14.dp))
                                            .padding(horizontal = 11.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            label.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.2.sp,
                                            maxLines = 1
                                        )
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            value,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                repeat(3 - rowTiles.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        // "More like this" + "More from <actor>" rows from PlavioMeta.
        // Quietly absent when the endpoint has nothing (e.g. keyless mode).
        vm.relatedState.forEach { section ->
            item(key = "related_${section.id}") {
                com.pulsestream.app.ui.components.SectionTitle(section.title)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(section.metas, key = { it.id }) { related ->
                        com.pulsestream.app.ui.components.PosterCard(
                            meta = related,
                            // launchSingleTop-style: tapping the title that's already
                            // open is a no-op, so the backstack never stacks the
                            // same detail destination twice.
                            onClick = {
                                if (related.id != meta.id || related.type != meta.type) {
                                    onOpenDetail(addonId, related.type, related.id)
                                }
                            }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // Mini floating source-picker card: same-size button next to Play opens
    // this with a springy pop instead of scrolling to the sources section.
    // The popup gets the full stream list and runs its own per-addon tabs
    // (the inline section below keeps the shared "All" browser state).
    val streamsContent = vm.streamsState as? StreamsUiState.Content
    // Download confirmation dialog state
    var downloadStream by remember { mutableStateOf<StreamItem?>(null) }
    SourcePickerPopup(
        visible = sourcePickerOpen,
        streams = allStreams,
        loading = vm.streamsState is StreamsUiState.Loading,
        errorMessage = (vm.streamsState as? StreamsUiState.Error)?.message,
        onDismiss = { sourcePickerOpen = false },
        onRetry = { vm.loadStreams(meta.type, vm.selectedVideo?.id ?: meta.id) },
        onPick = { stream ->
            sourcePickerOpen = false
            if (streamsContent != null) {
                startPlayback(vm, repository, stream, streamsContent.subtitles, streamsContent.streams, onPlay)
            }
        },
        onDownload = { stream -> downloadStream = stream }
    )

    // Source picker (long-press Play): Bottom Sheet (Sample M;
    // owner's pick v0.12.14) — slides over the detail page, which
    // stays dimmed behind it. Same picker body, sheet mode.
    if (fullPageSourceOpen) {
      ModalBottomSheet(
        onDismissRequest = { fullPageSourceOpen = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = LocalPalette.current.background,
        dragHandle = { BottomSheetDefaults.DragHandle() }
      ) {
        FullPageSourcePicker(
            inSheet = true,
            meta = meta,
            streams = allStreams,
            loading = vm.streamsState is StreamsUiState.Loading,
            errorMessage = (vm.streamsState as? StreamsUiState.Error)?.message,
            onBack = { fullPageSourceOpen = false },
            onRetry = { vm.loadStreams(meta.type, vm.selectedVideo?.id ?: meta.id) },
            onPick = { stream ->
                fullPageSourceOpen = false
                if (streamsContent != null) {
                    startPlayback(vm, repository, stream, streamsContent.subtitles, streamsContent.streams, onPlay)
                }
            },
            onDownload = { stream -> downloadStream = stream }
        )
      }
    }

    // Download confirmation dialog
    downloadStream?.let { stream ->
        DownloadConfirmDialog(
            stream = stream,
            title = meta.name ?: "Video",
            onDismiss = { downloadStream = null }
        )
    }

    // "Add to list" bottom sheet
    if (showAddToList) {
        AddToListSheet(
            listsRepository = listsRepository,
            addonId = addonId,
            type = meta.type,
            metaId = meta.id,
            name = meta.name ?: "Video",
            poster = meta.poster,
            onDismiss = { showAddToList = false }
        )
    }
}

@Composable
private fun rememberSeasonList(meta: MetaDetail): List<Int> {
    return meta.videos.map { it.season }.distinct().sorted()
}

// ---------------------------------------------------------------------------
// Hero stat tiles (v0.12.4, Sample C)
// ---------------------------------------------------------------------------

/** Display names scanned for in stream text, from the app's canonical language list. */
private val HERO_LANGUAGE_NAMES: List<String> = SUBTITLE_LANGUAGE_OPTIONS.map { it.second }

/** Premium-first ordering for audio format tags. */
private val HERO_AUDIO_RANK: List<String> = listOf(
    "Atmos", "TrueHD", "DTS-HDMA", "DTS-HD", "DTS", "DD+", "DD5.1", "DD", "Dolby", "AAC5.1", "AAC"
)

/** Value accent for a stat tile: gold rating, mint languages, plain rest. */
private enum class HeroTileKind { PLAIN, GOLD, MINT }

/** One Sample E hero fact row: a small-caps light label + a bold value. */
private data class HeroFactRow(
    val label: String,
    val value: String,
    val kind: HeroTileKind = HeroTileKind.PLAIN
)

@Composable
private fun HeroFactRowView(fact: HeroFactRow, shadow: Shadow) {
    val valueColor = when (fact.kind) {
        HeroTileKind.GOLD -> Color(0xFFFFD166)
        HeroTileKind.MINT -> LocalPalette.current.accent
        HeroTileKind.PLAIN -> TextPrimary
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            fact.label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, shadow = shadow),
            color = TextSecondary,
            maxLines = 1
        )
        Spacer(Modifier.width(8.dp))
        Text(
            fact.value,
            style = MaterialTheme.typography.titleSmall.copy(shadow = shadow),
            fontWeight = FontWeight.ExtraBold,
            color = valueColor,
            maxLines = 1
        )
    }
}

/** ISO 639-2 → 639-1 for the subtitle codes addons most often print. */
private val SUBTITLE_CODE3_TO_2: Map<String, String> = mapOf(
    "eng" to "EN", "hin" to "HI", "urd" to "UR", "ara" to "AR", "spa" to "ES",
    "fra" to "FR", "fre" to "FR", "deu" to "DE", "ger" to "DE", "ita" to "IT",
    "por" to "PT", "rus" to "RU", "jpn" to "JA", "kor" to "KO", "zho" to "ZH",
    "chi" to "ZH", "tam" to "TA", "tel" to "TE", "mar" to "MR", "ben" to "BN",
    "pan" to "PA", "guj" to "GU", "tur" to "TR", "per" to "FA", "fas" to "FA",
    "ind" to "ID", "msa" to "MS", "may" to "MS", "tha" to "TH", "vie" to "VI",
    "ukr" to "UK", "pol" to "PL", "nld" to "NL", "dut" to "NL"
)

/** Short display code ("EN") for a subtitle language tag of any shape. */
private fun subtitleDisplayCode(raw: String): String {
    val code = raw.trim().lowercase()
    if (code.length == 2) return code.uppercase()
    SUBTITLE_CODE3_TO_2[code]?.let { return it }
    // Some addons print full names ("English") instead of codes.
    SUBTITLE_LANGUAGE_OPTIONS.firstOrNull { it.second.equals(code, ignoreCase = true) }
        ?.let { return it.first.uppercase() }
    return code.take(3).uppercase()
}

/**
 * Fact rows for the detail hero's right-side stack (v0.12.5, Sample E —
 * replaces the v0.12.4 stat tiles; same computations): what the loaded
 * addons offer for this title, known BEFORE pressing play, computed
 * across the loaded streams — best quality, top audio format, audio
 * languages (stream name/title/filename text is the only place addons
 * print languages, word-matched against the canonical names,
 * English/Hindi leading as in v0.12.3), subtitle count from the stream
 * result, and the stream / addon counts. The IMDb rating is NOT a row
 * here — it sits in the hero's bottom-left meta line. Absent data
 * yields no row — never a placeholder.
 */
private fun heroFactRows(
    streams: List<StreamItem>,
    subtitles: List<Subtitle>
): List<HeroFactRow> {
    if (streams.isEmpty()) return emptyList()
    val rows = mutableListOf<HeroFactRow>()

    availableStreamQualities(streams).firstOrNull()?.let { quality ->
        rows += HeroFactRow("Best quality", if (quality == "4K") "4K UHD" else quality)
    }

    val topAudio = streams.mapNotNull { parseStreamInfo(it).audio }.distinct()
        .minByOrNull { tag ->
            HERO_AUDIO_RANK.indexOfFirst { tag.equals(it, ignoreCase = true) }
                .let { rank -> if (rank < 0) Int.MAX_VALUE else rank }
        }
    topAudio?.let { rows += HeroFactRow("Top audio", it) }

    val haystack = streams.joinToString("\n") { stream ->
        listOfNotNull(stream.title, stream.name, stream.filename).joinToString(" ")
    }
    if (haystack.isNotBlank()) {
        val found = HERO_LANGUAGE_NAMES.filter { name ->
            Regex("""\b${Regex.escape(name)}\b""", RegexOption.IGNORE_CASE).containsMatchIn(haystack)
        }
        // English and Hindi lead (the owner's most-wanted pair), the
        // rest follow the canonical list order.
        val lead = listOf("English", "Hindi")
        val ordered = lead.filter { it in found } + found.filter { it !in lead }
        if (ordered.isNotEmpty()) {
            val codes = ordered.map { name ->
                SUBTITLE_LANGUAGE_OPTIONS.firstOrNull { it.second == name }?.first?.uppercase()
                    ?: name.take(2).uppercase()
            }
            val extra = if (ordered.size > 2) " +${ordered.size - 2}" else ""
            rows += HeroFactRow(
                "Languages",
                codes.take(2).joinToString(" · ") + extra,
                HeroTileKind.MINT
            )
        }
    }

    val subCodes = subtitles.map { subtitleDisplayCode(it.lang) }.distinct()
    if (subCodes.isNotEmpty()) {
        rows += HeroFactRow("Subtitles", "${subCodes.size}")
    }

    val addonCount = streams.map { it.addonKey() }.distinct().size
    rows += HeroFactRow("Streams · $addonCount addons", "${streams.size}")
    return rows
}

private data class PlaybackIds(
    val imdbId: String,
    val season: Int,
    val episode: Int,
    val isMovie: Boolean
)

/**
 * Cinemeta ids are IMDb ids ("tt1234567"); series episode ids look like
 * "tt1234567:1:2". Everything downstream (IntroDB, subtitle search) wants
 * the bare IMDb id plus season/episode.
 */
private fun resolvePlaybackIds(meta: MetaDetail, video: Video?): PlaybackIds {
    val vid = video?.id ?: meta.id
    return PlaybackIds(
        imdbId = vid.substringBefore(":"),
        season = video?.season ?: 0,
        episode = video?.episode ?: 0,
        isMovie = meta.type == "movie"
    )
}


/** Rounded-rectangle shape for the picker card — follows the app theme. */
private val PickerShape = RoundedCornerShape(20.dp)

/**
 * Per-addon tab state for the source picker. Unlike the shared inline
 * [com.pulsestream.app.ui.components.StreamBrowserState] (which has an "All"
 * tab), the picker shows one tab per addon and selects the first addon by
 * default.
 */
@Stable
private class PickerTabState(
    val tabs: List<StreamTab>,
    val selectedKey: String?,
    val filtered: List<StreamItem>,
    val onSelect: (String) -> Unit
)

@Composable
private fun rememberPickerTabState(streams: List<StreamItem>): PickerTabState {
    var selectedKey by remember { mutableStateOf<String?>(null) }
    // groupBy preserves first-encounter order; the stream list arrives
    // prioritized (starred addons first), so the first tab is the top addon.
    val tabs = remember(streams) {
        streams.groupBy { it.addonKey() }
            .map { (key, list) -> StreamTab(key, key, list.size) }
    }
    val safeKey = if (tabs.any { it.key == selectedKey }) selectedKey else tabs.firstOrNull()?.key
    val filtered = remember(streams, safeKey) {
        if (safeKey == null) emptyList() else streams.filter { it.addonKey() == safeKey }
    }
    return remember(tabs, safeKey, filtered) {
        PickerTabState(tabs, safeKey, filtered) { selectedKey = it }
    }
}

/**
 * Mini floating source-picker card. The same-size button next to Play pops
 * this open with a springy scale+fade+rise animation; tapping the dimmed
 * scrim or the close button dismisses it the same way in reverse.
 *
 * The card is a plain rounded rectangle in the app theme's panel color.
 * A tab row lists one tab per addon ("Name · count"); tapping a tab filters
 * the list to that addon's streams. On stream failure the REAL error is shown
 * with a working Retry instead of the generic "no sources" line.
 */
@Composable
private fun SourcePickerPopup(
    visible: Boolean,
    streams: List<StreamItem>,
    loading: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onPick: (StreamItem) -> Unit,
    onDownload: ((StreamItem) -> Unit)? = null
) {
    if (rememberIsTv()) {
        // TV: show the picker in its OWN window (a Compose Dialog), exactly like
        // Nuvio TV does. A same-window overlay leaves D-pad focus on the screen
        // underneath; a dialog window keeps every key press inside the popup,
        // Back dismisses it, and focus returns to the Source button afterwards.
        if (visible) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = onDismiss,
                properties = androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = false
                )
            ) {
                SourcePickerOverlay(true, streams, loading, errorMessage, onDismiss, onRetry, onPick, onDownload)
            }
        }
    } else {
        SourcePickerOverlay(visible, streams, loading, errorMessage, onDismiss, onRetry, onPick, onDownload)
    }
}

@Composable
private fun SourcePickerOverlay(
    visible: Boolean,
    streams: List<StreamItem>,
    loading: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onPick: (StreamItem) -> Unit,
    onDownload: ((StreamItem) -> Unit)? = null
) {
    val springy = androidx.compose.animation.core.spring<Float>(
        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
        stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
    )
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.fadeIn(
            animationSpec = androidx.compose.animation.core.tween(180)
        ),
        exit = androidx.compose.animation.fadeOut(
            animationSpec = androidx.compose.animation.core.tween(150)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
                .focusProperties { canFocus = false } // scrim must never take D-pad focus
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = visible,
                enter = androidx.compose.animation.scaleIn(
                    initialScale = 0.82f, animationSpec = springy
                ) + androidx.compose.animation.fadeIn(
                    animationSpec = androidx.compose.animation.core.tween(160)
                ) + androidx.compose.animation.slideInVertically(
                    initialOffsetY = { it / 6 },
                    animationSpec = androidx.compose.animation.core.tween(220)
                ),
                exit = androidx.compose.animation.scaleOut(
                    targetScale = 0.9f,
                    animationSpec = androidx.compose.animation.core.tween(140)
                ) + androidx.compose.animation.fadeOut(
                    animationSpec = androidx.compose.animation.core.tween(140)
                )
            ) {
                // Stop scrim clicks from falling through the card.
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .heightIn(max = 520.dp)
                        .clip(PickerShape)
                        .background(LocalPalette.current.panel)
                        .border(1.dp, GlassBorder, PickerShape)
                        .focusProperties { canFocus = false } // only the rows inside are focus stops
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                ) {
                    PickerCardBody(
                        streams = streams,
                        loading = loading,
                        errorMessage = errorMessage,
                        onDismiss = onDismiss,
                        onRetry = onRetry,
                        onPick = onPick,
                        onDownload = onDownload
                    )
                }
            }
        }
    }
}

/**
 * Picker card content: header, per-addon tab row, then the filtered stream
 * list (or the loading / real-error / empty states).
 */
@Composable
private fun PickerCardBody(
    streams: List<StreamItem>,
    loading: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onPick: (StreamItem) -> Unit,
    onDownload: ((StreamItem) -> Unit)? = null
) {
    val palette = LocalPalette.current
    val tabState = rememberPickerTabState(streams)
    val isTv = rememberIsTv()
    val firstTabFocus = remember { FocusRequester() }
    // On TV, land D-pad focus on the first addon tab once tabs exist.
    var tvFocusLanded by remember { mutableStateOf(false) }
    LaunchedEffect(isTv, tabState.tabs.isNotEmpty(), tvFocusLanded) {
        if (isTv && tabState.tabs.isNotEmpty() && !tvFocusLanded) {
            tvFocusLanded = true
            delay(300)
            runCatching { firstTabFocus.requestFocus() }
        }
    }

    Column(Modifier.fillMaxWidth().tvSheetFocus()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Choose source",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = TextSecondary
                )
            }
        }
        if (tabState.tabs.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(tabState.tabs, key = { _, tab -> tab.key }) { index, tab ->
                    val selected = tab.key == tabState.selectedKey
                    Box(
                        modifier = Modifier
                            .then(if (index == 0) Modifier.focusRequester(firstTabFocus) else Modifier)
                            .tvFocusRing(RoundedCornerShape(50))
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (selected) palette.accent.copy(alpha = 0.22f)
                                else Color.White.copy(alpha = 0.06f)
                            )
                            .border(
                                1.dp,
                                if (selected) palette.accent else GlassBorder,
                                RoundedCornerShape(50)
                            )
                            .clickable { tabState.onSelect(tab.key) }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "${tab.label} · ${tab.count}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) TextPrimary else TextSecondary,
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        when {
            loading && streams.isEmpty() -> {
                Box(
                    Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    OrbitDots(modifier = Modifier.size(34.dp))
                }
            }
            errorMessage != null && streams.isEmpty() -> {
                // Audit #24: show the REAL stream error (not "no sources yet"),
                // with a Retry that re-queries every addon.
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Text(
                        errorMessage,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                    ) {
                        Text("Retry", color = Color(0xFF0A0A12), fontWeight = FontWeight.Bold)
                    }
                }
            }
            streams.isEmpty() -> {
                Text(
                    "No sources found yet — they appear here as each addon answers.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                )
            }
            else -> {
                StreamList(
                    streams = tabState.filtered,
                    onPick = onPick,
                    modifier = Modifier.heightIn(max = 400.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    onDownload = onDownload
                )
            }
        }
    }
}


/** Full-screen picker opened by long-pressing Play. All content scrolls on small screens. */
@Composable
internal fun FullPageSourcePicker(
    meta: MetaDetail,
    streams: List<StreamItem>,
    loading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPick: (StreamItem) -> Unit,
    onDownload: ((StreamItem) -> Unit)? = null,
    inSheet: Boolean = false
) {
    val palette = LocalPalette.current
    val browserState = rememberStreamBrowserState(streams)
    androidx.activity.compose.BackHandler(onBack = onBack)
    val pickerBody: @Composable () -> Unit = {
        Column(
            (if (inSheet) Modifier.fillMaxWidth().fillMaxHeight(0.85f)
             else Modifier.fillMaxSize()).tvSheetFocus()
        ) {
            if (inSheet) {
                // Sheet header (Sample M): "<Title> · choose a source".
                // The sheet's drag handle sits above this; no back /
                // close row and no banner inside the sheet.
                Text(
                    buildAnnotatedString {
                        append(meta.name)
                        withStyle(SpanStyle(color = TextSecondary, fontWeight = FontWeight.Normal)) {
                            append("  \u00b7  choose a source")
                        }
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 6.dp)
                )
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                    Text(
                        "Choose stream",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Close, contentDescription = "Close stream picker", tint = TextSecondary)
                    }
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("stream-picker-list"),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                if (!inSheet) {
                item(key = "poster") {
                    // Design I banner (user's approved design): full-width backdrop
                    // with gradient into the page background, title + count below.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    ) {
                        SafePosterImage(
                            model = meta.background ?: meta.poster,
                            contentDescription = "${meta.name} banner",
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            palette.background.copy(alpha = 0.7f),
                                            palette.background
                                        )
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Text(
                                meta.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 2
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = listOfNotNull(
                                    "${streams.size} sources available",
                                    meta.releaseInfo?.takeIf { it.isNotBlank() },
                                    meta.imdbRating?.takeIf { it.isNotBlank() }?.let { "★ $it" }
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
                }
                item(key = "filters") {
                    Text(
                        "Add-ons",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    AddonTabs(browserState, compact = true)
                    com.pulsestream.app.ui.components.QualityPills(
                        browserState,
                        compact = true,
                        modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            browserState.selectedQuality?.let { "$it streams" } ?: "Available streams",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${browserState.filtered.size} results",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                    }
                    if (loading && streams.isNotEmpty()) {
                        androidx.compose.material3.LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            color = palette.accent
                        )
                    }
                }
                when {
                    loading && streams.isEmpty() -> item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            OrbitDots(modifier = Modifier.size(34.dp))
                        }
                    }
                    errorMessage != null && streams.isEmpty() -> item(key = "error") {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
                            Text(errorMessage, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = palette.accent)) {
                                Text("Retry", color = Color(0xFF0A0A12), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    streams.isEmpty() -> item(key = "empty") {
                        Text(
                            "No sources found yet — they appear here as each addon answers.",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    browserState.filtered.isEmpty() -> item(key = "no-matches") {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                "No streams match this add-on and quality. Choose Any or another add-on.",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            androidx.compose.material3.TextButton(onClick = {
                                browserState.onSelectTab(com.pulsestream.app.ui.components.STREAM_TAB_ALL)
                                browserState.onSelectQuality(null)
                            }) { Text("Clear filters", color = palette.accent) }
                        }
                    }
                    else -> streamListItems(
                        streams = browserState.filtered,
                        onPick = onPick,
                        onDownload = onDownload,
                        compact = true
                    )
                }
            }
        }
    }
    if (inSheet) pickerBody() else GlowBackground { pickerBody() }
}

/** TMDB recommendation item. */
private data class TmdbRecommendation(
    val id: Int,
    val title: String,
    val posterPath: String?,
    val mediaType: String
)

/**
 * Fetch TMDB recommendations. Uses the find API to resolve IMDb ID to TMDB ID first,
 * then fetches recommendations. Returns empty list on any failure.
 */
private suspend fun fetchTmdbRecommendations(
    imdbId: String,
    type: String,
    apiKey: String
): List<TmdbRecommendation> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        // Step 1: Resolve IMDb ID to TMDB ID via find API
        val findUrl = "https://api.themoviedb.org/3/find/$imdbId?api_key=$apiKey&external_source=imdb_id"
        val findRequest = okhttp3.Request.Builder().url(findUrl).get().build()
        val findResponse = client.newCall(findRequest).execute()
        if (!findResponse.isSuccessful) return@withContext emptyList()
        val findJson = org.json.JSONObject(findResponse.body?.string() ?: return@withContext emptyList())

        // Get TMDB ID from movie_results or tv_results
        val tmdbId: Int
        val tmdbType: String
        val movieResults = findJson.optJSONArray("movie_results")
        val tvResults = findJson.optJSONArray("tv_results")
        if (movieResults != null && movieResults.length() > 0) {
            tmdbId = movieResults.getJSONObject(0).getInt("id")
            tmdbType = "movie"
        } else if (tvResults != null && tvResults.length() > 0) {
            tmdbId = tvResults.getJSONObject(0).getInt("id")
            tmdbType = "tv"
        } else {
            return@withContext emptyList()
        }

        // Step 2: Fetch recommendations
        val recUrl = "https://api.themoviedb.org/3/$tmdbType/$tmdbId/recommendations?api_key=$apiKey&page=1"
        val recRequest = okhttp3.Request.Builder().url(recUrl).get().build()
        val recResponse = client.newCall(recRequest).execute()
        if (!recResponse.isSuccessful) return@withContext emptyList()
        val recJson = org.json.JSONObject(recResponse.body?.string() ?: return@withContext emptyList())
        val results = recJson.optJSONArray("results") ?: return@withContext emptyList()

        val recommendations = mutableListOf<TmdbRecommendation>()
        for (i in 0 until minOf(results.length(), 20)) {
            val obj = results.getJSONObject(i)
            val title = obj.optString("title").ifBlank { obj.optString("name") }
            if (title.isNotBlank()) {
                recommendations.add(
                    TmdbRecommendation(
                        id = obj.getInt("id"),
                        title = title,
                        posterPath = obj.optString("poster_path").takeIf { it.isNotBlank() },
                        mediaType = obj.optString("media_type").ifBlank { tmdbType }
                    )
                )
            }
        }
        recommendations
    } catch (e: Exception) {
        emptyList()
    }
}

/** Facts for the Show Details tiles — only what the meta actually carries. */
private fun buildDetailTiles(meta: MetaDetail): List<Pair<String, String>> {
    val tiles = mutableListOf<Pair<String, String>>()
    meta.status?.takeIf { it.isNotBlank() }?.let { tiles += "Status" to it }
    // Series: a year range from episode air dates says more than the bare year.
    val release = if (meta.type == "series" && meta.videos.isNotEmpty()) {
        val years = meta.videos.mapNotNull { it.released?.take(4)?.toIntOrNull() }
        if (years.isNotEmpty()) {
            val lo = years.min()
            val hi = years.max()
            if (lo != hi) "$lo\u2013$hi" else meta.releaseInfo ?: lo.toString()
        } else meta.releaseInfo
    } else meta.releaseInfo
    release?.takeIf { it.isNotBlank() }?.let { tiles += "Release" to it }
    meta.certification?.takeIf { it.isNotBlank() }?.let { tiles += "Certification" to it }
    meta.country?.takeIf { it.isNotBlank() }?.let { tiles += "Origin" to it }
    meta.runtime?.takeIf { it.isNotBlank() }?.let { tiles += "Runtime" to it }
    meta.network?.takeIf { it.isNotBlank() }?.let { tiles += "Network" to it }
    meta.imdbRating?.takeIf { it.isNotBlank() }?.let { tiles += "IMDb" to it }
    if (meta.genres.isNotEmpty()) tiles += "Genres" to meta.genres.take(2).joinToString(", ")
    return tiles.take(6)
}

/** "2011-09-22T00:00:00.000Z" -> "Sep 22, 2011"; anything odd passes through short. */
private fun formatEpisodeDate(released: String?): String {
    if (released.isNullOrBlank()) return ""
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val parts = released.take(10).split("-")
    if (parts.size == 3) {
        val year = parts[0]
        val month = parts[1].toIntOrNull()
        val day = parts[2].toIntOrNull()
        if (month != null && month in 1..12 && day != null) {
            return "${months[month - 1]} $day, $year"
        }
    }
    return released.take(10)
}

/**
 * Sample A episode card (owner's pick, 2026-10-24): still on top with the
 * S/E chip and the WATCHED pill, words below on glass. Tap selects the
 * episode (same behavior as the old list rows).
 */
@Composable
private fun EpisodeCardA(
    video: Video,
    selected: Boolean,
    hideDetails: Boolean,
    isWatched: Boolean,
    spoilerMode: Boolean,
    isTv: Boolean,
    onClick: () -> Unit,
    onReveal: () -> Unit,
    onHide: () -> Unit
) {
    val palette = LocalPalette.current
    Column(
        modifier = Modifier
            .width(272.dp)
            .liquidGlass(RoundedCornerShape(18.dp))
            .border(
                if (selected) 1.5.dp else 0.5.dp,
                if (selected) palette.accent else Color.White.copy(alpha = 0.10f),
                RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Box(Modifier.fillMaxWidth().height(132.dp)) {
            if (!hideDetails && video.thumbnail != null) {
                SafePosterImage(video.thumbnail, null, Modifier.fillMaxSize())
            } else {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(listOf(Color(0xFF2A3448), Color(0xFF121826)))
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "S${video.season} \u00b7 E${video.episode}",
                        color = TextSecondary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Box(
                Modifier.padding(9.dp)
                    .background(Color(0xBF05080E), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    "S${video.season} \u00b7 E${video.episode}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            if (isWatched && !hideDetails) {
                WatchedPill(Modifier.align(Alignment.BottomStart).padding(9.dp))
            }
            if (selected) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(9.dp)
                        .background(palette.accent, RoundedCornerShape(50))
                        .padding(5.dp)
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color(0xFF04140B),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
            if (hideDetails) {
                Text(
                    "Episode details hidden",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!isTv) {
                    TextButton(onClick = onReveal) { Text("Reveal") }
                }
            } else {
                Text(
                    "${video.episode}. ${video.title}",
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!video.overview.isNullOrBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        video.overview!!,
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatEpisodeDate(video.released),
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(Modifier.weight(1f))
                    if (spoilerMode && !isTv) {
                        Text(
                            "Hide details",
                            color = palette.accent,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.clickable(onClick = onHide)
                        )
                    }
                }
            }
        }
    }
}
