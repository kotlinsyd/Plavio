package com.pulsestream.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
// ---------------------------------------------------------------------------
// Stream card styles — user-selectable in Appearance settings
// ---------------------------------------------------------------------------

/** The 5 selectable stream card visual styles. */
enum class StreamCardStyle(val key: String, val label: String) {
    FUSION("fusion", "Fusion"),
    ELEGANT("elegant", "Elegant"),
    MIDNIGHT("midnight", "Midnight Neon"),
    COMPACT_LIST("compact_list", "Compact List"),
    SIDE_RAIL("side_rail", "Side Rail"),
    TILE_GRID("tile_grid", "Tile Grid"),
    METER("meter", "Meter");

    companion object {
        fun fromKey(key: String?): StreamCardStyle =
            values().firstOrNull { it.key == key } ?: FUSION
    }
}

// Shared card palette bits.
private val CardTeal = Color(0xFF2DD4BF)
private val CardGold = Color(0xFFF5B942)
private val CardBlue = Color(0xFF3B82F6)
private val CardGray = Color(0xFF6B7280)
private val CardMuted = Color(0xFFAEB6C6)
private val CardDimText = Color(0xFF9AA3B5)

/** Left accent-bar color, coded by parsed quality. */
private fun qualityAccentColor(quality: String?): Color = when {
    quality == null -> CardGray
    quality.equals("4K", ignoreCase = true) || quality.equals("UHD", ignoreCase = true) || quality.startsWith("2160") -> CardGold
    quality.startsWith("1080") -> CardTeal
    quality.startsWith("720") -> CardBlue
    else -> CardGray
}

/** Non-blank lines of the stream title. */
private fun titleLines(stream: StreamItem): List<String> =
    stream.title?.lineSequence()
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?.toList()
        .orEmpty()

private const val FOLDER_MARK = "\uD83D\uDCC1" // 📁

/**
 * The release filename from a stream title.
 * NEW (PlavioHD v1.7.1): "\uD83D\uDCC1 {filename}\n{source} \u00b7 {quality} \u00b7 {size}".
 * OLD: "PlavioHD \u00b7 {source} \u00b7 {quality} \u00b7 {size}\n\uD83D\uDCC1 {filename}".
 */
private fun streamFilename(stream: StreamItem): String? {
    val lines = titleLines(stream)
    if (lines.isEmpty()) return null
    // New format: filename is the first line, marked with 📁.
    if (lines[0].startsWith(FOLDER_MARK)) return lines[0]
    // Old format: filename is the second line.
    if (lines.size >= 2 && lines[1].startsWith(FOLDER_MARK)) return lines[1]
    return null
}

/**
 * Upstream source name, e.g. "HDHub".
 * NEW: info line is "{source} · {quality} · {size}" (line 2 when line 1 is the 📁 filename).
 * OLD: info line is "PlavioHD · {source} · …".
 * Null for addons that don't tag their titles this way.
 */
private fun streamUpstreamSource(stream: StreamItem): String? {
    val lines = titleLines(stream)
    if (lines.isEmpty()) return null
    val infoLine =
        if (lines[0].startsWith(FOLDER_MARK) && lines.size >= 2) lines[1] else lines[0]
    val parts = infoLine.split("·").map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.isEmpty()) return null
    val qualitySizeRe = Regex("(?i)^(\\d+p|4k|uhd|\\d+(\\.\\d+)?\\s?(gb|mb))$")
    return if (parts[0].equals(stream.addonKey(), ignoreCase = true)) {
        // Old format: "PlavioHD · {source} · …" — source is the second part.
        parts.getOrNull(1)?.takeIf { it.isNotBlank() && !it.matches(qualitySizeRe) }
    } else if (parts.size >= 2) {
        // New format: "{source} · {quality} · {size}" — source is the first part.
        parts[0].takeIf { it.isNotBlank() && !it.matches(qualitySizeRe) }
    } else {
        null
    }
}

/** Everything a card layout needs, parsed once per stream. */
private data class CardInfo(
    val qualityBig: String,   // "1080p" / "2160p" / "HD"
    val qualitySuffix: String?, // "4K" or null
    val title: String,         // filename (📁 stripped) or headline fallback
    val upstream: String?,     // e.g. "HDHub"
    val addonBrand: String,    // uppercase, e.g. "PLAVIOHD"
    val brandDisplay: String,  // as-is, e.g. "PlavioHD"
    val size: String?,         // e.g. "5.96 GB"
    val sourceTag: String?,    // e.g. "WEB-DL"
    val codec: String?,        // e.g. "X264"
    val accent: Color,         // quality-coded color
    val qualityFrac: Float      // 0..1 for meter bars
)

private fun buildCardInfo(stream: StreamItem, info: ParsedStreamInfo): CardInfo {
    val (qualityBig, qualitySuffix) = when {
        info.quality.equals("4K", ignoreCase = true) ||
                info.quality.equals("UHD", ignoreCase = true) -> "2160p" to "4K"
        info.quality != null -> info.quality!! to null
        else -> "HD" to null
    }
    val rawFilename = streamFilename(stream)?.removePrefix(FOLDER_MARK)?.trim()
    val title = rawFilename?.takeIf { it.isNotBlank() } ?: streamHeadline(stream)
    val brand = stream.addonKey()
    val qualityFrac = when {
        qualityBig.startsWith("2160") -> 1f
        qualityBig.startsWith("1080") -> 0.72f
        qualityBig.startsWith("720") -> 0.5f
        qualityBig.startsWith("480") -> 0.35f
        else -> 0.25f
    }
    return CardInfo(
        qualityBig = qualityBig,
        qualitySuffix = qualitySuffix,
        title = title,
        upstream = streamUpstreamSource(stream),
        addonBrand = brand.uppercase(),
        brandDisplay = brand,
        size = info.size,
        sourceTag = info.source,
        codec = info.codec,
        accent = qualityAccentColor(info.quality),
        qualityFrac = qualityFrac
    )
}

/**
 * Shared card chrome: outer margins, clip, background, selected border,
 * tap handling and TV focus ring. Layouts add their own inner padding.
 *
 * [onClick] null = non-interactive preview: no click handling, no focus ring.
 * [compact] = true skips the list outer margins (for the settings preview).
 */
@Composable
private fun Modifier.streamCardBase(
    selected: Boolean,
    onClick: (() -> Unit)?,
    shape: RoundedCornerShape,
    background: Color,
    border: Color,
    selectedBorder: Color,
    compact: Boolean = false
): Modifier = this
    .fillMaxWidth()
    .then(if (compact) Modifier else Modifier.padding(horizontal = 16.dp, vertical = 5.dp))
    .clip(shape)
    .background(background)
    .border(
        if (selected) 1.5.dp else 1.dp,
        if (selected) selectedBorder else border,
        shape
    )
    .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    .then(if (onClick != null) Modifier.tvFocusRing(shape) else Modifier)

/** Entry point: parses once, then renders the user's chosen style. */
@Composable
fun StreamCard(
    stream: StreamItem,
    selected: Boolean = false,
    style: StreamCardStyle = StreamCardStyle.FUSION,
    onClick: () -> Unit
) {
    val palette = LocalPalette.current
    val info = remember(stream) { parseStreamInfo(stream) }
    val data = remember(stream, info) { buildCardInfo(stream, info) }
    when (style) {
        StreamCardStyle.FUSION -> FusionCard(data, selected, palette, onClick)
        StreamCardStyle.ELEGANT -> ElegantCard(data, selected, palette, onClick)
        StreamCardStyle.MIDNIGHT -> MidnightCard(data, selected, palette, onClick)
        StreamCardStyle.SIDE_RAIL -> SideRailCard(data, selected, palette, onClick)
        StreamCardStyle.METER -> MeterCard(data, selected, palette, onClick)
        StreamCardStyle.TILE_GRID -> TileStreamCard(stream, selected, onClick)
        // COMPACT_LIST is list-level (CompactListContainer), not per-card.
        StreamCardStyle.COMPACT_LIST -> FusionCard(data, selected, palette, onClick)
    }
}

/** Fixed sample data for the style-picker previews. */
private fun sampleCardInfo(): CardInfo = CardInfo(
    qualityBig = "1080p",
    qualitySuffix = null,
    title = "Game of Thrones S01E01 1080p BluRay",
    upstream = "HDHub",
    addonBrand = "PLAVIOHD",
    brandDisplay = "PlavioHD",
    size = "5.96 GB",
    sourceTag = "WEB-DL",
    codec = "X264",
    accent = CardTeal,
    qualityFrac = 0.72f
)

/** Three sample variants so multi-item previews show quality color variety. */
private fun sampleCardInfos(): List<CardInfo> = listOf(
    sampleCardInfo(),
    sampleCardInfo().copy(
        qualityBig = "2160p",
        qualitySuffix = "4K",
        title = "Dune Part Two 2024 2160p WEB-DL",
        upstream = "Vidy",
        size = "14.2 GB",
        sourceTag = "WEB-DL",
        codec = "X265",
        accent = CardGold,
        qualityFrac = 1f
    ),
    sampleCardInfo().copy(
        qualityBig = "720p",
        title = "Breaking Bad S05E16 720p HDTV",
        upstream = "Cinezo",
        size = "1.1 GB",
        sourceTag = "HDTV",
        codec = "X264",
        accent = CardBlue,
        qualityFrac = 0.5f
    )
)

/**
 * Non-interactive miniature of a stream card in the given style, used by the
 * Appearance settings style picker so users see each style before choosing.
 */
@Composable
fun StreamCardPreview(
    style: StreamCardStyle,
    modifier: Modifier = Modifier
) {
    val palette = LocalPalette.current
    val data = remember { sampleCardInfo() }
    Box(modifier = modifier) {
        when (style) {
            StreamCardStyle.FUSION -> FusionCard(data, false, palette, null, compact = true)
            StreamCardStyle.ELEGANT -> ElegantCard(data, false, palette, null, compact = true)
            StreamCardStyle.MIDNIGHT -> MidnightCard(data, false, palette, null, compact = true)
            StreamCardStyle.SIDE_RAIL -> SideRailCard(data, false, palette, null, compact = true)
            StreamCardStyle.METER -> MeterCard(data, false, palette, null, compact = true)
            StreamCardStyle.TILE_GRID -> {
                val samples = remember { sampleCardInfos().take(2) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    samples.forEach { s ->
                        TileCard(
                            data = s,
                            selected = false,
                            palette = palette,
                            onClick = null,
                            compact = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            StreamCardStyle.COMPACT_LIST -> {
                val samples = remember { sampleCardInfos() }
                CompactListPreview(samples)
            }
        }
    }
}

/** Non-interactive mini container with sample rows for the Compact List preview. */
@Composable
private fun CompactListPreview(samples: List<CardInfo>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF121828))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(vertical = 4.dp)
    ) {
        samples.forEachIndexed { i, s ->
            CompactListRow(data = s, selected = false, onClick = null)
            if (i < samples.lastIndex) {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .padding(horizontal = 12.dp)
                        .background(Color.White.copy(alpha = 0.06f))
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Style 1 — Fusion: gradient top edge, glowing gradient badge, pill chips
// ---------------------------------------------------------------------------

private val FusionBlue = Color(0xFF0EA5E9)
private val FusionViolet = Color(0xFF8B5CF6)

@Composable
private fun FusionCard(
    data: CardInfo,
    selected: Boolean,
    palette: AppPalette,
    onClick: (() -> Unit)?,
    compact: Boolean = false
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier.streamCardBase(
            selected, onClick, shape,
            background = Color(0xFF121828),
            border = FusionViolet.copy(alpha = 0.25f),
            selectedBorder = palette.accent,
        compact = compact
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(
                    Brush.horizontalGradient(listOf(CardTeal, FusionBlue, FusionViolet))
                )
        )
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(50),
                            spotColor = FusionViolet.copy(alpha = 0.6f)
                        )
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.linearGradient(listOf(CardTeal, FusionViolet))
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = data.qualityBig,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                }
                if (data.upstream != null) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = data.upstream!!.uppercase(),
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.weight(1f))
                if (data.size != null) {
                    Text(
                        text = data.size!!,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = data.title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (data.sourceTag != null) FusionPill(data.sourceTag!!, CardTeal)
                if (data.codec != null) FusionPill(data.codec!!, CardTeal)
                FusionPill(data.addonBrand, CardDimText, dim = true)
            }
        }
    }
}

@Composable
private fun FusionPill(text: String, color: Color, dim: Boolean = false) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (dim) Color.White.copy(alpha = 0.06f)
                else color.copy(alpha = 0.12f)
            )
            .border(
                1.dp,
                if (dim) Color.White.copy(alpha = 0.10f) else color.copy(alpha = 0.25f),
                RoundedCornerShape(50)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = if (dim) CardDimText else color,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}

// ---------------------------------------------------------------------------
// Style 2 — Elegant: refined minimal, thin quality type, letter-spaced labels
// ---------------------------------------------------------------------------

private val ElegantViolet = Color(0xFFA78BFA)

@Composable
private fun ElegantCard(
    data: CardInfo,
    selected: Boolean,
    palette: AppPalette,
    onClick: (() -> Unit)?,
    compact: Boolean = false
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .streamCardBase(
                selected, onClick, shape,
                background = Color(0xFF0F0F12),
                border = Color(0xFF26262E),
                selectedBorder = palette.accent,
            compact = compact
            )
            .padding(16.dp)
    ) {
        if (data.upstream != null) {
            Text(
                text = data.upstream!!.uppercase(),
                color = ElegantViolet,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 2.sp,
                maxLines = 1
            )
            Spacer(Modifier.height(6.dp))
        }
        Text(
            text = data.title,
            color = Color(0xFFF5F3FF),
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = data.qualityBig,
                color = Color.White,
                fontWeight = FontWeight.Light,
                fontSize = 22.sp,
                letterSpacing = 0.5.sp,
                maxLines = 1
            )
            if (data.qualitySuffix != null) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = data.qualitySuffix!!,
                    color = TextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .width(1.dp)
                    .height(20.dp)
                    .background(Color(0xFF33333D))
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = buildString {
                    var first = true
                    if (data.size != null) {
                        append(data.size); first = false
                    }
                    if (data.sourceTag != null) {
                        if (!first) append(" · "); append(data.sourceTag); first = false
                    }
                    if (data.codec != null) {
                        if (!first) append(" · "); append(data.codec)
                    }
                },
                color = Color(0xFF8E8E99),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = data.addonBrand,
                color = ElegantViolet,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 1.5.sp,
                maxLines = 1
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Style 3 — Midnight Neon: deep black, rainbow edge, neon stat cells
// ---------------------------------------------------------------------------

private val NeonTeal = Color(0xFF00F5D4)
private val NeonBlue = Color(0xFF00BBF9)
private val NeonViolet = Color(0xFF9B5DE5)
private val NeonPink = Color(0xFFF15BB5)

@Composable
private fun MidnightCard(
    data: CardInfo,
    selected: Boolean,
    palette: AppPalette,
    onClick: (() -> Unit)?,
    compact: Boolean = false
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier.streamCardBase(
            selected, onClick, shape,
            background = Color(0xFF05070D),
            border = Color(0xFF1A2333),
            selectedBorder = palette.accent,
        compact = compact
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(NeonTeal, NeonBlue, NeonViolet, NeonPink)
                    )
                )
        )
        Column(Modifier.padding(14.dp)) {
            Text(
                text = data.title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 19.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = listOfNotNull(
                    data.upstream?.uppercase(),
                    data.addonBrand
                ).joinToString(" · "),
                color = Color(0xFF5B6B85),
                fontSize = 11.sp,
                letterSpacing = 0.4.sp,
                maxLines = 1
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MidnightCell(
                    label = "Quality",
                    value = data.qualityBig,
                    color = NeonTeal,
                    modifier = Modifier.weight(1f)
                )
                MidnightCell(
                    label = "Size",
                    value = data.size ?: "—",
                    color = NeonViolet,
                    modifier = Modifier.weight(1f)
                )
                MidnightCell(
                    label = "Codec",
                    value = data.codec ?: data.sourceTag ?: "—",
                    color = NeonBlue,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MidnightCell(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(
                1.dp,
                Color.White.copy(alpha = 0.07f),
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label.uppercase(),
            color = Color(0xFF5B6B85),
            fontSize = 9.sp,
            letterSpacing = 1.sp,
            maxLines = 1
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            color = color,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ---------------------------------------------------------------------------
// Style 4 — Compact List: dense file-manager rows in one rounded container
// ---------------------------------------------------------------------------

/** Signal-bar count for the Meter style, derived from the quality label. */
private fun qualityBarCount(qualityBig: String): Int = when {
    qualityBig.startsWith("2160") -> 4
    qualityBig.startsWith("1080") -> 3
    qualityBig.startsWith("720") -> 2
    else -> 1
}

/** Friendly quality name for the Meter style sub-caption. */
private fun qualityDisplayName(qualityBig: String): String = when {
    qualityBig.startsWith("2160") -> "4K Ultra HD"
    qualityBig.startsWith("1080") -> "Full HD"
    qualityBig.startsWith("720") -> "HD"
    qualityBig.startsWith("480") -> "SD"
    else -> qualityBig
}

/**
 * One dense row: quality pill, stacked title + meta, bold size.
 * No per-item card chrome — lives inside [CompactListContainer].
 */
@Composable
private fun CompactListRow(
    data: CardInfo,
    selected: Boolean,
    onClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) data.accent.copy(alpha = 0.12f) else Color.Transparent)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(if (onClick != null) Modifier.tvFocusRing(RoundedCornerShape(6.dp)) else Modifier)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(data.accent)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = data.qualityBig,
                color = Color(0xFF0B0F14),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = data.title,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = buildString {
                    append(data.upstream ?: data.brandDisplay)
                    if (data.codec != null) append(" · ${data.codec}")
                    append(" · ${data.brandDisplay}")
                },
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = data.size ?: "",
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            maxLines = 1
        )
    }
}

/**
 * List-level container for the Compact List style: all rows share one
 * rounded container with thin dividers — no per-item card chrome.
 */
@Composable
fun CompactListContainer(
    streams: List<StreamItem>,
    onPick: (StreamItem) -> Unit,
    selectedUrl: String? = null,
    modifier: Modifier = Modifier
) {
    val rows = remember(streams) {
        streams.map { s -> s to buildCardInfo(s, parseStreamInfo(s)) }
    }
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(shape)
            .background(Color(0xFF121828))
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
            .padding(vertical = 4.dp)
    ) {
        rows.forEachIndexed { i, (stream, data) ->
            CompactListRow(
                data = data,
                selected = stream.url == selectedUrl,
                onClick = { onPick(stream) }
            )
            if (i < rows.lastIndex) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .padding(horizontal = 12.dp)
                        .background(Color.White.copy(alpha = 0.06f))
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Style 5 — Side Rail: full-height quality-colored rail, content on the right
// ---------------------------------------------------------------------------

@Composable
private fun SideRailCard(
    data: CardInfo,
    selected: Boolean,
    palette: AppPalette,
    onClick: (() -> Unit)?,
    compact: Boolean = false
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .streamCardBase(
                selected, onClick, shape,
                background = Color(0xFF101623),
                border = data.accent.copy(alpha = 0.22f),
                selectedBorder = palette.accent,
                compact = compact
            )
            .height(IntrinsicSize.Min)
    ) {
        Box(
            modifier = Modifier
                .width(76.dp)
                .fillMaxHeight()
                .background(
                    Brush.verticalGradient(
                        listOf(data.accent, data.accent.copy(alpha = 0.45f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = data.qualityBig,
                    color = Color(0xFF0B0F14),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    maxLines = 1
                )
                if (data.qualitySuffix != null) {
                    Text(
                        text = data.qualitySuffix,
                        color = Color(0xFF0B0F14),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "QUALITY",
                    color = Color(0xFF0B0F14).copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.sp,
                    letterSpacing = 1.5.sp,
                    maxLines = 1
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(12.dp)
        ) {
            Text(
                text = (data.upstream ?: data.brandDisplay).uppercase(),
                color = data.accent,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = data.title,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildString {
                    if (data.size != null) append(data.size)
                    if (data.codec != null) {
                        if (isNotEmpty()) append(" · ")
                        append(data.codec)
                    }
                    if (isNotEmpty()) append(" · ")
                    append(data.brandDisplay)
                },
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Style 6 — Tile Grid: 2-column grid of compact tiles
// ---------------------------------------------------------------------------

@Composable
private fun TileCard(
    data: CardInfo,
    selected: Boolean,
    palette: AppPalette,
    onClick: (() -> Unit)?,
    compact: Boolean = true,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .streamCardBase(
                selected, onClick, shape,
                background = Color(0xFF121828),
                border = data.accent.copy(alpha = 0.25f),
                selectedBorder = palette.accent,
                compact = compact
            )
            .padding(12.dp)
    ) {
        Text(
            text = data.qualityBig,
            color = data.accent,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 24.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = data.title,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = buildString {
                append(data.upstream ?: data.brandDisplay)
                if (data.codec != null) append(" · ${data.codec}")
            },
            color = TextSecondary,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = buildString {
                if (data.size != null) append(data.size)
                if (isNotEmpty()) append(" · ")
                append(data.brandDisplay)
            },
            color = TextSecondary,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Per-stream tile entry point: parses once, then renders the tile. */
@Composable
fun TileStreamCard(
    stream: StreamItem,
    selected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalPalette.current
    val info = remember(stream) { parseStreamInfo(stream) }
    val data = remember(stream, info) { buildCardInfo(stream, info) }
    TileCard(data, selected, palette, onClick, compact = true, modifier = modifier)
}

/** Non-lazy 2-column tile layout for use inside another LazyColumn. */
@Composable
private fun TileGridRows(
    streams: List<StreamItem>,
    onPick: (StreamItem) -> Unit,
    selectedUrl: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        streams.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { stream ->
                    TileStreamCard(
                        stream = stream,
                        selected = stream.url == selectedUrl,
                        onClick = { onPick(stream) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Style 7 — Meter: 4-bar signal meter visualizing the quality
// ---------------------------------------------------------------------------

@Composable
private fun MeterCard(
    data: CardInfo,
    selected: Boolean,
    palette: AppPalette,
    onClick: (() -> Unit)?,
    compact: Boolean = false
) {
    val shape = RoundedCornerShape(14.dp)
    val bars = qualityBarCount(data.qualityBig)
    Column(
        modifier = Modifier
            .streamCardBase(
                selected, onClick, shape,
                background = Color(0xFF101623),
                border = Color.White.copy(alpha = 0.08f),
                selectedBorder = palette.accent,
                compact = compact
            )
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                listOf(8.dp, 13.dp, 18.dp, 23.dp).forEachIndexed { i, h ->
                    Box(
                        Modifier
                            .width(5.dp)
                            .height(h)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (i < bars) data.accent
                                else Color.White.copy(alpha = 0.10f)
                            )
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = data.qualityBig,
                    color = TextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    maxLines = 1
                )
                Text(
                    text = "${qualityDisplayName(data.qualityBig)} · ${data.upstream ?: data.brandDisplay}",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = data.title,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = buildString {
                    if (data.codec != null) append(data.codec)
                    if (isNotEmpty()) append(" · ")
                    append(data.brandDisplay)
                },
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (data.size != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = data.size,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// List-level rendering: container adapts to the chosen style
// ---------------------------------------------------------------------------

/**
 * Stream list that adapts its container to the style:
 * - TILE_GRID: 2-column LazyVerticalGrid of tiles
 * - COMPACT_LIST: one rounded container with dense rows
 * - others: regular LazyColumn of cards
 */
@Composable
fun StreamList(
    streams: List<StreamItem>,
    style: StreamCardStyle,
    onPick: (StreamItem) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    selectedUrl: String? = null
) {
    when (style) {
        StreamCardStyle.TILE_GRID -> LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = modifier,
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(streams, key = { it.url }) { stream ->
                TileStreamCard(
                    stream = stream,
                    selected = stream.url == selectedUrl,
                    onClick = { onPick(stream) }
                )
            }
        }
        StreamCardStyle.COMPACT_LIST -> LazyColumn(
            modifier = modifier,
            contentPadding = contentPadding
        ) {
            item(key = "compact_list") {
                CompactListContainer(
                    streams = streams,
                    onPick = onPick,
                    selectedUrl = selectedUrl
                )
            }
        }
        else -> LazyColumn(
            modifier = modifier,
            contentPadding = contentPadding
        ) {
            itemsIndexed(streams, key = { index, s -> "${index}_${s.url}" }) { _, stream ->
                StreamCard(
                    stream = stream,
                    selected = stream.url == selectedUrl,
                    style = style
                ) { onPick(stream) }
            }
        }
    }
}

/**
 * LazyListScope variant for stream sections nested inside another LazyColumn
 * (e.g. the detail screen's inline sources): tile grid and compact list
 * render as single items, other styles as regular items.
 */
fun LazyListScope.streamListItems(
    streams: List<StreamItem>,
    style: StreamCardStyle,
    onPick: (StreamItem) -> Unit,
    selectedUrl: String? = null
) {
    when (style) {
        StreamCardStyle.TILE_GRID -> item(key = "tile_grid") {
            TileGridRows(streams = streams, onPick = onPick, selectedUrl = selectedUrl)
        }
        StreamCardStyle.COMPACT_LIST -> item(key = "compact_list") {
            CompactListContainer(streams = streams, onPick = onPick, selectedUrl = selectedUrl)
        }
        else -> itemsIndexed(streams, key = { index, s -> "${index}_${s.url}" }) { _, stream ->
            StreamCard(
                stream = stream,
                selected = stream.url == selectedUrl,
                style = style
            ) { onPick(stream) }
        }
    }
}
