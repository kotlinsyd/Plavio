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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulsestream.app.ui.components.PlayButton
import com.pulsestream.app.ui.components.SafePosterImage
import com.pulsestream.app.data.MetaDetail
import com.pulsestream.app.data.PlaybackRequest
import com.pulsestream.app.data.TrailerResolver
import com.pulsestream.app.data.RelatedSection
import com.pulsestream.app.data.StreamItem
import com.pulsestream.app.data.StreamProber
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.data.Subtitle
import com.pulsestream.app.data.Video
import com.pulsestream.app.data.streamQualityLabel
import com.pulsestream.app.ui.components.ErrorState
import com.pulsestream.app.ui.components.GlassCard
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.components.LoadingState
import com.pulsestream.app.ui.components.AddonTabs
import com.pulsestream.app.ui.components.pickAutoplayStream
import com.pulsestream.app.ui.components.rememberStreamBrowserState
import com.pulsestream.app.ui.components.StreamCard
import com.pulsestream.app.ui.components.StreamList
import com.pulsestream.app.ui.components.LocalBadgeStyleKey
import com.pulsestream.app.ui.components.streamListItems
import com.pulsestream.app.ui.components.StreamTab
import com.pulsestream.app.ui.components.addonKey
import com.pulsestream.app.ui.components.rememberStreamBrowserState
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.GlassBorder
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import com.pulsestream.app.ui.tv.rememberIsTv
import com.pulsestream.app.ui.tv.tvFocusRing
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
    data class Content(val streams: List<StreamItem>, val subtitles: List<Subtitle>) : StreamsUiState
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
        val first = episodes.firstOrNull()
        if (first != null) selectEpisode(first)
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
                        streamsState = StreamsUiState.Content(result.streams, result.subtitles)
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
                // succeeded. Do one automatic retry before showing "no sources"
                // so users never see a false empty state.
                if (!gotAny) {
                    kotlinx.coroutines.delay(1500)
                    repo.streamResultsFlow(type, videoId).collect { result ->
                        if (result.streams.isNotEmpty()) {
                            gotAny = true
                            streamsState = StreamsUiState.Content(result.streams, result.subtitles)
                            maybeProbePick(result.streams)
                            if (pendingAutoPlay) {
                                pendingAutoPlay = false
                                playBestOrFirst()
                            }
                        }
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
        if (pickState is PickState.Found) return
        probeJob?.cancel()
        probeJob = viewModelScope.launch {
            pickState = PickState.Probing
            val best = StreamProber.findBest(streams, isTv)
            pickState = if (best != null) PickState.Found(best) else PickState.None
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
        // Settings > Streams autoplay: pick by the user's quality/size rule
        // instead of the probed fastest stream.
        if (repo.settings.streamAutoplay) {
            val ruled = pickAutoplayStream(s.streams, repo.settings)
            if (ruled != null) {
                autoPlayEvent = AutoPlay(ruled, s.subtitles)
                return
            }
        }
        val pick = pickState as? PickState.Found
        if (pick != null) {
            autoPlayEvent = AutoPlay(pick.stream, s.subtitles)
            return
        }
        if (pickState is PickState.Probing) {
            viewModelScope.launch {
                withTimeoutOrNull(2500) { probeJob?.join() }
                val best = pickState as? PickState.Found
                val cur = streamsState as? StreamsUiState.Content ?: return@launch
                if (best != null) autoPlayEvent = AutoPlay(best.stream, cur.subtitles)
                else if (cur.streams.isNotEmpty()) autoPlayEvent = AutoPlay(cur.streams.first(), cur.subtitles)
            }
            return
        }
        if (s.streams.isNotEmpty()) autoPlayEvent = AutoPlay(s.streams.first(), s.subtitles)
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

@Composable
fun DetailScreen(
    repository: StreamRepository,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailContent(
    vm: DetailViewModel,
    meta: MetaDetail,
    addonId: String,
    repository: StreamRepository,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onPlayTrailer: (PlaybackRequest) -> Unit = {},
    onOpenActor: (Int) -> Unit = {},
    onOpenDetail: (addonId: String, type: String, metaId: String) -> Unit
) {
    val isSeries = meta.type == "series" && meta.videos.isNotEmpty()
    val seasons = rememberSeasonList(meta)
    // Shared stream-browser state: addon tabs + filtered list stay in sync as
    // sources stream in progressively.
    val allStreams = (vm.streamsState as? StreamsUiState.Content)?.streams ?: emptyList()
    val browserState = rememberStreamBrowserState(allStreams, repository.settings)
    // Mini floating source-picker card (the button next to Play opens this
    // instead of scrolling to the sources section).
    var sourcePickerOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // On TV, land D-pad focus on the Play button when the detail screen opens.
    val isTvDetail = rememberIsTv()
    val playButtonFocus = remember { FocusRequester() }
    var playFocusLanded by remember { mutableStateOf(false) }
    LaunchedEffect(isTvDetail, playFocusLanded) {
        if (isTvDetail && !playFocusLanded) {
            playFocusLanded = true
            delay(400)
            runCatching { playButtonFocus.requestFocus() }
        }
    }

    // Badge pack for stream rows comes from the Appearance setting.
    androidx.compose.runtime.CompositionLocalProvider(
        LocalBadgeStyleKey provides repository.settings.streamCardStyle
    ) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Box(Modifier.fillMaxWidth().height(260.dp)) {
                SafePosterImage(
                    model = meta.background ?: meta.poster,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xFF0A0A12)),
                            startY = 200f
                        )
                    )
                )
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.padding(8.dp).align(Alignment.TopStart)
                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))
                ) {
                    Icon(Icons.Filled.ArrowBack, "Back", tint = TextPrimary)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                SafePosterImage(
                    model = meta.poster,
                    contentDescription = meta.name,
                    modifier = Modifier
                        .width(110.dp).height(165.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(meta.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.height(6.dp))
                    val info = listOfNotNull(
                        meta.releaseInfo?.take(4),
                        meta.imdbRating?.let { "★ $it" },
                        meta.type.replaceFirstChar { it.uppercase() }
                    ).joinToString("  •  ")
                    if (info.isNotBlank()) {
                        Text(info, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(meta.genres.take(5), key = { index, genre -> "${genre}_$index" }) { _, genre ->
                            Box(
                                Modifier.border(1.dp, GlassBorder, RoundedCornerShape(50))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(genre, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            }
                        }
                    }
                }
            }
        }
        if (!meta.description.isNullOrBlank()) {
            item {
                Text(
                    meta.description!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        item {
            val canPlay = vm.streamsState !is StreamsUiState.Error
            val watchKey = "$addonId|${meta.type}|${meta.id}"
            val watchItem by repository.observeWatchlistItem(watchKey).collectAsState(initial = null)
            val scope = rememberCoroutineScope()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayButton(
                    onClick = { vm.onPlayPressed() },
                    enabled = canPlay,
                    text = if (vm.streamsState is StreamsUiState.Loading) "Loading…" else "Play",
                    modifier = Modifier.weight(1f).focusRequester(playButtonFocus)
                )
                // Trailer button: plays this title's trailer through the
                // trailer flow (full portrait floating player). Hidden when
                // the addon provides no trailer — never a dead button.
                if (meta.trailerStreams.isNotEmpty()) {
                    var resolvingTrailer by remember { mutableStateOf(false) }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = {
                            if (resolvingTrailer) return@Button
                            val ytId = meta.trailerStreams.firstOrNull()?.ytId ?: return@Button
                            resolvingTrailer = true
                            scope.launch {
                                try {
                                    val url = withTimeoutOrNull(30_000) {
                                        TrailerResolver.resolveUrl(ytId)
                                    }
                                    if (url != null) {
                                        onPlayTrailer(
                                            PlaybackRequest(
                                                streamUrl = url,
                                                title = "${meta.name} — Trailer",
                                                poster = meta.background ?: meta.poster,
                                                videoId = "trailer_$ytId",
                                                isMovie = meta.type == "movie",
                                                type = meta.type,
                                                streamLabel = "TRAILER",
                                                metaId = meta.id,
                                                isTrailer = true
                                            )
                                        )
                                    }
                                } finally {
                                    resolvingTrailer = false
                                }
                            }
                        },
                        enabled = !resolvingTrailer,
                        modifier = Modifier.height(52.dp).tvFocusRing(RoundedCornerShape(28.dp)),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            disabledContainerColor = Color.White.copy(alpha = 0.04f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder),
                        contentPadding = PaddingValues(horizontal = 18.dp)
                    ) {
                        if (resolvingTrailer) {
                            androidx.compose.material3.CircularProgressIndicator(
                                color = TextPrimary,
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Icon(
                                Icons.Filled.Movie,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Trailer",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                // Source button: server icon with a thin circular loading ring.
                // Ring circles in white while streams load; turns full + theme
                // accent once loading completes.
                run {
                    val palette = LocalPalette.current
                    val isLoading = vm.streamsState is StreamsUiState.Loading
                    val isLoaded = vm.streamsState is StreamsUiState.Content
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(52.dp)
                    ) {
                        if (isLoading) {
                            androidx.compose.material3.CircularProgressIndicator(
                                color = Color.White.copy(alpha = 0.7f),
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(52.dp)
                            )
                        } else if (isLoaded) {
                            androidx.compose.material3.CircularProgressIndicator(
                                progress = { 1f },
                                color = palette.accent,
                                strokeWidth = 2.dp,
                                trackColor = Color.Transparent,
                                modifier = Modifier.size(52.dp)
                            )
                        }
                        IconButton(
                            onClick = { sourcePickerOpen = true },
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(50))
                                .border(1.dp, GlassBorder, RoundedCornerShape(50))
                                .tvFocusRing(RoundedCornerShape(50))
                        ) {
                            Icon(
                                painter = androidx.compose.ui.res.painterResource(
                                    id = R.drawable.ic_source_server
                                ),
                                contentDescription = "Choose source manually",
                                tint = TextPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                IconButton(
                    onClick = {
                        scope.launch {
                            repository.toggleWatchlist(addonId, meta.type, meta.id, meta.name, meta.poster)
                        }
                    },
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(50))
                        .border(1.dp, GlassBorder, RoundedCornerShape(50))
                        .tvFocusRing(RoundedCornerShape(50))
                ) {
                    Icon(
                        if (watchItem != null) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = if (watchItem != null) "Remove from watchlist" else "Add to watchlist",
                        tint = LocalPalette.current.accent
                    )
                }
            }
        }
        // Cast row: each actor is tappable and opens their filmography.
        // Only actors with a TMDB person id navigate (keyless mode has names only).
        if (meta.cast.isNotEmpty()) {
            item(key = "cast") {
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
                    items(meta.cast, key = { it.id.toString() + "_" + it.name }) { actor ->
                        val clickable = actor.id > 0
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(72.dp)
                                .then(
                                    if (clickable) Modifier.clickable { onOpenActor(actor.id) }
                                    else Modifier
                                )
                                .tvFocusRing(RoundedCornerShape(12.dp))
                        ) {
                            SafePosterImage(
                                model = actor.photo,
                                contentDescription = actor.name,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, GlassBorder, CircleShape)
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
        if (isSeries) {
            item {
                Text("Seasons", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(seasons, key = { it }) { season ->
                        FilterChip(
                            selected = vm.selectedSeason == season,
                            onClick = { vm.selectSeason(season, meta) },
                            label = { Text(if (season == 0) "Specials" else "S$season") },
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
            val episodes = meta.videos.filter { it.season == vm.selectedSeason }
                .sortedWith(compareBy({ it.episode }, { it.title }))
            itemsIndexed(episodes, key = { index, video -> "${video.id}_$index" }) { _, video ->
                val selected = vm.selectedVideo?.id == video.id
                GlassCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                        .border(
                            if (selected) 1.dp else 0.dp,
                            if (selected) LocalPalette.current.accent else Color.Transparent,
                            RoundedCornerShape(20.dp)
                        ),
                    onClick = { vm.selectEpisode(video) }
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(72.dp, 48.dp).clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.06f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (video.thumbnail != null) {
                                SafePosterImage(video.thumbnail, null, modifier = Modifier.fillMaxSize())
                            } else {
                                Text("E${video.episode}", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${video.episode}. ${video.title}",
                                color = TextPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!video.overview.isNullOrBlank()) {
                                Text(video.overview!!, color = TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (selected) {
                            Icon(Icons.Filled.PlayArrow, null, tint = LocalPalette.current.accent)
                        }
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            Column(Modifier) {
                Text(
                    if (isSeries) "Sources for selected episode" else "Sources",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                AddonTabs(browserState)
            }
        }
        when (val s = vm.streamsState) {
            is StreamsUiState.Idle, StreamsUiState.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = LocalPalette.current.accent, modifier = Modifier.size(32.dp))
                }
            }
            is StreamsUiState.Error -> item {
                Text(
                    s.message,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            is StreamsUiState.Content -> {
                if (browserState.filtered.isEmpty()) {
                    item {
                        Text(
                            "No sources in this addon yet — they appear here as each addon answers.",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                } else {
                    streamListItems(
                        streams = browserState.filtered,
                        onPick = { stream ->
                            startPlayback(vm, repository, stream, s.subtitles, s.streams, onPlay)
                        }
                    )
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
        }
    )
    } // CompositionLocalProvider (badge style)
}

@Composable
private fun rememberSeasonList(meta: MetaDetail): List<Int> {
    return meta.videos.map { it.season }.distinct().sorted()
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
    onPick: (StreamItem) -> Unit
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
                        onPick = onPick
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
    onPick: (StreamItem) -> Unit
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

    Column(Modifier.fillMaxWidth()) {
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
                    CircularProgressIndicator(
                        color = palette.accent,
                        modifier = Modifier.size(32.dp)
                    )
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
                    contentPadding = PaddingValues(bottom = 12.dp)
                )
            }
        }
    }
}
