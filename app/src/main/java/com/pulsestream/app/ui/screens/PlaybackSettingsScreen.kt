package com.pulsestream.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.unit.dp
import com.pulsestream.app.data.AUDIO_LANGUAGE_OPTIONS
import com.pulsestream.app.data.BUFFER_PRESETS
import com.pulsestream.app.data.DISK_CACHE_OPTIONS
import com.pulsestream.app.data.PlayerChoice
import com.pulsestream.app.data.SettingsStore
import com.pulsestream.app.data.audioLanguageName
import com.pulsestream.app.data.bufferPresetName
import com.pulsestream.app.data.diskCacheName
import com.pulsestream.app.data.SUBTITLE_BG_OPTIONS
import com.pulsestream.app.data.SUBTITLE_COLOR_OPTIONS
import com.pulsestream.app.data.SUBTITLE_EDGE_OPTIONS
import com.pulsestream.app.data.SUBTITLE_LANGUAGE_OPTIONS
import com.pulsestream.app.data.SUBTITLE_SIZE_OPTIONS
import com.pulsestream.app.data.subtitleBgName
import com.pulsestream.app.data.subtitleColorName
import com.pulsestream.app.data.subtitleEdgeName
import com.pulsestream.app.data.subtitleSizeName
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.tv.tvFocusRing
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary

@Composable
fun PlaybackSettingsScreen(
    repository: StreamRepository,
    onBack: () -> Unit = {}
) {
    val settings = repository.settings
    var autoplay by remember { mutableStateOf(settings.autoplay) }
    var autoPlayNext by remember { mutableStateOf(settings.autoPlayNext) }
    var keepScreenOn by remember { mutableStateOf(settings.keepScreenOn) }
    var showSkip by remember { mutableStateOf(settings.showSkipButton) }
    var pipEnabled by remember { mutableStateOf(settings.pipEnabled) }
    var audioLang by remember { mutableStateOf(settings.preferredAudioLanguage) }
    var audioLangPickerOpen by remember { mutableStateOf(false) }
    var bufferPresetOpen by remember { mutableStateOf(false) }
    var diskCacheOpen by remember { mutableStateOf(false) }
    var playerPickerOpen by remember { mutableStateOf(false) }
    // Refresh key so the preferred-player subtitle updates after the dialog closes.
    var playerUiTick by remember { mutableStateOf(0) }
    // Refresh key so the buffer/disk-cache subtitles update after the dialogs close.
    var bufferUiTick by remember { mutableStateOf(0) }

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
                        "How videos start, behave and sound while playing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 56.dp, end = 8.dp)
                    )
                }
            }
            item(key = "player_group") {
                // Read inside the item so recomposition picks up dialog changes.
                playerUiTick
                bufferUiTick
                SettingsGroup(
                    {
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
                    },
                    {
                        SettingsToggleRow(
                            icon = Icons.Filled.SkipNext,
                            title = "Auto-play next episode",
                            subtitle = "Start the next episode when one finishes",
                            checked = autoPlayNext,
                            onCheckedChange = {
                                autoPlayNext = it
                                settings.autoPlayNext = it
                            }
                        )
                    },
                    {
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
                    },
                    {
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
                    },
                    {
                        SettingsToggleRow(
                            icon = Icons.Filled.PictureInPicture,
                            title = "Picture-in-Picture",
                            subtitle = "Float the video when you leave the player",
                            checked = pipEnabled,
                            onCheckedChange = {
                                pipEnabled = it
                                settings.pipEnabled = it
                            }
                        )
                    },
                    {
                        SettingsNavRow(
                            icon = Icons.Filled.OpenInNew,
                            title = "Preferred player",
                            subtitle = "Currently: ${PlayerChoice.fromName(settings.preferredPlayer).label}",
                            onClick = { playerPickerOpen = true }
                        )
                    },
                    {
                        SettingsNavRow(
                            icon = Icons.Filled.Audiotrack,
                            title = "Preferred audio language",
                            subtitle = if (audioLang.isNullOrBlank()) {
                                "Not set \u2014 title's default audio is used"
                            } else {
                                "Auto-plays ${audioLanguageName(audioLang!!)} when available"
                            },
                            onClick = { audioLangPickerOpen = true }
                        )
                    },
                    {
                        SettingsNavRow(
                            icon = Icons.Filled.Speed,
                            title = "Buffer preset",
                            subtitle = "Currently: ${bufferPresetName(settings.bufferMinMs, settings.bufferMaxMs, settings.bufferForPlaybackMs)}",
                            onClick = { bufferPresetOpen = true }
                        )
                    },
                    {
                        SettingsNavRow(
                            icon = Icons.Filled.Storage,
                            title = "Disk cache",
                            subtitle = "Cache played media on device: ${diskCacheName(settings.diskCacheMb)}",
                            onClick = { diskCacheOpen = true }
                        )
                    }
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

    if (bufferPresetOpen) {
        BufferPresetDialog(
            settings = settings,
            onDismiss = { bufferPresetOpen = false },
            onDone = { bufferPresetOpen = false; bufferUiTick++ }
        )
    }

    if (diskCacheOpen) {
        DiskCacheDialog(
            settings = settings,
            onDismiss = { diskCacheOpen = false },
            onDone = { diskCacheOpen = false; bufferUiTick++ }
        )
    }

    if (playerPickerOpen) {
        PlayerChoiceDialog(
            settings = settings,
            onDismiss = { playerPickerOpen = false },
            onDone = { playerPickerOpen = false; playerUiTick++ }
        )
    }
}

/**
 * Buffer preset picker (Settings > Playback > Buffer & Cache): Low /
 * Balanced / Aggressive presets mapping to concrete ExoPlayer buffer
 * durations. Applies on the next player build (next title played).
 */
@Composable
fun BufferPresetDialog(
    settings: SettingsStore,
    onDismiss: () -> Unit,
    onDone: () -> Unit
) {
    var selected by remember {
        mutableStateOf(
            BUFFER_PRESETS.indexOfFirst {
                it.second == Triple(settings.bufferMinMs, settings.bufferMaxMs, settings.bufferForPlaybackMs)
            }.takeIf { it >= 0 }
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buffer preset", color = TextPrimary) },
        text = {
            Column {
                Text(
                    "How much video is buffered ahead. Higher = fewer stalls on slow networks, more RAM used. Applies to the next title you play.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                    items(BUFFER_PRESETS.size) { index ->
                        val (label, values) = BUFFER_PRESETS[index]
                        val (minMs, maxMs, forPlaybackMs) = values
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = index }
                                .tvFocusRing(RoundedCornerShape(10.dp))
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = selected == index,
                                onClick = { selected = index },
                                colors = androidx.compose.material3.RadioButtonDefaults.colors(
                                    selectedColor = LocalPalette.current.accent
                                )
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(label, color = TextPrimary)
                                Text(
                                    "${minMs / 1000}s min · ${maxMs / 1000}s max · starts after ${forPlaybackMs / 1000.0} s",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val idx = selected
                if (idx != null && idx >= 0) {
                    val (_, values) = BUFFER_PRESETS[idx]
                    settings.bufferMinMs = values.first
                    settings.bufferMaxMs = values.second
                    settings.bufferForPlaybackMs = values.third
                }
                onDone()
            }) {
                Text("Done", color = LocalPalette.current.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

/**
 * Disk cache picker (Settings > Playback > Buffer & Cache): caches played
 * media on device so re-watches don't re-download. Applies on the next
 * player build.
 */
@Composable
fun DiskCacheDialog(
    settings: SettingsStore,
    onDismiss: () -> Unit,
    onDone: () -> Unit
) {
    var selected by remember { mutableStateOf(settings.diskCacheMb) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Disk cache", color = TextPrimary) },
        text = {
            Column {
                Text(
                    "Keep played video on your device so re-watching doesn't download it again. Applies to the next title you play.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                    items(DISK_CACHE_OPTIONS, key = { it.first }) { (mb, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = mb }
                                .tvFocusRing(RoundedCornerShape(10.dp))
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = selected == mb,
                                onClick = { selected = mb },
                                colors = androidx.compose.material3.RadioButtonDefaults.colors(
                                    selectedColor = LocalPalette.current.accent
                                )
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(label, color = TextPrimary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                settings.diskCacheMb = selected
                onDone()
            }) {
                Text("Done", color = LocalPalette.current.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

/**
 * Preferred-player picker (Settings > Playback): built-in Plavio player,
 * an external app via the system chooser, or ask every time.
 */
@Composable
fun PlayerChoiceDialog(
    settings: SettingsStore,
    onDismiss: () -> Unit,
    onDone: () -> Unit
) {
    var selected by remember {
        mutableStateOf(PlayerChoice.fromName(settings.preferredPlayer))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Preferred player", color = TextPrimary) },
        text = {
            Column {
                Text(
                    "Which app plays your videos. External players open through Android's app chooser; some addons' streams may need their original headers, which only the built-in player guarantees.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                    items(PlayerChoice.values(), key = { it.name }) { choice ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = choice }
                                .tvFocusRing(RoundedCornerShape(10.dp))
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = selected == choice,
                                onClick = { selected = choice },
                                colors = androidx.compose.material3.RadioButtonDefaults.colors(
                                    selectedColor = LocalPalette.current.accent
                                )
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(choice.label, color = TextPrimary)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    choice.blurb,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                settings.preferredPlayer = selected.name
                onDone()
            }) {
                Text("Done", color = LocalPalette.current.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

/** Multi-select dialog for the preferred subtitle languages (max 2). */
@Composable
fun SubtitleLanguagePickerDialog(
    initial: List<String>,
    onDismiss: () -> Unit,
    onDone: (List<String>) -> Unit
) {
    var selected by remember { mutableStateOf(initial.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Preferred subtitle languages", color = TextPrimary) },
        text = {
            Column {
                Text(
                    "Pick up to 2. Matching subtitles turn on automatically in the player.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                    items(SUBTITLE_LANGUAGE_OPTIONS, key = { it.first }) { (code, name) ->
                        val isSelected = code in selected
                        val enabled = isSelected || selected.size < 2
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = enabled) {
                                    selected =
                                        if (isSelected) selected - code else selected + code
                                }
                                .tvFocusRing(RoundedCornerShape(10.dp))
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = null,
                                enabled = enabled,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = LocalPalette.current.accent
                                )
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                name,
                                color = if (enabled) TextPrimary
                                else TextSecondary.copy(alpha = 0.45f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDone(
                        SUBTITLE_LANGUAGE_OPTIONS
                            .filter { it.first in selected }
                            .map { it.first }
                    )
                }
            ) {
                Text("Done", color = LocalPalette.current.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

/** Single-select dialog for the preferred audio language. */
@Composable
fun AudioLanguagePickerDialog(
    initial: String?,
    onDismiss: () -> Unit,
    onDone: (String?) -> Unit
) {
    var selected by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Preferred audio language", color = TextPrimary) },
        text = {
            Column {
                Text(
                    "If a title's audio tracks include this language, it plays in it automatically. Otherwise the title's default is used.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                    item(key = "none") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = null }
                                .tvFocusRing(RoundedCornerShape(10.dp))
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = selected == null,
                                onClick = { selected = null },
                                colors = androidx.compose.material3.RadioButtonDefaults.colors(
                                    selectedColor = LocalPalette.current.accent
                                )
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("Not set", color = TextPrimary)
                        }
                    }
                    items(AUDIO_LANGUAGE_OPTIONS, key = { it.first }) { (code, name) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = code }
                                .tvFocusRing(RoundedCornerShape(10.dp))
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = selected == code,
                                onClick = { selected = code },
                                colors = androidx.compose.material3.RadioButtonDefaults.colors(
                                    selectedColor = LocalPalette.current.accent
                                )
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(name, color = TextPrimary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(selected) }) {
                Text("Done", color = LocalPalette.current.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

/**
 * Subtitle styling dialog (Settings > Playback): text color, size,
 * background/shade and edge style — with a live preview. Transactional:
 * every change edits a DRAFT; "Done" persists it, "Cancel" (or dismissing)
 * throws it away. "System default" skips the app's styling entirely so
 * Android's Settings > Accessibility > Captions preferences apply.
 */
@Composable
fun SubtitleStyleDialog(
    settings: SettingsStore,
    onDismiss: () -> Unit
) {
    var useSystem by remember { mutableStateOf(settings.useSystemCaptions) }
    var textColor by remember { mutableStateOf(settings.subtitleTextColor) }
    var bgColor by remember { mutableStateOf(settings.subtitleBackgroundColor) }
    var sizeScale by remember { mutableStateOf(settings.subtitleTextSizeScale) }
    var edgeType by remember { mutableStateOf(settings.subtitleEdgeType) }

    fun confirm() {
        settings.useSystemCaptions = useSystem
        settings.subtitleTextColor = textColor
        settings.subtitleBackgroundColor = bgColor
        settings.subtitleTextSizeScale = sizeScale
        settings.subtitleEdgeType = edgeType
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Subtitle appearance", color = TextPrimary) },
        text = {
            Column {
                // ---- System default ----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { useSystem = !useSystem }
                        .tvFocusRing(RoundedCornerShape(10.dp))
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = useSystem,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(
                            checkedColor = LocalPalette.current.accent
                        )
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("System default", color = TextPrimary)
                        Text(
                            "Use Android's Settings > Accessibility > Captions",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))

                // The custom styling controls edit the draft only while
                // "System default" is off (dimmed + guarded when it is on).
                Column(
                    Modifier
                        .fillMaxWidth()
                        .alpha(if (useSystem) 0.4f else 1f)
                ) {
                    // ---- Live preview ----
                    SubtitlePreview(
                        textColor = textColor,
                        bgColor = bgColor,
                        sizeScale = sizeScale,
                        edgeType = edgeType
                    )
                    Spacer(Modifier.height(12.dp))

                    Text("Text color", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    Spacer(Modifier.height(6.dp))
                    SwatchRow(
                        options = SUBTITLE_COLOR_OPTIONS,
                        selected = textColor,
                        onSelect = { if (!useSystem) textColor = it }
                    )
                    Spacer(Modifier.height(12.dp))

                    Text("Background", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    Spacer(Modifier.height(6.dp))
                    SwatchRow(
                        options = SUBTITLE_BG_OPTIONS,
                        selected = bgColor,
                        onSelect = { if (!useSystem) bgColor = it }
                    )
                    Spacer(Modifier.height(12.dp))

                    Text("Size", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    Spacer(Modifier.height(6.dp))
                    OptionChips(
                        options = SUBTITLE_SIZE_OPTIONS.map { it.second },
                        selected = subtitleSizeName(sizeScale),
                        onSelect = { name ->
                            if (!useSystem) {
                                SUBTITLE_SIZE_OPTIONS.find { it.second == name }?.let {
                                    sizeScale = it.first
                                }
                            }
                        }
                    )
                    Spacer(Modifier.height(12.dp))

                    Text("Edge style", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    Spacer(Modifier.height(6.dp))
                    OptionChips(
                        options = SUBTITLE_EDGE_OPTIONS.map { it.second },
                        selected = subtitleEdgeName(edgeType),
                        onSelect = { name ->
                            if (!useSystem) {
                                SUBTITLE_EDGE_OPTIONS.find { it.second == name }?.let {
                                    edgeType = it.first
                                }
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { confirm() }) {
                Text("Done", color = LocalPalette.current.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

/** Row of color swatches for the subtitle styling dialog. */
@Composable
fun SwatchRow(
    options: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
    ) {
        items(options.size) { i ->
            val (color, name) = options[i]
            val isSelected = color == selected
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(androidx.compose.ui.graphics.Color(color))
                    .clickable { onSelect(color) }
                    .tvFocusRing(androidx.compose.foundation.shape.CircleShape)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) LocalPalette.current.accent
                        else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.2f),
                        shape = androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (name == "None") {
                    Text(
                        "✕",
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

/** Wrapping row of text chips for size / edge options. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OptionChips(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { name ->
            val isSelected = name == selected
            androidx.compose.material3.FilterChip(
                selected = isSelected,
                onClick = { onSelect(name) },
                label = { Text(name) },
                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                    selectedContainerColor = LocalPalette.current.accent.copy(alpha = 0.25f),
                    selectedLabelColor = TextPrimary,
                    containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.06f),
                    labelColor = TextSecondary
                )
            )
        }
    }
}

/** Live subtitle preview that mirrors the chosen styling. */
@Composable
fun SubtitlePreview(
    textColor: Int,
    bgColor: Int,
    sizeScale: Float,
    edgeType: Int
) {
    val textCol = androidx.compose.ui.graphics.Color(textColor)
    val bgCol = androidx.compose.ui.graphics.Color(bgColor)
    val shadow = when (edgeType) {
        1 -> androidx.compose.ui.graphics.Shadow(
            color = androidx.compose.ui.graphics.Color.Black,
            offset = androidx.compose.ui.geometry.Offset(2f, 2f),
            blurRadius = 0f
        )
        2 -> androidx.compose.ui.graphics.Shadow(
            color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.9f),
            offset = androidx.compose.ui.geometry.Offset(3f, 3f),
            blurRadius = 4f
        )
        3 -> androidx.compose.ui.graphics.Shadow(
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f),
            offset = androidx.compose.ui.geometry.Offset(-2f, -2f),
            blurRadius = 1f
        )
        4 -> androidx.compose.ui.graphics.Shadow(
            color = androidx.compose.ui.graphics.Color.Black,
            offset = androidx.compose.ui.geometry.Offset(0f, 3f),
            blurRadius = 2f
        )
        else -> null
    }
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .background(androidx.compose.ui.graphics.Color.Black)
            .border(
                1.dp,
                androidx.compose.ui.graphics.Color.White.copy(alpha = 0.12f),
                androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "This is how subtitles will look",
            color = textCol,
            fontSize = (16 * sizeScale).sp,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.bodyLarge.copy(shadow = shadow),
            modifier = Modifier
                .background(bgCol, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
