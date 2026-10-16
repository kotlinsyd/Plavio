package com.pulsestream.app.ui.tv

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import com.pulsestream.app.BuildConfig

/**
 * True on real Android TV / Google TV devices — some cheap TV boxes report
 * UI_MODE_TYPE_NORMAL, so the tv product flavor ALSO counts: if the user
 * installed the TV APK, they get TV behavior regardless of what the box
 * claims. (This was the v0.8.29 invisible-focus bug: the rail used its own
 * focus tracking and lit up green, while every tvFocusRing() card silently
 * no-op'd because isTelevision() returned false.)
 */
fun Context.isTelevision(): Boolean {
    if (BuildConfig.FLAVOR == "tv") return true
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
 * The 6dp breathing room is reserved PERMANENTLY (not toggled on focus):
 * toggling padding on every focus change forced a full remeasure/re-layout
 * on the UI thread per D-pad press = the v0.10.x "lag with every click".
 * The focus pop uses a fast 120ms tween (not the loose default spring) so
 * rapid D-pad presses never pile up unfinished animations.
 */
@Composable
fun Modifier.tvFocusRing(shape: Shape = RoundedCornerShape(16.dp)): Modifier {
    if (!rememberIsTv()) return this
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.1f else 1f,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "tvFocusScale"
    )
    return this
        .onFocusChanged { focused = it.isFocused }
        .padding(6.dp)
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
