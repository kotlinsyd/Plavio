package com.pulsestream.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulsestream.app.data.AUTOPLAY_QUALITY_OPTIONS
import com.pulsestream.app.data.AUTOPLAY_SIZE_OPTIONS
import com.pulsestream.app.data.QUALITY_ORDER_OPTIONS
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.data.autoplaySizeName
import com.pulsestream.app.ui.components.GlassCard
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import com.pulsestream.app.ui.tv.tvFocusRing

/**
 * Settings > Streams (v0.8.31+): how the stream picker lists sources and
 * how the Play button auto-picks a stream.
 */
@Composable
fun StreamsSettingsScreen(
    repository: StreamRepository,
    onBack: () -> Unit = {}
) {
    val settings = repository.settings
    var perSource by remember { mutableStateOf(settings.streamsPerSource) }
    var qualityOrder by remember { mutableStateOf(settings.qualityOrder) }
    var autoplay by remember { mutableStateOf(settings.streamAutoplay) }
    var autoplayQuality by remember { mutableStateOf(settings.autoplayQuality) }
    var autoplayMaxSize by remember { mutableStateOf(settings.autoplayMaxSizeGb) }
    var qualityPickerOpen by remember { mutableStateOf(false) }
    var sizePickerOpen by remember { mutableStateOf(false) }

    if (qualityPickerOpen) {
        SingleSelectDialog(
            title = "Preferred quality",
            options = AUTOPLAY_QUALITY_OPTIONS,
            selected = autoplayQuality,
            onDismiss = { qualityPickerOpen = false },
            onSelect = {
                autoplayQuality = it
                settings.autoplayQuality = it
                qualityPickerOpen = false
            }
        )
    }
    if (sizePickerOpen) {
        SingleSelectDialog(
            title = "Max file size",
            options = AUTOPLAY_SIZE_OPTIONS.map { it.second },
            selected = autoplaySizeName(autoplayMaxSize),
            onDismiss = { sizePickerOpen = false },
            onSelect = { label ->
                val gb = AUTOPLAY_SIZE_OPTIONS.find { it.second == label }?.first ?: 0
                autoplayMaxSize = gb
                settings.autoplayMaxSizeGb = gb
                sizePickerOpen = false
            }
        )
    }

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
                            text = "Streams",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "How sources are listed, ordered and auto-picked.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 56.dp, end = 8.dp)
                    )
                }
            }

            item(key = "perSource") {
                GlassCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SettingsIconTile(Icons.Filled.FilterList, "Streams per source")
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Streams per source",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Max streams shown from one addon",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        IconButton(
                            onClick = {
                                perSource = (perSource - 1).coerceIn(1, 10)
                                settings.streamsPerSource = perSource
                            },
                            enabled = perSource > 1
                        ) {
                            Icon(Icons.Filled.Remove, "Fewer", tint = TextPrimary)
                        }
                        Text(
                            text = perSource.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = LocalPalette.current.accent,
                            modifier = Modifier.width(28.dp),
                        )
                        IconButton(
                            onClick = {
                                perSource = (perSource + 1).coerceIn(1, 10)
                                settings.streamsPerSource = perSource
                            },
                            enabled = perSource < 10
                        ) {
                            Icon(Icons.Filled.Add, "More", tint = TextPrimary)
                        }
                    }
                }
            }

            item(key = "qualityOrderHeader") {
                SettingsSectionHeader("Quality order")
            }
            itemsIndexed(qualityOrder, key = { _, q -> q }) { index, quality ->
                GlassCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            modifier = Modifier.width(28.dp)
                        )
                        Icon(
                            Icons.Filled.HighQuality,
                            contentDescription = null,
                            tint = LocalPalette.current.accent,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = quality,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                if (index > 0) {
                                    qualityOrder = qualityOrder.toMutableList().also {
                                        val t = it[index - 1]; it[index - 1] = it[index]; it[index] = t
                                    }
                                    settings.qualityOrder = qualityOrder
                                }
                            },
                            enabled = index > 0
                        ) {
                            Icon(Icons.Filled.ArrowUpward, "Move up", tint = TextPrimary)
                        }
                        IconButton(
                            onClick = {
                                if (index < qualityOrder.size - 1) {
                                    qualityOrder = qualityOrder.toMutableList().also {
                                        val t = it[index + 1]; it[index + 1] = it[index]; it[index] = t
                                    }
                                    settings.qualityOrder = qualityOrder
                                }
                            },
                            enabled = index < qualityOrder.size - 1
                        ) {
                            Icon(Icons.Filled.ArrowDownward, "Move down", tint = TextPrimary)
                        }
                    }
                }
            }
            item(key = "qualityOrderNote") {
                Text(
                    "Top = highest priority in the stream list.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }

            item(key = "autoplayHeader") {
                SettingsSectionHeader("Autoplay")
            }
            item(key = "autoplay") {
                SettingsToggleRow(
                    icon = Icons.Filled.PlayArrow,
                    title = "Autoplay by rule",
                    subtitle = "Play picks the stream matching your quality and size rule",
                    checked = autoplay,
                    onCheckedChange = {
                        autoplay = it
                        settings.streamAutoplay = it
                    }
                )
            }
            item(key = "autoplayQuality") {
                val enabled = autoplay
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 5.dp)
                        .alpha(if (enabled) 1f else 0.45f),
                    onClick = { if (enabled) qualityPickerOpen = true }
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SettingsIconTile(Icons.Filled.Sort, "Preferred quality")
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Preferred quality",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                if (autoplayQuality == "Auto") "Follow the quality order above"
                                else "Prefer $autoplayQuality streams",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Text(
                            autoplayQuality,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = LocalPalette.current.accent
                        )
                    }
                }
            }
            item(key = "autoplaySize") {
                val enabled = autoplay
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 5.dp)
                        .alpha(if (enabled) 1f else 0.45f),
                    onClick = { if (enabled) sizePickerOpen = true }
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SettingsIconTile(Icons.Filled.Storage, "Max file size")
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Max file size",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Skip streams bigger than this when auto-playing",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Text(
                            autoplaySizeName(autoplayMaxSize),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = LocalPalette.current.accent
                        )
                    }
                }
            }
            item(key = "autoplayNote") {
                Text(
                    "The source list button next to Play always opens the full stream list.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/** Single-select AlertDialog used by the Streams settings pickers. */
@Composable
private fun SingleSelectDialog(
    title: String,
    options: List<String>,
    selected: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = TextPrimary) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                itemsIndexed(options) { _, option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .tvFocusRing(RoundedCornerShape(10.dp))
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RadioButton(
                            selected = option == selected,
                            onClick = { onSelect(option) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = LocalPalette.current.accent
                            )
                        )
                        Text(option, color = TextPrimary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = LocalPalette.current.accent)
            }
        }
    )
}
