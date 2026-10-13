package com.pulsestream.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.pulsestream.app.data.BadgePack
import com.pulsestream.app.data.BadgePacks
import com.pulsestream.app.data.StreamItem
import com.pulsestream.app.ui.theme.AppPalette
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
// Stream preference sorting (Settings > Streams, v0.8.31+)
// ---------------------------------------------------------------------------

/**
 * Canonical quality bucket for sort priority: "2160p" / "1080p" / "720p" /
 * "480p", or "" when the stream carries no recognizable quality tag.
 */
fun canonicalStreamQuality(stream: StreamItem): String {
    val q = parseStreamInfo(stream).quality ?: return ""
    return when {
        q.contains("2160") || q.equals("4K", ignoreCase = true) || q.equals("UHD", ignoreCase = true) -> "2160p"
        q.contains("1080") -> "1080p"
        q.contains("720") -> "720p"
        q.contains("480") -> "480p"
        else -> ""
    }
}

/**
 * File size in GB parsed from the stream info ("9.92 GB" / "430.3 MB").
 * Null when the stream carries no size tag.
 */
fun streamSizeGb(stream: StreamItem): Double? {
    val s = parseStreamInfo(stream).size ?: return null
    val m = Regex("""([\d.]+)\s*(GB|MB)""", RegexOption.IGNORE_CASE).find(s) ?: return null
    val v = m.groupValues[1].toDoubleOrNull() ?: return null
    return if (m.groupValues[2].equals("GB", ignoreCase = true)) v else v / 1024.0
}

/**
 * Applies the user's stream preferences to a raw stream list:
 *  1. per-source cap — at most [SettingsStore.streamsPerSource] streams per
 *     addon, so one source can't flood the picker;
 *  2. quality priority — sorted by [SettingsStore.qualityOrder], top first.
 * The sort is stable: within the same quality bucket the original
 * (star-priority) order is preserved. Unknown-quality streams sink last.
 */
fun applyStreamPreferences(
    streams: List<StreamItem>,
    settings: com.pulsestream.app.data.SettingsStore
): List<StreamItem> {
    val perSource = settings.streamsPerSource
    val order = settings.qualityOrder
    val capped = streams.groupBy { it.addonKey() }
        .flatMap { (_, list) -> list.take(perSource) }
    return capped.sortedWith(compareBy { s ->
        val idx = order.indexOf(canonicalStreamQuality(s))
        if (idx >= 0) idx else Int.MAX_VALUE
    })
}

/**
 * Picks the autoplay stream under the Settings > Streams rule:
 * quality priority (preferred quality jumps first when set to a specific
 * bucket), then the size limit. Falls back to the first stream when nothing
 * matches, so autoplay never dead-ends on an empty pick.
 */
fun pickAutoplayStream(
    streams: List<StreamItem>,
    settings: com.pulsestream.app.data.SettingsStore
): StreamItem? {
    if (streams.isEmpty()) return null
    val order = settings.qualityOrder
    val prefQ = settings.autoplayQuality
    val maxGb = settings.autoplayMaxSizeGb
    val sorted = streams.sortedWith(compareBy { s ->
        val q = canonicalStreamQuality(s)
        when {
            prefQ != "Auto" && q == prefQ -> -1
            else -> order.indexOf(q).takeIf { it >= 0 } ?: Int.MAX_VALUE
        }
    })
    return sorted.firstOrNull { s ->
        maxGb <= 0 || (streamSizeGb(s)?.let { it <= maxGb } ?: true)
    } ?: sorted.firstOrNull()
}

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
fun rememberStreamBrowserState(
    streams: List<StreamItem>,
    settings: com.pulsestream.app.data.SettingsStore? = null
): StreamBrowserState {
    // User stream preferences (per-source cap + quality order) apply to the
    // displayed list everywhere this state is used (detail + player).
    val ordered = remember(streams, settings?.streamsPerSource, settings?.qualityOrder) {
        if (settings != null) applyStreamPreferences(streams, settings) else streams
    }
    // Saveable: the addon tab the user picked survives rotation. A stale
    // key (tab gone after the stream list changed) falls back to "All".
    var selectedTab by rememberSaveable { mutableStateOf(STREAM_TAB_ALL) }
    val tabs = remember(ordered) {
        val groups = ordered.groupBy { it.addonKey() }
        buildList {
            add(StreamTab(STREAM_TAB_ALL, "All", ordered.size))
            groups.entries.sortedByDescending { it.value.size }.forEach { (key, list) ->
                add(StreamTab(key, key, list.size))
            }
        }
    }
    val safeTab = if (tabs.any { it.key == selectedTab }) selectedTab else STREAM_TAB_ALL
    val filtered = remember(ordered, safeTab) {
        if (safeTab == STREAM_TAB_ALL) ordered
        else ordered.filter { it.addonKey() == safeTab }
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
// ---------------------------------------------------------------------------
// Stream badge packs — user-selectable in Appearance settings (v0.8.31+)
// ---------------------------------------------------------------------------
// Each stream row shows the title plus image badges matched against the
// stream's title via the selected pack's regex filters. One clean layout.

/** Loads the user's selected badge pack (cached). Null when unavailable. */
@Composable
private fun rememberBadgePack(): BadgePack? {
    val context = LocalContext.current
    // The raw setting key: a built-in pack key or "custom:<name>".
    val styleKey = LocalBadgeStyleKey.current
    return remember(styleKey) { BadgePacks.loadByKey(context, styleKey) }
}

/**
 * Provides the badge-style key down the stream-list tree. Screens set this
 * from `repository.settings.streamCardStyle` (now a badge-pack key).
 */
val LocalBadgeStyleKey: androidx.compose.runtime.ProvidableCompositionLocal<String> =
    androidx.compose.runtime.compositionLocalOf { "black" }

/**
 * Single stream row: title, badge strip, and a compact quality/size info
 * line. Badges come from the selected pack; the info line stays because
 * badges don't cover everything (e.g. exact file size).
 */
@Composable
fun StreamCard(
    stream: StreamItem,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val palette = LocalPalette.current
    val pack = rememberBadgePack()
    val badges = remember(stream, pack) {
        if (pack != null) BadgePacks.match(pack, stream) else emptyList()
    }
    val info = remember(stream) { parseStreamInfo(stream) }
    val headline = remember(stream) { streamHeadline(stream) }
    val shape = RoundedCornerShape(14.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(shape)
            .background(
                if (selected) palette.accent.copy(alpha = 0.14f)
                else Color.White.copy(alpha = 0.05f)
            )
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) palette.accent else GlassBorder,
                shape
            )
            .clickable(onClick = onClick)
            .tvFocusRing(shape)
            .padding(12.dp)
    ) {
        Text(
            text = headline,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            maxLines = 2
        )
        if (badges.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                badges.forEach { badge ->
                    AsyncImage(
                        model = badge.imageUrl,
                        contentDescription = badge.name,
                        modifier = Modifier.height(15.dp),
                        contentScale = ContentScale.FillHeight
                    )
                }
            }
        }
        val infoLine = buildString {
            info.quality?.let { append(it.uppercase()) }
            info.source?.let { if (isNotEmpty()) append(" · "); append(it) }
            info.codec?.let { if (isNotEmpty()) append(" · "); append(it) }
            info.audio?.let { if (isNotEmpty()) append(" · "); append(it) }
            info.size?.let { if (isNotEmpty()) append(" · "); append(it) }
        }
        if (infoLine.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = infoLine,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = 1
            )
        }
    }
}

/**
 * Non-interactive badge-pack preview for the Appearance settings picker:
 * sample badges from the pack.
 */
@Composable
fun BadgePackPreview(
    packKey: String,
    label: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pack = remember(packKey) { BadgePacks.loadByKey(context, packKey) }
    val sampleBadges = remember(pack) {
        // Show a spread of badges from different groups.
        val seen = mutableSetOf<String>()
        pack?.filters?.filter { it.pattern != null && seen.add(it.groupId) }
            ?.take(6) ?: emptyList()
    }
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Spacer(Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            sampleBadges.forEach { badge ->
                AsyncImage(
                    model = badge.imageUrl,
                    contentDescription = badge.name,
                    modifier = Modifier.height(24.dp),
                    contentScale = ContentScale.FillHeight
                )
            }
        }
    }
}
// ---------------------------------------------------------------------------
// List-level rendering: container adapts to the chosen style
// ---------------------------------------------------------------------------

/**
 * Stream list: one clean layout of badge rows. The badge pack comes from
 * [LocalBadgeStyleKey], set by screens from the user's Appearance setting.
 */
@Composable
fun StreamList(
    streams: List<StreamItem>,
    onPick: (StreamItem) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    selectedUrl: String? = null
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding
    ) {
        itemsIndexed(streams, key = { index, s -> "${index}_${s.url}" }) { _, stream ->
            StreamCard(
                stream = stream,
                selected = stream.url == selectedUrl
            ) { onPick(stream) }
        }
    }
}

/**
 * LazyListScope variant for stream sections nested inside another LazyColumn
 * (e.g. the detail screen's inline sources).
 */
fun LazyListScope.streamListItems(
    streams: List<StreamItem>,
    onPick: (StreamItem) -> Unit,
    selectedUrl: String? = null
) {
    itemsIndexed(streams, key = { index, s -> "${index}_${s.url}" }) { _, stream ->
        StreamCard(
            stream = stream,
            selected = stream.url == selectedUrl
        ) { onPick(stream) }
    }
}
