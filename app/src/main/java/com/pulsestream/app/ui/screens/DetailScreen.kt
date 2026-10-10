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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulsestream.app.ui.components.PlayButton
import com.pulsestream.app.ui.components.SafePosterImage
import com.pulsestream.app.data.MetaDetail
import com.pulsestream.app.data.PlaybackRequest
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
import com.pulsestream.app.ui.components.StreamCard
import com.pulsestream.app.ui.components.rememberStreamBrowserState
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.GlassBorder
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
                            autoPlayEvent = AutoPlay(result.streams.first(), result.subtitles)
                        }
                    }
                    // Empty intermediate emissions: stay on Loading, don't flash an error.
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
            val best = StreamProber.findBest(streams)
            pickState = if (best != null) PickState.Found(best) else PickState.None
        }
    }

    /** Plavio Pick button: play the best verified stream. */
    fun onPickPressed() {
        val pick = pickState as? PickState.Found ?: return
        val subtitles = (streamsState as? StreamsUiState.Content)?.subtitles ?: emptyList()
        autoPlayEvent = AutoPlay(pick.stream, subtitles)
    }

    /**
     * Play button: start the first available stream immediately.
     * If streams are still loading, fire as soon as they arrive.
     */
    fun onPlayPressed() {
        when (val s = streamsState) {
            is StreamsUiState.Content -> {
                if (s.streams.isNotEmpty()) autoPlayEvent = AutoPlay(s.streams.first(), s.subtitles)
            }
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

    fun consumeAutoPlay(): AutoPlay? {
        val e = autoPlayEvent
        autoPlayEvent = null
        return e
    }
}

@Composable
fun DetailScreen(
    repository: StreamRepository,
    addonId: String,
    type: String,
    metaId: String,
    onBack: () -> Unit,
    onPlay: () -> Unit
) {
    val vm: DetailViewModel = viewModel(factory = vmFactory { DetailViewModel(repository) })

    // Resolve the addon base URL (from search cache or the database) as a hint,
    // then load. Metadata/streams may come from *other* installed addons.
    LaunchedEffect(addonId, metaId) {
        val baseUrl = repository.getAddonBaseUrl(addonId)
        vm.load(baseUrl, type, metaId)
    }

    // Play button: autoplay the first available stream as soon as it's ready.
    LaunchedEffect(vm.autoPlayEvent) {
        vm.consumeAutoPlay()?.let { autoplay ->
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
                onPlay = onPlay
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

@Composable
private fun DetailContent(
    vm: DetailViewModel,
    meta: MetaDetail,
    addonId: String,
    repository: StreamRepository,
    onBack: () -> Unit,
    onPlay: () -> Unit
) {
    val isSeries = meta.type == "series" && meta.videos.isNotEmpty()
    val seasons = rememberSeasonList(meta)
    // Shared stream-browser state: addon tabs + filtered list stay in sync as
    // sources stream in progressively.
    val allStreams = (vm.streamsState as? StreamsUiState.Content)?.streams ?: emptyList()
    val browserState = rememberStreamBrowserState(allStreams)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
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
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                PlavioPickButton(
                    pickState = vm.pickState,
                    onClick = { vm.onPickPressed() }
                )
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
                ) {
                    Icon(
                        if (watchItem != null) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = if (watchItem != null) "Remove from watchlist" else "Add to watchlist",
                        tint = LocalPalette.current.accent
                    )
                }
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
            Text(
                if (isSeries) "Sources for selected episode" else "Sources",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            AddonTabs(browserState)
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
                    itemsIndexed(
                        browserState.filtered,
                        key = { index, stream -> "${index}_${stream.url}" }
                    ) { _, stream ->
                        StreamCard(stream = stream) {
                            startPlayback(vm, repository, stream, s.subtitles, s.streams, onPlay)
                        }
                    }
                }
            }
        }
    }
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

/**
 * "Plavio Pick": small green play button next to the main Play button.
 * Hidden until the background probe verifies a best stream; shows a
 * spinner while checking.
 */
@Composable
private fun PlavioPickButton(
    pickState: PickState,
    onClick: () -> Unit
) {
    val green = Color(0xFF22C55E)
    when (pickState) {
        PickState.Idle, PickState.None -> {
            // Nothing verified yet — stay out of the way.
        }
        PickState.Probing -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Plavio Pick",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = green
                )
                Spacer(Modifier.height(6.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(26.dp),
                    color = green,
                    strokeWidth = 3.dp
                )
            }
        }
        is PickState.Found -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Plavio Pick",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = green
                )
                Spacer(Modifier.height(4.dp))
                IconButton(
                    onClick = onClick,
                    modifier = Modifier
                        .size(52.dp)
                        .background(green, RoundedCornerShape(50))
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Play Plavio Pick",
                        tint = Color(0xFF0A0A12),
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
    }
}
