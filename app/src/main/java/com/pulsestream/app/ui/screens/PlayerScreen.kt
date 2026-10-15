package com.pulsestream.app.ui.screens

import com.pulsestream.app.ui.screens.player.*

import android.app.Activity
import android.app.ActivityManager
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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureInPicture
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
import androidx.compose.material3.Button
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.stringResource
import com.pulsestream.app.MainActivity
import com.pulsestream.app.R
import com.pulsestream.app.ui.tv.rememberIsTv
import com.pulsestream.app.ui.tv.tvFocusRing
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.VideoSize
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.pulsestream.app.data.ASPECT_MODES
import com.pulsestream.app.data.IntroDbApi
import com.pulsestream.app.data.PlaybackHeaders
import com.pulsestream.app.data.PlaybackRequest
import com.pulsestream.app.util.ExternalPlayer
import com.pulsestream.app.data.TvChannelRef
import com.pulsestream.app.data.ResolvedSubtitle
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import com.pulsestream.app.data.SkipSegment
import com.pulsestream.app.data.StreamItem
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.data.Subtitle
import com.pulsestream.app.data.resolveSubtitleFiles
import com.pulsestream.app.data.shiftSubtitles
import com.pulsestream.app.data.formatOffsetMs
import com.pulsestream.app.data.Video
import com.pulsestream.app.data.subtitleLangMatches
import com.pulsestream.app.data.simkl.SimklManager
import com.pulsestream.app.data.scrobble.ScrobbleManager
import com.pulsestream.app.data.trakt.TraktManager
import com.pulsestream.app.data.buttonLabel
import com.pulsestream.app.data.streamQualityLabel
import com.pulsestream.app.ui.components.AddonTabs
import com.pulsestream.app.ui.components.ErrorState
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.components.DotsLoadingIndicator
import com.pulsestream.app.ui.components.SafePosterImage
import com.pulsestream.app.ui.components.StreamCard
import com.pulsestream.app.ui.components.LocalBadgeStyleKey
import com.pulsestream.app.ui.components.StreamList
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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

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

    // PiP (v0.9.0+): track whether the video floats so overlay UI can hide,
    // and mark the player active so MainActivity.onUserLeaveHint auto-enters
    // PiP when Home/recents is pressed mid-playback.
    val inPip by MainActivity.pipMode.collectAsState()
    DisposableEffect(Unit) {
        MainActivity.isPlayerActive = true
        onDispose { MainActivity.isPlayerActive = false }
    }

    // (Back handling is registered below, after the UI state it depends on.)

    if (initialRequest == null) {
        GlowBackground { ErrorState("Nothing to play.", onRetry = onBack) }
        return
    }

    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    val ioScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    val isTv = rememberIsTv()
    /**
     * Set in onDispose. Background work (zap, episode load, source switch)
     * checks this before building a new ExoPlayer so no player is ever
     * created on disposed state (#9).
     */
    val disposed = remember { AtomicBoolean(false) }
    /** Non-null while the current stream is in a failed state (#4). */
    var playerError by remember { mutableStateOf<String?>(null) }
    /** Auto-failover: counts consecutive auto-advance attempts (max 3). */
    var autoFailoverCount by remember { mutableStateOf(0) }
    /** Nuvio parity: self-heal — probe Content-Type and retry once on format errors. */
    var mimeHealAttempted by remember { mutableStateOf(false) }
    /**
     * Set after player state exists; invoked by errorListener for the
     * self-heal path. Indirection avoids forward-reference issues.
     */
    var healOnFormatError: ((PlaybackException) -> Unit)? by remember { mutableStateOf(null) }
    /**
     * Set after the local helpers below are declared; invoked by errorListener
     * for the auto-failover path. Indirection avoids forward-reference issues
     * (same pattern as healOnFormatError).
     */
    var failoverToNextSource: ((attempt: Int) -> Unit)? by remember { mutableStateOf(null) }
    /**
     * Registered on every player instance we build (#4). ExoPlayer invokes
     * listeners on the player's application thread (main here), so touching
     * Compose state directly is safe.
     */
    val errorListener = remember {
        object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (disposed.get()) return
                val cause = generateSequence<Throwable>(error) { it.cause }.firstOrNull {
                    it::class.java.simpleName == "UnrecognizedInputFormatException"
                }
                if (cause != null && !mimeHealAttempted) {
                    mimeHealAttempted = true
                    healOnFormatError?.invoke(error)
                    return
                }
                // Auto-failover: on source error, automatically try the next stream
                // (across all addons) up to 3 times before showing the error UI.
                // Also covers decoder errors (e.g. 4K on phones that can't decode it).
                val isSourceError = error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
                    error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
                    error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED
                if (isSourceError && autoFailoverCount < 3) {
                    autoFailoverCount++
                    failoverToNextSource?.invoke(autoFailoverCount)
                    return
                }
                autoFailoverCount = 0
                playerError = error.message?.takeIf { it.isNotBlank() }
                    ?: "Playback failed (code ${error.errorCode})"
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // Keeps the PiP window's play/pause RemoteAction icon in sync.
                (activity as? MainActivity)?.updatePipActions(isPlaying)
                // Reset auto-failover counter on successful playback
                if (isPlaying) autoFailoverCount = 0
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                // Keeps the PiP aspect ratio current (portrait videos float
                // portrait like YouTube) for Home-press auto-PiP.
                if (videoSize.width > 0 && videoSize.height > 0) {
                    MainActivity.pipVideoW = videoSize.width
                    MainActivity.pipVideoH = videoSize.height
                }
            }
        }
    }

    /**
     * Builds subtitle configurations from already-resolved (downloaded +
     * format-sniffed) subtitle files, with the same default-selection rules
     * as [attachResolvedSubtitles].
     */
    fun subtitleConfigs(
        resolved: List<ResolvedSubtitle>,
        defaultUrl: String? = null
    ): List<MediaItem.SubtitleConfiguration> {
        if (resolved.isEmpty()) return emptyList()
        val prefs = settings.subtitleLanguages
        val defaultIndex = when {
            defaultUrl != null ->
                resolved.indexOfFirst { it.sub.url == defaultUrl }.takeIf { it >= 0 } ?: -1
            prefs.isEmpty() -> 0
            else -> resolved.indexOfFirst { r ->
                prefs.any { pref -> subtitleLangMatches(r.sub.lang, pref) }
            }
        }
        return resolved.mapIndexed { index, r ->
            MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(r.file))
                .setMimeType(r.mimeType)
                .setLanguage(r.sub.lang)
                .setId(r.sub.url)
                .apply { if (index == defaultIndex) setSelectionFlags(C.SELECTION_FLAG_DEFAULT) }
                .build()
        }
    }

    /**
     * Builds the player for a request. Already-resolved side-loaded subtitles
     * can be passed in so the player carries them from its first prepare —
     * attaching them later would force a setMediaItem+prepare() and a visible
     * rebuffer (#29). Their URLs rarely carry a usable file extension, so
     * still-unresolved subtitle lists are resolved in the background and
     * attached via [attachResolvedSubtitles].
     */
    /**
     * Builds an OkHttp-backed data source factory for stream playback with
     * Nuvio-parity networking: trust-all TLS (scoped to this client only),
     * 15s timeouts, redirect following. The trust-all socket factory lives
     * on this OkHttpClient instance — the addon API client and updater are
     * untouched.
     */
    fun buildPlaybackDataSourceFactory(
        headers: Map<String, String>
    ): DefaultHttpDataSource.Factory {
        ensurePlaybackTlsInstalled()
        return DefaultHttpDataSource.Factory().apply {
            setDefaultRequestProperties(headers)
            setConnectTimeoutMs(15_000)
            setReadTimeoutMs(15_000)
            setAllowCrossProtocolRedirects(true)
        }
    }

    fun buildExo(
        req: PlaybackRequest,
        subs: List<ResolvedSubtitle> = emptyList(),
        forcedMime: String? = null
    ): ExoPlayer {
        val prefs = settings.subtitleLanguages
        val audioPref = settings.preferredAudioLanguage
        // Nuvio parity: forward the addon's proxyHeaders verbatim (dropping
        // only blanks + Range). The addon-supplied User-Agent is honored —
        // upstreams that ship one expect it, and a missing UA is itself a
        // bot-filter trigger. Falls back to a Chrome UA when none supplied.
        val playbackHeaders = PlaybackHeaders.buildPlaybackHeaders(req.headers, isTv)
        // Nuvio parity: OkHttp-backed data source with trust-all TLS scoped
        // to stream playback only (some proxied upstreams serve certs
        // Android rejects). Timeouts 15s, cross-protocol redirects allowed.
        val dataSourceFactory = buildPlaybackDataSourceFactory(playbackHeaders)
        // Nuvio parity: infer HLS/DASH from the FULL URL (path + query) so
        // proxied URLs like /api/manifest?url=<encoded ...m3u8> play as HLS
        // instead of being misidentified as progressive.
        val inferredMime = forcedMime ?: PlaybackHeaders.inferMimeType(req.streamUrl)
        // Memory-conscious buffering: on low-RAM devices (e.g. 48MB-heap TVs)
        // the default ~50s HLS buffer exhausts the heap mid-playback and OOMs.
        // The user's buffer settings (Settings > Playback > Buffer & Cache)
        // apply here, capped to the safe low-memory window on such devices.
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val lowMemory = am.isLowRamDevice || am.memoryClass <= 64
        val minBufferMs = if (lowMemory) minOf(settings.bufferMinMs, 15_000) else settings.bufferMinMs
        val maxBufferMs = if (lowMemory) minOf(settings.bufferMaxMs, 30_000) else settings.bufferMaxMs
        val forPlaybackMs = if (lowMemory) minOf(settings.bufferForPlaybackMs, 2_500) else settings.bufferForPlaybackMs
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ minBufferMs,
                /* maxBufferMs = */ maxBufferMs,
                /* bufferForPlaybackMs = */ forPlaybackMs,
                /* bufferForPlaybackAfterRebufferMs = */ (forPlaybackMs * 2).coerceAtMost(30_000)
            )
            .build()
        // Optional on-disk playback cache (Settings > Playback > Buffer &
        // Cache): caches media so re-watches don't re-download. 0 = off.
        val cacheMb = settings.diskCacheMb
        val mediaSourceFactory = if (cacheMb > 0) {
            val upstreamFactory = dataSourceFactory
            val cacheDataSourceFactory = CacheDataSource.Factory()
                .setCache(PlaybackCache.get(context, cacheMb * 1024L * 1024L))
                .setUpstreamDataSourceFactory(upstreamFactory)
            DefaultMediaSourceFactory(cacheDataSourceFactory)
        } else {
            DefaultMediaSourceFactory(dataSourceFactory)
        }
        return ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setRenderersFactory(
                DefaultRenderersFactory(context).setExtensionRendererMode(
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER))
            .setMediaSourceFactory(mediaSourceFactory)
            // Belt and suspenders: also tell the track selector the preferred
            // subtitle languages, so his languages win even if the default
            // flag is ever ignored.
            .setTrackSelector(
                DefaultTrackSelector(context).apply {
                    val params = buildUponParameters()
                    if (prefs.isNotEmpty()) {
                        params.setPreferredTextLanguages(*prefs.toTypedArray())
                    }
                    // Preferred audio language (Settings > Playback): when the
                    // title's audio tracks include it, ExoPlayer picks it
                    // automatically at playback start.
                    if (!audioPref.isNullOrBlank()) {
                        params.setPreferredAudioLanguages(audioPref)
                    }
                    setParameters(params)
                }
            )
            .build()
            .apply {
                setMediaItem(
                    MediaItem.Builder()
                        .setUri(req.streamUrl)
                        .apply { inferredMime?.let { setMimeType(it) } }
                        .setSubtitleConfigurations(subtitleConfigs(subs))
                        .build()
                )
            }
    }


    /**
     * Applies the user's subtitle styling (Settings > Playback) to the
     * PlayerView's caption view: text color, size, background/shade and
     * edge style. Called when the view is created and on every update.
     *
     * SYSTEM-CAPTIONS HOOK (fix #20): when the user picked "System
     * default", skip setStyle() entirely so Android's
     * Settings > Accessibility > Captions preferences apply.
     */
    fun applySubtitleStyle(pv: PlayerView) {
        if (settings.useSystemCaptions) return
        val sv = pv.subtitleView ?: return
        val edge = when (settings.subtitleEdgeType) {
            0 -> CaptionStyleCompat.EDGE_TYPE_NONE
            1 -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
            3 -> CaptionStyleCompat.EDGE_TYPE_RAISED
            4 -> CaptionStyleCompat.EDGE_TYPE_DEPRESSED
            else -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
        }
        sv.setStyle(
            CaptionStyleCompat(
                settings.subtitleTextColor,
                settings.subtitleBackgroundColor,
                android.graphics.Color.TRANSPARENT,
                edge,
                android.graphics.Color.BLACK,
                null
            )
        )
        sv.setFractionalTextSize(
            SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * settings.subtitleTextSizeScale
        )
    }


    var currentRequest by remember { mutableStateOf(initialRequest) }
    var player by remember {
        mutableStateOf(buildExo(initialRequest).also { it.addListener(errorListener) })
    }
    val latestPlayer = rememberUpdatedState(player)
    val latestRequest = rememberUpdatedState(currentRequest)
    var playerView by remember { mutableStateOf<PlayerView?>(null) }

    // PiP RemoteAction: the floating window's play/pause button toggles the
    // player here. The player listener's onIsPlayingChanged then refreshes
    // the RemoteAction icon via MainActivity.updatePipActions.
    // (Placed after `player` is declared — composable locals are positional.)
    LaunchedEffect(Unit) {
        MainActivity.pipToggleRequest.collect {
            player.playWhenReady = !player.playWhenReady
        }
    }
    /**
     * Side-loaded subtitles, resolved (downloaded + format-sniffed) in the
     * background for the current request. Kept in state so a rebuilt player
     * (source switch) can carry them from its first prepare instead of
     * re-preparing the video to attach them (#29).
     */
    var resolvedSubs by remember { mutableStateOf(emptyList<ResolvedSubtitle>()) }
    /**
     * Raw (unshifted) downloaded subtitles — the source of truth for timing
     * shifts. The in-player +/- timing buttons re-shift from these files,
     * never from already-shifted output (which would compound the offset).
     */
    var rawResolvedSubs by remember { mutableStateOf(emptyList<ResolvedSubtitle>()) }
    /**
     * In-memory per-title subtitle timing override (ms), added to the global
     * [SettingsStore.subtitleOffsetMs]. Resets when the title changes — only
     * the global setting persists across titles.
     */
    var subOffsetOverrideMs by remember { mutableLongStateOf(0L) }
    /**
     * Effective subtitle timing offset for the current title: global default
     * plus the in-player per-title adjustment, clamped to ±10s.
     */
    fun effectiveSubOffsetMs(): Long =
        (settings.subtitleOffsetMs + subOffsetOverrideMs).coerceIn(-10_000L, 10_000L)
    /**
     * Re-applies the effective timing offset to the raw subtitles and
     * publishes the shifted list (which re-attaches to the player via the
     * existing resolvedSubs effect). Runs on IO; no-op when there is nothing
     * to shift.
     */
    fun applySubtitleTiming() {
        val raw = rawResolvedSubs
        if (raw.isEmpty()) return
        val offset = effectiveSubOffsetMs()
        ioScope.launch {
            val shifted = shiftSubtitles(raw, offset, context.cacheDir)
            if (!disposed.get()) resolvedSubs = shifted
        }
    }
    // Self-heal handler: probe Content-Type, rebuild once with probed MIME.
    healOnFormatError = { error ->
        ioScope.launch {
            val probed = probePlaybackContentType(
                latestRequest.value.streamUrl,
                PlaybackHeaders.buildPlaybackHeaders(latestRequest.value.headers, isTv)
            )
            if (probed != null && !disposed.get()) {
                val req = latestRequest.value
                val old = latestPlayer.value
                val fresh = buildExo(req, resolvedSubs, forcedMime = probed)
                    .also { it.addListener(errorListener) }
                old.release()
                player = fresh
                playerError = null
                fresh.prepare()
                fresh.play()
            } else if (!disposed.get()) {
                playerError = error.message?.takeIf { it.isNotBlank() }
                    ?: "Playback failed (code ${error.errorCode})"
            }
        }
    }
    /**
     * Which subtitle set is already attached to which player instance.
     * Attaching the same set twice would re-prepare the video for nothing.
     */
    var subsAttached by remember { mutableStateOf<Pair<ExoPlayer, String>?>(null) }

    // Unified scrobbling: one pipeline fans playback events out to every
    // linked backend (Trakt, Simkl, …). Each service is enabled/disabled
    // independently via its own login state; auth flows are untouched.
    val simkl = remember { SimklManager(settings) }
    val trakt = remember { TraktManager(settings) }
    val scrobbler = remember { ScrobbleManager(listOf(simkl, trakt), ioScope) }

    var showControls by remember { mutableStateOf(true) }
    var controlsTick by remember { mutableIntStateOf(0) }
    // PiP: drop the transport UI the moment the window floats (the system
    // draws its own PiP controls); restore the default on return.
    LaunchedEffect(inPip) {
        if (inPip) showControls = false
    }
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
     *
     * Skips silently when this exact set is already attached to this player
     * instance — re-attaching would call setMediaItem+prepare() again and
     * visibly rebuffer the video (#29). A fresh player built carrying the
     * same subtitles (source switch) never reaches the re-prepare path below.
     */
    fun attachResolvedSubtitles(resolved: List<ResolvedSubtitle>, defaultUrl: String? = null) {
        if (resolved.isEmpty()) return
        val key = subtitleIdentity(resolved)
        val attached = subsAttached
        if (attached != null && attached.first === player && attached.second == key) return
        val pos = player.currentPosition
        val wasPlaying = player.isPlaying
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(currentRequest.streamUrl)
                .setSubtitleConfigurations(subtitleConfigs(resolved, defaultUrl))
                .build(),
            pos
        )
        player.prepare()
        player.playWhenReady = wasPlaying
        subsAttached = player to key
        trackTick++
    }

    // Sources + episodes sheets
    var sourcesOpen by remember { mutableStateOf(false) }
    var episodesOpen by remember { mutableStateOf(false) }
    var downloadStream by remember { mutableStateOf<com.pulsestream.app.data.StreamItem?>(null) }
    var zapping by remember { mutableStateOf(false) }
    var availableStreams by remember { mutableStateOf(initialRequest.streams) }
    /** Count of streams hidden because the device can't decode them (e.g. 4K on budget phones). */
    var hiddenIncompatibleCount by remember { mutableStateOf(0) }
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

    // Back hierarchy, in one handler so the order is explicit: sheets first,
    // then hide visible controls, and only then leave the player (#27).
    // (ModalBottomSheets register their own later-composed back handling, so
    // they still dismiss themselves before this runs.)
    BackHandler {
        when {
            tracksOpen || subSearchOpen || sourcesOpen || episodesOpen -> {
                tracksOpen = false
                subSearchOpen = false
                sourcesOpen = false
                episodesOpen = false
            }
            showControls && !locked -> showControls = false
            else -> onBack()
        }
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
        val c = player
        val dur = c.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        c.seekTo((c.currentPosition + deltaMs).coerceIn(0L, dur))
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
        val c = player
        if (c.isPlaying) c.pause() else c.play()
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
    // then attach. Files are cached so this is instant on repeat visits. The
    // resolved list goes into state; a separate effect attaches it, so a
    // source switch can carry the subtitles into the fresh player instead of
    // re-preparing the video (#29).
    LaunchedEffect(currentRequest, addedSubs) {
        val reqAtStart = currentRequest
        val all = reqAtStart.subtitles + addedSubs
        subLoadError = false
        resolvedSubs = emptyList()
        rawResolvedSubs = emptyList()
        subOffsetOverrideMs = 0L
        if (all.isEmpty() || disposed.get()) return@LaunchedEffect
        val resolved = resolveSubtitleFiles(all, context.cacheDir)
        // Superseded while resolving (source/episode/zap switch) — the newer
        // effect run owns the result.
        if (currentRequest != reqAtStart || disposed.get()) return@LaunchedEffect
        subLoadError = resolved.isEmpty()
        // Store the raw files, then publish the timing-shifted list. The
        // separate attach effect picks up resolvedSubs.
        rawResolvedSubs = resolved
        resolvedSubs = shiftSubtitles(resolved, effectiveSubOffsetMs(), context.cacheDir)
    }

    // Attach resolved subtitles to the player. Skips silently when the fresh
    // player was already built carrying them (source switch) — no re-prepare.
    LaunchedEffect(player, resolvedSubs) {
        if (resolvedSubs.isEmpty() || disposed.get()) return@LaunchedEffect
        val wantDefault = pendingDefaultSubUrl
        pendingDefaultSubUrl = null
        attachResolvedSubtitles(resolvedSubs, wantDefault)
    }

    /**
     * Serializes player rebuilds (#5): rapid source switches/zaps used to
     * orphan the first fresh player (still playWhenReady → double audio) and
     * could complete out of order. Only the latest request wins; a superseded
     * request releases nothing — the winner owns the old player.
     */
    val replaceMutex = remember { Mutex() }
    val replaceGen = remember { AtomicInteger(0) }

    /** Rebuilds the ExoPlayer around a new stream, preserving position. */
    fun replaceStream(newReq: PlaybackRequest, startAtMs: Long, autoplay: Boolean) {
        if (disposed.get()) return
        val gen = replaceGen.incrementAndGet()
        val old = player
        val oldReq = currentRequest
        // Read the old player's position on THIS thread (the player's own
        // thread, normally main). Touching it from a background thread crashes.
        val posMs = old.currentPosition
        val durMs = old.duration.takeIf { it > 0 } ?: 0L
        ioScope.launch {
            replaceMutex.withLock {
                if (gen != replaceGen.get() || disposed.get()) return@withLock
                // #8: live TV never enters Continue Watching.
                if (oldReq.type != "tv") {
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
                }
                // #17: positions are keyed by URL — the old source's entry is
                // kept, not deleted, so its progress survives a switch-back.
                // Everything below touches the player or Compose state, so it must
                // run on the main thread no matter which thread called us.
                withContext(Dispatchers.Main) {
                    if (gen != replaceGen.get() || disposed.get()) return@withContext
                    playerError = null
                    mimeHealAttempted = false
                    currentRequest = newReq
                    repository.pendingPlayback = newReq
                    resumeOfferMs = null
                    // #29: carry already-resolved subtitles (same title) into
                    // the fresh player so the attach effect doesn't re-prepare
                    // the video just to add them back.
                    val carrySubs =
                        newReq.subtitles == oldReq.subtitles && resolvedSubs.isNotEmpty()
                    val carried = if (carrySubs) resolvedSubs else emptyList()
                    val fresh = buildExo(newReq, carried).also { it.addListener(errorListener) }
                    // #16: a fresh player defaults to 1x — re-apply the pill.
                    fresh.setPlaybackSpeed(speed)
                    subsAttached =
                        if (carried.isNotEmpty()) fresh to subtitleIdentity(carried) else null
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

    /**
     * Live TV channel zapping. The Live tab hands the visible channel list
     * over via [StreamRepository.setTvZapList]; zap targets wrap around.
     * M3U channels play directly (the URL is the stream); addon channels
     * re-resolve their stream first.
     */
    val zapChannels: List<TvChannelRef> = repository.pendingTvChannels

    fun canZap(): Boolean =
        currentRequest.type == "tv" && zapChannels.size > 1

    fun zapTo(index: Int) {
        // #9: never start zap work on disposed state (ghost audio).
        if (disposed.get()) return
        val list = zapChannels
        if (list.size < 2) return
        val target = list[index.mod(list.size)]
        // Same channel tapped twice — nothing to do. M3U ids are URLs and
        // videoId holds the URL too, so this covers addon AND M3U channels:
        // double-tap CH+ must not rebuild the player (#19).
        if (target.metaId == currentRequest.videoId) return
        zapping = true
        ioScope.launch {
            try {
                val zapTarget: Pair<String, Map<String, String>> = if (target.addonId == "m3u") {
                    Pair(target.metaId, emptyMap())
                } else {
                    val result = withTimeoutOrNull(20_000) {
                        repository.streamResultsFlow("tv", target.metaId)
                            .firstOrNull { it.streams.isNotEmpty() }
                    }
                    val stream = result?.streams?.firstOrNull()
                    if (stream == null) {
                        withContext(Dispatchers.Main) {
                            pokeIndicator(PlayerIndicator.Message("No stream for ${target.name}"))
                        }
                        return@launch
                    }
                    Pair(stream.url, stream.headers)
                }
                val url = zapTarget.first
                val headers = zapTarget.second
                repository.pendingTvIndex = list.indexOf(target)
                withContext(Dispatchers.Main) {
                    pokeIndicator(PlayerIndicator.Message(target.name))
                    replaceStream(
                        currentRequest.copy(
                            streamUrl = url,
                            headers = headers,
                            title = target.name,
                            poster = target.poster,
                            videoId = target.metaId,
                            streamLabel = "LIVE",
                            sourceName = null,
                            streams = emptyList()
                        ),
                        startAtMs = 0L,
                        autoplay = true
                    )
                }
            } finally {
                withContext(Dispatchers.Main) { zapping = false }
            }
        }
    }

    fun zapDelta(delta: Int) {
        val list = zapChannels
        if (list.size < 2) return
        zapTo(repository.pendingTvIndex + delta)
    }

    /** Quietly (re)fills the Sources list in the background, progressively. */
    fun refreshStreamsBackground(type: String, videoId: String) {
        streamsJob?.cancel()
        streamsJob = ioScope.launch {
            streamsLoading = true
            try {
                repository.streamResultsFlow(type, videoId).collect { result ->
                    if (result.streams.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            val (playable, hidden) = ( result.streams to 0 )
                            availableStreams = playable
                            hiddenIncompatibleCount = hidden
                        }
                    }
                }
            } finally {
                // NonCancellable: a replaced job must still clear the spinner.
                withContext(Dispatchers.Main + NonCancellable) { streamsLoading = false }
            }
        }
    }

    /** Re-prepares the current stream after a fatal error (#4). */
    fun retryCurrentStream() {
        if (disposed.get()) return
        playerError = null
        replaceStream(
            currentRequest,
            startAtMs = player.currentPosition.coerceAtLeast(0L),
            autoplay = true
        )
    }

    /**
     * Advances to the next available stream for this title (#4). When there is
     * nothing to advance to, says so and quietly refreshes the Sources list.
     */
    fun nextSource() {
        val streams = availableStreams
        val cur = streams.indexOfFirst { it.url == currentRequest.streamUrl }
        val next = if (streams.size < 2) null else streams[(cur + 1).mod(streams.size)]
        if (next == null) {
            pokeIndicator(PlayerIndicator.Message("No other sources available"))
            refreshStreamsBackground(currentRequest.type, currentRequest.videoId)
            return
        }
        switchSource(next)
    }

    // Wire the auto-failover path now that the helpers exist.
    failoverToNextSource = { attempt ->
        pokeIndicator(PlayerIndicator.Message("Trying next source… ($attempt/3)"))
        nextSource()
    }

    fun playEpisode(video: Video) {
        // #9: never start episode work on disposed state (ghost audio).
        if (disposed.get()) return
        episodesOpen = false
        switchingMedia = true
        ioScope.launch {
            val type = currentRequest.type
            val result = withTimeoutOrNull(20_000) {
                repository.streamResultsFlow(type, video.id).first { it.streams.isNotEmpty() }
            }
            // Back to the main thread: everything below touches Compose state.
            withContext(Dispatchers.Main) {
                val (playableStreams, hidden) = result?.streams?.let { ( it to 0 ) } ?: (emptyList<com.pulsestream.app.data.StreamItem>() to 0)
                val stream = playableStreams.firstOrNull()
                if (stream == null) {
                    pokeIndicator(PlayerIndicator.Message("No sources found for this episode"))
                } else {
                    val showName = currentRequest.seriesName ?: currentRequest.title
                    availableStreams = playableStreams
                    hiddenIncompatibleCount = hidden
                    replaceStream(
                        currentRequest.copy(
                            streamUrl = stream.url,
                            headers = stream.headers,
                            title = "$showName S${video.season}E${video.episode}",
                            poster = video.thumbnail ?: currentRequest.poster,
                            videoId = video.id,
                            subtitles = result?.subtitles ?: emptyList(),
                            season = video.season,
                            episode = video.episode,
                            episodeTitle = video.title,
                            streamLabel = streamQualityLabel(stream),
                            sourceName = stream.sourceName,
                            streams = result?.streams ?: emptyList()
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
        val pl: Player = latestPlayer.value
        val req = latestRequest.value
        // #8: live TV never enters Continue Watching.
        if (req.type == "tv") return
        // Trailers never enter Continue Watching either.
        if (req.isTrailer) return
        val pos = pl.currentPosition
        val dur = pl.duration.takeIf { it > 0 } ?: C.TIME_UNSET
        // NonCancellable: onDispose cancels ioScope's children (#9) and must
        // not kill this final save.
        ioScope.launch(NonCancellable) {
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
    var endedHandledFor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(player) {
        while (true) {
            val c = player
            positionMs = c.currentPosition
            durationMs = c.duration.takeIf { it > 0 } ?: 0L
            isPlaying = c.isPlaying
            isBuffering = c.playbackState == Player.STATE_BUFFERING ||
                c.playbackState == Player.STATE_IDLE
            volumePercent = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) *
                100 / maxVolume.coerceAtLeast(1)
            activeSegment = if (settings.showSkipButton) {
                skipSegments.firstOrNull { positionMs >= it.startMs && positionMs <= it.endMs }
            } else null
            // Auto-play next episode: when a series episode ends and the
            // setting is on, advance to the next episode automatically.
            if (c.playbackState == Player.STATE_ENDED &&
                settings.autoPlayNext &&
                !currentRequest.isMovie &&
                !currentRequest.isTrailer &&
                currentRequest.episodes.isNotEmpty() &&
                endedHandledFor != currentRequest.videoId
            ) {
                endedHandledFor = currentRequest.videoId
                val eps = currentRequest.episodes.sortedWith(
                    compareBy({ it.season }, { it.episode })
                )
                // v0.9.0: shuffle mode picks a random episode instead of the
                // sequential next one (never the just-finished episode itself).
                val next = if (settings.shuffleEpisodes) {
                    eps.filter { it.id != currentRequest.videoId }.randomOrNull()
                        ?: eps.randomOrNull()
                } else {
                    val idx = eps.indexOfFirst { it.id == currentRequest.videoId }
                    if (idx >= 0) eps.getOrNull(idx + 1) else null
                }
                if (next != null) {
                    pokeIndicator(PlayerIndicator.Message("Playing next episode…"))
                    playEpisode(next)
                }
            }
            if (!currentRequest.isTrailer) {
                scrobbler.tick(
                    currentRequest,
                    positionMs,
                    durationMs,
                    isPlaying,
                    c.playbackState == Player.STATE_ENDED
                )
            }
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
        var req: PlaybackRequest = initialRequest
        var offerResume = saved > 0
        if (saved > 0 && req.streams.isEmpty() && req.type != "tv" && !disposed.get()) {
            // #15: Continue Watching resume — re-resolve the title's streams
            // first so signed/expiring URLs are fresh, and resume at the saved
            // position on the fresh URL. Falls back to the stored URL when
            // re-resolution fails.
            val fresh = withTimeoutOrNull(20_000) {
                repository.streamResultsFlow(req.type, req.videoId)
                    .firstOrNull { it.streams.isNotEmpty() }
            }
            val (playableFresh, hiddenFresh) = fresh?.streams?.let { ( it to 0 ) } ?: (emptyList<com.pulsestream.app.data.StreamItem>() to 0)
            val stream = playableFresh.firstOrNull()
            if (fresh != null && stream != null && !disposed.get()) {
                availableStreams = playableFresh
                hiddenIncompatibleCount = hiddenFresh
                req = req.copy(
                    streamUrl = stream.url,
                    headers = stream.headers,
                    streamLabel = streamQualityLabel(stream),
                    sourceName = stream.sourceName,
                    subtitles = fresh.subtitles,
                    streams = fresh.streams
                )
                repository.pendingPlayback = req
                // Fresh URL: go straight in at the saved position, no prompt.
                offerResume = false
            } else {
                // Stored URL stays; quietly fill Sources for a manual re-pick.
                refreshStreamsBackground(req.type, req.videoId)
            }
        }
        if (disposed.get()) return@LaunchedEffect
        if (req !== initialRequest) {
            // Rebuild the player around the refreshed request so the first
            // prepare already uses the fresh URL (#15; guarded by #9 above).
            val oldPlayer = player
            playerView?.player = null
            oldPlayer.release()
            val rebuilt = buildExo(req).also { it.addListener(errorListener) }
            rebuilt.setPlaybackSpeed(speed)
            player = rebuilt
            mimeHealAttempted = false
            currentRequest = req
        }
        val p = player
        p.seekTo(saved)
        p.prepare()
        if (offerResume) {
            p.playWhenReady = false
            resumeOfferMs = saved
            showControls = true
        } else if (settings.autoplay) {
            p.playWhenReady = true
        } else {
            p.playWhenReady = false
            showControls = true
        }
        // Sources sheet fallback: if we arrived without a stream list
        // (e.g. from Continue Watching), fill it quietly in the background.
        if (availableStreams.isEmpty()) {
            refreshStreamsBackground(req.type, req.videoId)
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
            if (event == Lifecycle.Event.ON_PAUSE) {
                savePosition()
                // PiP: the activity pauses when the video floats, but playback
                // must continue in the PiP window — only pause when the app is
                // truly backgrounded (not floating).
                if (activity?.isInPictureInPictureMode == true) return@LifecycleEventObserver
                // #10: pause on background — release stays in onDispose.
                latestPlayer.value.pause()
            }
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
            // #9: from here on, no late work may build a player (ghost audio).
            disposed.set(true)
            savePosition()
            scrobbler.shutdown()
            // #9: kill late zap/episode/source-switch work. The scope itself
            // stays valid (the scrobblers above hold it); only its children
            // are cancelled. savePosition()'s save runs NonCancellable so it
            // survives this; the scrobblers' final stop-events are best-effort.
            ioScope.coroutineContext.cancelChildren()
            latestPlayer.value.release()
            // #18: swipe-brightness must not leak into the rest of the app.
            window?.let { w ->
                val lp = w.attributes
                lp.screenBrightness = -1f
                w.attributes = lp
            }
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

    // ---- Android TV D-pad: works when the touch gestures can't. ----
    val playPauseFocus = remember { FocusRequester() }
    // TV: the player container itself is focusable and holds D-pad focus
    // whenever the transport controls are hidden, so the remote never goes
    // dead (NuvioTV parity: containerFocusRequester pattern).
    val containerFocus = remember { FocusRequester() }
    // TV: land D-pad focus on the error overlay's Retry button when it shows.
    val errorRetryFocus = remember { FocusRequester() }
    // Tracks whether any transport button currently holds focus. The D-pad
    // key handler uses this: if controls are visible but nothing grabbed
    // focus (e.g. requestFocus failed), Center/Up/Down re-request focus on
    // the play/pause button instead of being swallowed with no effect.
    var transportFocused by remember { mutableStateOf(false) }
    /** True while the TV seekbar holds D-pad focus (arrows then seek). */
    var seekbarFocused by remember { mutableStateOf(false) }
    LaunchedEffect(playerError, isTv) {
        if (isTv && playerError != null) {
            delay(300)
            runCatching { errorRetryFocus.requestFocus() }
        }
    }
    LaunchedEffect(showControls, isTv, locked) {
        if (!isTv) return@LaunchedEffect
        if (showControls && !locked) {
            delay(350) // let the controls fade in first
            runCatching { playPauseFocus.requestFocus() }
        } else if (!showControls && playerError == null) {
            // Controls hidden: park focus on the container so D-pad events
            // always have a target (container's onPreviewKeyEvent toggles
            // controls back on). Without this the remote goes dead.
            runCatching { containerFocus.requestFocus() }
        }
    }
    // TV: initial focus on the container so the remote works immediately,
    // before the user ever opens the controls.
    LaunchedEffect(isTv) {
        if (isTv && playerError == null) {
            delay(300)
            runCatching { containerFocus.requestFocus() }
        }
    }
    val tvKeys = if (isTv) {
        // A settings sheet/dialog is up: the D-pad must drive the dialog's
        // own focus ( Up/Down through rows, OK to pick ), not the player.
        // Media keys and the channel rocker keep working regardless.
        val dialogOpen = tracksOpen || subSearchOpen || sourcesOpen || episodesOpen ||
            speedMenuOpen || resumeOfferMs != null || playerError != null
        Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            // Remote channel rocker zaps while watching live TV.
            when (event.nativeKeyEvent.keyCode) {
                android.view.KeyEvent.KEYCODE_CHANNEL_UP -> {
                    if (canZap()) { zapDelta(1); true } else false
                }
                android.view.KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    if (canZap()) { zapDelta(-1); true } else false
                }
                else -> when (event.key) {
                // Media keys always act directly.
                Key.MediaPlayPause -> { togglePlay(); true }
                Key.MediaPlay -> { player.play(); true }
                Key.MediaPause -> { player.pause(); true }
                Key.MediaFastForward -> { seekByWithFeedback(10_000); true }
                Key.MediaRewind -> { seekByWithFeedback(-10_000); true }
                // D-pad Left/Right: with a dialog open, hand off to the
                // dialog. With the seekbar focused, its own handler seeks.
                // With controls visible, let 2D focus search move between
                // buttons (this is what makes every control reachable).
                // With controls hidden, arrows seek directly.
                Key.DirectionLeft -> when {
                    dialogOpen || seekbarFocused || showControls -> false
                    else -> { seekByWithFeedback(-10_000); true }
                }
                Key.DirectionRight -> when {
                    dialogOpen || seekbarFocused || showControls -> false
                    else -> { seekByWithFeedback(10_000); true }
                }
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter ->
                    if (dialogOpen) false
                    else if (showControls && !transportFocused) {
                        runCatching { playPauseFocus.requestFocus() }; true
                    } else if (showControls) false else { toggleControls(); true }
                // D-pad Up/Down: dialogs handle their own; visible controls
                // get 2D focus navigation between rows; hidden controls are
                // summoned (Down brings them up per the TV spec).
                Key.DirectionUp, Key.DirectionDown ->
                    if (dialogOpen) false
                    else if (showControls && !transportFocused) {
                        runCatching { playPauseFocus.requestFocus() }; true
                    } else if (showControls) false else { toggleControls(); true }
                else -> false
                }
            }
        }
    } else Modifier

    // TV: the root container is focusable and holds the containerFocus
    // requester, so D-pad key events always reach onPreviewKeyEvent even
    // when no transport button has focus (NuvioTV parity).
    val tvContainerModifier = if (isTv) {
        Modifier
            .focusRequester(containerFocus)
            .focusable()
    } else Modifier
    Box(tvKeys.fillMaxSize().background(Color.Black).then(tvContainerModifier)) {
        // Video surface with the full gesture set (gestures off while floating —
        // taps on the PiP window belong to the system's PiP controls).
        Box(Modifier.fillMaxSize().then(if (inPip) Modifier else Modifier.videoGestures())) {
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
                        applySubtitleStyle(pv)
                        playerView = pv
                    }
                },
                update = { pv ->
                    pv.player = player
                    applySubtitleStyle(pv)
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // PiP (v0.9.0+): while the video floats, skip every overlay layer —
        // the system draws its own PiP controls. Only the PlayerView surface
        // stays composed, so playback continues uninterrupted.
        if (inPip) return@Box

        // Pulse-branded loading line while the stream connects / buffers.
        // Suppressed on a fatal error — the error overlay takes its place (#4).
        if ((isBuffering || switchingMedia) && playerError == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                DotsLoadingIndicator(if (switchingMedia) "Loading episode…" else "Finding source…")
            }
        }

        // Fatal stream error (#4): Retry re-prepares this stream, Next source
        // advances to the next available stream, and the floating Sources
        // switcher stays openable underneath via "Browse all sources".
        playerError?.let { err ->
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.78f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    Text(
                        "Couldn't play this source",
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        err,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { retryCurrentStream() },
                            modifier = Modifier
                                .focusRequester(errorRetryFocus)
                                .tvFocusRing(RoundedCornerShape(20.dp))
                        ) {
                            Text("Retry")
                        }
                        Button(
                            onClick = { nextSource() },
                            modifier = Modifier.tvFocusRing(RoundedCornerShape(20.dp))
                        ) {
                            Text("Next source")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { sourcesOpen = true }) {
                        Text(
                            "Browse all sources",
                            color = LocalPalette.current.accent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
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
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.tvFocusRing(RoundedCornerShape(50))
                        ) {
                            Icon(Icons.Filled.ArrowBack, "Back", tint = TextPrimary)
                        }
                    }
                }

                // ---- Center transport ----
                Row(
                    Modifier.align(Alignment.Center)
                        .onFocusChanged { transportFocused = it.hasFocus },
                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (canZap()) {
                        IconButton(
                            onClick = { zapDelta(-1) },
                            enabled = !zapping,
                            modifier = Modifier.tvFocusRing(RoundedCornerShape(50))
                        ) {
                            Icon(
                                Icons.Filled.SkipPrevious, "Previous channel",
                                tint = TextPrimary, modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = { seekByWithFeedback(-10_000) },
                        modifier = Modifier.tvFocusRing(RoundedCornerShape(50))
                    ) {
                        Icon(
                            Icons.Filled.Replay10, "Back 10 seconds",
                            tint = TextPrimary, modifier = Modifier.size(56.dp)
                        )
                    }
                    IconButton(
                        onClick = { togglePlay() },
                        modifier = Modifier
                            .focusRequester(playPauseFocus)
                            .tvFocusRing(RoundedCornerShape(50))
                    ) {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            if (isPlaying) "Pause" else "Play",
                            tint = TextPrimary, modifier = Modifier.size(76.dp)
                        )
                    }
                    IconButton(
                        onClick = { seekByWithFeedback(10_000) },
                        modifier = Modifier.tvFocusRing(RoundedCornerShape(50))
                    ) {
                        Icon(
                            Icons.Filled.Forward10, "Forward 10 seconds",
                            tint = TextPrimary, modifier = Modifier.size(56.dp)
                        )
                    }
                    if (canZap()) {
                        IconButton(
                            onClick = { zapDelta(1) },
                            enabled = !zapping,
                            modifier = Modifier.tvFocusRing(RoundedCornerShape(50))
                        ) {
                            Icon(
                                Icons.Filled.SkipNext, "Next channel",
                                tint = TextPrimary, modifier = Modifier.size(48.dp)
                            )
                        }
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
                        onSeek = { player.seekTo(it) },
                        isTv = isTv,
                        onSeekDelta = { seekByWithFeedback(it) },
                        onFocusChanged = { seekbarFocused = it }
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
                                // Lock controls (moved from top-right).
                                PillBarButton(
                                    icon = if (locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                                    label = if (locked) "Unlock" else "Lock",
                                    onClick = {
                                        locked = !locked
                                        if (locked) {
                                            showControls = false
                                            pokeIndicator(PlayerIndicator.Message("Controls locked"))
                                        } else {
                                            showControls = true
                                            controlsTick++
                                            pokeIndicator(PlayerIndicator.Message("Controls unlocked"))
                                        }
                                    }
                                )
                                // Open in external player (VLC/MX) — instant
                                // fallback if the built-in player isn't working.
                                PillBarButton(
                                    icon = Icons.Filled.OpenInNew,
                                    label = "External",
                                    onClick = {
                                        val ctx = activity ?: return@PillBarButton
                                        ExternalPlayer.play(ctx, req)
                                    }
                                )
                                // Speed control.
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
                                PillBarButton(
                                    icon = Icons.Filled.PictureInPicture,
                                    label = "PiP",
                                    onClick = {
                                        if (!settings.pipEnabled) return@PillBarButton
                                        val act = activity as? MainActivity ?: return@PillBarButton
                                        val vs = player.videoSize
                                        if (vs.width > 0 && vs.height > 0) {
                                            MainActivity.pipVideoW = vs.width
                                            MainActivity.pipVideoH = vs.height
                                        }
                                        act.enterPictureInPictureMode(
                                            MainActivity.buildPipParams(act, player.isPlaying)
                                        )
                                    }
                                )
                                SpeedMenuButton(
                                    speed = speed,
                                    menuOpen = speedMenuOpen,
                                    onOpenMenu = { speedMenuOpen = true },
                                    onDismissMenu = { speedMenuOpen = false },
                                    onSpeedSelected = { s ->
                                        speed = s
                                        player.setPlaybackSpeed(s)
                                        speedMenuOpen = false
                                        pokeIndicator(PlayerIndicator.Speed(speedLabel(s)))
                                    }
                                )
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
            ResumePromptDialog(
                savedMs = saved,
                title = headerTitle,
                onResume = {
                    // Tapping outside keeps their place and resumes.
                    resumeOfferMs = null
                    player.playWhenReady = true
                },
                onStartOver = {
                    resumeOfferMs = null
                    ioScope.launch { repository.deleteSavedPosition(currentRequest.streamUrl) }
                    player.seekTo(0)
                    player.playWhenReady = true
                }
            )
        }

        // Track switching: Subs button opens subtitles only, Audio button opens audio only.
        // Bottom sheet (not AlertDialog) so the root media/channel-key handler
        // keeps working while it's open (#28).
        if (tracksOpen) {
            val isAudio = tracksMode == "audio"
            // TV: land D-pad focus on the first row when the picker opens.
            val firstRowFocus = remember { FocusRequester() }
            LaunchedEffect(isTv, tracksMode) {
                if (isTv) {
                    delay(300)
                    runCatching { firstRowFocus.requestFocus() }
                }
            }
            ModalBottomSheet(
                onDismissRequest = { tracksOpen = false },
                sheetState = rememberModalBottomSheetState(),
                containerColor = Color(0xFF14141F)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)
                ) {
                    item {
                        Text(
                            if (isAudio) "Audio" else "Subtitles",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                        )
                    }
                    if (isAudio) {
                        item {
                            TrackRow(
                                label = "Auto",
                                selected = audioOpts.none { it.selected },
                                onClick = { applyAudioSelection(null) },
                                modifier = Modifier.focusRequester(firstRowFocus)
                            )
                        }
                        items(audioOpts) { opt ->
                            TrackRow(
                                label = opt.label,
                                selected = opt.selected,
                                onClick = { applyAudioSelection(opt) }
                            )
                        }
                    } else {
                        item {
                            TrackRow(
                                label = "Off",
                                selected = subtitlesOff,
                                onClick = { applySubtitleSelection(null) },
                                modifier = Modifier.focusRequester(firstRowFocus)
                            )
                        }
                        items(subOpts) { opt ->
                            TrackRow(
                                label = opt.label,
                                selected = opt.selected,
                                onClick = { applySubtitleSelection(opt) }
                            )
                        }
                        if (subLoadError && subOpts.isEmpty()) {
                            item {
                                Text(
                                    "Couldn't load subtitles — check your connection and try again.",
                                    color = Color(0xFFEF4444),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                        // Subtitle timing shift (v0.9.0+): nudges all subtitle
                        // timestamps for this title. Per-title only — the
                        // global default lives in Settings > Subtitles.
                        if (rawResolvedSubs.isNotEmpty()) {
                            item {
                                val offset = effectiveSubOffsetMs()
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                ) {
                                    Text(
                                        "Subtitle timing",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary,
                                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                subOffsetOverrideMs =
                                                    (subOffsetOverrideMs - 500L).coerceIn(-10_000L, 10_000L)
                                                applySubtitleTiming()
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("−500 ms")
                                        }
                                        Text(
                                            formatOffsetMs(offset),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = LocalPalette.current.accent,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )
                                        OutlinedButton(
                                            onClick = {
                                                subOffsetOverrideMs =
                                                    (subOffsetOverrideMs + 500L).coerceIn(-10_000L, 10_000L)
                                                applySubtitleTiming()
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("+500 ms")
                                        }
                                    }
                                    if (offset != 0L) {
                                        TextButton(
                                            onClick = {
                                                subOffsetOverrideMs = 0L
                                                applySubtitleTiming()
                                            },
                                            modifier = Modifier.align(Alignment.CenterHorizontally)
                                        ) {
                                            Text(
                                                "Reset timing",
                                                color = TextSecondary,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            TextButton(onClick = { openSubtitleSearch() }) {
                                Text(
                                    "Search subtitles online",
                                    color = LocalPalette.current.accent,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // In-player subtitle search results.
        // Bottom sheet (not AlertDialog) so the root media/channel-key handler
        // keeps working while it's open (#28).
        if (subSearchOpen) {
            // TV: land D-pad focus on the first result row when results land.
            val firstResultFocus = remember { FocusRequester() }
            LaunchedEffect(isTv, subSearchResults) {
                if (isTv && subSearchResults.isNotEmpty()) {
                    delay(300)
                    runCatching { firstResultFocus.requestFocus() }
                }
            }
            ModalBottomSheet(
                onDismissRequest = { subSearchOpen = false },
                sheetState = rememberModalBottomSheetState(),
                containerColor = Color(0xFF14141F)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)
                ) {
                    item {
                        Text(
                            "Find subtitles",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                    when {
                        subSearchLoading -> item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    color = LocalPalette.current.accent,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text("Searching subtitle addons…", color = TextSecondary)
                            }
                        }
                        subSearchError != null -> item {
                            Text(
                                subSearchError!!,
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        else -> itemsIndexed(subSearchResults) { index, sub ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (index == 0) Modifier.focusRequester(firstResultFocus)
                                        else Modifier
                                    )
                                    .clickable { addExternalSubtitle(sub) }
                                    .tvFocusRing(RoundedCornerShape(10.dp))
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
            }
        }

        // Sources switcher: pick another stream without leaving the player.
        if (sourcesOpen) {
            val browserState = rememberStreamBrowserState(availableStreams, repository.settings)
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
                        modifier = Modifier.padding(bottom = if (hiddenIncompatibleCount > 0) 4.dp else 12.dp)
                    )
                    if (hiddenIncompatibleCount > 0) {
                        Text(
                            "$hiddenIncompatibleCount 4K stream${if (hiddenIncompatibleCount > 1) "s" else ""} hidden — your device doesn't support 4K playback",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
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
                        androidx.compose.runtime.CompositionLocalProvider(
                            LocalBadgeStyleKey provides repository.settings.streamCardStyle
                        ) {
                            StreamList(
                                streams = browserState.filtered,
                                onPick = { switchSource(it) },
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(
                                    start = 4.dp, end = 4.dp, bottom = 32.dp
                                ),
                                selectedUrl = currentRequest.streamUrl,
                                onDownload = { downloadStream = it }
                            )
                        }
                    }
                }
            }
        }

        // Download confirmation dialog
        downloadStream?.let { stream ->
            com.pulsestream.app.ui.components.DownloadConfirmDialog(
                stream = stream,
                title = currentRequest.title,
                onDismiss = { downloadStream = null }
            )
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
                                modifier = Modifier.tvFocusRing(RoundedCornerShape(50)),
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

