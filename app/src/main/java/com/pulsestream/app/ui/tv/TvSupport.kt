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
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
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
/** Low-RAM / old-Android TV boxes: skip scale animation, keep the ring. */
@Composable
fun rememberIsLowEndTv(): Boolean {
    val ctx = LocalContext.current
    return remember {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        am?.isLowRamDevice == true ||
            android.os.Build.VERSION.SDK_INT <= 28 ||
            (am?.memoryClass ?: 256) <= 192
    }
}

/** Draws an [Outline] on this [DrawScope], dispatching on the concrete outline type. */
private fun DrawScope.drawOutlineCompat(outline: Outline, color: Color, style: DrawStyle = Fill) {
    when (outline) {
        is Outline.Rectangle -> drawRect(color = color, topLeft = outline.rect.topLeft, size = outline.rect.size, style = style)
        is Outline.Rounded -> {
            val rr = outline.roundRect
            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(rr.left, rr.top),
                size = androidx.compose.ui.geometry.Size(rr.width, rr.height),
                cornerRadius = rr.topLeftCornerRadius,
                style = style
            )
        }
        is Outline.Generic -> drawPath(path = outline.path, color = color, style = style)
    }
}

@Composable
fun Modifier.tvFocusRing(
    shape: Shape = RoundedCornerShape(16.dp),
    focusable: Boolean = false
): Modifier {
    if (!rememberIsTv()) return this
    val lowEnd = rememberIsLowEndTv()
    // Focus is held in a State and only READ in the draw / layer phases, so a
    // D-pad move redraws two nodes instead of recomposing the whole screen
    // (the old version read it in composition = lag on weak TVs).
    val focused = remember { mutableStateOf(false) }
    val scale = remember { androidx.compose.animation.core.Animatable(1f) }
    androidx.compose.runtime.LaunchedEffect(focused.value, lowEnd) {
        if (!lowEnd) scale.animateTo(
            if (focused.value) 1.08f else 1f,
            tween(durationMillis = 100, easing = FastOutSlowInEasing)
        )
    }
    // RULE (v0.8.37-tv): onFocusChanged only sees focus targets that come AFTER
    // it in the modifier chain. So tvFocusRing MUST be written BEFORE
    // .clickable()/.focusable()/.combinedClickable() (to their left):
    //     Modifier.tvFocusRing(shape).clickable { ... }      // correct
    //     Modifier.clickable { ... }.tvFocusRing(shape)      // ring never lights
    // Material Button/IconButton/FilterChip wrappers are fine either way.
    return this
        .onFocusChanged { focused.value = it.isFocused || it.hasFocus }
        .then(if (focusable) Modifier.focusable() else Modifier)
        .padding(6.dp)
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .drawWithContent {
            if (focused.value) {
                val outline = shape.createOutline(size, layoutDirection, this)
                drawOutlineCompat(outline, TvFocusGreen.copy(alpha = 0.28f))
            }
            drawContent()
            if (focused.value) {
                val outline = shape.createOutline(size, layoutDirection, this)
                drawOutlineCompat(outline, TvFocusGreen, style = Stroke(width = 6.dp.toPx()))
            }
        }
}

/**
 * D-pad control for the video player (TV only; no-op on phones). Same shape
 * as Nuvio TV's player: the root container owns focus, Back/Escape are taken
 * in the PREVIEW phase, transport keys in the normal (bubbling) phase so a
 * focused overlay button still gets its own keys first.
 *
 * Holding Left/Right accelerates the seek (10s -> 30s -> 60s). Pass
 * enabled = false while a track/source panel or dialog is open so the player
 * stops stealing keys from it.
 */
@Composable
fun Modifier.tvPlayerKeys(
    onTogglePlay: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onVolumeBy: (Int) -> Unit,
    onShowControls: () -> Unit,
    onBack: () -> Unit,
    enabled: Boolean = true
): Modifier {
    if (!rememberIsTv()) return this
    val requester = remember { FocusRequester() }
    LaunchedEffect(enabled) { if (enabled) runCatching { requester.requestFocus() } }
    androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)
    return this
        .focusRequester(requester)
        .onPreviewKeyEvent { e ->
            if (!enabled) return@onPreviewKeyEvent false
            if (e.key == Key.Back || e.key == Key.Escape) {
                if (e.type == KeyEventType.KeyUp) onBack()
                true
            } else false
        }
        .onKeyEvent { e ->
            if (!enabled) return@onKeyEvent false
            val down = e.type == KeyEventType.KeyDown
            val repeats = e.nativeKeyEvent.repeatCount
            val step = when {
                repeats > 12 -> 60_000L
                repeats > 4 -> 30_000L
                else -> 10_000L
            }
            when (e.key) {
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter,
                Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                    if (down && repeats == 0) { onTogglePlay(); onShowControls() }
                    true
                }
                Key.DirectionLeft -> { if (down) { onSeekBy(-step); onShowControls() }; true }
                Key.DirectionRight -> { if (down) { onSeekBy(step); onShowControls() }; true }
                Key.MediaRewind -> { if (down) onSeekBy(-30_000L); true }
                Key.MediaFastForward -> { if (down) onSeekBy(30_000L); true }
                Key.DirectionUp -> { if (down) { onVolumeBy(1); onShowControls() }; true }
                Key.DirectionDown -> { if (down) { onVolumeBy(-1); onShowControls() }; true }
                else -> false
            }
        }
        .focusable()
}

/**
 * For ModalBottomSheet/dialog CONTENT on TV: wrap the content in a Column with
 * this modifier. It grabs D-pad focus into the sheet shortly after it opens
 * (retrying while the rows compose), so the remote drives the sheet instead of
 * the screen behind it. Focus moves to the first focusable row.
 */
@Composable
fun Modifier.tvSheetFocus(): Modifier {
    if (!rememberIsTv()) return this
    val requester = remember { FocusRequester() }
    var inside by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        repeat(8) {
            kotlinx.coroutines.delay(if (it == 0) 600L else 250L)
            // A row already took focus (e.g. a screen's own first-item request)? Leave it.
            if (inside) return@LaunchedEffect
            if (runCatching { requester.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
    return this
        .onFocusChanged { inside = it.hasFocus }
        .focusRequester(requester)
        .focusGroup()
}

/**
 * Modal focus for in-composition popups (e.g. the "Manual source" picker).
 *
 * 1) Wrap the SCREEN BEHIND the popup with `.tvFocusBlocked(popupOpen)` so the
 *    D-pad can no longer reach it while the popup is up.
 * 2) Put `.tvModalFocus(popupOpen)` on the popup container: it grabs focus on
 *    open and traps Up/Down/Left/Right inside it (focus never leaks out).
 * When popupOpen goes false the block lifts; call your previous item's
 * FocusRequester.requestFocus() in the popup's onDismiss to restore focus.
 */
fun Modifier.tvFocusBlocked(blocked: Boolean): Modifier =
    if (blocked) this.focusProperties { canFocus = false } else this

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.tvModalFocus(open: Boolean = true): Modifier {
    if (!rememberIsTv()) return this
    val requester = remember { FocusRequester() }
    LaunchedEffect(open) { if (open) runCatching { requester.requestFocus() } }
    return this
        .focusRequester(requester)
        .focusProperties {
            // Keep the first focus landing on a child, and keep arrows inside.
            exit = { FocusRequester.Cancel }
        }
        .focusGroup()
}

/** Vivid green used for TV focus states — readable from the couch. */
val TvFocusGreen = Color(0xFF4ADE80)
