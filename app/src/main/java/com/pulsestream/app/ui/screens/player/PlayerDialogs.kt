package com.pulsestream.app.ui.screens.player

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary

/** Resume-from-saved-position prompt. Callbacks cover dismiss/confirm/start-over. */
@Composable
internal fun ResumePromptDialog(
    savedMs: Long,
    title: String,
    onResume: () -> Unit,
    onStartOver: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            // Tapping outside keeps their place and resumes.
            onResume()
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
                "Resume \"${title}\" from ${formatMs(savedMs)}?",
                color = TextSecondary
            )
        },
        confirmButton = {
            TextButton(onClick = onResume) {
                Text("Resume", color = LocalPalette.current.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onStartOver) {
                Text("Start over", color = TextSecondary)
            }
        }
    )
}

/** Speed pill + dropdown menu for the bottom toolbar. */
@Composable
internal fun SpeedMenuButton(
    speed: Float,
    menuOpen: Boolean,
    onOpenMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onSpeedSelected: (Float) -> Unit
) {
    Box {
        PillBarButton(
            icon = Icons.Filled.Speed,
            label = speedLabel(speed),
            onClick = onOpenMenu
        )
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = onDismissMenu
        ) {
            SPEEDS.forEach { s ->
                DropdownMenuItem(
                    text = { Text(speedLabel(s)) },
                    onClick = { onSpeedSelected(s) }
                )
            }
        }
    }
}
