package com.pulsestream.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary

@Composable
fun PlaybackSettingsScreen(
    repository: StreamRepository,
    onBack: () -> Unit = {}
) {
    val settings = repository.settings
    var autoplay by remember { mutableStateOf(settings.autoplay) }
    var keepScreenOn by remember { mutableStateOf(settings.keepScreenOn) }
    var showSkip by remember { mutableStateOf(settings.showSkipButton) }

    GlowBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item(key = "header") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Playback",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "How videos start, stay awake and skip ahead.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 56.dp, end = 8.dp)
                    )
                }
            }
            item(key = "autoplay") {
                SettingsToggleRow(
                    icon = Icons.Filled.PlayArrow,
                    title = "Autoplay",
                    subtitle = "Play the first source automatically",
                    checked = autoplay,
                    onCheckedChange = {
                        autoplay = it
                        settings.autoplay = it
                    }
                )
            }
            item(key = "keepScreenOn") {
                SettingsToggleRow(
                    icon = Icons.Filled.BrightnessHigh,
                    title = "Keep screen on",
                    subtitle = "Prevent the screen from sleeping while playing",
                    checked = keepScreenOn,
                    onCheckedChange = {
                        keepScreenOn = it
                        settings.keepScreenOn = it
                    }
                )
            }
            item(key = "showSkip") {
                SettingsToggleRow(
                    icon = Icons.Filled.FastForward,
                    title = "Skip intro button",
                    subtitle = "Show a skip button for intros, recaps and outros",
                    checked = showSkip,
                    onCheckedChange = {
                        showSkip = it
                        settings.showSkipButton = it
                    }
                )
            }
        }
    }
}
