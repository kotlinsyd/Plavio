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
import androidx.compose.material.icons.filled.Audiotrack
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
import com.pulsestream.app.data.audioLanguageName
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary

/**
 * Settings > Audio: the preferred audio language. When a title's audio
 * tracks include it, the player selects it automatically at playback start.
 */
@Composable
fun AudioSettingsScreen(
    repository: StreamRepository,
    onBack: () -> Unit = {}
) {
    val settings = repository.settings
    var audioLang by remember { mutableStateOf(settings.preferredAudioLanguage) }
    var audioLangPickerOpen by remember { mutableStateOf(false) }

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
                            text = "Audio",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Which audio track the player picks automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 56.dp, end = 8.dp)
                    )
                }
            }
            item(key = "audioLang") {
                SettingsNavRow(
                    icon = Icons.Filled.Audiotrack,
                    title = "Preferred audio language",
                    subtitle = if (audioLang.isNullOrBlank()) {
                        "Not set — title's default audio is used"
                    } else {
                        "Auto-plays ${audioLanguageName(audioLang!!)} when available"
                    },
                    onClick = { audioLangPickerOpen = true }
                )
            }
        }
    }

    if (audioLangPickerOpen) {
        AudioLanguagePickerDialog(
            initial = audioLang,
            onDismiss = { audioLangPickerOpen = false },
            onDone = { lang ->
                settings.preferredAudioLanguage = lang
                audioLang = lang
                audioLangPickerOpen = false
            }
        )
    }
}
