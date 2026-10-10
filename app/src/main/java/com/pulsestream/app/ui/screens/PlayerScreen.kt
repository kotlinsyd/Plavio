package com.pulsestream.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.pulsestream.app.data.ASPECT_MODES
import com.pulsestream.app.data.IntroDbApi
import com.pulsestream.app.data.PlaybackRequest
import com.pulsestream.app.data.ResolvedSubtitle
import com.pulsestream.app.data.SkipSegment
import com.pulsestream.app.data.StreamItem
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.data.Subtitle
import com.pulsestream.app.data.resolveSubtitleFiles
import com.pulsestream.app.data.Video
import com.pulsestream.app.data.subtitleLangMatches
import com.pulsestream.app.data.simkl.SimklManager
import com.pulsestream.app.data.simkl.SimklScrobbler
import com.pulsestream.app.data.trakt.TraktManager
import com.pulsestream.app.data.trakt.TraktScrobbler
import com.pulsestream.app.data.buttonLabel
import com.pulsestream.app.data.streamQualityLabel
import com.pulsestream.app.ui.components.AddonTabs
import com.pulsestream.app.ui.components.ErrorState
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.components.DotsLoadingIndicator
import com.pulsestream.app.ui.components.SafePosterImage
import com.pulsestream.app.ui.components.StreamCard
import com.pulsestream.app.ui.components.rememberStreamBrowserState
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.GlassBorder
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

/** Transient on-screen feedback for gestures, speed, aspect and track changes. */
private sealed interface PlayerIndicator {
    data class Volume(val percent: Int) : PlayerIndicator
    data class Brightness(val percent: Int) : PlayerIndicator
    data class Seek(val text: String) : PlayerIndicator
    data class Speed(val label: String) : PlayerIndicator
    data class Message(val text: String) : PlayerIndicator
}

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

private fun speedLabel(s: Float): String =
    if (s == s.toInt().toFloat()) "${s.toInt()}x" else "${s}x"

private fun formatMs(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** ISO 639-2/B (and -1) codes seen in the wild → display names. */
private val LANG_NAMES = mapOf(
    "eng" to "English", "en" to "English",
    "spa" to "Spanish", "es" to "Spanish",
    "fre" to "French", "fra" to "French", "fr" to "French",
    "deu" to "German", "ger" to "German", "de" to "German",
    "ita" to "Italian", "it" to "Italian",
    "por" to "Portuguese", "pob" to "Portuguese (BR)", "pt" to "Portuguese",
    "rus" to "Russian", "ru" to "Russian",
    "ara" to "Arabic", "ar" to "Arabic",
    "hin" to "Hindi", "hi" to "Hindi",
    "urd" to "Urdu", "ur" to "Urdu",
    "tur" to "Turkish", "tr" to "Turkish",
    "zho" to "Chinese", "chi" to "Chinese", "zh" to "Chinese",
    "jpn" to "Japanese", "ja" to "Japanese",
    "kor" to "Korean", "ko" to "Korean",
    "nld" to "Dutch", "dut" to "Dutch", "nl" to "Dutch",
    "swe" to "Swedish", "sv" to "Swedish",
    "nor" to "Norwegian", "nob" to "Norwegian", "no" to "Norwegian",
    "dan" to "Danish", "da" to "Danish",
    "fin" to "Finnish", "fi" to "Finnish",
    "pol" to "Polish", "pl" to "Polish",
    "ces" to "Czech", "cze" to "Czech", "cs" to "Czech",
    "slk" to "Slovak", "slo" to "Slovak", "sk" to "Slovak",
    "ron" to "Romanian", "rum" to "Romanian", "ro" to "Romanian",
    "ell" to "Greek", "gre" to "Greek", "el" to "Greek",
    "heb" to "Hebrew", "he" to "Hebrew",
    "tha" to "Thai", "th" to "Thai",
    "vie" to "Vietnamese", "vi" to "Vietnamese",
    "ind" to "Indonesian", "id" to "Indonesian",
    "msa" to "Malay", "may" to "Malay", "ms" to "Malay",
    "tam" to "Tamil", "ta" to "Tamil",
    "tel" to "Telugu", "te" to "Telugu",
    "ben" to "Bengali", "bn" to "Bengali",
    "mar" to "Marathi", "mr" to "Marathi",
    "pan" to "Punjabi", "pa" to "Punjabi",
    "guj" to "Gujarati", "gu" to "Gujarati",
    "ukr" to "Ukrainian", "uk" to "Ukrainian",
    "hun" to "Hungarian", "hu" to "Hungarian",
    "srp" to "Serbian", "sr" to "Serbian",
    "hrv" to "Croatian", "hr" to "Croatian",
    "bos" to "Bosnian", "ba" to "Bosnian",
    "slv" to "Slovenian", "sl" to "Slovenian",
    "bul" to "Bulgarian", "bg" to "Bulgarian",
    "cat" to "Catalan", "ca" to "Catalan"
)

private fun displayLanguage(code: String?): String? {
    if (code.isNullOrBlank() || code == "und") return null
    return LANG_NAMES[code.lowercase()] ?: code.uppercase()
}

private data class TrackOption(
    val group: TrackGroup,
    val trackIndex: Int,
    val label: String,
    val selected: Boolean
)

private fun trackLabel(format: Format, fallbackIndex: Int): String {
    if (!format.label.isNullOrBlank()) return format.label!!
    return displayLanguage(format.language) ?: "Track ${fallbackIndex + 1}"
}

private fun trackOptions(player: ExoPlayer, type: Int): List<TrackOption> =
    player.currentTracks.groups
        .filter { it.type == type }
        .flatMapIndexed { gi, g ->
            (0 until g.length).map { i ->
                TrackOption(g.mediaTrackGroup, i, trackLabel(g.getTrackFormat(i), gi), g.isSelected)
            }
        }

private fun hasSelectedTrack(player: ExoPlayer, type: Int): Boolean =
    player.currentTracks.groups.any { it.type == type && it.isSelected }

/**
 * Reference-style player: title block top-left, icon cluster top-right,
 * big center transport, slim seekbar + time chips, bottom pill toolbar
 * (Aspect / Speed / Subs / Audio / Sources / Episodes), edge volume pill,
 * and a controls lock — with all of Pulse's gesture strengths kept.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    repository: StreamRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val initialRequest = repository.pendingPlayback
    val settings = repository.settings

    BackHandler(onBack = onBack)

    if (initialRequest == null) {
        GlowBackground { ErrorState("Nothing to play.", onRetry = onBack) }
        return
    }

    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    val ioScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

    /**
     * Builds the player for a request. Side-loaded subtitles are NOT attached
     * here — they are resolved (downloaded + format-sniffed) in the background
     * and attached via [attachResolvedSubtitles], because their URLs rarely
     * carry a usable file extension.
     */
    fun buildExo(req: PlaybackRequest): ExoPlayer {
        val prefs = settings.subtitleLanguages
        // Debrid/proxied addons can require extra headers on the stream URL
        // (parsed from behaviorHints.proxyHeaders.request) — send them here.
        val dataSourceFactory = DefaultHttpDataSource.Factory().apply {
            if (req.headers.isNotEmpty()) setDefaultRequestProperties(req.headers)
        }
        return ExoPlayer.Builder(context)
            .setRenderersFactory(
                DefaultRenderersFactory(context).setExtensionRendererMode(
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER))
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            // Belt and suspenders: also tell the track selector the preferred
            // subtitle languages, so his languages win even if the default
            // flag is ever ignored.
            .setTrackSelector(
                DefaultTrackSelector(context).apply {
                    if (prefs.isNotEmpty()) {
                        setParameters(
                            buildUponParameters()
                                .setPreferredTextLanguages(*prefs.toTypedArray())
                        )
                    }
                }
            )
            .build()
            .apply {
                setMediaItem(
                    MediaItem.Builder()
                        .setUri(req.streamUrl)
                        .build()
                )
            }
    }


    var currentRequest by remember { mutableStateOf(initialRequest) }
    var player by remember { mutableStateOf(buildExo(initialRequest)) }
    val latestPlayer = rememberUpdatedState(player)
    val latestRequest = rememberUpdatedState(currentRequest)
    var playerView by remember { mutableStateOf<PlayerView?>(null) }

    // Simkl sync: scrobbles playback progress when the account is linked.
    val simkl = remember { SimklManager(settings) }
    val simklScrobbler = remember { SimklScrobbler(simkl, ioScope) }
    // Trakt sync: same, when the Trakt account is linked.
    val trakt = remember { TraktManager(settings) }
    val traktScrobbler = remember { TraktScrobbler(trakt, ioScope) }

    var showControls by remember { mutableStateOf(true) }
    var controlsTick by remember { mutableIntStateOf(0) }
    var locked by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var volumePercent by remember { mutableIntStateOf(0) }
    var speed by remember { mutableFloatStateOf(1f) }
    var speedMenuOpen by remember { mutableStateOf(false) }
    var indicator by remember { mutableStateOf<PlayerIndicator?>(null) }
    var indicatorTick by remember { mutableIntStateOf(0) }

    var isPortrait by remember { mutableStateOf(false) }
    // Aspect ratio (in-player only — cycles Fit → Fill → Zoom)
    var aspectMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    val aspectLabel = ASPECT_MODES.firstOrNull { it.first == aspectMode }?.second ?: "Fit"

    var isBuffering by remember { mutableStateOf(true) }
    var switchingMedia by remember { mutableStateOf(false) }

    // Track switching
    var tracksOpen by remember { mutableStateOf(false) }
    var tracksMode by remember { mutableStateOf("subs") } // "subs" or "audio"
    var trackTick by remember { mutableIntStateOf(0) }

    /**
     * Attaches resolved (downloaded + format-sniffed) subtitles to the current
     * player, preserving position. Auto-picks the default: the explicitly
     * requested URL first, else the first track matching the preferred
     * languages, else the first subtitle (or none when a preference is set
     * but nothing matches).
     */
    fun attachResolvedSubtitles(resolved: List<ResolvedSubtitle>, defaultUrl: String? = null) {
        if (resolved.isEmpty()) return
        val pos = player.currentPosition
        val wasPlaying = player.isPlaying
        val prefs = settings.subtitleLanguages
        val defaultIndex = when {
            defaultUrl != null ->
                resolved.indexOfFirst { it.sub.url == defaultUrl }.takeIf { it >= 0 } ?: -1
            prefs.isEmpty() -> 0
            else -> resolved.indexOfFirst { r ->
                prefs.any { pref -> subtitleLangMatches(r.sub.lang, pref) }
            }
        }
        val configs = resolved.mapIndexed { index, r ->
            MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(r.file))
                .setMimeType(r.mimeType)
                .setLanguage(r.sub.lang)
                .setId(r.sub.url)
                .apply { if (index == defaultIndex) setSelectionFlags(C.SELECTION_FLAG_DEFAULT) }
                .build()
        }
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(currentRequest.streamUrl)
                .setSubtitleConfigurations(configs)
                .build(),
            pos
        )
        player.prepare()
        player.playWhenReady = wasPlaying
        trackTick++
    }

    // Sources + episodes sheets
    var sourcesOpen by remember { mutableStateOf(false) }
    var episodesOpen by remember { mutableStateOf(false) }
    var availableStreams by remember { mutableStateOf(initialRequest.streams) }
    var streamsLoading by remember { mutableStateOf(false) }
    var streamsJob by remember { mutableStateOf<Job?>(null) }

    // Resume prompt
    var resumeOfferMs by remember { mutableStateOf<Long?>(null) }

    // Skip segments
    var skipSegments by remember { mutableStateOf(emptyList<SkipSegment>()) }
    var activeSegment by remember { mutableStateOf<SkipSegment?>(null) }

    // Subtitle search
    var subSearchOpen by remember { mutableStateOf(false) }
    var subSearchLoading by remember { mutableStateOf(false) }
    var subSearchResults by remember { mutableStateOf(emptyList<Subtitle>()) }
    var subSearchError by remember { mutableStateOf<String?>(null) }
    var addedSubs by remember { mutableStateOf(emptyList<Subtitle>()) }

    // Back closes sheets/dialogs before leaving the player.
    BackHandler(enabled = tracksOpen || subSearchOpen || sourcesOpen || episodesOpen) {
        tracksOpen = false
        subSearchOpen = false
        sourcesOpen = false
        episodesOpen = false
    }

    val audioOpts = remember(trackTick, player) { trackOptions(player, C.TRACK_TYPE_AUDIO) }
    val textOpts = remember(trackTick, player) { trackOptions(player, C.TRACK_TYPE_TEXT) }
    // When preferred subtitle languages are set, the Subs picker only offers
    // tracks in those languages (plus Off and Search). Preferences are read
    // fresh every time the dialog opens. Falls back to all tracks if none
    // match, so the menu is never empty.
    val subOpts = remember(trackTick, player, tracksOpen) {
        val prefs = settings.subtitleLanguages
        if (prefs.isEmpty()) textOpts
        else textOpts.filter { opt ->
            prefs.any { pref ->
                subtitleLangMatches(opt.group.getFormat(opt.trackIndex).language, pref)
            }
        }.ifEmpty { textOpts }
    }
    val subtitlesOff = remember(trackTick, player) { !hasSelectedTrack(player, C.TRACK_TYPE_TEXT) }

    fun pokeIndicator(i: PlayerIndicator) {
        indicator = i
        indicatorTick++
    }

    fun seekBy(deltaMs: Long) {
        val dur = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        player.seekTo((player.currentPosition + deltaMs).coerceIn(0L, dur))
    }

    fun seekByWithFeedback(deltaMs: Long) {
        seekBy(deltaMs)
        pokeIndicator(PlayerIndicator.Seek(if (deltaMs >= 0) "+10s" else "−10s"))
    }

    fun toggleControls() {
        if (locked) return
        showControls = !showControls
        if (showControls) controlsTick++
    }

    fun togglePlay() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun cycleAspect() {
        aspectMode = when (aspectMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        playerView?.resizeMode = aspectMode
        pokeIndicator(PlayerIndicator.Message("Aspect: $aspectLabel"))
    }

    fun startSeekPreview() {
    }

    fun applyAudioSelection(opt: TrackOption?) {
        val params = player.trackSelectionParameters.buildUpon()
        if (opt == null) params.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
        else params.setOverrideForType(TrackSelectionOverride(opt.group, listOf(opt.trackIndex)))
        player.trackSelectionParameters = params.build()
        trackTick++
        pokeIndicator(PlayerIndicator.Message(if (opt == null) "Audio: Auto" else "Audio: ${opt.label}"))
    }

    fun applySubtitleSelection(opt: TrackOption?) {
        val params = player.trackSelectionParameters.buildUpon()
        if (opt == null) {
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            params.setOverrideForType(TrackSelectionOverride(opt.group, listOf(opt.trackIndex)))
        }
        player.trackSelectionParameters = params.build()
        trackTick++
        pokeIndicator(
            PlayerIndicator.Message(if (opt == null) "Subtitles: Off" else "Subtitles: ${opt.label}")
        )
    }

    fun openSubtitleSearch() {
        tracksOpen = false
        subSearchOpen = true
        subSearchLoading = true
        subSearchError = null
        subSearchResults = emptyList()
        // Capture request data on the main thread before hopping to IO.
        val imdb = currentRequest.imdbId
        val isMovie = currentRequest.isMovie
        val existingUrls = (currentRequest.subtitles + addedSubs).map { it.url }.toSet()
        ioScope.launch {
            try {
                if (imdb.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        subSearchError = "No title info available for subtitle search."
                    }
                } else {
                    val type = if (isMovie) "movie" else "series"
                    val results = repository.searchSubtitles(type, imdb)
                        .filter { it.url !in existingUrls }
                    withContext(Dispatchers.Main) {
                        subSearchResults = results
                        if (results.isEmpty()) subSearchError = "No subtitles found for this title."
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    subSearchError = e.message ?: "Subtitle search failed."
                }
            } finally {
                withContext(Dispatchers.Main + NonCancellable) { subSearchLoading = false }
            }
        }
    }

    var pendingDefaultSubUrl by remember { mutableStateOf<String?>(null) }
    // True when side-loaded subtitles existed but none could be downloaded.
    var subLoadError by remember { mutableStateOf(false) }

    fun addExternalSubtitle(sub: Subtitle) {
        addedSubs = addedSubs + sub
        subSearchOpen = false
        // The resolver effect below picks this up, downloads it and makes it
        // the active subtitle.
        pendingDefaultSubUrl = sub.url
        pokeIndicator(
            PlayerIndicator.Message("Subtitles: ${displayLanguage(sub.lang) ?: sub.lang.uppercase()}")
        )
    }

    // URL of a just-added subtitle that should become active once resolved.

    // Resolve side-loaded subtitles in the background once per request (or
    // when the user adds one via search): download, sniff the real format,
    // then attach. Files are cached so this is instant on repeat visits.
    LaunchedEffect(currentRequest, addedSubs) {
        val all = currentRequest.subtitles + addedSubs
        subLoadError = false
        if (all.isEmpty()) return@LaunchedEffect
        val resolved = resolveSubtitleFiles(all, context.cacheDir)
        subLoadError = resolved.isEmpty()
        val wantDefault = pendingDefaultSubUrl
        pendingDefaultSubUrl = null
        attachResolvedSubtitles(resolved, wantDefault)
    }

    /** Rebuilds the ExoPlayer around a new stream, preserving position. */
    fun replaceStream(newReq: PlaybackRequest, startAtMs: Long, autoplay: Boolean) {
        val old = player
        val oldReq = currentRequest
        // Read the old player's position on THIS thread (the player's own
        // thread, normally main). Touching it from a background thread crashes.
        val posMs = old.currentPosition
        val durMs = old.duration.takeIf { it > 0 } ?: 0L
        ioScope.launch {
            // Persist the old stream's position, then drop its entry so
            // Continue Watching doesn't accumulate duplicates per source.
            repository.savePlayback(
                title = oldReq.title,
                poster = oldReq.poster,
                videoId = oldReq.videoId,
                streamUrl = oldReq.streamUrl,
                positionMs = posMs,
                durationMs = durMs,
                imdbId = oldReq.imdbId,
                season = oldReq.season,
                episode = oldReq.episode
            )
            repository.deleteSavedPosition(oldReq.streamUrl)
            // Everything below touches the player or Compose state, so it must
            // run on the main thread no matter which thread called us.
            withContext(Dispatchers.Main) {
                currentRequest = newReq
                repository.pendingPlayback = newReq
                resumeOfferMs = null
                val fresh = buildExo(newReq)
                // Detach the surface from the old player before releasing it.
                playerView?.player = null
                player = fresh
                old.release()
                fresh.seekTo(startAtMs)
                fresh.prepare()
                fresh.playWhenReady = autoplay
                showControls = true
                controlsTick++
            }
        }
    }

    fun switchSource(stream: StreamItem) {
        if (stream.url == currentRequest.streamUrl) return
        sourcesOpen = false
        pokeIndicator(
            PlayerIndicator.Message("Source: ${streamQualityLabel(stream) ?: stream.sourceName ?: "switched"}")
        )
        replaceStream(
            currentRequest.copy(
                streamUrl = stream.url,
                headers = stream.headers,
                streamLabel = streamQualityLabel(stream),
                sourceName = stream.sourceName
            ),
            startAtMs = player.currentPosition,
            autoplay = true
        )
    }

    /** Quietly (re)fills the Sources list in the background, progressively. */
    fun refreshStreamsBackground(type: String, videoId: String) {
        streamsJob?.cancel()
        streamsJob = ioScope.launch {
            streamsLoading = true
            try {
                repository.streamResultsFlow(type, videoId).collect { result ->
                    if (result.streams.isNotEmpty()) {
                        withContext(Dispatchers.Main) { availableStreams = result.streams }
                    }
                }
            } finally {
                // NonCancellable: a replaced job must still clear the spinner.
                withContext(Dispatchers.Main + NonCancellable) { streamsLoading = false }
            }
        }
    }

    fun playEpisode(video: Video) {
        episodesOpen = false
        switchingMedia = true
        ioScope.launch {
            val type = currentRequest.type
            val result = withTimeoutOrNull(20_000) {
                repository.streamResultsFlow(type, video.id).first { it.streams.isNotEmpty() }
            }
            // Back to the main thread: everything below touches Compose state.
            withContext(Dispatchers.Main) {
                val stream = result?.streams?.firstOrNull()
                if (stream == null) {
                    pokeIndicator(PlayerIndicator.Message("No sources found for this episode"))
                } else {
                    val showName = currentRequest.seriesName ?: currentRequest.title
                    availableStreams = result.streams
                    replaceStream(
                        currentRequest.copy(
                            streamUrl = stream.url,
                            headers = stream.headers,
                            title = "$showName S${video.season}E${video.episode}",
                            poster = video.thumbnail ?: currentRequest.poster,
                            videoId = video.id,
                            subtitles = result.subtitles,
                            season = video.season,
                            episode = video.episode,
                            episodeTitle = video.title,
                            streamLabel = streamQualityLabel(stream),
                            sourceName = stream.sourceName,
                            streams = result.streams
                        ),
                        startAtMs = 0L,
                        autoplay = true
                    )
                    // Keep filling the Sources list with the remaining addons.
                    refreshStreamsBackground(type, video.id)
                }
                switchingMedia = false
            }
        }
    }

    // Auto-select a freshly added subtitle track once ExoPlayer exposes it.
    LaunchedEffect(addedSubs.size) {
        val sub = addedSubs.lastOrNull() ?: return@LaunchedEffect
        repeat(15) {
            val group = player.currentTracks.groups.firstOrNull { g ->
                g.type == C.TRACK_TYPE_TEXT &&
                    (0 until g.length).any { i -> g.getTrackFormat(i).id == sub.url }
            }
            if (group != null) {
                val idx = (0 until group.length).first { i -> group.getTrackFormat(i).id == sub.url }
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(idx)))
                    .build()
                trackTick++
                return@LaunchedEffect
            }
            delay(300)
        }
    }

    /**
     * Full gesture set for the video surface: tap, double-tap, and
     * directional swipes. Disabled while the controls lock is on.
     */
    fun Modifier.videoGestures(): Modifier = this
        .pointerInput(player, locked) {
            detectTapGestures(
                onTap = {
                    if (locked) pokeIndicator(PlayerIndicator.Message("Locked — tap the lock icon to unlock"))
                    else toggleControls()
                },
                onDoubleTap = { offset ->
                    if (!locked) {
                        if (offset.x < size.width / 2f) seekByWithFeedback(-10_000)
                        else seekByWithFeedback(10_000)
                    }
                }
            )
        }
        .pointerInput(player, locked) {
            if (locked) return@pointerInput
            val slop = viewConfiguration.touchSlop
            var mode = -1 // 0 = seek, 1 = volume, 2 = brightness
            var startX = 0f
            var accX = 0f
            var accY = 0f
            var seekBase = 0L
            var seekTarget = -1L
            var volBase = 0
            var brightBase = 0.5f
            detectDragGestures(
                onDragStart = { offset ->
                    startX = offset.x
                    accX = 0f
                    accY = 0f
                    mode = -1
                    seekTarget = -1L
                    seekBase = player.currentPosition
                    volBase = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    brightBase = activity?.window?.attributes
                        ?.screenBrightness?.takeIf { it >= 0 } ?: 0.5f
                },
                onDragEnd = {
                    if (mode == 0 && seekTarget >= 0) player.seekTo(seekTarget)
                    mode = -1
                },
                onDragCancel = { mode = -1 },
                onDrag = { change, dragAmount ->
                    change.consume()
                    accX += dragAmount.x
                    accY += dragAmount.y
                    if (mode == -1 && (abs(accX) > slop || abs(accY) > slop)) {
                        mode = if (abs(accX) > abs(accY)) 0
                        else if (startX < size.width / 2f) 2 else 1
                    }
                    when (mode) {
                        0 -> { // horizontal swipe: seek proportional to swipe distance
                            val dur = player.duration
                            if (dur > 0) {
                                seekTarget = (seekBase + accX / size.width * dur)
                                    .toLong().coerceIn(0L, dur)
                                val delta = seekTarget - player.currentPosition
                                pokeIndicator(
                                    PlayerIndicator.Seek(
                                        (if (delta >= 0) "+" else "−") + formatMs(abs(delta))
                                    )
                                )
                            }
                        }
                        1 -> { // vertical swipe, right half: volume
                            val v = (volBase - accY / size.height * maxVolume)
                                .roundToInt().coerceIn(0, maxVolume)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
                            volumePercent = v * 100 / maxVolume.coerceAtLeast(1)
                            pokeIndicator(PlayerIndicator.Volume(volumePercent))
                        }
                        2 -> { // vertical swipe, left half: brightness
                            val b = (brightBase - accY / size.height).coerceIn(0.01f, 1f)
                            activity?.window?.let { w ->
                                val lp = w.attributes
                                lp.screenBrightness = b
                                w.attributes = lp
                            }
                            pokeIndicator(PlayerIndicator.Brightness((b * 100).toInt()))
                        }
                    }
                }
            )
        }

    /** Tap-only gestures for the controls overlay (slider drags stay clean). */
    fun Modifier.tapGestures(): Modifier = this.pointerInput(player, locked) {
        detectTapGestures(
            onTap = {
                if (locked) pokeIndicator(PlayerIndicator.Message("Locked — tap the lock icon to unlock"))
                else toggleControls()
            },
            onDoubleTap = { offset ->
                if (!locked) {
                    if (offset.x < size.width / 2f) seekByWithFeedback(-10_000)
                    else seekByWithFeedback(10_000)
                }
            }
        )
    }

    fun savePosition() {
        val pl = latestPlayer.value
        val req = latestRequest.value
        val pos = pl.currentPosition
        val dur = pl.duration.takeIf { it > 0 } ?: C.TIME_UNSET
        ioScope.launch {
            repository.savePlayback(
                title = req.title,
                poster = req.poster,
                videoId = req.videoId,
                streamUrl = req.streamUrl,
                positionMs = pos,
                durationMs = if (dur == C.TIME_UNSET) 0L else dur,
                imdbId = req.imdbId,
                season = req.season,
                episode = req.episode
            )
        }
    }

    // Poll position / duration / playing / buffering / volume for the UI,
    // and track the active skip-segment window.
    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition
            durationMs = player.duration.takeIf { it > 0 } ?: 0L
            isPlaying = player.isPlaying
            isBuffering = player.playbackState == Player.STATE_BUFFERING ||
                player.playbackState == Player.STATE_IDLE
            volumePercent = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) *
                100 / maxVolume.coerceAtLeast(1)
            activeSegment = if (settings.showSkipButton) {
                skipSegments.firstOrNull { positionMs >= it.startMs && positionMs <= it.endMs }
            } else null
            simklScrobbler.tick(
                currentRequest,
                positionMs,
                durationMs,
                isPlaying,
                player.playbackState == Player.STATE_ENDED
            )
            traktScrobbler.tick(
                currentRequest,
                positionMs,
                durationMs,
                isPlaying,
                player.playbackState == Player.STATE_ENDED
            )
            delay(500)
        }
    }

    // Auto-hide controls and indicators.
    LaunchedEffect(showControls, controlsTick) {
        if (showControls && !locked) {
            delay(3500)
            showControls = false
        }
    }
    LaunchedEffect(indicatorTick) {
        if (indicator != null) {
            delay(1000)
            indicator = null
        }
    }

    // One-time init: resume prompt / autoplay, then prepare.
    LaunchedEffect(Unit) {
        val saved = repository.getSavedPosition(initialRequest.streamUrl)
        if (saved > 0) player.seekTo(saved)
        player.prepare()
        if (saved > 0) {
            player.playWhenReady = false
            resumeOfferMs = saved
            showControls = true
        } else if (settings.autoplay) {
            player.playWhenReady = true
        } else {
            player.playWhenReady = false
            showControls = true
        }
        // Sources sheet fallback: if we arrived without a stream list
        // (e.g. from Continue Watching), fill it quietly in the background.
        if (availableStreams.isEmpty()) {
            refreshStreamsBackground(initialRequest.type, initialRequest.videoId)
        }
    }

    // Skip segments follow the title, not the source — fetch once per episode.
    LaunchedEffect(initialRequest.imdbId, initialRequest.season, initialRequest.episode) {
        skipSegments = if (!initialRequest.imdbId.isNullOrBlank()) {
            runCatching {
                IntroDbApi().fetchSegments(
                    initialRequest.imdbId!!,
                    initialRequest.season,
                    initialRequest.episode,
                    initialRequest.isMovie
                )
            }.getOrDefault(emptyList())
        } else emptyList()
    }

    // Save on background so progress is never lost.
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) savePosition()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // Landscape + immersive; restore on exit; save + release on dispose.
    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        val window = activity?.window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
        val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        onDispose {
            savePosition()
            simklScrobbler.shutdown()
            traktScrobbler.shutdown()
            latestPlayer.value.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val req = currentRequest
    val headerTitle = req.seriesName ?: req.title
    val episodeLine = if (!req.isMovie) {
        "S${req.season}E${req.episode}" + (req.episodeTitle?.let { " • $it" } ?: "")
    } else null
    val sourceLine = listOfNotNull(req.streamLabel, req.sourceName)
        .joinToString(" • ").takeIf { it.isNotBlank() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Video surface with the full gesture set.
        Box(Modifier.fillMaxSize().videoGestures()) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).also { pv ->
                        pv.player = player
                        pv.useController = false
                        pv.keepScreenOn = settings.keepScreenOn
                        pv.resizeMode = aspectMode
                        pv.layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        playerView = pv
                    }
                },
                update = { pv -> pv.player = player },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Pulse-branded loading line while the stream connects / buffers.
        if (isBuffering || switchingMedia) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                DotsLoadingIndicator(if (switchingMedia) "Loading episode…" else "Finding source…")
            }
        }

        // Transient gesture feedback (never consumes touches).
        indicator?.let { ind ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Surface(
                    color = Color.Black.copy(alpha = 0.62f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when (ind) {
                            is PlayerIndicator.Volume -> {
                                Icon(Icons.Filled.VolumeUp, null, tint = LocalPalette.current.accent)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Volume ${ind.percent}%",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            is PlayerIndicator.Brightness -> {
                                Icon(Icons.Filled.SettingsBrightness, null, tint = LocalPalette.current.accent)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Brightness ${ind.percent}%",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            is PlayerIndicator.Seek -> {
                                Text(
                                    ind.text,
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            is PlayerIndicator.Speed -> {
                                Text(
                                    "Speed ${ind.label}",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            is PlayerIndicator.Message -> {
                                Text(
                                    ind.text,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Skip intro / recap / outro button (IntroDB).
        activeSegment?.let { seg ->
            Box(
                Modifier.fillMaxSize().padding(end = 24.dp, bottom = 170.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                TextButton(
                    onClick = {
                        player.seekTo(seg.endMs + 400)
                        activeSegment = null
                    },
                    modifier = Modifier.background(
                        Color.Black.copy(alpha = 0.72f),
                        RoundedCornerShape(10.dp)
                    )
                ) {
                    Text(
                        seg.type.buttonLabel(),
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Unlock affordance while locked (controls stay hidden).
        if (locked) {
            IconButton(
                onClick = {
                    locked = false
                    showControls = true
                    controlsTick++
                    pokeIndicator(PlayerIndicator.Message("Controls unlocked"))
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
            ) {
                Icon(Icons.Filled.Lock, "Unlock controls", tint = LocalPalette.current.accent)
            }
        }

        // Controls overlay.
        AnimatedVisibility(
            visible = showControls && !locked,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(Modifier.fillMaxSize().tapGestures()) {
                // ---- Top: title block + icon cluster ----
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(Modifier.weight(1f).padding(top = 2.dp)) {
                        Text(
                            headerTitle,
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (episodeLine != null) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                episodeLine,
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (sourceLine != null) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                sourceLine,
                                color = TextSecondary.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            locked = true
                            showControls = false
                            pokeIndicator(PlayerIndicator.Message("Controls locked"))
                        }) {
                            Icon(Icons.Filled.LockOpen, "Lock controls", tint = TextPrimary)
                        }
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, "Back", tint = TextPrimary)
                        }
                    }
                }

                // ---- Center transport ----
                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { seekByWithFeedback(-10_000) }) {
                        Icon(
                            Icons.Filled.Replay10, "Back 10 seconds",
                            tint = TextPrimary, modifier = Modifier.size(56.dp)
                        )
                    }
                    IconButton(onClick = { togglePlay() }) {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            if (isPlaying) "Pause" else "Play",
                            tint = TextPrimary, modifier = Modifier.size(76.dp)
                        )
                    }
                    IconButton(onClick = { seekByWithFeedback(10_000) }) {
                        Icon(
                            Icons.Filled.Forward10, "Forward 10 seconds",
                            tint = TextPrimary, modifier = Modifier.size(56.dp)
                        )
                    }
                }

                // ---- Bottom: seekbar, time chips, pill toolbar ----
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                            )
                        )
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 14.dp)
                ) {
                    SlimSeekBar(
                        positionMs = positionMs,
                        durationMs = durationMs,
                        onSeek = { player.seekTo(it) }
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TimeChip(formatMs(positionMs))
                        TimeChip(formatMs(durationMs))
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(50),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, GlassBorder
                            )
                        ) {
                            Row(
                                Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PillBarButton(
                                    icon = Icons.Filled.AspectRatio,
                                    label = aspectLabel,
                                    onClick = { cycleAspect() }
                                )
                                PillBarButton(
                                    icon = Icons.Filled.ScreenRotation,
                                    label = "Rotate",
                                    onClick = {
                                        isPortrait = !isPortrait
                                        activity?.requestedOrientation = if (isPortrait)
                                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                        else
                                            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                    }
                                )
                                Box {
                                    PillBarButton(
                                        icon = Icons.Filled.Speed,
                                        label = speedLabel(speed),
                                        onClick = { speedMenuOpen = true }
                                    )
                                    DropdownMenu(
                                        expanded = speedMenuOpen,
                                        onDismissRequest = { speedMenuOpen = false }
                                    ) {
                                        SPEEDS.forEach { s ->
                                            DropdownMenuItem(
                                                text = { Text(speedLabel(s)) },
                                                onClick = {
                                                    speed = s
                                                    player.setPlaybackSpeed(s)
                                                    speedMenuOpen = false
                                                    pokeIndicator(PlayerIndicator.Speed(speedLabel(s)))
                                                }
                                            )
                                        }
                                    }
                                }
                                PillBarButton(
                                    icon = Icons.Filled.ClosedCaption,
                                    label = "Subs",
                                    onClick = { trackTick++; tracksMode = "subs"; tracksOpen = true }
                                )
                                PillBarButton(
                                    icon = Icons.Filled.Audiotrack,
                                    label = "Audio",
                                    onClick = { trackTick++; tracksMode = "audio"; tracksOpen = true }
                                )
                                PillBarButton(
                                    icon = Icons.Filled.CloudDownload,
                                    label = "Sources",
                                    onClick = { sourcesOpen = true }
                                )
                                if (!req.isMovie && req.episodes.isNotEmpty()) {
                                    PillBarButton(
                                        icon = Icons.Filled.VideoLibrary,
                                        label = "Episodes",
                                        onClick = { episodesOpen = true }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Resume prompt.
        resumeOfferMs?.let { saved ->
            fun dismissResume() {
                resumeOfferMs = null
            }
            AlertDialog(
                onDismissRequest = {
                    // Tapping outside keeps their place and resumes.
                    dismissResume()
                    player.playWhenReady = true
                },
                containerColor = Color(0xFF14141F),
                title = {
                    Text(
                        "Resume playback",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        "Resume \"${headerTitle}\" from ${formatMs(saved)}?",
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        dismissResume()
                        player.playWhenReady = true
                    }) {
                        Text("Resume", color = LocalPalette.current.accent, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        dismissResume()
                        ioScope.launch { repository.deleteSavedPosition(currentRequest.streamUrl) }
                        player.seekTo(0)
                        player.playWhenReady = true
                    }) {
                        Text("Start over", color = TextSecondary)
                    }
                }
            )
        }

        // Track switching: Subs button opens subtitles only, Audio button opens audio only.
        if (tracksOpen) {
            val isAudio = tracksMode == "audio"
            AlertDialog(
                onDismissRequest = { tracksOpen = false },
                containerColor = Color(0xFF14141F),
                title = {
                    Text(
                        if (isAudio) "Audio" else "Subtitles",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        if (isAudio) {
                            TrackRow(
                                label = "Auto",
                                selected = audioOpts.none { it.selected },
                                onClick = { applyAudioSelection(null) }
                            )
                            audioOpts.forEach { opt ->
                                TrackRow(
                                    label = opt.label,
                                    selected = opt.selected,
                                    onClick = { applyAudioSelection(opt) }
                                )
                            }
                        } else {
                            TrackRow(
                                label = "Off",
                                selected = subtitlesOff,
                                onClick = { applySubtitleSelection(null) }
                            )
                            subOpts.forEach { opt ->
                                TrackRow(
                                    label = opt.label,
                                    selected = opt.selected,
                                    onClick = { applySubtitleSelection(opt) }
                                )
                            }
                            if (subLoadError && subOpts.isEmpty()) {
                                Text(
                                    "Couldn't load subtitles — check your connection and try again.",
                                    color = Color(0xFFEF4444),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            Spacer(Modifier.size(8.dp))
                            TextButton(onClick = { openSubtitleSearch() }) {
                                Text(
                                    "Search subtitles online",
                                    color = LocalPalette.current.accent,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { tracksOpen = false }) {
                        Text("Done", color = LocalPalette.current.accent, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // In-player subtitle search results.
        if (subSearchOpen) {
            AlertDialog(
                onDismissRequest = { subSearchOpen = false },
                containerColor = Color(0xFF14141F),
                title = {
                    Text(
                        "Find subtitles",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    when {
                        subSearchLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                color = LocalPalette.current.accent,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("Searching subtitle addons…", color = TextSecondary)
                        }
                        subSearchError != null -> Text(
                            subSearchError!!,
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        else -> Column(Modifier.verticalScroll(rememberScrollState())) {
                            subSearchResults.forEach { sub ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { addExternalSubtitle(sub) }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            displayLanguage(sub.lang) ?: sub.lang.uppercase(),
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            sub.url.substringAfter("://").take(52),
                                            color = TextSecondary,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { subSearchOpen = false }) {
                        Text("Close", color = TextSecondary)
                    }
                }
            )
        }

        // Sources switcher: pick another stream without leaving the player.
        if (sourcesOpen) {
            val browserState = rememberStreamBrowserState(availableStreams)
            ModalBottomSheet(
                onDismissRequest = { sourcesOpen = false },
                sheetState = rememberModalBottomSheetState(),
                containerColor = Color(0xFF14141F)
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    Text(
                        "Sources",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        "${availableStreams.size} available",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                AddonTabs(browserState, modifier = Modifier.padding(bottom = 4.dp))
                when {
                    availableStreams.isEmpty() && streamsLoading -> {
                        Box(
                            Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = LocalPalette.current.accent)
                                Spacer(Modifier.height(12.dp))
                                Text("Finding sources…", color = TextSecondary)
                            }
                        }
                    }
                    availableStreams.isEmpty() -> {
                        Column(
                            Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "No sources loaded for this title.",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(12.dp))
                            TextButton(
                                onClick = {
                                    refreshStreamsBackground(
                                        currentRequest.type,
                                        currentRequest.videoId
                                    )
                                }
                            ) {
                                Text("Load sources", color = LocalPalette.current.accent, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                start = 4.dp, end = 4.dp, bottom = 32.dp
                            )
                        ) {
                            items(
                                browserState.filtered,
                                key = { s -> "src_${s.url.hashCode()}" }
                            ) { stream ->
                                StreamCard(
                                    stream = stream,
                                    selected = stream.url == currentRequest.streamUrl,
                                    onClick = { switchSource(stream) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Episodes picker for series.
        if (episodesOpen) {
            val episodes = currentRequest.episodes
            val seasons = remember(episodes) { episodes.map { it.season }.distinct().sorted() }
            var selSeason by remember(episodes) { mutableStateOf(currentRequest.season) }
            ModalBottomSheet(
                onDismissRequest = { episodesOpen = false },
                sheetState = rememberModalBottomSheetState(),
                containerColor = Color(0xFF14141F)
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    Text(
                        "Episodes",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        items(seasons, key = { it }) { season ->
                            FilterChip(
                                selected = selSeason == season,
                                onClick = { selSeason = season },
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
                val eps = remember(episodes, selSeason) {
                    episodes.filter { it.season == selSeason }
                        .sortedWith(compareBy({ it.episode }, { it.title }))
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 20.dp, end = 20.dp, bottom = 32.dp
                    )
                ) {
                    items(eps, key = { it.id }) { video ->
                        val selected = video.id == currentRequest.videoId
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (selected) LocalPalette.current.accent.copy(alpha = 0.14f)
                                    else Color.White.copy(alpha = 0.04f)
                                )
                                .clickable { playEpisode(video) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(64.dp, 44.dp).clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.06f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (video.thumbnail != null) {
                                    SafePosterImage(
                                        video.thumbnail, null,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(
                                        "E${video.episode}",
                                        color = TextSecondary,
                                        style = MaterialTheme.typography.labelMedium
                                    )
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
                                    Text(
                                        video.overview!!,
                                        color = TextSecondary,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (selected) {
                                Icon(Icons.Filled.PlayArrow, null, tint = LocalPalette.current.accent)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = LocalPalette.current.accent)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            color = TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SlimSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragFrac by remember { mutableStateOf<Float?>(null) }
    val frac = (dragFrac ?: if (durationMs > 0) positionMs.toFloat() / durationMs else 0f)
        .coerceIn(0f, 1f)
    val density = LocalDensity.current
    val progressColor = LocalPalette.current.accent
    Canvas(
        modifier
            .fillMaxWidth()
            .height(30.dp)
            .pointerInput(durationMs) {
                detectTapGestures(onTap = { offset ->
                    onSeek(((offset.x / size.width).coerceIn(0f, 1f) * durationMs).toLong())
                })
            }
            .pointerInput(durationMs) {
                detectDragGestures(
                    onDragStart = { offset ->
                        dragFrac = (offset.x / size.width).coerceIn(0f, 1f)
                    },
                    onDrag = { change, _ ->
                        dragFrac = (change.position.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        dragFrac?.let { onSeek((it * durationMs).toLong()) }
                        dragFrac = null
                    },
                    onDragCancel = { dragFrac = null }
                )
            }
    ) {
        val trackH = with(density) { 4.dp.toPx() }
        val handle = with(density) { 14.dp.toPx() }
        val cy = size.height / 2f
        drawRoundRect(
            color = Color.White.copy(alpha = 0.28f),
            topLeft = Offset(0f, cy - trackH / 2f),
            size = Size(size.width, trackH),
            cornerRadius = CornerRadius(trackH / 2f, trackH / 2f)
        )
        val pw = size.width * frac
        if (pw > 1f) {
            drawRoundRect(
                color = progressColor,
                topLeft = Offset(0f, cy - trackH / 2f),
                size = Size(pw, trackH),
                cornerRadius = CornerRadius(trackH / 2f, trackH / 2f)
            )
        }
        val hx = (pw - handle / 2f).coerceIn(0f, size.width - handle)
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(hx, cy - handle / 2f),
            size = Size(handle, handle),
            cornerRadius = CornerRadius(handle / 2f, handle / 2f)
        )
    }
}

/** Small time pill shown under the seekbar. */
@Composable
private fun TimeChip(text: String) {
    Surface(
        color = Color.White.copy(alpha = 0.14f),
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimary
        )
    }
}

/** One icon+label button in the bottom pill toolbar. */
@Composable
private fun PillBarButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = TextPrimary, modifier = Modifier.size(21.dp))
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            maxLines = 1
        )
    }
}
