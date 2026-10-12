package com.pulsestream.app.ui.components

import android.content.Context
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.pulsestream.app.data.PlaybackRequest
import com.pulsestream.app.ui.theme.GlassBorder

/**
 * App-level mini trailer player (YouTube-style): one trailer at a time,
 * floats above all tabs. The ExoPlayer instance is shared with the full
 * [TrailerPlayerScreen] so expanding never restarts playback.
 */
class TrailerMiniController {
    var request by mutableStateOf<PlaybackRequest?>(null)
        private set
    var minimized by mutableStateOf(false)
        private set
    var player: ExoPlayer? = null
        private set

    val isActive: Boolean get() = request != null

    fun play(context: Context, req: PlaybackRequest) {
        stop()
        request = req
        player = ExoPlayer.Builder(context.applicationContext).build().apply {
            setMediaItem(MediaItem.fromUri(req.streamUrl))
            playWhenReady = true
            prepare()
        }
        minimized = true
    }

    fun expand() {
        if (request != null) minimized = false
    }

    fun minimize() {
        if (request != null) minimized = true
    }

    fun stop() {
        player?.release()
        player = null
        request = null
        minimized = false
    }
}

/** Floating mini player overlay — bottom-right, above the tab dock. */
@Composable
fun MiniTrailerOverlay(
    controller: TrailerMiniController,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val req = controller.request
    val player = controller.player
    if (!controller.minimized || req == null || player == null) return

    var isPlaying by remember(player) { mutableStateOf(player.isPlaying) }
    var isBuffering by remember(player) { mutableStateOf(true) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_ENDED) {
                    player.seekTo(0)
                    player.playWhenReady = false
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(end = 16.dp, bottom = 108.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        Box(
            modifier = Modifier
                .width(200.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
                .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                .clickable(onClick = onExpand)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).also { pv ->
                        pv.player = player
                        pv.useController = false
                        pv.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        pv.layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { pv -> pv.player = player },
                modifier = Modifier.fillMaxSize()
            )
            // Close (top-end).
            IconButton(
                onClick = { controller.stop() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .size(28.dp)
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Close trailer",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
            // Center play/pause.
            IconButton(
                onClick = { if (isPlaying) player.pause() else player.play() },
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .size(44.dp)
            ) {
                if (isBuffering) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
