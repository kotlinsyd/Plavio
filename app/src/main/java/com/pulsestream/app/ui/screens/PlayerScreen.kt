@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.pulsestream.app.ui.screens

import com.pulsestream.app.ui.screens.player.*

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Hd
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
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.AlertDialog
import com.pulsestream.app.ui.components.OrbitDots
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.pulsestream.app.ui.tv.tvSheetFocus
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
import androidx.media3.common.Tracks
import androidx.media3.common.PlaybackException
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.VideoSize
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import com.pulsestream.app.player.PlavioAudioRenderersFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
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
import com.pulsestream.app.ui.components.liquidGlass
import com.pulsestream.app.ui.components.StreamCard
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
    // PiP dismissed via its X button: MainActivity bumps the counter (the
    // screen never leaves composition on its own in that path) — exit
    // through the normal back path so onDispose stops and releases.
    val pipDismissCount by MainActivity.pipDismissedCount.collectAsState()
    val pipDismissBaseline = remember { pipDismissCount }
    LaunchedEffect(pipDismissCount) {
        if (pipDismissCount != pipDismissBaseline) onBack()
    }
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
    /** Monotonic generation: a stale subtitle transform may never overwrite a newer selection/offset. */
    val subtitleShiftGeneration = remember { AtomicInteger(0) }
    /** Non-null while the current stream is in a failed state (#4). */
    var playerError by remember { mutableStateOf<String?>(null) }
    /** Auto-failover count; generic playback caps at 3, mobile Live uses its policy cap. */
    var autoFailoverCount by remember { mutableStateOf(0) }
    /** Bounded recovery budget for streams that play and later stall, per channel session. */
    var liveRebufferRecoveries by remember { mutableIntStateOf(0) }
    /** Invalidates callbacks from listeners attached to released/replaced players. */
    val activePlayerListenerGeneration = remember { AtomicInteger(0) }
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
                // Auto-failover: generic playback tries 3 sources; mobile Live
                // uses its separate bounded forward-only policy.
                // Also covers decoder errors (e.g. 4K on phones that can't decode it).
                val isSourceError = error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
                    error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
                    error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED
                val mobileLiveFallback = com.pulsestream.app.data.LivePlaybackPolicy.shouldRetry(
                    isLive = initialRequest.isLive,
                    isTv = isTv,
                    errorCode = error.errorCode,
                    attemptsSoFar = autoFailoverCount
                )
                if ((isSourceError && autoFailoverCount < 3) || mobileLiveFallback) {
                    autoFailoverCount++
                    if (initialRequest.isLive && !isTv) {
                        com.pulsestream.app.data.SourceDiagnostics.record("Live playback", "fallback_attempt", 0)
                    }
                    failoverToNextSource?.invoke(autoFailoverCount)
                    return
                }
                if (initialRequest.isLive && !isTv &&
                    com.pulsestream.app.data.LivePlaybackPolicy.isRecoverableSourceError(error.errorCode)) {
                    com.pulsestream.app.data.SourceDiagnostics.record("Live playback", "fallback_exhausted", 0)
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

    /** Each player gets its own listener wrapper; stale instances become inert immediately. */
    fun guardedPlayerListener(): Player.Listener {
        val generation = activePlayerListenerGeneration.incrementAndGet()
        return object : Player.Listener {
            private fun isCurrent() = !disposed.get() && activePlayerListenerGeneration.get() == generation
            override fun onPlayerError(error: PlaybackException) {
                if (isCurrent()) errorListener.onPlayerError(error)
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isCurrent()) errorListener.onIsPlayingChanged(isPlaying)
            }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (isCurrent()) errorListener.onVideoSizeChanged(videoSize)
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
    /** Builds the playback HTTP data source using platform TLS validation. */
    fun buildPlaybackDataSourceFactory(
        headers: Map<String, String>,
        originUrl: String
    ): androidx.media3.datasource.DataSource.Factory {
        val httpFactory = com.pulsestream.app.data.PlaybackHttp.factory(originUrl, headers)
        // DefaultDataSource delegates per URI scheme: FileDataSource for
        // file:// (downloads), HttpDataSource for http(s):// (streams).
        return DefaultDataSource.Factory(context, httpFactory)
    }

    /**
     * MediaSource.Factory for a request (headers + optional disk cache).
     * Shared by [buildPlaybackMediaSource] and the player's own factory so
     * later setMediaItem calls use the same networking.
     */
    fun playbackMediaSourceFactory(req: PlaybackRequest): MediaSource.Factory {
        val playbackHeaders = PlaybackHeaders.buildPlaybackHeaders(req.headers, isTv)
        val dataSourceFactory = buildPlaybackDataSourceFactory(playbackHeaders, req.streamUrl)
        val cacheMb = settings.diskCacheMb
        return if (cacheMb > 0) {
            val cacheDataSourceFactory = CacheDataSource.Factory()
                .setCache(PlaybackCache.get(context, cacheMb * 1024L * 1024L))
                .setUpstreamDataSourceFactory(dataSourceFactory)
            DefaultMediaSourceFactory(cacheDataSourceFactory)
        } else {
            DefaultMediaSourceFactory(dataSourceFactory)
        }
    }

    /**
     * Builds the MediaSource for a request. YouTube DASH qualities are
     * video-only — when [PlaybackRequest.mergeAudioUrl] is set, the video is
     * merged with that audio stream so every quality plays with sound.
     */
    fun buildPlaybackMediaSource(
        req: PlaybackRequest,
        subs: List<ResolvedSubtitle> = emptyList(),
        forcedMime: String? = null
    ): MediaSource {
        val mediaSourceFactory = playbackMediaSourceFactory(req)
        // Nuvio parity: infer HLS/DASH from the FULL URL (path + query) so
        // proxied URLs like /api/manifest?url=<encoded ...m3u8> play as HLS
        // instead of being misidentified as progressive.
        val inferredMime = forcedMime
            ?: com.pulsestream.app.data.PlaybackContentProbe.recognizedMime(req.mimeType)
            ?: PlaybackHeaders.inferMimeType(req.streamUrl)
        val videoItem = MediaItem.Builder()
            .setUri(req.streamUrl)
            .apply { inferredMime?.let { setMimeType(it) } }
            .setSubtitleConfigurations(subtitleConfigs(subs))
            .build()
        val mergeAudio = req.mergeAudioUrl
        return if (mergeAudio != null) {
            MergingMediaSource(
                mediaSourceFactory.createMediaSource(videoItem),
                mediaSourceFactory.createMediaSource(MediaItem.fromUri(mergeAudio))
            )
        } else {
            mediaSourceFactory.createMediaSource(videoItem)
        }
    }

    fun buildExo(
        req: PlaybackRequest,
        subs: List<ResolvedSubtitle> = emptyList(),
        forcedMime: String? = null
    ): ExoPlayer {
        val prefs = settings.subtitleLanguages
        val audioPref = settings.preferredAudioLanguage
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
        val mediaSourceFactory = playbackMediaSourceFactory(req)
        return ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setRenderersFactory(PlavioAudioRenderersFactory(context))
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
                // YouTube DASH (video-only + merged audio) goes through
                // setMediaSource; everything else keeps the setMediaItem path.
                setMediaSource(buildPlaybackMediaSource(req, subs, forcedMime))
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
        // Style rail application (v0.14.5): embedded styling/font sizes
        // are overridden so the user's style always wins, the size is a
        // fixed sp (fractional in PiP, where the window is small and
        // captions must scale with it), and the bottom offset follows
        // the rail's 0–200 range.
        sv.setApplyEmbeddedStyles(false)
        sv.setApplyEmbeddedFontSizes(false)
        sv.setStyle(
            CaptionStyleCompat(
                settings.subtitleTextColor,
                settings.subtitleBackgroundColor,
                android.graphics.Color.TRANSPARENT,
                edge,
                settings.subtitleEdgeColor,
                if (settings.subtitleBold) android.graphics.Typeface.DEFAULT_BOLD else null
            )
        )
        val fontSp = settings.subtitleFontSizeSp
        if (inPip) {
            sv.setFractionalTextSize(
                SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * (fontSp / 18f)
            )
        } else {
            sv.setFixedTextSize(
                android.util.TypedValue.COMPLEX_UNIT_SP, fontSp.toFloat()
            )
        }
        // Bottom offset: base fraction plus offset/1000 (the offset term
        // clamped to 0..0.2), the total clamped to 0..0.4.
        val offsetFraction = (settings.subtitleBottomOffset / 1000f).coerceIn(0f, 0.2f)
        sv.setBottomPaddingFraction(
            (SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION * (2f / 3f) + offsetFraction)
                .coerceIn(0f, 0.4f)
        )
    }


    var currentRequest by remember { mutableStateOf(initialRequest) }
    LaunchedEffect(currentRequest.videoId, currentRequest.liveChannel) {
        liveRebufferRecoveries = 0
    }
    var player by remember {
        mutableStateOf(buildExo(initialRequest).also { it.addListener(guardedPlayerListener()) })
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
     * Per-video subtitle delay (ms), added to the global
     * [SettingsStore.subtitleOffsetMs]. Set from the Style rail's delay
     * stepper / Auto Sync and persisted per video id
     * ([SettingsStore.subtitleDelayMsFor]); reloaded whenever the title
     * changes (see the load effect below the resolver).
     */
    var subOffsetOverrideMs by remember { mutableLongStateOf(0L) }
    /**
     * Effective subtitle timing offset for the current title: global default
     * plus the per-video delay, clamped to ±60s.
     */
    fun effectiveSubOffsetMs(): Long =
        (settings.subtitleOffsetMs + subOffsetOverrideMs).coerceIn(-60_000L, 60_000L)
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
        val generation = subtitleShiftGeneration.incrementAndGet()
        ioScope.launch {
            val shifted = withContext(Dispatchers.IO) { shiftSubtitles(raw, offset, context.cacheDir) }
            if (!disposed.get() && subtitleShiftGeneration.get() == generation) resolvedSubs = shifted
        }
    }
    // Self-heal handler: probe Content-Type, rebuild once with probed MIME.
    healOnFormatError = { error ->
        val req = latestRequest.value
        val old = latestPlayer.value
        ioScope.launch {
            val probed = probePlaybackContentType(
                req.streamUrl,
                PlaybackHeaders.buildPlaybackHeaders(req.headers, isTv)
            )
            // A source switch/zap can finish while the probe is in flight.
            // Never apply the old source's MIME (or release its replacement).
            withContext(Dispatchers.Main) {
                if (disposed.get() || latestRequest.value != req || latestPlayer.value !== old) return@withContext
                if (probed != null) {
                    val playWhenReady = old.playWhenReady
                    val position = old.currentPosition
                    val playbackSpeed = old.playbackParameters.speed
                    val fresh = buildExo(req, resolvedSubs, forcedMime = probed)
                        .also { it.addListener(guardedPlayerListener()) }
                    playerView?.player = null
                    old.release()
                    player = fresh
                    playerError = null
                    fresh.setPlaybackSpeed(playbackSpeed)
                    fresh.seekTo(position)
                    fresh.prepare()
                    fresh.playWhenReady = playWhenReady
                } else if (com.pulsestream.app.data.LivePlaybackPolicy.shouldRetry(
                        isLive = req.isLive,
                        isTv = isTv,
                        errorCode = error.errorCode,
                        attemptsSoFar = autoFailoverCount
                    )) {
                    autoFailoverCount++
                    com.pulsestream.app.data.SourceDiagnostics.record("Live playback", "fallback_attempt", 0)
                    playerError = null
                    failoverToNextSource?.invoke(autoFailoverCount)
                } else {
                    if (req.isLive && !isTv &&
                        com.pulsestream.app.data.LivePlaybackPolicy.isRecoverableSourceError(error.errorCode)) {
                        com.pulsestream.app.data.SourceDiagnostics.record("Live playback", "fallback_exhausted", 0)
                    }
                    playerError = error.message?.takeIf { it.isNotBlank() }
                        ?: "Playback failed (code ${error.errorCode})"
                }
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
        // Caption sizing switches between fixed sp and fractional in
        // PiP — re-apply so the change lands without a player rebuild.
        playerView?.let { applySubtitleStyle(it) }
    }
    var locked by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var volumePercent by remember { mutableIntStateOf(0) }
    // Volume boost (past 100%): a LoudnessEnhancer on the player's audio
    // session adds up to +10 dB on top of the system max. volumeBoost is
    // 0f..1f; the shown percent is 100 + boost*100 while system is at max.
    var volumeBoost by remember { mutableFloatStateOf(0f) }
    val boostHolder = remember {
        object {
            var enhancer: LoudnessEnhancer? = null
            var sessionId: Int = -1
        }
    }
    fun applyVolumeBoost() {
        val sid = runCatching { player.audioSessionId }.getOrDefault(-1)
        if (sid <= 0) return
        if (boostHolder.enhancer == null || boostHolder.sessionId != sid) {
            runCatching { boostHolder.enhancer?.release() }
            boostHolder.enhancer = runCatching { LoudnessEnhancer(sid) }.getOrNull()
            boostHolder.sessionId = sid
        }
        runCatching {
            boostHolder.enhancer?.apply {
                setTargetGain((volumeBoost * 1000).roundToInt())
                enabled = volumeBoost > 0f
            }
        }
    }
    fun displayVolumePercent(): Int {
        val sys = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val sysPct = sys * 100 / maxVolume.coerceAtLeast(1)
        return if (sys >= maxVolume) sysPct + (volumeBoost * 100).roundToInt() else sysPct
    }
    var speed by remember { mutableFloatStateOf(1f) }
    var speedMenuOpen by remember { mutableStateOf(false) }
    /** True while the hold-for-speed gesture is boosting playback (v0.12.0). */
    var holdSpeedActive by remember { mutableStateOf(false) }
    /** Current hold-boost step (2..5) shown in the hold pill (v0.12.1). */
    var holdSpeedStep by remember { mutableIntStateOf(2) }
    /** Main-thread scope for the delayed single-tap toggle in videoGestures. */
    val gestureUiScope = rememberCoroutineScope()
    var indicator by remember { mutableStateOf<PlayerIndicator?>(null) }
    var indicatorTick by remember { mutableIntStateOf(0) }

    // YouTube opens portrait-first (user rotates via the Rotate pill);
    // everything else opens landscape as before.
    var isPortrait by remember { mutableStateOf(initialRequest.sourceName == "YouTube") }
    // Aspect ratio (in-player only — cycles Fit → Fill → Zoom)
    var aspectMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    val aspectLabel = ASPECT_MODES.firstOrNull { it.first == aspectMode }?.second ?: "Fit"

    var isBuffering by remember { mutableStateOf(true) }
    var switchingMedia by remember { mutableStateOf(false) }
    /** True once the current request has actually played — gates the
     * startup presentation (backdrop + orbit) against a plain rebuffer
     * orbit. Resets whenever the request changes, so episode and
     * source switches start a fresh startup. */
    var hasStartedPlayback by remember(currentRequest) { mutableStateOf(false) }

    // Track switching
    var tracksOpen by remember { mutableStateOf(false) }
    // External-player protected-link warning (owner's order 2026-10-26):
    // non-null while the warning popup is up for that request.
    var externalWarningReq by remember { mutableStateOf<PlaybackRequest?>(null) }
    var externalWarningHide by remember { mutableStateOf(false) }
    var tracksMode by remember { mutableStateOf("subs") } // "subs" or "audio"
    var trackTick by remember { mutableIntStateOf(0) }
    /** Set when the user hand-picks an audio track — the preferred-language
     * auto-select then leaves their pick alone for this player session. */
    var userPickedAudio by remember { mutableStateOf(false) }

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
        // YouTube DASH keeps its merged audio via the shared builder;
        // everything else keeps the setMediaItem path.
        if (currentRequest.mergeAudioUrl != null) {
            player.setMediaSource(buildPlaybackMediaSource(currentRequest, resolved, null), pos)
        } else {
            player.setMediaItem(
                MediaItem.Builder()
                    .setUri(currentRequest.streamUrl)
                    .apply {
                        (com.pulsestream.app.data.PlaybackContentProbe.recognizedMime(currentRequest.mimeType)
                            ?: PlaybackHeaders.inferMimeType(currentRequest.streamUrl))?.let { setMimeType(it) }
                    }
                    .setSubtitleConfigurations(subtitleConfigs(resolved, defaultUrl))
                    .build(),
                pos
            )
        }
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
    var availableStreams by remember {
        mutableStateOf(com.pulsestream.app.ui.screens.player.mergePlayerSourceSet(
            emptyList(), initialRequest.streams, includeHeadersInIdentity = initialRequest.isLive && !isTv
        ))
    }
    /** Live fallback never retries the same URL/header tuple during this channel session. */
    var attemptedLiveSourceIds by remember { mutableStateOf(emptySet<String>()) }
    /** Count of streams hidden because the device can't decode them (e.g. 4K on budget phones). */
    var hiddenIncompatibleCount by remember { mutableStateOf(0) }
    var streamsLoading by remember { mutableStateOf(false) }
    var streamsJob by remember { mutableStateOf<Job?>(null) }
    /** A bounded late-source wait is invalidated by manual navigation and disposal. */
    val liveFallbackGeneration = remember { AtomicInteger(0) }
    var pendingLiveFallbackJob by remember { mutableStateOf<Job?>(null) }

    fun preemptPendingLiveFallback() {
        liveFallbackGeneration.incrementAndGet()
        pendingLiveFallbackJob?.cancel()
        pendingLiveFallbackJob = null
    }

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
    // Subtitle overlay (v0.14.5): chosen language rail + the URL of the
    // subtitle the user last picked in the panel (drives the Style
    // rail's appearance before the player finishes attaching it).
    var panelLang by remember { mutableStateOf<String?>(null) }
    var panelPickedUrl by remember { mutableStateOf<String?>(null) }
    // Style-rail mirrors of the persisted style: font size (sp), text
    // colour (ARGB — its alpha is the opacity control), bold / outline /
    // outline colour / bottom offset, plus auto-sync capture state and
    // the reload tick that re-runs the subtitle resolver effect.
    var subFontSpUi by remember { mutableIntStateOf(settings.subtitleFontSizeSp) }
    var subColorUi by remember { mutableIntStateOf(settings.subtitleTextColor) }
    var subBoldUi by remember { mutableStateOf(settings.subtitleBold) }
    var subEdgeUi by remember { mutableIntStateOf(settings.subtitleEdgeType) }
    var subEdgeColorUi by remember { mutableIntStateOf(settings.subtitleEdgeColor) }
    var subBottomUi by remember { mutableIntStateOf(settings.subtitleBottomOffset) }
    var subReloadTick by remember { mutableIntStateOf(0) }
    var captureCues by remember { mutableStateOf<List<Pair<Long, String>>?>(null) }
    var captureLoading by remember { mutableStateOf(false) }
    /** Playback position stored when Auto Sync > Capture is pressed. */
    var capturePositionMs by remember { mutableLongStateOf(0L) }

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

    // TV remote fallback: MainActivity.onKeyDown forwards Left/Right/FF/RW
    // here ONLY when Compose didn't consume the key (e.g. focus was lost).
    LaunchedEffect(Unit) {
        MainActivity.playerSeekRequest.collect { seekByWithFeedback(it) }
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
        // A manual pick (including "Auto", which hands selection back) is
        // the user's word for this session — auto-select steps aside.
        userPickedAudio = true
        val params = player.trackSelectionParameters.buildUpon()
        if (opt == null) params.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
        else params.setOverrideForType(TrackSelectionOverride(opt.group, listOf(opt.trackIndex)))
        player.trackSelectionParameters = params.build()
        trackTick++
        pokeIndicator(PlayerIndicator.Message(if (opt == null) "Audio: Auto" else "Audio: ${opt.label}"))
    }

    /**
     * Tightened preferred-audio enforcement (v0.12.0): buildExo only passes
     * the Settings > Playback language as a track-selector *hint*, which
     * the selector could outvote (channel count / role flags), so the
     * chosen language sometimes didn't play even when present. Now the
     * preference is applied as a hard TrackSelectionOverride for the first
     * audio track whose language matches, re-applied whenever the track
     * set changes (playback start, source switch, late track arrivals).
     * No matching track → parameters stay untouched (graceful fallback to
     * the default selection).
     */
    fun applyPreferredAudioLanguage() {
        if (userPickedAudio) return
        val pref = settings.preferredAudioLanguage?.takeIf { it.isNotBlank() } ?: return
        val p = player
        // Already playing the preferred language — nothing to tighten.
        val alreadySelected = p.currentTracks.groups
            .filter { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
            .any { g ->
                (0 until g.length).any { i ->
                    g.isTrackSelected(i) && subtitleLangMatches(g.getTrackFormat(i).language, pref)
                }
            }
        if (alreadySelected) return
        val match = p.currentTracks.groups
            .filter { it.type == C.TRACK_TYPE_AUDIO }
            .firstNotNullOfOrNull { g ->
                (0 until g.length).firstOrNull { i ->
                    subtitleLangMatches(g.getTrackFormat(i).language, pref)
                }?.let { idx -> g to idx }
            } ?: return
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguages(pref)
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .setOverrideForType(TrackSelectionOverride(match.first.mediaTrackGroup, listOf(match.second)))
            .build()
        trackTick++
    }

    // Apply the preferred audio language on player (re)creation and on
    // every track-set change for that player.
    LaunchedEffect(player) {
        userPickedAudio = false
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                applyPreferredAudioLanguage()
            }
        }
        player.addListener(listener)
        applyPreferredAudioLanguage()
        try {
            kotlinx.coroutines.awaitCancellation()
        } finally {
            player.removeListener(listener)
        }
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
        // Panel-integrated (v0.14.2): results merge into the subtitle
        // panel's list instead of opening a separate sheet.

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

    // ---- Subtitle panel plumbing (v0.14.2, owner's reference design) ----

    /** Every subtitle the panel can offer, instantly: the request's own
     *  (straight from the addon response — no waiting), the user's
     *  additions and online-search results, deduped by URL. */
    fun panelSubs(): List<Subtitle> =
        (currentRequest.subtitles + addedSubs + subSearchResults).distinctBy { it.url }

    fun panelTrackId(opt: TrackOption): String? =
        runCatching { opt.group.getFormat(opt.trackIndex).id }.getOrNull()

    fun panelLangName(code: String?): String =
        displayLanguage(code) ?: code?.uppercase() ?: "Unknown"

    /** Player text tracks that did NOT come from the panel's subtitle
     *  list — i.e. tracks embedded in the video file itself. */
    fun panelEmbeddedOpts(): List<TrackOption> {
        val subs = panelSubs()
        return textOpts.filter { o -> subs.none { it.url == panelTrackId(o) } }
    }

    /** (language name, track count) pairs in first-seen order. */
    fun panelLanguages(): List<Pair<String, Int>> {
        val counts = linkedMapOf<String, Int>()
        panelSubs().forEach {
            val n = panelLangName(it.lang)
            counts[n] = (counts[n] ?: 0) + 1
        }
        panelEmbeddedOpts().forEach { o ->
            val n = panelLangName(
                runCatching { o.group.getFormat(o.trackIndex).language }.getOrNull()
            )
            counts[n] = (counts[n] ?: 0) + 1
        }
        return counts.entries.map { it.key to it.value }
    }

    /** Tap-to-apply for a panel subtitle: if its track is already in the
     *  player, select it; if it is still resolving, make it the default
     *  the attach effect picks; otherwise add it (download + apply). */
    fun activatePanelSubtitle(sub: Subtitle) {
        val opt = textOpts.firstOrNull { panelTrackId(it) == sub.url }
        when {
            opt != null -> applySubtitleSelection(opt)
            addedSubs.any { it.url == sub.url } ||
                currentRequest.subtitles.any { it.url == sub.url } -> {
                pendingDefaultSubUrl = sub.url
                pokeIndicator(
                    PlayerIndicator.Message(
                        "Subtitles: ${displayLanguage(sub.lang) ?: sub.lang.uppercase()}"
                    )
                )
            }
            else -> addExternalSubtitle(sub)
        }
    }

    fun panelHostLabel(url: String): String =
        runCatching { android.net.Uri.parse(url).host }.getOrNull()
            ?.removePrefix("www.")?.takeIf { it.isNotBlank() } ?: "Addon"

    fun panelFileLabel(sub: Subtitle): String =
        sub.url.substringAfterLast('/').substringBefore('?')
            .ifBlank { displayLanguage(sub.lang) ?: sub.lang.uppercase() }

    fun panelFormatLabel(mimeType: String): String? = when (mimeType) {
        "application/x-subrip" -> "SRT"
        "text/vtt" -> "VTT"
        "application/ttml+xml" -> "TTML"
        else -> null
    }

    /** Panel delay readout in the reference format: "+0.500s". */
    fun panelDelayLabel(ms: Long): String =
        String.format(java.util.Locale.US, "%+.3fs", ms / 1000.0)

    /** Parses (startMs, text) cues out of a downloaded SRT/VTT file. */
    fun parseSubtitleCues(file: java.io.File): List<Pair<Long, String>> {
        val out = mutableListOf<Pair<Long, String>>()
        val timing = Regex("(\\d{1,2}):(\\d{2}):(\\d{2})[.,](\\d{3})\\s*-->")
        val lines = runCatching { file.readLines(Charsets.UTF_8) }.getOrDefault(emptyList())
        var i = 0
        while (i < lines.size && out.size < 4000) {
            val m = timing.find(lines[i])
            if (m == null) { i++; continue }
            val (h, min, sec, ms) = m.destructured
            val start = ((h.toLong() * 60 + min.toLong()) * 60 + sec.toLong()) * 1000 + ms.toLong()
            val text = StringBuilder()
            i++
            while (i < lines.size && lines[i].isNotBlank() && !timing.containsMatchIn(lines[i])) {
                val t = lines[i].replace(Regex("<[^>]+>"), "").trim()
                if (t.isNotEmpty() && !t.all { ch -> ch.isDigit() }) {
                    if (text.isNotEmpty()) text.append(' ')
                    text.append(t)
                }
                i++
            }
            if (text.isNotEmpty()) out.add(start to text.toString())
        }
        return out
    }

    /**
     * Style rail delay control: set the per-video delay (the stepper's
     * ±100ms, Reset's 0, or an Auto Sync cue tap), persist it for this
     * video, cancel any in-progress auto-sync cue pick (a manual change
     * always wins), and re-shift the downloaded subtitles.
     */
    fun setSubtitleDelay(delayMs: Long) {
        val clamped = delayMs.coerceIn(-60_000L, 60_000L)
        subOffsetOverrideMs = clamped
        settings.setSubtitleDelayMsFor(currentRequest.videoId, clamped)
        captureCues = null
        applySubtitleTiming()
    }

    /** Auto Sync > Reload: drop the cached downloads + shifted copies
     *  and re-resolve every subtitle fresh from its source. */
    fun reloadPanelSubtitles() {
        rawResolvedSubs.forEach { raw ->
            runCatching { raw.file.delete() }
            val key = raw.file.absolutePath.hashCode().toUInt().toString(16)
            context.cacheDir.listFiles()?.forEach { f ->
                if (f.name.startsWith("shift_") && f.name.contains(key)) {
                    runCatching { f.delete() }
                }
            }
        }
        subReloadTick++
        pokeIndicator(PlayerIndicator.Message("Subtitles reloading…"))
    }

    /** Auto Sync > Capture: collect the cues of the active downloaded
     *  subtitle around the current position; the user then taps the
     *  line they are hearing and the delay is set from it. */
    fun startCaptureSync() {
        val selectedUrl = textOpts.firstOrNull { it.selected }?.let { panelTrackId(it) }
        val raw = rawResolvedSubs.firstOrNull { it.sub.url == selectedUrl }
            ?: rawResolvedSubs.firstOrNull()
        if (raw == null) {
            pokeIndicator(PlayerIndicator.Message("Auto sync needs a downloaded subtitle track"))
            return
        }
        // The position is captured NOW (when the line is heard); a cue
        // tap later computes the delay from this stored position.
        capturePositionMs = player.currentPosition
        captureLoading = true
        ioScope.launch {
            val cues = parseSubtitleCues(raw.file)
            val center = (capturePositionMs - effectiveSubOffsetMs()).coerceAtLeast(0L)
            // The five cues nearest the captured position, in time order.
            val near = cues.sortedBy { kotlin.math.abs(it.first - center) }
                .take(5).sortedBy { it.first }
            withContext(Dispatchers.Main) {
                captureLoading = false
                captureCues = near
                if (near.isEmpty()) {
                    pokeIndicator(PlayerIndicator.Message("No subtitle lines found to sync from"))
                }
            }
        }
    }

    // URL of a just-added subtitle that should become active once resolved.

    // Resolve side-loaded subtitles in the background once per request (or
    // when the user adds one via search): download, sniff the real format,
    // then attach. Files are cached so this is instant on repeat visits. The
    // resolved list goes into state; a separate effect attaches it, so a
    // source switch can carry the subtitles into the fresh player instead of
    // re-preparing the video (#29).
    LaunchedEffect(currentRequest, addedSubs, subReloadTick) {
        val reqAtStart = currentRequest
        val all = reqAtStart.subtitles + addedSubs
        subLoadError = false
        resolvedSubs = emptyList()
        rawResolvedSubs = emptyList()
        if (all.isEmpty() || disposed.get()) return@LaunchedEffect
        val resolved = resolveSubtitleFiles(
            all, context.cacheDir,
            requestHeaders = reqAtStart.headers,
            streamOriginUrl = reqAtStart.streamUrl,
            isTv = isTv
        )
        // Superseded while resolving (source/episode/zap switch) — the newer
        // effect run owns the result.
        if (currentRequest != reqAtStart || disposed.get()) return@LaunchedEffect
        subLoadError = resolved.isEmpty()
        // Store the raw files, then publish the timing-shifted list. The
        // separate attach effect picks up resolvedSubs.
        rawResolvedSubs = resolved
        val generation = subtitleShiftGeneration.incrementAndGet()
        val shifted = withContext(Dispatchers.IO) {
            shiftSubtitles(resolved, effectiveSubOffsetMs(), context.cacheDir)
        }
        if (currentRequest == reqAtStart && subtitleShiftGeneration.get() == generation && !disposed.get()) {
            resolvedSubs = shifted
        }
    }

    // Load the persisted per-video delay only when the actual
    // title/episode changes; adding a subtitle or switching source does
    // not discard the user's delay.
    LaunchedEffect(currentRequest.metaId, currentRequest.videoId, currentRequest.season, currentRequest.episode) {
        subOffsetOverrideMs = settings.subtitleDelayMsFor(currentRequest.videoId)
        if (rawResolvedSubs.isNotEmpty()) applySubtitleTiming()
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
        preemptPendingLiveFallback()
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
                if (!oldReq.isLive) {
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
                    if (oldReq.type != newReq.type || oldReq.videoId != newReq.videoId || oldReq.liveChannel != newReq.liveChannel) {
                        streamsJob?.cancel()
                        availableStreams = com.pulsestream.app.ui.screens.player.mergePlayerSourceSet(
                            emptyList(), newReq.streams, includeHeadersInIdentity = newReq.isLive && !isTv
                        )
                        attemptedLiveSourceIds = emptySet()
                    }
                    currentRequest = newReq
                    repository.pendingPlayback = newReq
                    resumeOfferMs = null
                    // #29: carry already-resolved subtitles (same title) into
                    // the fresh player so the attach effect doesn't re-prepare
                    // the video just to add them back.
                    val carrySubs =
                        newReq.subtitles == oldReq.subtitles && resolvedSubs.isNotEmpty()
                    val carried = if (carrySubs) resolvedSubs else emptyList()
                    val fresh = buildExo(newReq, carried).also { it.addListener(guardedPlayerListener()) }
                    // #16: a fresh player defaults to 1x — re-apply the pill.
                    fresh.setPlaybackSpeed(speed)
                    subsAttached =
                        if (carried.isNotEmpty()) fresh to subtitleIdentity(carried) else null
                    // Detach the surface from the old player before releasing it.
                    playerView?.player = null
                    player = fresh
                    old.release()
                    seekToPlaybackStart(
                        isMobileLive = newReq.isLive && !isTv,
                        startAtMs = startAtMs,
                        seekToPosition = { fresh.seekTo(it) },
                        seekToDefaultPosition = { fresh.seekToDefaultPosition() }
                    )
                    fresh.prepare()
                    fresh.playWhenReady = autoplay
                    showControls = true
                    controlsTick++
                }
            }
        }
    }

    fun switchSource(stream: StreamItem) {
        if (!com.pulsestream.app.data.isPlayableLiveStreamUrl(stream.url)) return
        preemptPendingLiveFallback()
        val mobileLive = currentRequest.isLive && !isTv
        val sameCandidate = if (mobileLive) {
            com.pulsestream.app.data.LivePlaybackPolicy.isSameMobileLiveCandidate(
                currentRequest.streamUrl, currentRequest.headers, stream.url, stream.headers
            )
        } else stream.url == currentRequest.streamUrl
        if (sameCandidate) return
        sourcesOpen = false
        pokeIndicator(
            PlayerIndicator.Message("Source: ${streamQualityLabel(stream) ?: stream.sourceName ?: "switched"}")
        )
        replaceStream(
            currentRequest.copy(
                streamUrl = stream.url,
                headers = stream.headers,
                mimeType = stream.mimeType,
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
        currentRequest.isLive && zapChannels.size > 1

    fun zapTo(index: Int) {
        // #9: never start zap work on disposed state (ghost audio).
        if (disposed.get()) return
        val list = zapChannels
        if (list.size < 2) return
        val target = list[index.mod(list.size)]
        // Same channel tapped twice — nothing to do. M3U ids are URLs and
        // videoId holds the URL too, so this covers addon AND M3U channels:
        // double-tap CH+ must not rebuild the player (#19).
        if (target.metaId == currentRequest.videoId &&
            (currentRequest.liveChannel == null || currentRequest.liveChannel == target)) return
        preemptPendingLiveFallback()
        zapping = true
        ioScope.launch {
            try {
                val result = if (target.addonId == "m3u") {
                    com.pulsestream.app.data.StreamResult(
                        listOf(com.pulsestream.app.data.StreamItem(url = target.metaId)), emptyList())
                } else {
                    withTimeoutOrNull(20_000) {
                        repository.liveStreamResultsFlow(target)
                            .firstOrNull { it.streams.any { s -> com.pulsestream.app.data.isPlayableLiveStreamUrl(s.url) } }
                    }
                }
                val playable = result?.streams.orEmpty().filter { com.pulsestream.app.data.isPlayableLiveStreamUrl(it.url) }
                val stream = playable.firstOrNull()
                if (stream == null) {
                    withContext(Dispatchers.Main) {
                        pokeIndicator(PlayerIndicator.Message("No stream for ${target.name}"))
                    }
                    return@launch
                }
                repository.pendingTvIndex = list.indexOf(target)
                withContext(Dispatchers.Main) {
                    pokeIndicator(PlayerIndicator.Message(target.name))
                    replaceStream(
                        currentRequest.copy(
                            streamUrl = stream.url,
                            headers = stream.headers,
                            mimeType = stream.mimeType,
                            title = target.name,
                            poster = target.poster,
                            videoId = target.metaId,
                            type = target.type,
                            liveChannel = target,
                            streamLabel = "LIVE",
                            sourceName = stream.sourceName,
                            streams = playable,
                            subtitles = result?.subtitles.orEmpty()
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
        val requestAtStart = currentRequest
        val liveChannel = requestAtStart.liveChannel
        streamsJob = ioScope.launch {
            streamsLoading = true
            try {
                val results = if (liveChannel != null) repository.liveStreamResultsFlow(liveChannel)
                    else repository.streamResultsFlow(type, videoId)
                results.collect { result ->
                    if (result.streams.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            if (disposed.get() || currentRequest.type != type || currentRequest.videoId != videoId ||
                                currentRequest.liveChannel != liveChannel) return@withContext
                            val incoming = if (requestAtStart.isLive) result.streams.filter {
                                com.pulsestream.app.data.isPlayableLiveStreamUrl(it.url)
                            } else result.streams
                            val complete = com.pulsestream.app.ui.screens.player.mergePlayerSourceSet(
                                availableStreams, incoming,
                                includeHeadersInIdentity = requestAtStart.isLive && !isTv
                            )
                            // Preserve the original handoff while slow addons and debrid finish.
                            if (complete != availableStreams) {
                                availableStreams = complete
                                hiddenIncompatibleCount = 0
                            }
                        }
                    }
                }
            } finally {
                // NonCancellable: a replaced job must still clear the spinner.
                withContext(Dispatchers.Main + NonCancellable) { streamsLoading = false }
            }
        }
    }

    /** Awaits late Live candidates instead of abandoning fallback after an empty first emission. */
    fun awaitMobileLiveFallbackCandidate() {
        if (pendingLiveFallbackJob?.isActive == true) return
        val requestAtStart = currentRequest
        val playerAtStart = player
        val generation = liveFallbackGeneration.incrementAndGet()
        val liveChannel = requestAtStart.liveChannel
        val current = StreamItem(url = requestAtStart.streamUrl, headers = requestAtStart.headers)
        val job = ioScope.launch {
            var selected: StreamItem? = null
            try {
                withTimeoutOrNull(20_000L) {
                    val results = if (liveChannel != null) repository.liveStreamResultsFlow(liveChannel)
                        else repository.streamResultsFlow(requestAtStart.type, requestAtStart.videoId)
                    results.firstOrNull { result ->
                        selected = withContext(Dispatchers.Main) {
                            if (disposed.get() || liveFallbackGeneration.get() != generation ||
                                currentRequest != requestAtStart || player !== playerAtStart) return@withContext null
                            val incoming = result.streams.filter {
                                com.pulsestream.app.data.isPlayableLiveStreamUrl(it.url)
                            }
                            if (incoming.isEmpty()) return@withContext null
                            val merged = com.pulsestream.app.ui.screens.player.mergePlayerSourceSet(
                                availableStreams, incoming, includeHeadersInIdentity = true
                            )
                            if (merged != availableStreams) availableStreams = merged
                            com.pulsestream.app.data.LivePlaybackPolicy.nextUntriedCandidate(
                                merged, current, attemptedLiveSourceIds
                            )?.also { candidate ->
                                attemptedLiveSourceIds = attemptedLiveSourceIds +
                                    com.pulsestream.app.data.LivePlaybackPolicy.candidateIdentity(candidate)
                            }
                        }
                        selected != null
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A failed or completed source flow is equivalent to the bounded wait expiring.
            }
            withContext(Dispatchers.Main) {
                if (disposed.get() || liveFallbackGeneration.get() != generation ||
                    currentRequest != requestAtStart || player !== playerAtStart) return@withContext
                if (selected != null) {
                    switchSource(selected!!)
                } else {
                    playerError = "No other sources available"
                    pokeIndicator(PlayerIndicator.Message("No other sources available"))
                }
                pendingLiveFallbackJob = null
            }
        }
        pendingLiveFallbackJob = job
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
    fun nextSource(automaticLiveFallback: Boolean = false) {
        val streams = availableStreams.filter { com.pulsestream.app.data.isPlayableLiveStreamUrl(it.url) }
        val mobileLive = currentRequest.isLive && !isTv
        val next = if (automaticLiveFallback && mobileLive) {
            val current = StreamItem(url = currentRequest.streamUrl, headers = currentRequest.headers)
            attemptedLiveSourceIds = attemptedLiveSourceIds +
                com.pulsestream.app.data.LivePlaybackPolicy.candidateIdentity(current)
            com.pulsestream.app.data.LivePlaybackPolicy.nextUntriedCandidate(
                streams, current, attemptedLiveSourceIds
            )?.also { candidate ->
                attemptedLiveSourceIds = attemptedLiveSourceIds +
                    com.pulsestream.app.data.LivePlaybackPolicy.candidateIdentity(candidate)
            }
        } else {
            val cur = streams.indexOfFirst {
                it.url == currentRequest.streamUrl && it.headers == currentRequest.headers
            }
            if (streams.size < 2) null else streams[(cur + 1).mod(streams.size)]
        }
        if (next == null) {
            if (automaticLiveFallback && mobileLive) {
                awaitMobileLiveFallbackCandidate()
                return
            }
            pokeIndicator(PlayerIndicator.Message("No other sources available"))
            refreshStreamsBackground(currentRequest.type, currentRequest.videoId)
            return
        }
        switchSource(next)
    }

    // Wire the auto-failover path now that the helpers exist.
    failoverToNextSource = { attempt ->
        val maxAttempts = if (currentRequest.isLive && !isTv)
            com.pulsestream.app.data.LivePlaybackPolicy.MAX_MOBILE_LIVE_FAILOVERS else 3
        pokeIndicator(PlayerIndicator.Message("Trying next source… ($attempt/$maxAttempts)"))
        nextSource(automaticLiveFallback = currentRequest.isLive && !isTv)
    }

    // A mobile Live source can stall in BUFFERING forever without producing a
    // Media3 error. Bound only first-start recovery: a source that has played
    // once, VOD, TV, or an intentionally paused player is never interrupted.
    LaunchedEffect(player, currentRequest, isTv) {
        if (!currentRequest.isLive || isTv) return@LaunchedEffect
        val watchedPlayer = player
        var waitingSinceMs: Long? = null
        val attemptsAtStart = autoFailoverCount
        val replacementAtStart = replaceGen.get()
        var hasEverPlayed = watchedPlayer.isPlaying
        while (true) {
            if (disposed.get() || watchedPlayer !== latestPlayer.value || currentRequest != latestRequest.value) {
                return@LaunchedEffect
            }
            if (autoFailoverCount != attemptsAtStart || replaceGen.get() != replacementAtStart) {
                return@LaunchedEffect
            }
            hasEverPlayed = hasEverPlayed || watchedPlayer.isPlaying
            if (hasEverPlayed) return@LaunchedEffect
            val state = watchedPlayer.playbackState
            val buffering = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE
            val nowMs = System.nanoTime() / 1_000_000L
            waitingSinceMs = liveStartupWaitingSince(
                waitingSinceMs, nowMs, watchedPlayer.playWhenReady, buffering
            )
            val elapsedMs = waitingSinceMs?.let { nowMs - it } ?: 0L
            if (shouldFailoverStalledLiveStartup(
                    isLive = true,
                    isTv = false,
                    playWhenReady = watchedPlayer.playWhenReady,
                    hasEverPlayed = hasEverPlayed,
                    isBuffering = buffering,
                    elapsedMs = elapsedMs
                )) {
                val maxAttempts = com.pulsestream.app.data.LivePlaybackPolicy.MAX_MOBILE_LIVE_FAILOVERS
                if (autoFailoverCount < maxAttempts) {
                    autoFailoverCount++
                    com.pulsestream.app.data.SourceDiagnostics.record("Live playback", "startup_timeout", elapsedMs)
                    failoverToNextSource?.invoke(autoFailoverCount)
                } else {
                    com.pulsestream.app.data.SourceDiagnostics.record("Live playback", "fallback_exhausted", 0)
                    playerError = "Live source did not start"
                }
                return@LaunchedEffect
            }
            delay(500)
        }
    }

    // Recovery after first successful playback: the startup watchdog above deliberately
    // stops once a stream has played. A separate bounded watchdog handles streams that
    // subsequently stall without a Media3 error. It never acts while paused or seeking.
    LaunchedEffect(player, currentRequest, isTv) {
        if (!currentRequest.isLive || isTv) return@LaunchedEffect
        val watchedPlayer = player
        var hasPlayed = watchedPlayer.isPlaying
        var rebufferSinceMs: Long? = null
        while (true) {
            if (disposed.get() || watchedPlayer !== latestPlayer.value || currentRequest != latestRequest.value) {
                return@LaunchedEffect
            }
            hasPlayed = hasPlayed || watchedPlayer.isPlaying
            if (!hasPlayed || !watchedPlayer.playWhenReady || watchedPlayer.playbackState != Player.STATE_BUFFERING) {
                rebufferSinceMs = null
            } else {
                val nowMs = System.nanoTime() / 1_000_000L
                rebufferSinceMs = rebufferSinceMs ?: nowMs
                if (com.pulsestream.app.ui.screens.player.shouldFailoverStalledLiveRebuffer(
                        isLive = currentRequest.isLive,
                        isTv = isTv,
                        playWhenReady = watchedPlayer.playWhenReady,
                        hasEverPlayed = hasPlayed,
                        isBuffering = watchedPlayer.playbackState == Player.STATE_BUFFERING,
                        elapsedMs = nowMs - rebufferSinceMs!!
                    )) {
                    val maxRecoveries = 2
                    if (liveRebufferRecoveries < maxRecoveries) {
                        liveRebufferRecoveries++
                        com.pulsestream.app.data.SourceDiagnostics.record("Live playback", "rebuffer_timeout", nowMs - rebufferSinceMs!!)
                        pokeIndicator(PlayerIndicator.Message("Live stream stalled — trying another source…"))
                        nextSource(automaticLiveFallback = true)
                    } else {
                        com.pulsestream.app.data.SourceDiagnostics.record("Live playback", "fallback_exhausted", nowMs - rebufferSinceMs!!)
                        playerError = "Live stream stopped responding. Try Retry or choose another source."
                    }
                    return@LaunchedEffect
                }
            }
            delay(500)
        }
    }

    // VOD stall watchdog (owner's player round, 2026-10-23): a movie/episode
    // source that never starts, or freezes mid-play with no position
    // progress — hosts that ignore byte-range requests behave exactly like
    // this — fails over to the next source instead of spinning forever.
    // Bounded by the same generic failover cap as playback errors (3).
    LaunchedEffect(player, currentRequest, isTv) {
        if (currentRequest.isLive) return@LaunchedEffect
        val watchedPlayer = player
        val replacementAtStart = replaceGen.get()
        var hasPlayed = watchedPlayer.isPlaying
        var stallSinceMs: Long? = null
        var lastPositionMs = -1L
        var lastProgressAtMs = 0L
        while (true) {
            if (disposed.get() || watchedPlayer !== latestPlayer.value ||
                currentRequest != latestRequest.value || replaceGen.get() != replacementAtStart
            ) {
                return@LaunchedEffect
            }
            hasPlayed = hasPlayed || watchedPlayer.isPlaying
            val nowMs = System.nanoTime() / 1_000_000L
            val posMs = runCatching { watchedPlayer.currentPosition }.getOrDefault(0L)
            if (lastPositionMs < 0L || posMs > lastPositionMs + 1_000L) {
                lastPositionMs = posMs
                lastProgressAtMs = nowMs
            }
            val buffering = watchedPlayer.playbackState == Player.STATE_BUFFERING ||
                watchedPlayer.playbackState == Player.STATE_IDLE
            val progressed = nowMs - lastProgressAtMs < 3_000L
            stallSinceMs = if (watchedPlayer.playWhenReady && buffering && !progressed) {
                stallSinceMs ?: nowMs
            } else null
            val elapsedMs = stallSinceMs?.let { nowMs - it } ?: 0L
            if (com.pulsestream.app.ui.screens.player.shouldFailoverStalledVod(
                    isLive = false,
                    playWhenReady = watchedPlayer.playWhenReady,
                    hasEverPlayed = hasPlayed,
                    isBuffering = buffering,
                    positionProgressed = progressed,
                    elapsedMs = elapsedMs
                )) {
                if (autoFailoverCount < 3) {
                    autoFailoverCount++
                    com.pulsestream.app.data.SourceDiagnostics.record("VOD playback", "stall_timeout", elapsedMs)
                    failoverToNextSource?.invoke(autoFailoverCount)
                } else {
                    com.pulsestream.app.data.SourceDiagnostics.record("VOD playback", "fallback_exhausted", elapsedMs)
                    playerError = "Stream stalled. Try Retry or choose another source."
                }
                return@LaunchedEffect
            }
            delay(500)
        }
    }

    fun playEpisode(video: Video) {
        // #9: never start episode work on disposed state (ghost audio).
        if (disposed.get()) return
        episodesOpen = false
        switchingMedia = true
        ioScope.launch {
            val type = currentRequest.type
            val result = withTimeoutOrNull(20_000) {
                repository.streamResultsFlow(type, video.id).firstOrNull { it.streams.any { stream -> com.pulsestream.app.data.isPlayableLiveStreamUrl(stream.url) } }
            }
            // Back to the main thread: everything below touches Compose state.
            withContext(Dispatchers.Main) {
                val (playableStreams, hidden) = result?.streams?.let { ( it to 0 ) } ?: (emptyList<com.pulsestream.app.data.StreamItem>() to 0)
                val stream = playableStreams.firstOrNull { com.pulsestream.app.data.isPlayableLiveStreamUrl(it.url) }
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
                            mimeType = stream.mimeType,
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
     * Full gesture set for the video surface in ONE pointerInput handler:
     * tap (toggle controls), double-tap (±10s seek), directional drags
     * (seek / volume / brightness) and hold-for-speed with slide steps.
     *
     * v0.12.1 regression fix: v0.12.0 stacked THREE separate detectors
     * (tap + a second detectTapGestures for the hold + drag) on this same
     * node. Concurrent detectors share PointerInputChange consumption, so
     * the tap detector's wait-for-up was cancelled by its siblings on every
     * tap — controls never appeared and every control (incl. Back) was
     * unreachable; only the hold still fired. A single unified state
     * machine removes that whole class of interference: nothing else on
     * this node consumes pointer changes.
     *
     * Hold-for-speed (GitHub issue #2 + owner's slide extension): a press
     * held ~350ms without dragging engages 2x; while still holding, each
     * ~60dp of rightward slide steps up (2x → 3x → 4x → max 5x), sliding
     * left steps back down (floor 2x). Release restores the exact speed
     * that was playing before the hold. Disabled while locked (tap only
     * shows the locked hint, as before).
     */
    fun Modifier.videoGestures(): Modifier = this.pointerInput(player, locked) {
        if (locked) {
            detectTapGestures(onTap = {
                pokeIndicator(PlayerIndicator.Message("Locked — tap the lock icon to unlock"))
            })
            return@pointerInput
        }
        var lastTapUpMs = 0L
        var pendingToggle: Job? = null
        awaitEachGesture {
            val down = awaitFirstDown()
            val downPos = down.position
            val downTime = down.uptimeMillis
            val slop = viewConfiguration.touchSlop
            val stepPx = 60.dp.toPx()
            val holdThresholdMs = 350L
            var lastEventMs = downTime
            var lastPos = downPos
            var accX = 0f
            var accY = 0f
            // Drag state (same semantics as the old drag detector):
            // -1 undecided, 0 = seek, 1 = volume, 2 = brightness.
            var dragMode = -1
            var seekBase = 0L
            var seekTarget = -1L
            var volBase = 0f
            var brightBase = 0.5f
            // Hold state.
            var holdEngaged = false
            var prevSpeed = 1f
            var slideBaseX = 0f
            var upChange: androidx.compose.ui.input.pointer.PointerInputChange? = null

            while (upChange == null) {
                val event = if (!holdEngaged && dragMode == -1) {
                    // Still a tap/hold candidate: wake up at the hold
                    // threshold even if no further pointer event arrives.
                    val remaining = (downTime + holdThresholdMs - lastEventMs).coerceAtLeast(0L)
                    withTimeoutOrNull(remaining) { awaitPointerEvent() }
                } else {
                    awaitPointerEvent()
                }
                if (event == null) {
                    // Threshold reached with the finger still down and no
                    // drag in progress: engage the speed hold.
                    holdEngaged = true
                    prevSpeed = player.playbackParameters.speed
                    slideBaseX = accX
                    holdSpeedStep = 2
                    runCatching { player.setPlaybackSpeed(2f) }
                    holdSpeedActive = true
                    continue
                }
                val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                lastEventMs = change.uptimeMillis
                if (!change.pressed) {
                    // Pointer went up (Compose 1.7 no longer distinguishes
                    // a system cancel here — it also arrives as !pressed).
                    upChange = change
                    break
                }
                val delta = change.position - lastPos
                lastPos = change.position
                accX += delta.x
                accY += delta.y
                if (holdEngaged) {
                    // Slide right = step up, left = step down (floor 2x).
                    val step = 2 + ((accX - slideBaseX) / stepPx).toInt().coerceIn(0, 3)
                    if (step != holdSpeedStep) {
                        holdSpeedStep = step
                        runCatching { player.setPlaybackSpeed(step.toFloat()) }
                    }
                } else {
                    if (dragMode == -1 && (abs(accX) > slop || abs(accY) > slop)) {
                        dragMode = if (abs(accX) > abs(accY)) 0
                        else if (downPos.x < size.width / 2f) 2 else 1
                        seekBase = player.currentPosition
                        seekTarget = -1L
                        volBase = displayVolumePercent().toFloat()
                        brightBase = activity?.window?.attributes
                            ?.screenBrightness?.takeIf { it >= 0 } ?: 0.5f
                    }
                    if (dragMode != -1) {
                        change.consume()
                        when (dragMode) {
                            0 -> { // horizontal swipe: seek proportional to swipe distance
                                val dur = player.duration
                                if (dur > 0) {
                                    seekTarget = (seekBase + accX / size.width * dur)
                                        .toLong().coerceIn(0L, dur)
                                    val deltaMs = seekTarget - player.currentPosition
                                    pokeIndicator(
                                        PlayerIndicator.Seek(
                                            (if (deltaMs >= 0) "+" else "−") + formatMs(abs(deltaMs))
                                        )
                                    )
                                }
                            }
                            1 -> { // vertical swipe, right half: volume (+ boost past max)
                                // Total range 0..200%: 0..100 drives the system
                                // volume; past 100 the system stays at max and
                                // the boost takes over. Swiping back down drains
                                // the boost first, then the system volume.
                                val total = (volBase - accY / size.height * 200f)
                                    .coerceIn(0f, 200f)
                                if (total >= 100f) {
                                    audioManager.setStreamVolume(
                                        AudioManager.STREAM_MUSIC, maxVolume, 0
                                    )
                                    volumeBoost = (total - 100f) / 100f
                                } else {
                                    volumeBoost = 0f
                                    val v = (total / 100f * maxVolume)
                                        .roundToInt().coerceIn(0, maxVolume)
                                    audioManager.setStreamVolume(
                                        AudioManager.STREAM_MUSIC, v, 0
                                    )
                                }
                                applyVolumeBoost()
                                volumePercent = displayVolumePercent()
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
                }
            }

            // Gesture finished — settle the outcome.
            when {
                holdEngaged -> {
                    holdSpeedActive = false
                    runCatching { player.setPlaybackSpeed(prevSpeed) }
                }
                dragMode == 0 -> {
                    if (seekTarget >= 0) player.seekTo(seekTarget)
                }
                dragMode == -1 -> {
                    // A tap: a second tap inside the double-tap window
                    // seeks ±10s by side; a lone tap toggles the controls
                    // once the window expires (same timing as the old
                    // detectTapGestures behaviour).
                    val now = upChange?.uptimeMillis ?: downTime
                    val window = viewConfiguration.doubleTapTimeoutMillis
                    if (lastTapUpMs != 0L && now - lastTapUpMs <= window) {
                        pendingToggle?.cancel()
                        pendingToggle = null
                        lastTapUpMs = 0L
                        if (downPos.x < size.width / 2f) seekByWithFeedback(-10_000)
                        else seekByWithFeedback(10_000)
                    } else {
                        lastTapUpMs = now
                        pendingToggle = gestureUiScope.launch {
                            delay(window.toLong())
                            toggleControls()
                        }
                    }
                }
            }
        }
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
        if (req.isLive) return
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
            if (c.isPlaying) hasStartedPlayback = true
            isBuffering = c.playbackState == Player.STATE_BUFFERING ||
                c.playbackState == Player.STATE_IDLE
            volumePercent = displayVolumePercent()
            // A rebuilt player (source/episode switch) has a new audio
            // session — re-attach the boost enhancer to it.
            if (volumeBoost > 0f) applyVolumeBoost()
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
            delay(if (isTv) 1000 else 500) // low-end TVs: half the recompositions
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

    // One-time init: begin buffering immediately, then apply saved-position / autoplay policy.
    LaunchedEffect(Unit) {
        val startupPlayer = player
        val isLocalFile = initialRequest.streamUrl.startsWith("file:", true) || initialRequest.streamUrl.startsWith("content:", true)
        val prepareEarly = initialRequest.streams.isNotEmpty() || initialRequest.isLive || isLocalFile
        val saved = preparePlayerBeforeSavedPositionLookup(
            preparePlayer = {
                // Prepare with playback held so a resume prompt never leaks audio/video
                // while progress storage (or a fresh-URL lookup) is still pending.
                startupPlayer.playWhenReady = false
                startupPlayer.prepare()
            },
            loadSavedPosition = {
                if (initialRequest.isLive && !isTv) 0L
                else repository.getSavedPosition(initialRequest.streamUrl)
            },
            prepareEarly = prepareEarly
        )
        var req: PlaybackRequest = initialRequest
        var rebuiltForFreshResumeUrl = false
        var offerResume = saved > 0
        // Local files (downloads): never re-resolve sources — play the file directly.
        if (saved > 0 && req.streams.isEmpty() && !req.isLive && !isLocalFile && !disposed.get()) {
            // #15: Continue Watching resume — re-resolve the title's streams
            // first so signed/expiring URLs are fresh, and resume at the saved
            // position on the fresh URL. Falls back to the stored URL when
            // re-resolution fails.
            val fresh = withTimeoutOrNull(20_000) {
                repository.streamResultsFlow(req.type, req.videoId)
                    .firstOrNull { it.streams.any { stream -> com.pulsestream.app.data.isPlayableLiveStreamUrl(stream.url) } }
            }
            val (playableFresh, hiddenFresh) = fresh?.streams?.let { ( it to 0 ) } ?: (emptyList<com.pulsestream.app.data.StreamItem>() to 0)
            val stream = playableFresh.firstOrNull { com.pulsestream.app.data.isPlayableLiveStreamUrl(it.url) }
            if (fresh != null && stream != null && !disposed.get()) {
                availableStreams = playableFresh
                hiddenIncompatibleCount = hiddenFresh
                req = req.copy(
                    streamUrl = stream.url,
                    headers = stream.headers,
                    mimeType = stream.mimeType,
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
            val rebuilt = buildExo(req).also { it.addListener(guardedPlayerListener()) }
            rebuilt.setPlaybackSpeed(speed)
            player = rebuilt
            mimeHealAttempted = false
            currentRequest = req
            rebuiltForFreshResumeUrl = true
        }
        val p = player
        seekToPlaybackStart(
            isMobileLive = req.isLive && !isTv,
            startAtMs = saved,
            seekToPosition = { p.seekTo(it) },
            seekToDefaultPosition = { p.seekToDefaultPosition() }
        )
        // The original instance is already prepared above. A refreshed URL has
        // a newly-built player and still needs its first prepare after seekTo.
        if (rebuiltForFreshResumeUrl || !prepareEarly) p.prepare()
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
    }

    // An early Play action may hand off only the first addon's results. Always finish
    // the same title's complete addon flow; a source switch alone does not restart it.
    LaunchedEffect(currentRequest.type, currentRequest.videoId, currentRequest.liveChannel) {
        val req = currentRequest
        val local = req.streamUrl.startsWith("file:", true) || req.streamUrl.startsWith("content:", true)
        val playlistChannel = req.isLive && req.liveChannel == null &&
            com.pulsestream.app.data.isPlayableLiveStreamUrl(req.videoId)
        if (!local && !playlistChannel) refreshStreamsBackground(req.type, req.videoId)
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
    // YouTube opens portrait-first (Rotate pill switches to landscape).
    DisposableEffect(Unit) {
        // PiP dismissal rework (v0.14.4): MainActivity stops playback
        // through this hook when a PiP window is X-closed — on the
        // owner's Samsung the stopped activity never recomposes, so
        // the old counter-only path left audio playing until the app
        // was killed from recents. stop()+release() here kills the
        // audio at the source; the dispose path below stays as-is.
        MainActivity.pipEmergencyStop = {
            runCatching { savePosition() }
            runCatching { latestPlayer.value.stop() }
            runCatching { latestPlayer.value.release() }
        }
        activity?.requestedOrientation = if (initialRequest.sourceName == "YouTube")
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        else
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        val window = activity?.window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
        val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        onDispose {
            MainActivity.pipEmergencyStop = null
            // #9: from here on, no late work may build a player (ghost audio).
            disposed.set(true)
            preemptPendingLiveFallback()
            savePosition()
            scrobbler.shutdown()
            // #9: kill late zap/episode/source-switch work. The scope itself
            // stays valid (the scrobblers above hold it); only its children
            // are cancelled. savePosition()'s save runs NonCancellable so it
            // survives this; the scrobblers' final stop-events are best-effort.
            ioScope.coroutineContext.cancelChildren()
            latestPlayer.value.release()
            runCatching { boostHolder.enhancer?.release() }
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
    // Focus bookkeeping: overlay flag resets when controls are hidden, and the
    // currently focused transport button registers its action so OK always
    // fires it (even if the button's own click handling misses the key).
    LaunchedEffect(showControls) { if (!showControls) transportFocused = false }
    var focusedKey by remember { mutableStateOf<String?>(null) }
    var focusedAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun act(key: String, action: () -> Unit): Modifier =
        Modifier.onFocusChanged {
            if (it.isFocused || it.hasFocus) { focusedKey = key; focusedAction = action }
            else if (focusedKey == key) { focusedKey = null; focusedAction = null }
        }
    val tvKeys = if (isTv) {
        // A settings sheet/dialog is up: the D-pad must drive the dialog's
        // own focus ( Up/Down through rows, OK to pick ), not the player.
        // Media keys and the channel rocker keep working regardless.
        val dialogOpen = tracksOpen || subSearchOpen || sourcesOpen || episodesOpen ||
            speedMenuOpen || resumeOfferMs != null || playerError != null
        Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            // Any remote activity keeps the controls on screen (they used to
            // auto-hide after 3.5s MID-navigation, so focus "vanished").
            if (showControls) controlsTick++
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
                    else if (showControls && focusedAction != null) {
                        if (event.nativeKeyEvent.repeatCount == 0) focusedAction?.invoke()
                        true
                    }
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
    // TV watchdog: if D-pad focus is lost for any reason (a focused button was
    // removed, a sheet closed, an overlay faded out) park it back on the player
    // container, so the remote can never stay dead. Skipped while a sheet or
    // dialog (separate window) legitimately owns focus.
    var rootHasFocus by remember { mutableStateOf(true) }
    val sheetUp = tracksOpen || subSearchOpen || sourcesOpen || episodesOpen ||
        speedMenuOpen || resumeOfferMs != null || downloadStream != null
    LaunchedEffect(rootHasFocus, isTv, sheetUp, showControls, playerError) {
        if (isTv && !rootHasFocus && !sheetUp && playerError == null) {
            delay(400)
            runCatching { if (showControls && !locked) playPauseFocus.requestFocus() else containerFocus.requestFocus() }
                .onFailure { runCatching { containerFocus.requestFocus() } }
        }
    }
    Box(tvKeys.fillMaxSize().background(Color.Black)
        .then(if (isTv) Modifier.onFocusChanged { rootHasFocus = it.hasFocus } else Modifier)
        .then(tvContainerModifier)) {
        // Video surface with the full gesture set (gestures off while floating —
        // taps on the PiP window belong to the system's PiP controls).
        Box(Modifier.fillMaxSize().then(if (inPip) Modifier else Modifier.videoGestures())) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).also { pv ->
                        pv.player = player
                        pv.useController = false
                        pv.isFocusable = false
                        pv.isFocusableInTouchMode = false
                        pv.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
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

        // Startup presentation (owner's pick 2026-10-24 — "Backdrop
        // + orbit"): while the current request has not played yet, the
        // connecting state is the title's art full-screen, dimmed, the
        // title and the orbit loader — no process wording at all. The
        // stall / failover machinery behind it is unchanged; only this
        // presentation follows the treatment. Once playback has begun,
        // a rebuffer shows just the orbit over the video. Suppressed on
        // a fatal error — the error overlay takes its place (#4).
        if ((isBuffering || switchingMedia) && playerError == null) {
            if (!hasStartedPlayback) {
                Box(Modifier.fillMaxSize()) {
                    val art = currentRequest.poster
                    if (!art.isNullOrBlank()) {
                        SafePosterImage(
                            model = art,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f)))
                    }
                    Column(
                        Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            currentRequest.title,
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(20.dp))
                        OrbitDots(Modifier.size(54.dp))
                    }
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    OrbitDots(Modifier.size(48.dp))
                }
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
                    TextButton(onClick = {
                        // Fresh fetch when opening Sources — ensures full list
                        refreshStreamsBackground(currentRequest.type, currentRequest.videoId)
                        sourcesOpen = true
                    }) {
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
        // v0.14.3 (owner's order): pop-in spring + fade on every
        // indicator; volume/brightness get an animated level bar; the
        // volume indicator changes colour at the extremes — soft red
        // at mute (0), amber in the boost zone (past 100).
        val indScale = remember { Animatable(1f) }
        val indAlpha = remember { Animatable(1f) }
        LaunchedEffect(indicator) {
            if (indicator != null) {
                indScale.snapTo(0.82f)
                indAlpha.snapTo(0f)
                launch { indScale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 420f)) }
                indAlpha.animateTo(1f, tween(120))
            }
        }
        indicator?.let { ind ->
            val accentNow = LocalPalette.current.accent
            val volTint = when {
                ind is PlayerIndicator.Volume && ind.percent <= 0 -> Color(0xFFF87171)
                ind is PlayerIndicator.Volume && ind.percent > 100 -> Color(0xFFFBBF24)
                else -> accentNow
            }
            val levelFraction = when (ind) {
                is PlayerIndicator.Volume ->
                    if (ind.percent > 100) ind.percent / 200f else ind.percent / 100f
                is PlayerIndicator.Brightness -> ind.percent.coerceIn(0, 100) / 100f
                else -> null
            }
            val levelAnim by animateFloatAsState(
                targetValue = levelFraction ?: 0f,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
                label = "indicatorLevel"
            )
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Surface(
                    color = Color.Black.copy(alpha = 0.62f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.graphicsLayer {
                        scaleX = indScale.value
                        scaleY = indScale.value
                        alpha = indAlpha.value
                    }
                ) {
                    Column(
                        Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            when (ind) {
                                is PlayerIndicator.Volume -> {
                                    Icon(
                                        if (ind.percent <= 0) Icons.Filled.VolumeOff
                                        else Icons.Filled.VolumeUp,
                                        null,
                                        tint = volTint
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Volume ${ind.percent}%",
                                        color = volTint,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                is PlayerIndicator.Brightness -> {
                                    Icon(Icons.Filled.SettingsBrightness, null, tint = accentNow)
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
                        if (levelFraction != null) {
                            Spacer(Modifier.height(8.dp))
                            Box(
                                Modifier
                                    .width(120.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(alpha = 0.25f))
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(levelAnim.coerceIn(0f, 1f))
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            if (ind is PlayerIndicator.Volume) volTint else accentNow
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }

        // Hold-for-speed pill: pinned top-center only while the hold boost
        // is active, showing the current step (2x..5x); never consumes touches.
        if (holdSpeedActive) {
            Box(
                Modifier.fillMaxSize().padding(top = 24.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.62f),
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.FastForward,
                            contentDescription = null,
                            tint = LocalPalette.current.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${holdSpeedStep}x",
                            color = TextPrimary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
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
            Box(Modifier.fillMaxSize().tapGestures()
                .then(if (isTv) Modifier.onFocusChanged { transportFocused = it.hasFocus } else Modifier)) {
                // ---- Top: title block + icon cluster ----
                Row(
                    Modifier
                        .fillMaxWidth()
                        .liquidGlass(RoundedCornerShape(20.dp), frost = 0.12f, darken = 0.03f)
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
                        // Top-right trio (owner's order 2026-10-25):
                        // Lock, Rotate, External player — icons only,
                        // moved out of the bottom pill bar. External
                        // wears a new icon (ExitToApp); the old
                        // OpenInNew was rejected as ugly.
                        IconButton(
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
                            },
                            modifier = Modifier.tvFocusRing(RoundedCornerShape(50))
                        ) {
                            Icon(
                                if (locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                                if (locked) "Unlock controls" else "Lock controls",
                                tint = TextPrimary
                            )
                        }
                        IconButton(
                            onClick = {
                                isPortrait = !isPortrait
                                activity?.requestedOrientation = if (isPortrait)
                                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                else
                                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            },
                            modifier = Modifier.tvFocusRing(RoundedCornerShape(50))
                        ) {
                            Icon(Icons.Filled.ScreenRotation, "Rotate", tint = TextPrimary)
                        }
                        IconButton(
                            onClick = {
                                val ctx = activity ?: return@IconButton
                                // Protected links warn once (owner's
                                // order 2026-10-26) instead of the old
                                // hard block; normal links open at once.
                                if (ExternalPlayer.needsWarning(ctx, req)) {
                                    externalWarningHide = false
                                    externalWarningReq = req
                                } else {
                                    ExternalPlayer.play(ctx, req)
                                }
                            },
                            modifier = Modifier.tvFocusRing(RoundedCornerShape(50))
                        ) {
                            Icon(Icons.Filled.ExitToApp, "Open in external player", tint = TextPrimary)
                        }
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.then(act("back") { onBack() })
                            .tvFocusRing(RoundedCornerShape(50))
                        ) {
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
                    if (!currentRequest.isLive) {
                        IconButton(
                            onClick = { seekByWithFeedback(-10_000) },
                            modifier = Modifier.then(act("rew") { seekByWithFeedback(-10_000) })
                                .tvFocusRing(RoundedCornerShape(50))
                        ) {
                            Icon(
                                Icons.Filled.Replay10, "Back 10 seconds",
                                tint = TextPrimary, modifier = Modifier.size(56.dp)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .liquidGlass(RoundedCornerShape(50), frost = 0.12f, darken = 0.03f)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = { togglePlay() },
                            modifier = Modifier
                                .focusRequester(playPauseFocus)
                                .then(act("play") { togglePlay() })
                                .tvFocusRing(RoundedCornerShape(50))
                        ) {
                            Icon(
                                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                if (isPlaying) "Pause" else "Play",
                                tint = TextPrimary, modifier = Modifier.size(76.dp)
                            )
                        }
                    }
                    if (!currentRequest.isLive) {
                        IconButton(
                            onClick = { seekByWithFeedback(10_000) },
                            modifier = Modifier.then(act("fwd") { seekByWithFeedback(10_000) })
                                .tvFocusRing(RoundedCornerShape(50))
                        ) {
                            Icon(
                                Icons.Filled.Forward10, "Forward 10 seconds",
                                tint = TextPrimary, modifier = Modifier.size(56.dp)
                            )
                        }
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
                        .liquidGlass(RoundedCornerShape(20.dp), frost = 0.12f, darken = 0.03f)
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 14.dp)
                ) {
                    if (currentRequest.isLive) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                color = Color(0xFFE5484D),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    "LIVE",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Text(
                                currentRequest.title,
                                color = TextPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                when { switchingMedia -> "Switching…"; isBuffering -> "Buffering…"; else -> "On air" },
                                color = TextSecondary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    } else {
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
                    }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier.liquidGlass(RoundedCornerShape(50), frost = 0.12f, darken = 0.03f)
                        ) {
                            Row(
                                Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Speed control.
                                PillBarButton(
                                    icon = Icons.Filled.AspectRatio,
                                    label = aspectLabel,
                                    onClick = { cycleAspect() }
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
                                    onClick = {
                                        trackTick++
                                        tracksMode = "subs"
                                        tracksOpen = true
                                    }
                                )
                                PillBarButton(
                                    icon = Icons.Filled.Audiotrack,
                                    label = "Audio",
                                    onClick = { trackTick++; tracksMode = "audio"; tracksOpen = true }
                                )
                                PillBarButton(
                                    icon = Icons.Filled.CloudDownload,
                                    label = "Sources",
                                    onClick = {
                                        // Fresh fetch when opening Sources — ensures full list, not stale partial
                                        // Fresh fetch when opening Sources — ensures full list, not stale partial
                                        refreshStreamsBackground(currentRequest.type, currentRequest.videoId)
                                        sourcesOpen = true
                                    }
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

        // External-player warning for protected links (owner's order
        // 2026-10-26 — replaces the old hard block): the link leaves
        // the app without its protected headers, so the outside player
        // may not be able to play it. "Don't show again" persists.
        externalWarningReq?.let { warnReq ->
            val warnCtx = activity
            AlertDialog(
                onDismissRequest = { externalWarningReq = null },
                title = { Text("Protected link") },
                text = {
                    Column {
                        Text(
                            "This source uses protected headers, which stay in Plavio — " +
                                "the other app may not be able to play this link.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { externalWarningHide = !externalWarningHide }
                        ) {
                            Checkbox(
                                checked = externalWarningHide,
                                onCheckedChange = { externalWarningHide = it }
                            )
                            Text(
                                "Don't show again",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (externalWarningHide && warnCtx != null) {
                            ExternalPlayer.setWarningSuppressed(warnCtx, true)
                        }
                        externalWarningReq = null
                        if (warnCtx != null) ExternalPlayer.play(warnCtx, warnReq)
                    }) {
                        Text("Open anyway", color = LocalPalette.current.accent)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { externalWarningReq = null }) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            )
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

        // Track switching: the Subs button opens the subtitle overlay
        // (v0.14.5, Nuvio-style three rails over the playing video); the
        // Audio button keeps its bottom sheet. Neither is an
        // AlertDialog, so the root media/channel-key handler keeps
        // working while they're open (#28).
        if (tracksOpen) {
            val isAudio = tracksMode == "audio"
            // TV: land D-pad focus on the first row when the picker opens.
            val firstRowFocus = remember { FocusRequester() }
            LaunchedEffect(isTv, tracksMode) {
                if (tracksMode == "subs") {
                    // Seed the Style rail's visibility from the track
                    // that is already playing when the overlay opens.
                    panelPickedUrl = textOpts.firstOrNull { it.selected }
                        ?.let { panelTrackId(it) }
                }
                if (isTv) {
                    delay(300)
                    runCatching { firstRowFocus.requestFocus() }
                }
            }
            if (isAudio) {
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
                                "Audio",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                            )
                        }
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
                    }
                }
            } else {
                // ===== Subtitle overlay (v0.14.5, owner's approved
                // design): a translucent full-player overlay — the video
                // keeps playing behind it and every style change lands
                // on the real captions live. Three side-by-side rails in
                // a horizontally scrollable row: Languages | Subtitles |
                // Style (the Style rail fades in only once a subtitle
                // is selected). =====
                val langs = panelLanguages()
                // Preferred languages first (Settings > Subtitles),
                // stable for the rest.
                val prefLangNames = settings.subtitleLanguages.map { panelLangName(it) }
                val sortedLangs = langs.sortedBy { (name, _) ->
                    prefLangNames.indexOf(name).let { if (it < 0) Int.MAX_VALUE else it }
                }
                val activeLang = panelLang?.takeIf { ln -> sortedLangs.any { it.first == ln } }
                    ?: textOpts.firstOrNull { it.selected }?.let {
                        panelLangName(
                            runCatching { it.group.getFormat(it.trackIndex).language }
                                .getOrNull()
                        )
                    }
                    ?: sortedLangs.firstOrNull()?.first
                val langSubs = panelSubs().filter { panelLangName(it.lang) == activeLang }
                val langEmbedded = panelEmbeddedOpts().filter {
                    panelLangName(
                        runCatching { it.group.getFormat(it.trackIndex).language }
                            .getOrNull()
                    ) == activeLang
                }
                val selectedSubUrl = textOpts.firstOrNull { it.selected }
                    ?.let { panelTrackId(it) }
                val selectedIsAddon = selectedSubUrl != null &&
                    panelSubs().any { it.url == selectedSubUrl }
                val styleVisible = !subtitlesOff || panelPickedUrl != null
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val railHeight = if (maxHeight < 400.dp) 236.dp else 300.dp
                    // Scrim: dims the video; a tap outside the rails
                    // dismisses the overlay.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { tracksOpen = false }
                    )
                    Column(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp, bottom = 20.dp)
                    ) {
                        Text(
                            "Subtitles",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // ---- Languages rail (200dp) ----
                            Column(
                                Modifier
                                    .width(200.dp)
                                    .height(railHeight)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF14141F).copy(alpha = 0.94f))
                                    .border(
                                        1.dp, Color.White.copy(alpha = 0.08f),
                                        RoundedCornerShape(14.dp)
                                    )
                                    .padding(10.dp)
                            ) {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    item {
                                        RailLanguageRow(
                                            name = "None",
                                            count = null,
                                            selected = subtitlesOff && panelPickedUrl == null,
                                            modifier = Modifier.focusRequester(firstRowFocus),
                                            onClick = {
                                                panelPickedUrl = null
                                                applySubtitleSelection(null)
                                            }
                                        )
                                    }
                                    items(sortedLangs) { (name, count) ->
                                        RailLanguageRow(
                                            name = name,
                                            count = count,
                                            selected = name == activeLang,
                                            onClick = { panelLang = name }
                                        )
                                    }
                                }
                            }
                            // ---- Subtitles rail (300dp) ----
                            Column(
                                Modifier
                                    .width(300.dp)
                                    .height(railHeight)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF14141F).copy(alpha = 0.94f))
                                    .border(
                                        1.dp, Color.White.copy(alpha = 0.08f),
                                        RoundedCornerShape(14.dp)
                                    )
                                    .padding(10.dp)
                            ) {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (activeLang == null && !subSearchLoading) {
                                        item {
                                            Text(
                                                "No subtitles for this title.",
                                                color = TextSecondary,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(vertical = 6.dp)
                                            )
                                        }
                                    }
                                    items(langSubs) { sub ->
                                        val resolvedSub =
                                            rawResolvedSubs.firstOrNull { it.sub.url == sub.url }
                                        PanelSubCard(
                                            chip = panelHostLabel(sub.url),
                                            title = panelFileLabel(sub),
                                            detail = listOfNotNull(
                                                panelLangName(sub.lang),
                                                resolvedSub?.let { panelFormatLabel(it.mimeType) },
                                                if (sub.url.contains("forced", ignoreCase = true)) {
                                                    "Forced"
                                                } else {
                                                    null
                                                }
                                            ).joinToString(" \u00b7 "),
                                            selected = selectedSubUrl == sub.url,
                                            onClick = {
                                                panelPickedUrl = sub.url
                                                activatePanelSubtitle(sub)
                                            }
                                        )
                                    }
                                    items(langEmbedded) { opt ->
                                        PanelSubCard(
                                            chip = "Built-in",
                                            title = opt.label,
                                            detail = listOfNotNull(
                                                activeLang,
                                                if (opt.label.contains("forced", ignoreCase = true)) {
                                                    "Forced"
                                                } else {
                                                    null
                                                }
                                            ).joinToString(" \u00b7 "),
                                            selected = opt.selected,
                                            onClick = {
                                                panelPickedUrl = "built-in"
                                                applySubtitleSelection(opt)
                                            }
                                        )
                                    }
                                    if (subSearchLoading) {
                                        item {
                                            Text(
                                                "Searching online\u2026",
                                                color = TextSecondary,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(vertical = 6.dp)
                                            )
                                        }
                                    }
                                    if (langSubs.isEmpty() && langEmbedded.isEmpty() &&
                                        !subSearchLoading
                                    ) {
                                        item {
                                            Row(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(Color.White.copy(alpha = 0.06f))
                                                    .clickable { openSubtitleSearch() }
                                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Filled.CloudDownload,
                                                    contentDescription = null,
                                                    tint = LocalPalette.current.accent,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    "Tap to fetch subtitles",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = TextPrimary
                                                )
                                            }
                                        }
                                    }
                                    if (subLoadError && panelSubs().isEmpty()) {
                                        item {
                                            Text(
                                                "Couldn't load subtitles \u2014 check your connection and try again.",
                                                color = Color(0xFFEF4444),
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(vertical = 8.dp)
                                            )
                                        }
                                    }
                                    subSearchError?.let { err ->
                                        item {
                                            Text(
                                                err,
                                                color = TextSecondary,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(start = 4.dp)
                                            )
                                        }
                                    }
                                    if (langSubs.isNotEmpty() || langEmbedded.isNotEmpty()) {
                                        item {
                                            TextButton(onClick = { openSubtitleSearch() }) {
                                                Text(
                                                    "Fetch subtitles online",
                                                    color = LocalPalette.current.accent,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            // ---- Style rail (280dp) ----
                            AnimatedVisibility(
                                visible = styleVisible,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                Column(
                                    Modifier
                                        .width(280.dp)
                                        .height(railHeight)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFF14141F).copy(alpha = 0.94f))
                                        .border(
                                            1.dp, Color.White.copy(alpha = 0.08f),
                                            RoundedCornerShape(14.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    // 1. Subtitle Delay
                                    StyleSectionLabel("Subtitle Delay")
                                    Spacer(Modifier.height(6.dp))
                                    StyleStepper(
                                        value = panelDelayLabel(effectiveSubOffsetMs()),
                                        onMinus = { setSubtitleDelay(subOffsetOverrideMs - 100L) },
                                        onPlus = { setSubtitleDelay(subOffsetOverrideMs + 100L) }
                                    )
                                    TextButton(
                                        onClick = { setSubtitleDelay(0L) },
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            "Reset",
                                            color = TextSecondary,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    // 2. Font Size
                                    StyleSectionLabel("Font Size")
                                    Spacer(Modifier.height(6.dp))
                                    StyleStepper(
                                        value = "${subFontSpUi}sp",
                                        onMinus = {
                                            val v = (subFontSpUi - 2).coerceIn(12, 40)
                                            subFontSpUi = v
                                            settings.subtitleFontSizeSp = v
                                            playerView?.let { applySubtitleStyle(it) }
                                        },
                                        onPlus = {
                                            val v = (subFontSpUi + 2).coerceIn(12, 40)
                                            subFontSpUi = v
                                            settings.subtitleFontSizeSp = v
                                            playerView?.let { applySubtitleStyle(it) }
                                        }
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    // 3. Bold
                                    StyleSectionLabel("Bold")
                                    Spacer(Modifier.height(6.dp))
                                    StyleChip(
                                        label = if (subBoldUi) "On" else "Off",
                                        on = subBoldUi,
                                        onClick = {
                                            val on = !subBoldUi
                                            subBoldUi = on
                                            settings.subtitleBold = on
                                            playerView?.let { applySubtitleStyle(it) }
                                        }
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    // 4. Color (keeps the current opacity)
                                    StyleSectionLabel("Color")
                                    Spacer(Modifier.height(6.dp))
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(
                                            listOf(
                                                0xFFFFFFFF, 0xFFFFD700, 0xFF00E5FF, 0xFFFF5C5C,
                                                0xFF00FF88, 0xFF9B59B6, 0xFFF97316, 0xFF22C55E,
                                                0xFF3B82F6, 0xFF000000
                                            )
                                        ) { c ->
                                            val rgb = c.toInt()
                                            StyleSwatch(
                                                color = Color(rgb),
                                                selected = (subColorUi and 0x00FFFFFF) ==
                                                    (rgb and 0x00FFFFFF),
                                                onClick = {
                                                    val merged =
                                                        (subColorUi and 0xFF000000.toInt()) or
                                                            (rgb and 0x00FFFFFF)
                                                    subColorUi = merged
                                                    settings.subtitleTextColor = merged
                                                    playerView?.let { applySubtitleStyle(it) }
                                                }
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    // 5. Text Opacity
                                    StyleSectionLabel("Text Opacity")
                                    Spacer(Modifier.height(6.dp))
                                    val opacityPct =
                                        (android.graphics.Color.alpha(subColorUi) * 100 + 127) / 255
                                    StyleStepper(
                                        value = "$opacityPct%",
                                        onMinus = {
                                            val p = (opacityPct - 10).coerceIn(0, 100)
                                            val a = (p * 255 + 50) / 100
                                            val merged = (a shl 24) or (subColorUi and 0x00FFFFFF)
                                            subColorUi = merged
                                            settings.subtitleTextColor = merged
                                            playerView?.let { applySubtitleStyle(it) }
                                        },
                                        onPlus = {
                                            val p = (opacityPct + 10).coerceIn(0, 100)
                                            val a = (p * 255 + 50) / 100
                                            val merged = (a shl 24) or (subColorUi and 0x00FFFFFF)
                                            subColorUi = merged
                                            settings.subtitleTextColor = merged
                                            playerView?.let { applySubtitleStyle(it) }
                                        }
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    // 6. Outline + Outline Color
                                    StyleSectionLabel("Outline")
                                    Spacer(Modifier.height(6.dp))
                                    StyleChip(
                                        label = if (subEdgeUi != 0) "On" else "Off",
                                        on = subEdgeUi != 0,
                                        onClick = {
                                            val t = if (subEdgeUi == 0) 1 else 0
                                            subEdgeUi = t
                                            settings.subtitleEdgeType = t
                                            playerView?.let { applySubtitleStyle(it) }
                                        }
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    StyleSectionLabel("Outline Color")
                                    Spacer(Modifier.height(6.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.alpha(if (subEdgeUi == 0) 0.42f else 1f)
                                    ) {
                                        listOf(0xFF000000, 0xFFFFFFFF, 0xFF00E5FF, 0xFFFF5C5C)
                                            .forEach { c ->
                                                val rgb = c.toInt()
                                                StyleSwatch(
                                                    color = Color(rgb),
                                                    selected = (subEdgeColorUi and 0x00FFFFFF) ==
                                                        (rgb and 0x00FFFFFF),
                                                    onClick = {
                                                        subEdgeColorUi = rgb
                                                        settings.subtitleEdgeColor = rgb
                                                        if (subEdgeUi == 0) {
                                                            subEdgeUi = 1
                                                            settings.subtitleEdgeType = 1
                                                        }
                                                        playerView?.let { applySubtitleStyle(it) }
                                                    }
                                                )
                                            }
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    // 7. Bottom Offset
                                    StyleSectionLabel("Bottom Offset")
                                    Spacer(Modifier.height(6.dp))
                                    StyleStepper(
                                        value = "$subBottomUi",
                                        onMinus = {
                                            val v = (subBottomUi - 5).coerceIn(0, 200)
                                            subBottomUi = v
                                            settings.subtitleBottomOffset = v
                                            playerView?.let { applySubtitleStyle(it) }
                                        },
                                        onPlus = {
                                            val v = (subBottomUi + 5).coerceIn(0, 200)
                                            subBottomUi = v
                                            settings.subtitleBottomOffset = v
                                            playerView?.let { applySubtitleStyle(it) }
                                        }
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    // 8. Auto Sync (addon subtitles only)
                                    StyleSectionLabel("Auto Sync")
                                    Spacer(Modifier.height(6.dp))
                                    if (!selectedIsAddon) {
                                        Text(
                                            "Select an addon subtitle first",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary
                                        )
                                    } else {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { reloadPanelSubtitles() },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    "Reload",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                            OutlinedButton(
                                                onClick = { startCaptureSync() },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    if (captureLoading) "Loading\u2026" else "Capture",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                        captureCues?.let { cues ->
                                            Spacer(Modifier.height(10.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    "Tap the line you hear now",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = TextPrimary,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                TextButton(onClick = { captureCues = null }) {
                                                    Text(
                                                        "Cancel",
                                                        color = TextSecondary,
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                            }
                                            cues.forEach { (startMs, cueText) ->
                                                Row(
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(Color.White.copy(alpha = 0.06f))
                                                        .clickable {
                                                            // Delay from the CAPTURED
                                                            // position minus the cue's
                                                            // start, with the 300ms
                                                            // reaction-time compensation.
                                                            setSubtitleDelay(
                                                                capturePositionMs - startMs - 300L -
                                                                    settings.subtitleOffsetMs
                                                            )
                                                            pokeIndicator(
                                                                PlayerIndicator.Message(
                                                                    "Subtitles synced"
                                                                )
                                                            )
                                                        }
                                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        String.format(
                                                            java.util.Locale.US,
                                                            "%d:%02d",
                                                            startMs / 60000,
                                                            (startMs / 1000) % 60
                                                        ),
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = LocalPalette.current.accent
                                                    )
                                                    Spacer(Modifier.width(10.dp))
                                                    Text(
                                                        cueText,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = TextPrimary,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Spacer(Modifier.height(6.dp))
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    // 9. Reset Defaults
                                    TextButton(
                                        onClick = {
                                            setSubtitleDelay(0L)
                                            subFontSpUi = 18
                                            settings.subtitleFontSizeSp = 18
                                            subBoldUi = false
                                            settings.subtitleBold = false
                                            subColorUi = 0xFFFFFFFF.toInt()
                                            settings.subtitleTextColor = 0xFFFFFFFF.toInt()
                                            settings.subtitleBackgroundColor =
                                                android.graphics.Color.TRANSPARENT
                                            subEdgeUi = 1
                                            settings.subtitleEdgeType = 1
                                            subEdgeColorUi = 0xFF000000.toInt()
                                            settings.subtitleEdgeColor = 0xFF000000.toInt()
                                            subBottomUi = 20
                                            settings.subtitleBottomOffset = 20
                                            playerView?.let { applySubtitleStyle(it) }
                                        }
                                    ) {
                                        Text(
                                            "Reset defaults",
                                            color = TextSecondary,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
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
                                OrbitDots(modifier = Modifier.size(30.dp))
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
                                    .tvFocusRing(RoundedCornerShape(10.dp))
                                    .then(
                                        if (index == 0) Modifier.focusRequester(firstResultFocus)
                                        else Modifier
                                    )
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
            }
        }

        // In-player Sources (Design I): full-width banner + tabs + cards.
        if (sourcesOpen) {
            ModalBottomSheet(
                onDismissRequest = { sourcesOpen = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                dragHandle = null,
                containerColor = Color(0xFF14141F)
            ) {
                com.pulsestream.app.ui.screens.player.PlayerSourcesContent(
                    title = currentRequest.title,
                    streams = availableStreams,
                    selectedUrl = currentRequest.streamUrl,
                    loading = streamsLoading,
                    onPick = { switchSource(it) },
                    onDownload = { downloadStream = it },
                    onRetry = { refreshStreamsBackground(currentRequest.type, currentRequest.videoId) },
                    onClose = { sourcesOpen = false },
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f),
                    settings = repository.settings,
                    poster = currentRequest.poster
                )
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

        // TEST: YouTube quality picker dialog.
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
                Column(Modifier.tvSheetFocus()) {
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
}


/**
 * One row of the subtitle overlay's Languages rail (v0.14.5): the
 * selected language is a full WHITE pill with dark text and the track
 * count sits in a circle (the v0.14.3 reference look).
 */
@Composable
private fun RailLanguageRow(
    name: String,
    count: Int?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color.White else Color.Transparent)
            .clickable { onClick() }
            .tvFocusRing(RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.Black else TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (count != null) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) Color.Black else Color.White),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$count",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) Color.White else Color.Black
                )
            }
        }
    }
}

/** Small white section label above a Style-rail control (v0.14.5). */
@Composable
private fun StyleSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = TextPrimary
    )
}

/** One 40dp rounded square stepper button (white 8% background). */
@Composable
private fun StyleStepperButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable { onClick() }
            .tvFocusRing(RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = TextPrimary,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * The Style rail's only numeric control (v0.14.5): [−] value [+] —
 * 40dp rounded buttons flanking a value box (white 6% background,
 * min-width 84dp). No sliders anywhere in the rail.
 */
@Composable
private fun StyleStepper(
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        StyleStepperButton(Icons.Filled.Remove, "Decrease", onMinus)
        Box(
            Modifier
                .widthIn(min = 84.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
        }
        StyleStepperButton(Icons.Filled.Add, "Increase", onPlus)
    }
}

/** On/Off chip for the Style rail: accent background when On. */
@Composable
private fun StyleChip(
    label: String,
    on: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalPalette.current
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (on) palette.accent else Color.White.copy(alpha = 0.08f))
            .clickable { onClick() }
            .tvFocusRing(RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (on) palette.accentText else TextPrimary
        )
    }
}

/** 32dp circular color swatch; the selected one gets a 2dp white border. */
@Composable
private fun StyleSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) Color.White else Color.White.copy(alpha = 0.25f),
                CircleShape
            )
            .clickable { onClick() }
            .tvFocusRing(CircleShape)
    )
}

/**
 * One subtitle track card in the player's subtitle panel (v0.14.2):
 * source chip, track name, language/format line, accent check when
 * selected — flat Onyx card like the rest of the app.
 */
@Composable
private fun PanelSubCard(
    chip: String,
    title: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color.White else Color(0xFF1B1B1D))
            .border(
                1.dp,
                if (selected) Color.White else Color.White.copy(alpha = 0.09f),
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (selected) Color.Black.copy(alpha = 0.08f)
                        else palette.accent.copy(alpha = 0.18f)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    chip,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) Color(0xFF111111) else palette.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (selected) Color(0xFF111111) else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (detail.isNotBlank()) {
                Text(
                    detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) Color(0xFF555555) else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Filled.Check,
                contentDescription = "Selected",
                tint = if (selected) Color.Black else palette.accent,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
