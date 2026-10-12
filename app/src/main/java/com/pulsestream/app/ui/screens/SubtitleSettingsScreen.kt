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
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.data.subtitleColorName
import com.pulsestream.app.data.subtitleEdgeName
import com.pulsestream.app.data.subtitleLanguageName
import com.pulsestream.app.data.subtitleSizeName
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary

/**
 * Settings > Subtitles: preferred subtitle languages (auto-selected in the
 * player when available) and subtitle appearance (color, size, background,
 * edge style, or the Android system caption settings).
 */
@Composable
fun SubtitleSettingsScreen(
    repository: StreamRepository,
    onBack: () -> Unit = {}
) {
    val settings = repository.settings
    var subLangs by remember { mutableStateOf(settings.subtitleLanguages) }
    var subLangPickerOpen by remember { mutableStateOf(false) }
    var subStyleOpen by remember { mutableStateOf(false) }
    var subStyleVersion by remember { mutableIntStateOf(0) }

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
                            text = "Subtitles",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Which subtitles turn on automatically, and how they look.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 56.dp, end = 8.dp)
                    )
                }
            }
            item(key = "subLangs") {
                SettingsNavRow(
                    icon = Icons.Filled.Translate,
                    title = "Preferred subtitle languages",
                    subtitle = if (subLangs.isEmpty()) {
                        "Not set — first available subtitle is used"
                    } else {
                        subLangs.joinToString(", ") { subtitleLanguageName(it) }
                    },
                    onClick = { subLangPickerOpen = true }
                )
            }
            item(key = "subStyle") {
                val styleSummary = remember(subStyleVersion) {
                    if (settings.useSystemCaptions) {
                        "System default (Android caption settings)"
                    } else {
                        "${subtitleColorName(settings.subtitleTextColor)} • " +
                            "${subtitleSizeName(settings.subtitleTextSizeScale)} • " +
                            subtitleEdgeName(settings.subtitleEdgeType)
                    }
                }
                SettingsNavRow(
                    icon = Icons.Filled.ClosedCaption,
                    title = "Subtitle appearance",
                    subtitle = styleSummary,
                    onClick = { subStyleOpen = true }
                )
            }
        }
    }

    if (subLangPickerOpen) {
        SubtitleLanguagePickerDialog(
            initial = subLangs,
            onDismiss = { subLangPickerOpen = false },
            onDone = { langs ->
                settings.subtitleLanguages = langs
                subLangs = langs
                subLangPickerOpen = false
            }
        )
    }

    if (subStyleOpen) {
        SubtitleStyleDialog(
            settings = settings,
            onDismiss = {
                subStyleOpen = false
                subStyleVersion++
            }
        )
    }
}
