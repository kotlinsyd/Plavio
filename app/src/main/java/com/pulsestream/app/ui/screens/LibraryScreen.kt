package com.pulsestream.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulsestream.app.data.CustomListsRepository
import com.pulsestream.app.data.MetaPreview
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.data.download.DownloadManager
import com.pulsestream.app.data.db.CustomList
import com.pulsestream.app.data.simkl.SimklApi
import com.pulsestream.app.data.trakt.TraktApi
import com.pulsestream.app.data.trakt.TraktManager
import com.pulsestream.app.ui.components.OrbitDots
import com.pulsestream.app.ui.components.PosterCard
import com.pulsestream.app.ui.components.SafePosterImage
import com.pulsestream.app.ui.components.SectionTitle
import com.pulsestream.app.ui.components.liquidGlass
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Library (owner's design, 2026-10-24): one home for everything that is
 * the user's — Up Next from Trakt/Simkl progress (continue where you
 * left in any app; the player already scrobbles back), the Trakt and
 * Simkl watchlists, the local watchlist, and custom lists. Replaces
 * the Live tab in the dock; the old Watchlist/Lists screens stay as
 * the "See all" destinations.
 */
private data class UpNextCard(
    val imdbId: String,
    val showTitle: String,
    val service: String,
    val nextLabel: String,
    val metaLine: String,
    val progress: Float?,
    val poster: String?,
    val still: String?,
    val lastWatchedMs: Long
)

@Composable
fun LibraryScreen(
    repository: StreamRepository,
    listsRepository: CustomListsRepository,
    onOpenDetail: (addonId: String, type: String, metaId: String) -> Unit,
    onOpenList: (listId: String) -> Unit,
    onGoToWatchlist: () -> Unit,
    onGoToLists: () -> Unit,
    onGoToDownloads: () -> Unit = {},
    onPlayDownload: (filePath: String, title: String) -> Unit = { _, _ -> },
    onConnect: () -> Unit
) {
    val settings = repository.settings
    val palette = LocalPalette.current
    val traktLinked = !settings.traktAccessToken.isNullOrBlank()
    val simklLinked = !settings.simklToken.isNullOrBlank()

    var loading by remember { mutableStateOf(traktLinked || simklLinked) }
    var upNext by remember { mutableStateOf<List<UpNextCard>>(emptyList()) }
    var traktWatchlist by remember { mutableStateOf<List<MetaPreview>>(emptyList()) }
    var simklWatchlist by remember { mutableStateOf<List<MetaPreview>>(emptyList()) }

    val context = LocalContext.current
    val downloadManager = remember { DownloadManager.get(context) }
    val downloads by downloadManager.observeAll().collectAsState(initial = emptyList())
    val completedDownloads = remember(downloads) {
        downloads.filter { it.status == "COMPLETED" }
    }

    val myWatchlist by repository.watchlist.collectAsState(initial = emptyList())
    val myLists by listsRepository.lists.collectAsState(initial = emptyList())
    val syncedIds = remember(settings.librarySyncedKeys) {
        settings.librarySyncedKeys.map { it.substringAfter('|') }.toSet()
    }

    LaunchedEffect(traktLinked, simklLinked) {
        if (!traktLinked && !simklLinked) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        val traktApi = TraktApi()
        val simklApi = SimklApi()
        val traktToken = if (traktLinked) {
            runCatching { TraktManager(settings).validAccessToken() }.getOrNull()
        } else null
        val simklToken = settings.simklToken?.takeIf { it.isNotBlank() }

        // Posters/stills by IMDb id, fetched a few at a time.
        suspend fun metaFor(type: String, imdb: String) =
            runCatching { repository.getMetaFromAny(null, type, imdb) }.getOrNull()
        suspend fun previews(entries: List<Pair<String, Pair<String, String>>>): List<MetaPreview> =
            coroutineScope {
                entries.map { (type, entry) ->
                    async {
                        val (imdb, title) = entry
                        val meta = metaFor(type, imdb)
                        MetaPreview(
                            id = imdb, type = type,
                            name = meta?.name ?: title,
                            poster = meta?.poster
                        )
                    }
                }.awaitAll()
            }

        val cards = mutableListOf<UpNextCard>()
        if (traktToken != null) {
            runCatching { traktApi.getWatchlist(traktToken) }.getOrNull()?.let { entries ->
                traktWatchlist = previews(entries.take(20).map { it.type to (it.imdbId to it.title) })
            }
            runCatching { traktApi.getUpNext(traktToken) }.getOrNull()?.forEach { e ->
                val meta = metaFor("series", e.imdbId)
                val still = meta?.videos?.firstOrNull {
                    it.season == e.nextSeason && it.episode == e.nextEpisode
                }?.thumbnail
                cards += UpNextCard(
                    imdbId = e.imdbId,
                    showTitle = meta?.name ?: e.title,
                    service = "Trakt",
                    nextLabel = "S${e.nextSeason} · E${e.nextEpisode}" +
                        (e.nextTitle?.let { " — $it" } ?: ""),
                    metaLine = "You watched S${e.lastSeason} · E${e.lastEpisode} · on Trakt",
                    progress = if (e.airedCount > 0) {
                        e.watchedCount.toFloat() / e.airedCount.toFloat()
                    } else null,
                    poster = meta?.poster,
                    still = still,
                    lastWatchedMs = e.lastWatchedAtMs
                )
            }
        }
        if (simklToken != null) {
            runCatching { simklApi.getPlanToWatch(simklToken) }.getOrNull()?.let { entries ->
                simklWatchlist = previews(entries.take(20).map { it.type to (it.imdbId to it.title) })
            }
            val traktIds = cards.map { it.imdbId }.toSet()
            runCatching { simklApi.getWatching(simklToken) }.getOrNull()
                ?.filter { it.imdbId !in traktIds }
                ?.forEach { e ->
                    val meta = metaFor("series", e.imdbId)
                    val lastMs = e.lastWatchedAt?.let {
                        runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull()
                    } ?: 0L
                    val counts = if (e.watchedEpisodes != null && e.totalEpisodes != null) {
                        "${e.watchedEpisodes} of ${e.totalEpisodes} episodes watched · on Simkl"
                    } else "In progress · on Simkl"
                    cards += UpNextCard(
                        imdbId = e.imdbId,
                        showTitle = meta?.name ?: e.title,
                        service = "Simkl",
                        nextLabel = "Continue watching",
                        metaLine = counts,
                        progress = if (e.watchedEpisodes != null && e.totalEpisodes != null && e.totalEpisodes > 0) {
                            e.watchedEpisodes.toFloat() / e.totalEpisodes.toFloat()
                        } else null,
                        poster = meta?.poster,
                        still = null,
                        lastWatchedMs = lastMs
                    )
                }
        }
        upNext = cards.sortedByDescending { it.lastWatchedMs }.take(10)
        loading = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 110.dp)
    ) {
        item(key = "lib_header") {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp)) {
                Text(
                    "Library",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ServiceChip("Trakt", traktLinked, settings.traktUsername, Modifier.weight(1f), onConnect)
                    ServiceChip("Simkl", simklLinked, settings.simklUsername, Modifier.weight(1f), onConnect)
                }
            }
        }

        if (!traktLinked && !simklLinked) {
            item(key = "connect_card") {
                ConnectCard(onConnect)
            }
        }

        if (loading && upNext.isEmpty() && traktWatchlist.isEmpty() && simklWatchlist.isEmpty()) {
            item(key = "lib_loading") {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    OrbitDots(modifier = Modifier.size(38.dp))
                }
            }
        }

        if (upNext.isNotEmpty()) {
            item(key = "upnext_title") {
                SectionTitle("Up Next")
            }
            items(upNext, key = { "up_${it.service}_${it.imdbId}" }) { card ->
                UpNextRow(card) { onOpenDetail("", "series", card.imdbId) }
            }
        }

        if (traktWatchlist.isNotEmpty()) {
            item(key = "trakt_wl") {
                FeedRow("Watchlist", "TRAKT", Color(0xFFFF858D), traktWatchlist, onOpenDetail)
            }
        }
        if (simklWatchlist.isNotEmpty()) {
            item(key = "simkl_wl") {
                FeedRow("Watchlist", "SIMKL", Color(0xFFFFB35C), simklWatchlist, onOpenDetail)
            }
        }

        if (myWatchlist.isNotEmpty()) {
            item(key = "my_wl_title") {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "My Watchlist",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "See all",
                        color = palette.accent,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clickable { onGoToWatchlist() }
                    )
                }
            }
            item(key = "my_wl_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    items(myWatchlist.take(15), key = { it.key }) { item ->
                        PosterCard(
                            meta = MetaPreview(
                                id = item.metaId, type = item.type,
                                name = item.name, poster = item.poster
                            ),
                            onClick = { onOpenDetail(item.addonId, item.type, item.metaId) }
                        )
                    }
                }
            }
        }

        if (completedDownloads.isNotEmpty()) {
            item(key = "dl_title") {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Downloads",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "See all",
                        color = palette.accent,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clickable { onGoToDownloads() }
                    )
                }
            }
            item(key = "dl_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    items(completedDownloads.take(15), key = { it.id }) { item ->
                        DownloadCard(item) {
                            onPlayDownload(item.filePath, item.title)
                        }
                    }
                }
            }
        }

        if (myLists.isNotEmpty()) {
            item(key = "my_lists_title") {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "My Lists",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "See all",
                        color = palette.accent,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clickable { onGoToLists() }
                    )
                }
            }
            item(key = "my_lists_cards") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    myLists.take(2).forEach { list ->
                        ListCollageCard(
                            list = list,
                            listsRepository = listsRepository,
                            modifier = Modifier.weight(1f)
                        ) { onOpenList(list.id) }
                    }
                    if (myLists.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        if (!loading && upNext.isEmpty() && traktWatchlist.isEmpty() && simklWatchlist.isEmpty() &&
            myWatchlist.isEmpty() && myLists.isEmpty()
        ) {
            item(key = "lib_empty") {
                Text(
                    if (traktLinked || simklLinked) {
                        "Nothing here yet — your watchlists and progress will show up once there is something to show."
                    } else {
                        "Nothing here yet — add something to your watchlist, or connect Trakt/Simkl above."
                    },
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 28.dp)
                )
            }
        }
    }
}

private fun formatDownloadSize(totalBytes: Long): String {
    if (totalBytes <= 0L) return ""
    val gb = totalBytes / 1_073_741_824.0
    if (gb >= 1.0) return "%.1f GB".format(gb)
    val mb = totalBytes / 1_048_576.0
    if (mb >= 1.0) return "%d MB".format(mb.toLong())
    return "%d KB".format(totalBytes / 1024L)
}

/**
 * Downloads carry no posters, so the Library tile is a glass card with
 * the title's initial, a download badge and the file size — tap plays
 * the file, same as the Downloads screen.
 */
@Composable
private fun DownloadCard(
    item: com.pulsestream.app.data.db.DownloadItem,
    onClick: () -> Unit
) {
    val palette = LocalPalette.current
    Column(
        modifier = Modifier
            .width(104.dp)
            .liquidGlass(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(9.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                item.title.take(1).uppercase(),
                color = TextSecondary,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(palette.accent, RoundedCornerShape(50))
                    .size(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("↓", color = Color(0xFF04140B), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            item.title,
            color = TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        formatDownloadSize(item.totalBytes).takeIf { it.isNotBlank() }?.let { size ->
            Text(size, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ServiceChip(
    name: String,
    linked: Boolean,
    username: String?,
    modifier: Modifier = Modifier,
    onConnect: () -> Unit
) {
    val palette = LocalPalette.current
    Row(
        modifier = modifier
            .background(
                if (linked) Color.White.copy(alpha = 0.06f) else Color.Transparent,
                RoundedCornerShape(50)
            )
            .border(
                1.dp,
                if (linked) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.25f),
                RoundedCornerShape(50)
            )
            .clickable(enabled = !linked) { onConnect() }
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (linked) {
            Box(
                Modifier.size(7.dp).background(palette.accent, RoundedCornerShape(50))
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (username.isNullOrBlank()) name else "$name · $username",
                color = TextPrimary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            Text(
                "$name — connect",
                color = TextSecondary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ConnectCard(onConnect: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        palette.accent.copy(alpha = 0.12f),
                        palette.accent2.copy(alpha = 0.12f)
                    )
                ),
                RoundedCornerShape(16.dp)
            )
            .border(1.dp, palette.accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Bring your Trakt & Simkl",
                color = TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "Connect once and your watchlists, progress and next episodes follow you here — and back.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(listOf(palette.accent, palette.accent2)),
                    RoundedCornerShape(50)
                )
                .clickable { onConnect() }
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                "Connect",
                color = Color(0xFF04140B),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun UpNextRow(card: UpNextCard, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .liquidGlass(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(104.dp)
                .height(62.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.05f))
        ) {
            SafePosterImage(
                model = card.still ?: card.poster,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp)
                    .background(palette.accent, RoundedCornerShape(50))
                    .size(19.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("▶", color = Color(0xFF04140B), fontSize = 8.sp)
            }
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                card.showTitle,
                color = TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                card.nextLabel,
                color = TextPrimary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                card.metaLine,
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
            card.progress?.let { p ->
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.White.copy(alpha = 0.13f), RoundedCornerShape(50))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(p.coerceIn(0f, 1f))
                            .height(4.dp)
                            .background(
                                Brush.horizontalGradient(listOf(palette.accent, palette.accent2)),
                                RoundedCornerShape(50)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedRow(
    title: String,
    serviceTag: String,
    tagColor: Color,
    metas: List<MetaPreview>,
    onOpenDetail: (addonId: String, type: String, metaId: String) -> Unit
) {
    Column(Modifier.padding(top = 10.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                serviceTag,
                color = tagColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            items(metas, key = { "${it.type}_${it.id}" }) { meta ->
                PosterCard(
                    meta = meta,
                    onClick = { onOpenDetail("", meta.type, meta.id) }
                )
            }
        }
    }
}

@Composable
private fun ListCollageCard(
    list: CustomList,
    listsRepository: CustomListsRepository,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val items by listsRepository.observeItems(list.id).collectAsState(initial = emptyList())
    val posters = items.mapNotNull { it.poster }.take(4)
    Column(
        modifier = modifier
            .liquidGlass(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(9.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(76.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.05f))
        ) {
            if (posters.isEmpty()) {
                Text(
                    list.name.take(1).uppercase(),
                    color = TextSecondary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                Row(Modifier.fillMaxSize()) {
                    posters.forEach { poster ->
                        SafePosterImage(
                            model = poster,
                            contentDescription = null,
                            modifier = Modifier.weight(1f).fillMaxSize()
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            list.name,
            color = TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "${items.size} titles",
            color = TextSecondary,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
