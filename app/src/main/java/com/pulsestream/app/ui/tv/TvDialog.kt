package com.pulsestream.app.ui.tv

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pulsestream.app.ui.theme.GlassBorder
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextSecondary

/**
 * Remote-friendly popup, built the way Nuvio TV does it: a real Compose
 * [Dialog] (its own window, so the D-pad can NEVER reach the screen behind)
 * that pulls focus onto its first item on open and swallows the first
 * stray OK key-up (the one from the button that opened it). When it closes,
 * Android returns focus to the control that opened it.
 *
 * Use for the "Manual source" picker instead of an in-composition overlay.
 * Put [Modifier.tvFocusRing] on clickable rows, e.g. with [TvDialogItem].
 */
@Composable
fun TvDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    var ready by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    val palette = LocalPalette.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = palette.panel,
            border = BorderStroke(1.dp, GlassBorder),
            modifier = Modifier
                .width(560.dp)
                .heightIn(max = 520.dp)
                .onPreviewKeyEvent { e ->
                    val k = e.nativeKeyEvent
                    val select = k.keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                        k.keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
                        k.keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
                    if (select) {
                        if (k.action == AndroidKeyEvent.ACTION_DOWN && k.repeatCount == 0) ready = true
                        !ready
                    } else false
                }
        ) {
            Column(
                modifier = Modifier.padding(24.dp).focusRequester(first),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = TextSecondary)
                content()
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

/** A focusable, highlighted row for [TvDialog]. */
@Composable
fun TvDialogItem(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = LocalPalette.current.accent,
        modifier = modifier
            .fillMaxWidth()
            .tvFocusRing(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}
