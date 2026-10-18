package com.pulsestream.app.ui.screens.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import com.pulsestream.app.ui.components.liquidGlass
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import com.pulsestream.app.ui.tv.tvFocusRing

@Composable
internal fun TrackRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .tvFocusRing(RoundedCornerShape(10.dp))
            .padding(vertical = 8.dp, horizontal = 4.dp),
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
internal fun SlimSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isTv: Boolean = false,
    onSeekDelta: ((Long) -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null
) {
    var dragFrac by remember { mutableStateOf<Float?>(null) }
    val frac = (dragFrac ?: if (durationMs > 0) positionMs.toFloat() / durationMs else 0f)
        .coerceIn(0f, 1f)
    val density = LocalDensity.current
    val progressColor = LocalPalette.current.accent
    // TV: the seekbar is a D-pad stop with the green ring; Left/Right seeks
    // ±10s while focused, Up/Down moves to the other control rows.
    val tvModifier = if (isTv) {
        Modifier
            .focusable()
            .onFocusChanged { onFocusChanged?.invoke(it.isFocused) }
            .tvFocusRing(RoundedCornerShape(8.dp))
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { onSeekDelta?.invoke(-10_000); true }
                    Key.DirectionRight -> { onSeekDelta?.invoke(10_000); true }
                    else -> false
                }
            }
    } else Modifier
    Canvas(
        modifier
            .fillMaxWidth()
            .height(30.dp)
            .then(tvModifier)
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
internal fun TimeChip(text: String) {
    Box(
        modifier = Modifier.liquidGlass(RoundedCornerShape(50))
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
internal fun PillBarButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .clickable(onClick = onClick)
            .tvFocusRing(RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
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
