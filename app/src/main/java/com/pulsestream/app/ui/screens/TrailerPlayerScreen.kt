package com.pulsestream.app.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.pulsestream.app.data.PlaybackRequest
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * YouTube-style trailer player: portrait layout with the 16:9 video on top
 * and details below, plus a fullscreen toggle. Trailers never touch watch
 * history, scrobblers or auto-play-next — none of that wiring exists here.
 *
 * When [externalPlayer] is supplied (mini floating player), it is reused and
 * never released here — the [TrailerMiniController] owns it.
 *
 * [request] overrides `repository.pendingPlayback` (used by the app-level
 * floating overlay). [onSwipeDown], when supplied, is invoked on a downward
 * swipe over the video — the overlay uses it to collapse into the mini
 * player.
 */
@Composable
fun TrailerPlayerScreen(
    repository: StreamRepository,
    onBack: () -> Unit,
    externalPlayer: ExoPlayer? = null,
    request: PlaybackRequest? = null,
    onSwipeDown: (() -> Unit)? = null
) {
    val req = request ?: repository.pendingPlayback
    if (req == null || !req.isTrailer) {
        // Nothing to play — bail out rather than showing a dead screen.
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val context = LocalContext.current
    val activity = context as? Activity
    val palette = LocalPalette.current

    val player = externalPlayer ?: remember(req.streamUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(req.streamUrl))
            playWhenReady = true
            prepare()
        }
    }
    val ownsPlayer = externalPlayer == null

    var isFullscreen by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    // Initialize from the player's ACTUAL state: with the shared mini-player
    // the player may already be READY/playing when this listener attaches, in
    // which case no state transition ever fires and a hardcoded `true` would
    // leave the spinner running forever over a playing video.
    var isBuffering by remember {
        mutableStateOf(player.playbackState == Player.STATE_BUFFERING)
    }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }

    // Swipe-down-to-minimize: drag offset applied to the video box only.
    val swipeScope = rememberCoroutineScope()
    val swipeOffsetY = remember { Animatable(0f) }
    val swipeEnabled = onSwipeDown != null

    // System back minimizes to the floating player (YouTube behavior).
    BackHandler(onBack = onBack)

    // Portrait on entry (YouTube-style); restore whatever the app had on exit.
    DisposableEffect(Unit) {
        val prev = activity?.requestedOrientation
            ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            if (ownsPlayer) player.release()
            activity?.requestedOrientation = prev
        }
    }

    // Fullscreen: landscape + immersive; back to portrait when toggled off.
    LaunchedEffect(isFullscreen) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (isFullscreen) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Player state polling (lightweight — trailers are short).
    LaunchedEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_ENDED) {
                    // Stop at the end — no auto-play-next for trailers.
                    player.seekTo(0)
                    player.playWhenReady = false
                }
            }
        }
        player.addListener(listener)
        try {
            while (true) {
                positionMs = player.currentPosition
                val d = player.duration
                durationMs = if (d > 0) d else 0L
                delay(500)
            }
        } finally {
            player.removeListener(listener)
        }
    }

    // Auto-hide controls.
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(3000)
            showControls = false
        }
    }

    fun fmt(ms: Long): String {
        if (ms <= 0) return "0:00"
        val s = TimeUnit.MILLISECONDS.toSeconds(ms)
        return "%d:%02d".format(s / 60, s % 60)
    }

    GlowBackground {
        if (isFullscreen) {
            // Fullscreen: video fills everything, controls overlay.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { showControls = !showControls }
            ) {
                TrailerVideoView(player, Modifier.fillMaxSize())
                if (showControls) {
                    TrailerControls(
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        fmt = ::fmt,
                        onPlayPause = {
                            if (isPlaying) player.pause() else player.play()
                        },
                        onSeek = { player.seekTo(it) },
                        isFullscreen = true,
                        onFullscreen = { isFullscreen = false },
                        onClose = onBack,
                        accent = palette.accent
                    )
                }
            }
        } else {
            // Portrait: 16:9 video on top, details below.
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                        .offset { IntOffset(0, swipeOffsetY.value.roundToInt()) }
                        .then(
                            if (swipeEnabled && !isFullscreen) {
                                Modifier.pointerInput(onSwipeDown) {
                                    detectVerticalDragGestures(
                                        onDragEnd = {
                                            swipeScope.launch {
                                                if (swipeOffsetY.value > 200f) {
                                                    // Past the threshold: collapse into the mini
                                                    // player. The offset is left as-is — the
                                                    // overlay's exit animation continues the
                                                    // downward motion, and state resets on
                                                    // the next open.
                                                    onSwipeDown?.invoke()
                                                } else {
                                                    swipeOffsetY.animateTo(
                                                        0f,
                                                        tween(250, easing = FastOutSlowInEasing)
                                                    )
                                                }
                                            }
                                        },
                                        onVerticalDrag = { _, dragAmount ->
                                            swipeScope.launch {
                                                swipeOffsetY.snapTo(
                                                    (swipeOffsetY.value + dragAmount)
                                                        .coerceIn(0f, 700f)
                                                )
                                            }
                                        }
                                    )
                                }
                            } else Modifier
                        )
                        .clickable { showControls = !showControls }
                ) {
                    TrailerVideoView(player, Modifier.fillMaxSize())
                    if (showControls) {
                        TrailerControls(
                            isPlaying = isPlaying,
                            isBuffering = isBuffering,
                            positionMs = positionMs,
                            durationMs = durationMs,
                            fmt = ::fmt,
                            onPlayPause = {
                                if (isPlaying) player.pause() else player.play()
                            },
                            onSeek = { player.seekTo(it) },
                            isFullscreen = false,
                            onFullscreen = { isFullscreen = true },
                            onClose = onBack,
                            accent = palette.accent
                        )
                    }
                }
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = req.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(6.dp))
                    val sub = buildString {
                        append(if (req.isMovie) "Movie" else "Series")
                        append(" · Trailer")
                    }
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    req.streamLabel?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.accent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    // Full title details (TMDB via PlavioMeta) under the player.
                    if (req.metaId.isNotBlank()) {
                        Spacer(Modifier.height(16.dp))
                        TrailerMetaDetails(
                            repository = repository,
                            type = req.type,
                            metaId = req.metaId
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrailerVideoView(player: ExoPlayer, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).also { pv ->
                pv.player = player
                pv.useController = false
                pv.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                pv.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { pv -> pv.player = player },
        modifier = modifier
    )
}

/**
 * Full title details under the portrait trailer player, fetched from
 * PlavioMeta (same pipeline as the detail screens). Shows poster, title,
 * year/rating/type, genres and overview. Note: the meta model carries no
 * cast list, so cast is omitted.
 */
@Composable
private fun TrailerMetaDetails(
    repository: StreamRepository,
    type: String,
    metaId: String
) {
    var meta by remember(metaId) { mutableStateOf<com.pulsestream.app.data.MetaDetail?>(null) }
    var loading by remember(metaId) { mutableStateOf(true) }
    LaunchedEffect(metaId) {
        loading = true
        meta = runCatching {
            repository.getMetaFromAny(null, type, metaId)
        }.getOrNull()
        loading = false
    }
    if (loading) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = LocalPalette.current.accent,
                modifier = Modifier.size(28.dp),
                strokeWidth = 3.dp
            )
        }
        return
    }
    val m = meta ?: return
    Column(
        Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            com.pulsestream.app.ui.components.SafePosterImage(
                model = m.poster,
                contentDescription = m.name,
                modifier = Modifier
                    .width(72.dp)
                    .height(108.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = m.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                val info = listOfNotNull(
                    m.releaseInfo?.take(4),
                    m.imdbRating?.let { "★ $it" },
                    m.type.replaceFirstChar { it.uppercase() }
                ).joinToString(" · ")
                if (info.isNotBlank()) {
                    Text(
                        text = info,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                if (m.genres.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = m.genres.take(4).joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }
        if (!m.description.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = m.description!!,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun TrailerControls(
    isPlaying: Boolean,
    isBuffering: Boolean,
    positionMs: Long,
    durationMs: Long,
    fmt: (Long) -> String,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    isFullscreen: Boolean,
    onFullscreen: () -> Unit,
    onClose: () -> Unit,
    accent: Color
) {
    Box(Modifier.fillMaxSize()) {
        // Close button (top-left).
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
        }
        // Center play/pause (or buffering spinner).
        Box(Modifier.align(Alignment.Center)) {
            if (isBuffering) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(48.dp))
            } else {
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
        // Bottom bar: seek + time + fullscreen.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = fmt(positionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.width(44.dp)
                )
                Slider(
                    value = positionMs.toFloat(),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = accent,
                        activeTrackColor = accent,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    )
                )
                Text(
                    text = fmt(durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.width(44.dp)
                )
                IconButton(onClick = onFullscreen, modifier = Modifier.size(40.dp)) {
                    Icon(
                        if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                        contentDescription = if (isFullscreen) "Exit fullscreen" else "Fullscreen",
                        tint = Color.White
                    )
                }
            }
        }
    }
}
