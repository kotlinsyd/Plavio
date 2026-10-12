package com.pulsestream.app.ui.tv

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** True when running on an Android TV / Google TV device (or the tv product flavor). */
fun Context.isTelevision(): Boolean {
    val uiMode = (getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)
        ?.currentModeType ?: resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
    return uiMode == Configuration.UI_MODE_TYPE_TELEVISION
}

@Composable
fun rememberIsTv(): Boolean {
    val ctx = LocalContext.current
    return remember { ctx.isTelevision() }
}

/**
 * On TV devices, makes D-pad focus UNMISSABLE: the focused component pops
 * (scales up clearly) and gets a thick bright green ring + strong green tint.
 * No-op on phones/tablets so mobile UI is untouched.
 *
 * The ring, tint AND scale all live inside the same graphicsLayer, so the
 * scaled-up card art can never cover the ring (the v0.8.21-tv bug: the ring
 * was drawn at the unscaled bounds and the 1.08x poster hid it completely).
 * Focused cards also get breathing-room padding so the ring clears neighbours.
 */
@Composable
fun Modifier.tvFocusRing(shape: Shape = RoundedCornerShape(16.dp)): Modifier {
    if (!rememberIsTv()) return this
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.1f else 1f,
        label = "tvFocusScale"
    )
    return this
        .onFocusChanged { focused = it.isFocused }
        .then(if (focused) Modifier.padding(6.dp) else Modifier)
        .then(
            if (focused) Modifier
                .border(BorderStroke(8.dp, TvFocusGreen), shape)
                .background(TvFocusGreen.copy(alpha = 0.28f), shape)
            else Modifier
        )
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
}

/** Vivid green used for TV focus states — readable from the couch. */
val TvFocusGreen = Color(0xFF4ADE80)
