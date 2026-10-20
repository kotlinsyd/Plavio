package com.pulsestream.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
private val FOUR_K_RE = Regex("""\b(4K|UHD)\b""", RegexOption.IGNORE_CASE)
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

/** Canonical filter bucket for a parsed quality label; unknown labels have no filter bucket. */
fun normalizeStreamQuality(quality: String?): String? {
    val value = quality?.trim()?.lowercase()?.replace(" ", "") ?: return null
    return when (value) {
        "4k", "uhd", "2160p" -> "4K"
        "1440p", "1080p", "720p", "480p", "360p" -> value
        else -> null
    }
}

private val STREAM_QUALITY_ORDER = listOf("4K", "1440p", "1080p", "720p", "480p", "360p")

/** Qualities currently represented in [streams], regardless of source or preference caps. */
fun availableStreamQualities(streams: List<StreamItem>): List<String> =
    streams.mapNotNull { normalizeStreamQuality(parseStreamInfo(it).quality) }
        .distinct()
        .sortedBy { STREAM_QUALITY_ORDER.indexOf(it).let { index -> if (index < 0) Int.MAX_VALUE else index } }

/** Applies a canonical quality bucket across the whole combined list, retaining every matching addon. */
fun filterStreamsByQuality(streams: List<StreamItem>, quality: String?): List<StreamItem> {
    val bucket = normalizeStreamQuality(quality) ?: return streams
    return streams.filter { normalizeStreamQuality(parseStreamInfo(it).quality) == bucket }
}

/** Compare display labels after case/spacing/punctuation normalization, retaining original text. */
private fun comparableStreamLabel(value: String): String =
    value.lowercase().filter { it.isLetterOrDigit() }

private fun streamLinesEquivalent(first: String, second: String): Boolean {
    val firstLabel = comparableStreamLabel(first)
    val secondLabel = comparableStreamLabel(second)
    if (firstLabel == secondLabel) return true
    val firstQuality = normalizeStreamQuality(first)
    val secondQuality = normalizeStreamQuality(second)
    return firstQuality != null && firstQuality == secondQuality
}

/** Combine addon title/name while removing repeated and less-informative overlapping lines. */
fun streamDisplayName(title: String?, name: String?, fallbackUrl: String = ""): String {
    val t = title?.trim().orEmpty()
    val n = name?.trim().orEmpty()
    if (t.isBlank() && n.isBlank()) {
        return fallbackUrl.substringAfter("://").substringBefore("/").take(48)
    }

    val lines = mutableListOf<String>()
    (listOf(t, n).filter { it.isNotBlank() }.flatMap { field ->
        field.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    }).forEach { candidate ->
        val candidateLabel = comparableStreamLabel(candidate)
        if (candidateLabel.isEmpty()) return@forEach
        val overlaps = lines.indices.filter { index ->
            val existingLabel = comparableStreamLabel(lines[index])
            streamLinesEquivalent(lines[index], candidate) ||
                (existingLabel.isNotEmpty() &&
                    (existingLabel.contains(candidateLabel) || candidateLabel.contains(existingLabel)))
        }
        if (overlaps.isEmpty()) {
            lines += candidate
        } else {
            val richer = overlaps.filter { index ->
                comparableStreamLabel(lines[index]).length < candidateLabel.length
            }
            if (richer.isNotEmpty()) {
                val insertionIndex = richer.minOrNull()!!
                richer.asReversed().forEach { index -> lines.removeAt(index) }
                lines.add(insertionIndex.coerceAtMost(lines.size), candidate)
            }
        }
    }
    return lines.joinToString("\n").ifBlank {
        fallbackUrl.substringAfter("://").substringBefore("/").take(48)
    }
}

/** Best one-line label for a stream card headline. */
fun streamHeadline(stream: StreamItem): String {
    // Prefer name (e.g. "2160p · HDHub") — it includes the provider.
    // Fall back to title's first line, then URL host.
    if (!stream.name.isNullOrBlank()) return stream.name!!
    val firstLine = stream.title?.lineSequence()
        ?.map { it.trim() }?.firstOrNull { it.isNotBlank() }
    if (!firstLine.isNullOrBlank()) return firstLine
    return stream.url.substringAfter("://").substringBefore("/").take(48)
}

/** One authoritative set of labels for a compact source-picker card. */
data class StreamDisplayModel(
    val headline: String,
    val provider: String?,
    val metadata: List<String>
)

/** Keeps parsed details out of the chips when the full filename already shows them. */
fun streamDisplayModel(stream: StreamItem): StreamDisplayModel {
    val headline = streamDisplayName(stream.title, stream.name, stream.url)
    val normalizedHeadline = comparableStreamLabel(headline)
    val providerLabel = stream.sourceName?.takeIf { it.isNotBlank() } ?: stream.addonKey()
    val visibleProvider = providerLabel.takeUnless {
        normalizedHeadline.contains(comparableStreamLabel(it))
    }
    val info = parseStreamInfo(stream)
    val headlineQuality = FOUR_K_RE.find(headline)?.value
        ?: QUALITY_RE.find(headline)?.value
    val representedQuality = normalizeStreamQuality(headlineQuality)
    val details = listOfNotNull(
        info.quality?.uppercase(), info.codec?.uppercase(), info.audio, info.size, info.source
    ).distinct()
    val visibleDetails = details.filterNot {
        (normalizeStreamQuality(it) != null && normalizeStreamQuality(it) == representedQuality) ||
            normalizedHeadline.contains(comparableStreamLabel(it))
    }
    return StreamDisplayModel(headline, visibleProvider, visibleDetails)
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
    settings: com.pulsestream.app.data.SettingsStore,
    limitPerSource: Boolean = true
): List<StreamItem> {
    val perSource = settings.streamsPerSource
    val order = settings.qualityOrder
    val capped = if (limitPerSource) streams.groupBy { it.addonKey() }
        .flatMap { (_, list) -> list.take(perSource) } else streams
    // TorBox priority (user's order): when TorBox is linked, its instant ⚡ streams
    // sort first so they play first.
    val torboxLinked = !settings.torboxApiKey.isNullOrBlank()
    return capped.sortedWith(compareByDescending<StreamItem> { s ->
        // TorBox cached streams first (identified by ⚡ in title/name or TorBox in source name)
        if (torboxLinked && ((s.title?.contains("⚡") == true) || (s.name?.contains("⚡") == true) || (s.sourceName?.contains("TorBox", ignoreCase = true) == true))) 1 else 0
    }.thenBy { s ->
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
    // Filter out unresolved infohash: placeholders - only pick playable URLs.
    // Debrid resolution replaces these with real HTTP links asynchronously.
    val playable = streams.filter { !it.url.startsWith("infohash:") }
    if (playable.isEmpty()) return null
    val order = settings.qualityOrder
    val prefQ = settings.autoplayQuality
    val maxGb = settings.autoplayMaxSizeGb

    // Adaptive mode (user's order 2026-10-20): when quality is "Auto", cap the
    // pick at what the device screen + network can handle (Netflix/YouTube style)
    // instead of always grabbing the highest quality. Prefer the highest quality
    // AT or BELOW the cap; if everything is above the cap, take the lowest above.
    val adaptiveCap = if (prefQ == "Auto") settings.adaptiveQualityCap() else null
    val sorted = if (adaptiveCap != null) {
        fun rank(q: String): Int = when (q) {
            "2160p" -> 4; "1080p" -> 3; "720p" -> 2; "480p" -> 1; else -> 0
        }
        val capRank = rank(adaptiveCap)
        playable.sortedWith(
            compareBy<StreamItem> { s ->
                val r = rank(canonicalStreamQuality(s))
                if (r <= capRank) 0 else 1 // under cap first
            }.thenByDescending { s ->
                val r = rank(canonicalStreamQuality(s))
                if (r <= capRank) r else -r // highest under cap / lowest over cap
            }
        )
    } else {
        playable.sortedWith(compareBy { s ->
            val q = canonicalStreamQuality(s)
            when {
                prefQ != "Auto" && q == prefQ -> -1
                else -> order.indexOf(q).takeIf { it >= 0 } ?: Int.MAX_VALUE
            }
        })
    }
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
    val onSelectTab: (String) -> Unit,
    // Quality filter (user's order): dynamic pills, only shown qualities.
    val qualities: List<String>,
    val selectedQuality: String?,
    val onSelectQuality: (String?) -> Unit
)

@Composable
fun rememberStreamBrowserState(
    streams: List<StreamItem>,
    settings: com.pulsestream.app.data.SettingsStore? = null,
    limitPerSource: Boolean = true
): StreamBrowserState {
    // The player shows the complete loaded set; other pickers retain the user's cap.
    val ordered = remember(streams, settings?.streamsPerSource, settings?.qualityOrder, limitPerSource) {
        if (settings != null) applyStreamPreferences(streams, settings, limitPerSource) else streams
    }
    // Saveable: the addon tab the user picked survives rotation. A stale
    // key (tab gone after the stream list changed) falls back to "All".
    var selectedTab by rememberSaveable { mutableStateOf(STREAM_TAB_ALL) }
    // Quality filter: null = All qualities. Dynamic — only qualities present
    // in the current stream list get a pill (user's order).
    var selectedQuality by rememberSaveable { mutableStateOf<String?>(null) }
    val tabs = remember(ordered) {
        val groups = ordered.groupBy { it.addonKey() }
        buildList {
            add(StreamTab(STREAM_TAB_ALL, "All", ordered.size))
            groups.entries.sortedByDescending { it.value.size }.forEach { (key, list) ->
                add(StreamTab(key, key, list.size))
            }
        }
    }
    // Base choices on all fresh combined results, not the possibly capped
    // display list. This keeps every quality discoverable in the picker.
    val qualities = remember(streams) { availableStreamQualities(streams) }
    val safeTab = if (tabs.any { it.key == selectedTab }) selectedTab else STREAM_TAB_ALL
    val normalizedSelection = normalizeStreamQuality(selectedQuality)
    val safeQuality = normalizedSelection?.takeIf { it in qualities }
    val filtered = remember(ordered, safeTab, safeQuality) {
        var list = if (safeTab == STREAM_TAB_ALL) ordered
        else ordered.filter { it.addonKey() == safeTab }
        list = filterStreamsByQuality(list, safeQuality)
        list
    }
    return remember(tabs, safeTab, filtered, qualities, safeQuality) {
        StreamBrowserState(
            tabs, safeTab, filtered, { selectedTab = it }, qualities, safeQuality,
            { selectedQuality = normalizeStreamQuality(it) }
        )
    }
}

@Composable
fun AddonTabs(state: StreamBrowserState, modifier: Modifier = Modifier, compact: Boolean = false) {
    if (!compact && state.tabs.size <= 2) return // Preserve the original non-compact behavior.
    val palette = LocalPalette.current
    val tabs = if (compact) state.tabs.sortedBy { tab ->
        when {
            tab.key == STREAM_TAB_ALL -> 0
            tab.label.equals("PlavioHD", ignoreCase = true) -> 1
            tab.label.equals("TMAddon", ignoreCase = true) -> 2
            tab.label.equals("StAddon", ignoreCase = true) -> 3
            else -> 4
        }
    } else state.tabs
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = if (compact) 8.dp else 16.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)
    ) {
        items(tabs, key = { it.key }) { tab ->
            val selected = tab.key == state.selectedTab
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (selected && compact) palette.accent
                        else if (selected) palette.accent.copy(alpha = 0.22f)
                        else Color.White.copy(alpha = 0.06f)
                    )
                    .border(
                        1.dp,
                        if (selected) palette.accent else GlassBorder,
                        RoundedCornerShape(50)
                    )
                    .clickable(role = Role.Tab) { state.onSelectTab(tab.key) }
                    .semantics { this.selected = selected }
                    .tvFocusRing(RoundedCornerShape(50))
                    .padding(horizontal = if (compact) 11.dp else 14.dp, vertical = if (compact) 6.dp else 8.dp)
            ) {
                Text(
                    text = if (compact) tab.label else "${tab.label} • ${tab.count}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected && compact) Color.Black else if (selected) TextPrimary else TextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Quality filter pills (user's order): only qualities present in the stream
 * list get a pill. Tapping one filters across ALL addons.
 */
@Composable
fun QualityPills(state: StreamBrowserState, modifier: Modifier = Modifier, compact: Boolean = false) {
    if (state.qualities.isEmpty() || (!compact && state.qualities.size <= 1)) return // Nothing useful to filter.
    val palette = LocalPalette.current
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = if (compact) 8.dp else 16.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)
    ) {
        item(key = "quality_all") {
            val selected = state.selectedQuality == null
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (selected && compact) palette.accent
                        else if (selected) palette.accent.copy(alpha = 0.22f)
                        else Color.White.copy(alpha = 0.06f)
                    )
                    .border(
                        1.dp,
                        if (selected) palette.accent else GlassBorder,
                        RoundedCornerShape(50)
                    )
                    .clickable(role = Role.RadioButton) { state.onSelectQuality(null) }
                    .semantics { this.selected = selected }
                    .tvFocusRing(RoundedCornerShape(50))
                    .padding(horizontal = if (compact) 11.dp else 14.dp, vertical = if (compact) 6.dp else 8.dp)
            ) {
                Text(
                    text = if (compact) "Any" else "All",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected && compact) Color.Black else if (selected) TextPrimary else TextSecondary,
                    maxLines = 1
                )
            }
        }
        items(state.qualities, key = { "quality_$it" }) { quality ->
            val selected = state.selectedQuality == quality
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (selected && compact) palette.accent
                        else if (selected) palette.accent.copy(alpha = 0.22f)
                        else Color.White.copy(alpha = 0.06f)
                    )
                    .border(
                        1.dp,
                        if (selected) palette.accent else GlassBorder,
                        RoundedCornerShape(50)
                    )
                    .clickable(role = Role.RadioButton) { state.onSelectQuality(if (selected) null else quality) }
                    .semantics { this.selected = selected }
                    .tvFocusRing(RoundedCornerShape(50))
                    .padding(horizontal = if (compact) 11.dp else 14.dp, vertical = if (compact) 6.dp else 8.dp)
            ) {
                Text(
                    text = quality.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected && compact) Color.Black else if (selected) TextPrimary else TextSecondary,
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
@OptIn(ExperimentalLayoutApi::class)
fun StreamCard(
    stream: StreamItem,
    selected: Boolean = false,
    onClick: () -> Unit,
    onDownload: ((StreamItem) -> Unit)? = null,
    compact: Boolean = false
) {
    val palette = LocalPalette.current
    val pack = rememberBadgePack()
    val unresolvedTorrent = stream.url.startsWith("infohash:", true) || stream.url.startsWith("magnet:", true)
    val badges = remember(stream, pack) {
        if (pack != null) BadgePacks.match(pack, stream) else emptyList()
    }
    val info = remember(stream) { parseStreamInfo(stream) }
    // Full filename display (user's order): show the complete title/name,
    // never truncated. Prefer title (usually the full filename).
    val display = remember(stream) { streamDisplayModel(stream) }
    val fullName = display.headline
    // Language tags from title (Hindi, English, etc.)
    val languages = remember(stream) {
        val haystack = listOfNotNull(stream.title, stream.name).joinToString(" ")
        val found = mutableListOf<String>()
        val langRe = Regex("""\b(Hindi|English|Tamil|Telugu|Malayalam|Kannada|Punjabi|Bengali|Marathi|Gujarati|Urdu|Arabic|Spanish|French|German|Italian|Portuguese|Russian|Japanese|Korean|Chinese|Turkish)\b""", RegexOption.IGNORE_CASE)
        langRe.findAll(haystack).forEach { m ->
            val lang = m.value.lowercase().replaceFirstChar { it.uppercase() }
            if (!found.contains(lang)) found.add(lang)
        }
        found.take(3)
    }
    val shape = RoundedCornerShape(20.dp)
    val is4K = display.metadata.any { normalizeStreamQuality(it) == "4K" }

    if (compact) {
        CompactStreamCard(
            stream = stream,
            selected = selected,
            display = display,
            onClick = onClick,
            onDownload = onDownload
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(shape)
                .then(
                    if (selected) Modifier.border(1.5.dp, palette.accent, shape)
                    else Modifier
                )
                .clickable(enabled = !unresolvedTorrent, onClick = onClick)
                .tvFocusRing(shape)
                .padding(20.dp)
        ) {
            // Title row with 4K ribbon — full name, bigger text (user's order).
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = fullName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    lineHeight = 24.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = if (is4K) 52.dp else 0.dp)
                )
                if (is4K) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .background(
                                palette.accent,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "4K",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.Black
                        )
                    }
                }
            }

            // Spec chips
            val chips = buildList {
                display.provider?.let { add(it to false) }
                display.metadata.forEach { add(it to true) }
                val missingLanguages = languages.filterNot {
                    comparableStreamLabel(fullName).contains(comparableStreamLabel(it))
                }
                if (missingLanguages.isNotEmpty()) add(missingLanguages.joinToString(" + ") to false)
            }
            if (chips.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    chips.forEach { (label, highlighted) ->
                        Box(
                            modifier = Modifier
                                .background(
                                    if (highlighted) palette.accent.copy(alpha = 0.15f)
                                    else Color.White.copy(alpha = 0.06f),
                                    RoundedCornerShape(8.dp)
                                )
                                .border(
                                    1.dp,
                                    if (highlighted) palette.accent.copy(alpha = 0.3f)
                                    else Color.White.copy(alpha = 0.08f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (highlighted) palette.accent else TextSecondary
                            )
                        }
                    }
                }
            }

            // Badges (provider logos)
            if (badges.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
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

            // Bottom row: download button only (no URLs — user's order).
            if (onDownload != null) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = { onDownload(stream) },
                        enabled = !unresolvedTorrent,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Filled.Download,
                            contentDescription = "Download",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactStreamCard(
    stream: StreamItem,
    selected: Boolean,
    display: StreamDisplayModel,
    onClick: () -> Unit,
    onDownload: ((StreamItem) -> Unit)?
) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(14.dp)
    val unresolvedTorrent = stream.url.startsWith("infohash:", true) || stream.url.startsWith("magnet:", true)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .border(1.dp, if (selected) palette.accent else GlassBorder, shape)
            .background(Color.White.copy(alpha = 0.025f), shape)
            .semantics { this.selected = selected }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).clickable(enabled = !unresolvedTorrent, onClick = onClick).tvFocusRing(shape)) {
            Text(
                text = display.headline,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                lineHeight = 19.sp
            )
            if (unresolvedTorrent) {
                Text("Torrent source · cached debrid link required", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
            if (selected || display.provider != null || display.metadata.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (selected) {
                        Text(
                            text = "Playing",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.background(palette.accent.copy(alpha = 0.24f), RoundedCornerShape(6.dp))
                                .border(1.dp, palette.accent.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    display.provider?.let { provider ->
                        Text(
                            text = provider,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            modifier = Modifier.border(1.dp, GlassBorder, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    display.metadata.forEach { detail ->
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            modifier = Modifier.border(1.dp, GlassBorder, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
        if (onDownload != null) {
            IconButton(onClick = { onDownload(stream) }, enabled = !unresolvedTorrent, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Filled.Download,
                    contentDescription = "Download",
                    tint = TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        IconButton(onClick = onClick, enabled = !unresolvedTorrent, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "Play stream",
                tint = TextPrimary,
                modifier = Modifier.size(24.dp)
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
    selectedUrl: String? = null,
    onDownload: ((StreamItem) -> Unit)? = null,
    compact: Boolean = false
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding
    ) {
        itemsIndexed(streams, key = { index, s -> "${index}_${s.url}" }) { _, stream ->
            StreamCard(
                stream = stream,
                selected = stream.url == selectedUrl,
                onClick = { onPick(stream) },
                onDownload = onDownload,
                compact = compact
            )
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
    selectedUrl: String? = null,
    onDownload: ((StreamItem) -> Unit)? = null,
    compact: Boolean = false
) {
    itemsIndexed(streams, key = { index, s -> "${index}_${s.url}" }) { _, stream ->
        StreamCard(
            stream = stream,
            selected = stream.url == selectedUrl,
            onClick = { onPick(stream) },
            onDownload = onDownload,
            compact = compact
        )
    }
}
