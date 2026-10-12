package com.pulsestream.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulsestream.app.data.StreamItem
import com.pulsestream.app.ui.theme.GlassBorder
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import com.pulsestream.app.ui.tv.tvFocusRing

// ---------------------------------------------------------------------------
// Stream metadata parsing
// ---------------------------------------------------------------------------

/** Human-readable bits extracted from a stream's title/name. */
data class ParsedStreamInfo(
    val quality: String? = null,
    val source: String? = null,
    val codec: String? = null,
    val audio: String? = null,
    val size: String? = null
)

private val QUALITY_RE = Regex("""\b(2160p|1440p|1080p|720p|480p|360p)\b""", RegexOption.IGNORE_CASE)
private val FOUR_K_RE = Regex("""\b(4K|UHD)\b""")
private val SOURCE_RE =
    Regex("""\b(Blu[-\s]?Ray|BRRip|WEB[-\s]?DL|WEBRip|HDRip|HDCAM|HDTS|\bTS\b|CAM)\b""", RegexOption.IGNORE_CASE)
private val CODEC_RE = Regex("""\b(x265|HEVC|x264|AVC|AV1|VP9)\b""", RegexOption.IGNORE_CASE)
private val AUDIO_RE = Regex(
    """\b(Atmos|DTS[-\s]?HD(?:\s?MA)?|TrueHD|DTS|DD\+?(?:\s?[57]\.1)?|Dolby(?:\s?Digital)?|AAC(?:\s?5\.1)?)\b""",
    RegexOption.IGNORE_CASE
)
private val SIZE_RE = Regex("""(\d+(?:\.\d+)?\s?(?:GB|MB))""", RegexOption.IGNORE_CASE)

/** Scans a stream's title/name for quality, source, codec, audio and size tags. */
fun parseStreamInfo(stream: StreamItem): ParsedStreamInfo {
    val haystack = listOfNotNull(stream.title, stream.name).joinToString("\n")
    if (haystack.isBlank()) return ParsedStreamInfo()
    val quality = FOUR_K_RE.find(haystack)?.value?.uppercase()
        ?: QUALITY_RE.find(haystack)?.value?.lowercase()
    val source = SOURCE_RE.find(haystack)?.value
        ?.replace(Regex("""[-\s]+"""), "-")?.uppercase()
        ?.replace("BLU-RAY", "BluRay")?.replace("WEB-DL", "WEB-DL")
    val codec = CODEC_RE.find(haystack)?.value?.uppercase()
    val audio = AUDIO_RE.find(haystack)?.value
        ?.replace(Regex("""\s+"""), " ")?.trim()
        ?.let { a ->
            when {
                a.equals("Atmos", ignoreCase = true) -> "Atmos"
                a.startsWith("DTS", ignoreCase = true) -> a.uppercase().replace(" ", "")
                a.startsWith("DD", ignoreCase = true) -> a.uppercase().replace(" ", "")
                a.startsWith("Dolby", ignoreCase = true) -> "Dolby"
                else -> a.uppercase()
            }
        }
    val size = SIZE_RE.find(haystack)?.value?.uppercase()?.replace(" ", "")
        ?.let { s -> s.dropLast(2) + " " + s.takeLast(2) }
    return ParsedStreamInfo(quality, source, codec, audio, size)
}

/** Best one-line label for a stream card headline. */
fun streamHeadline(stream: StreamItem): String {
    val firstLine = stream.title?.lineSequence()
        ?.map { it.trim() }?.firstOrNull { it.isNotBlank() }
    if (!firstLine.isNullOrBlank()) return firstLine
    if (!stream.name.isNullOrBlank()) return stream.name!!
    return stream.url.substringAfter("://").substringBefore("/").take(48)
}

/** Addon grouping key for tabs. */
fun StreamItem.addonKey(): String = sourceName?.takeIf { it.isNotBlank() }
    ?: name?.takeIf { it.isNotBlank() } ?: "Unknown"

// ---------------------------------------------------------------------------
// Addon tabs + state
// ---------------------------------------------------------------------------

const val STREAM_TAB_ALL = "__all__"

data class StreamTab(val key: String, val label: String, val count: Int)

@Stable
class StreamBrowserState(
    val tabs: List<StreamTab>,
    val selectedTab: String,
    val filtered: List<StreamItem>,
    val onSelectTab: (String) -> Unit
)

@Composable
fun rememberStreamBrowserState(streams: List<StreamItem>): StreamBrowserState {
    // Saveable: the addon tab the user picked survives rotation. A stale
    // key (tab gone after the stream list changed) falls back to "All".
    var selectedTab by rememberSaveable { mutableStateOf(STREAM_TAB_ALL) }
    val tabs = remember(streams) {
        val groups = streams.groupBy { it.addonKey() }
        buildList {
            add(StreamTab(STREAM_TAB_ALL, "All", streams.size))
            groups.entries.sortedByDescending { it.value.size }.forEach { (key, list) ->
                add(StreamTab(key, key, list.size))
            }
        }
    }
    val safeTab = if (tabs.any { it.key == selectedTab }) selectedTab else STREAM_TAB_ALL
    val filtered = remember(streams, safeTab) {
        if (safeTab == STREAM_TAB_ALL) streams
        else streams.filter { it.addonKey() == safeTab }
    }
    return remember(tabs, safeTab, filtered) {
        StreamBrowserState(tabs, safeTab, filtered) { selectedTab = it }
    }
}

@Composable
fun AddonTabs(state: StreamBrowserState, modifier: Modifier = Modifier) {
    if (state.tabs.size <= 2) return // "All" + one addon: tabs add nothing.
    val palette = LocalPalette.current
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(state.tabs, key = { it.key }) { tab ->
            val selected = tab.key == state.selectedTab
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (selected) palette.accent.copy(alpha = 0.22f)
                        else Color.White.copy(alpha = 0.06f)
                    )
                    .border(
                        1.dp,
                        if (selected) palette.accent else GlassBorder,
                        RoundedCornerShape(50)
                    )
                    .clickable { state.onSelectTab(tab.key) }
                    .tvFocusRing(RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "${tab.label} • ${tab.count}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) TextPrimary else TextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Rich stream card
// ---------------------------------------------------------------------------

@Composable
fun StreamCard(
    stream: StreamItem,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val palette = LocalPalette.current
    val info = remember(stream) { parseStreamInfo(stream) }
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .border(
                if (selected) 1.5.dp else 0.dp,
                if (selected) palette.accent else Color.Transparent,
                RoundedCornerShape(20.dp)
            ),
        onClick = onClick
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Quality badge.
            if (info.quality != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.accent.copy(alpha = 0.16f))
                        .border(1.dp, palette.accent.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = info.quality,
                        color = palette.accent,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.width(12.dp))
            } else {
                Box(
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                        .background(palette.gradient),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.PlayArrow, null, tint = Color(0xFF0A0A12))
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = streamHeadline(stream),
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val metaBits = listOfNotNull(info.source, info.codec, info.audio)
                if (metaBits.isNotEmpty() || info.size != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        metaBits.forEach { bit ->
                            MetaPill(bit)
                        }
                        if (info.size != null) {
                            Text(
                                text = info.size,
                                color = palette.accent,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                val addon = stream.addonKey()
                Text(
                    text = "via $addon",
                    color = TextSecondary.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun MetaPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}
