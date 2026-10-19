package com.pulsestream.app.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulsestream.app.data.CatalogRow
import com.pulsestream.app.data.MetaPreview
import com.pulsestream.app.data.PlaybackRequest
import com.pulsestream.app.data.StreamItem
import com.pulsestream.app.data.TvChannelRef
import com.pulsestream.app.ui.components.EmptyState
import com.pulsestream.app.ui.components.ErrorState
import com.pulsestream.app.ui.components.LoadingState
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.components.PosterCard
import com.pulsestream.app.ui.components.SectionTitle
import com.pulsestream.app.ui.theme.GlassBorder
import com.pulsestream.app.ui.theme.GlassWhite
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private sealed interface LiveUiState {
    data object Loading : LiveUiState
    data class Content(
        val rows: List<CatalogRow>,
        val m3uRows: List<CatalogRow>,
        val playlists: List<String>,
        val m3uErrors: List<String> = emptyList()
    ) : LiveUiState
    data class Error(val message: String) : LiveUiState
    data object Empty : LiveUiState
}

private class LiveViewModel(
    private val repository: com.pulsestream.app.data.StreamRepository
) : androidx.lifecycle.ViewModel() {
    var uiState: LiveUiState by mutableStateOf(LiveUiState.Loading)
        private set

    init {
        load()
    }

    fun load() {
        uiState = LiveUiState.Loading
        viewModelScope.launch {
            try {
                val rows = repository.loadTvRows()
                val m3u = repository.loadM3uRows()
                val playlists = repository.settings.m3uPlaylists
                uiState = if (rows.isEmpty() && m3u.rows.isEmpty()) {
                    // A failed playlist must say so, not show the generic
                    // "no live TV" empty state.
                    if (m3u.errors.isNotEmpty()) LiveUiState.Error(m3u.errors.joinToString("\n"))
                    else LiveUiState.Empty
                } else {
                    LiveUiState.Content(rows, m3u.rows, playlists, m3u.errors)
                }
            } catch (e: Exception) {
                uiState = LiveUiState.Error("Couldn't load live TV: ${e.message}")
            }
        }
    }

    fun toggleFavorite(key: String) = repository.toggleLiveFavorite(key)
}

private fun channelKey(row: CatalogRow, meta: MetaPreview) = "${row.addonId}|${meta.id}"

/**
 * The Live tab: TV channels from addons + M3U playlists, with pinned
 * favorites, recently-watched channels, category chips that actually
 * filter, long-press to pin, and a top-right M3U import.
 */
@Composable
fun LiveScreen(
    onPlay: (PlaybackRequest) -> Unit,
    onGoToAddons: () -> Unit,
    repository: com.pulsestream.app.data.StreamRepository
) {
    val scope = rememberCoroutineScope()
    val vm: LiveViewModel = viewModel(factory = remember {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                LiveViewModel(repository) as T
        }
    })
    var query by remember { mutableStateOf("") }
    var resolvingKey by remember { mutableStateOf<String?>(null) }
    var resolveError by remember { mutableStateOf<String?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var showM3uDialog by remember { mutableStateOf(false) }
    /** Selected category chip; null = All. */
    var category by remember { mutableStateOf<String?>(null) }
    /** Bumped to re-read favorites/recents after a toggle. */
    var favVersion by remember { mutableIntStateOf(0) }

    // Back clears the query AND closes search — never just one of them.
    BackHandler(enabled = showSearch) {
        query = ""
        showSearch = false
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.load()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    fun channelRef(row: CatalogRow, meta: MetaPreview) = TvChannelRef(
        addonId = row.addonId,
        metaId = meta.id,
        name = meta.name,
        poster = meta.poster
    )

    fun playChannel(row: CatalogRow, meta: MetaPreview, flatList: List<TvChannelRef>) {
        // Player zapping: hand over the whole visible list + this channel's
        // index BEFORE playing, so channel up/down has a valid list to work
        // from on the very first play.
        val idx = flatList.indexOfFirst { it.addonId == row.addonId && it.metaId == meta.id }
        repository.setTvZapList(flatList, if (idx >= 0) idx else 0)
        if (row.addonId == "m3u") {
            // M3U: the channel id IS the stream URL — play it directly.
            // (No resolution step exists here, so this is as "after
            // successful playback start" as the Live tab can know.)
            repository.pushLiveRecent(channelKey(row, meta))
            favVersion++ // re-render recents row
            onPlay(
                PlaybackRequest(
                    streamUrl = meta.id,
                    title = meta.name,
                    poster = meta.poster,
                    type = "tv",
                    videoId = meta.id,
                    streamLabel = "LIVE"
                )
            )
            return
        }
        val key = channelKey(row, meta)
        resolvingKey = key
        resolveError = null
        scope.launch {
            try {
                var streams = emptyList<StreamItem>()
                withTimeoutOrNull(25_000) {
                    repository.streamResultsFlow("tv", meta.id).collect { r ->
                        if (r.streams.isNotEmpty()) streams = r.streams
                    }
                }
                val first = streams.firstOrNull()
                if (first == null) {
                    resolveError = "No playable stream found for \"${meta.name}\"."
                } else {
                    // Resolution succeeded — only now does the channel earn a
                    // place in Recently Watched; failed channels stay out.
                    repository.pushLiveRecent(key)
                    favVersion++ // re-render recents row
                    onPlay(
                        PlaybackRequest(
                            streamUrl = first.url,
                            headers = first.headers,
                            title = meta.name,
                            poster = meta.poster,
                            type = "tv",
                            videoId = meta.id,
                            streamLabel = "LIVE",
                            sourceName = first.sourceName
                        )
                    )
                }
            } catch (e: Exception) {
                resolveError = "Couldn't load \"${meta.name}\": ${e.message}"
            } finally {
                resolvingKey = null
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // ---- Header ----
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveDot()
                Spacer(Modifier.width(8.dp))
                Text(
                    "Live TV",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showSearch = !showSearch }) {
                Icon(
                    if (showSearch) Icons.Filled.Close else Icons.Filled.Search,
                    "Search",
                    tint = TextSecondary
                )
            }
        }

        if (showSearch) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search live channels…") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(50)
            )
        }

        when (val state = vm.uiState) {
            is LiveUiState.Loading -> LoadingState("Finding live channels…")
            is LiveUiState.Error -> ErrorState(state.message, onRetry = { vm.load() })
            is LiveUiState.Empty -> EmptyState(
                title = "No live TV yet",
                message = "Plavio doesn't bundle any channels. Install a live TV addon " +
                    "with its manifest URL — or import an M3U playlist from the top-right " +
                    "button — and its channels will appear here.",
                actionLabel = "Add an addon",
                onAction = onGoToAddons
            )
            is LiveUiState.Content -> {
                val allRows = remember(state.rows, state.m3uRows) {
                    state.rows + state.m3uRows
                }
                val q = query.trim()
                val queryFiltered = remember(allRows, q) {
                    if (q.isBlank()) allRows
                    else allRows.map { row ->
                        row.copy(items = row.items.filter {
                            it.name.contains(q, ignoreCase = true)
                        })
                    }.filter { it.items.isNotEmpty() }
                }
                // Category chips derived from catalog names (e.g. M3U group-titles
                // like News/Sports/Movies/Kids); tapping one shows ONLY those rows.
                val categories = remember(queryFiltered) {
                    queryFiltered.map { it.catalogName }.distinct()
                }
                val visibleRows = remember(queryFiltered, category) {
                    if (category == null) queryFiltered
                    else queryFiltered.filter { it.catalogName == category }
                }
                // Favorites + recents, resolved against the loaded rows.
                val favorites = remember(allRows, favVersion) {
                    val keys = repository.settings.liveFavorites
                    allRows.flatMap { row ->
                        row.items.filter { channelKey(row, it) in keys }
                            .map { row to it }
                    }.distinctBy { (row, meta) -> channelKey(row, meta) }
                }
                val recents = remember(allRows, favVersion) {
                    val keys = repository.settings.liveRecent
                    keys.mapNotNull { key ->
                        allRows.firstNotNullOfOrNull { row ->
                            row.items.firstOrNull { channelKey(row, it) == key }
                                ?.let { row to it }
                        }
                    }.distinctBy { (row, meta) -> channelKey(row, meta) }
                }
                // Flat visible list for the player zapping handoff.
                val flatList = remember(visibleRows, favorites, recents) {
                    (favorites + recents + visibleRows.flatMap { row ->
                        row.items.map { row to it }
                    }).distinctBy { (row, meta) -> channelKey(row, meta) }
                        .map { (row, meta) -> channelRef(row, meta) }
                }

                @Composable
                fun ChannelCard(row: CatalogRow, meta: MetaPreview, badge: String = "LIVE") {
                    val key = channelKey(row, meta)
                    val pinned = key in repository.settings.liveFavorites
                    val busy = resolvingKey == key
                    Box(contentAlignment = Alignment.Center) {
                        // The long-press lives ON the card itself: an inner
                        // clickable under an outer combinedClickable consumes
                        // the press, which is why pinning never fired.
                        PosterCard(
                            meta = meta,
                            badge = badge,
                            onClick = { playChannel(row, meta, flatList) },
                            onLongClick = { vm.toggleFavorite(key); favVersion++ }
                        )
                        if (pinned) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Star, "Pinned",
                                    tint = Color(0xFFFFD54F),
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .size(14.dp)
                                )
                            }
                        }
                        if (busy) {
                            CircularProgressIndicator(
                                color = LocalPalette.current.accent,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }

                resolveError?.let { err ->
                    Text(
                        err,
                        color = Color(0xFFFF6B6B),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                // Failed playlists are named, not silently dropped.
                state.m3uErrors.forEach { err ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "⚠ $err",
                            color = Color(0xFFFFB74D),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { vm.load() }) {
                            Text("Retry", color = LocalPalette.current.accent)
                        }
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    // ---- Category chips ----
                    if (categories.size > 1 || (categories.size == 1 && category != null)) {
                        item(key = "live|chips") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                item(key = "chip|all") {
                                    FilterChip(
                                        selected = category == null,
                                        onClick = { category = null },
                                        label = { Text("All") },
                                        colors = chipColors()
                                    )
                                }
                                categories.forEach { cat ->
                                    item(key = "chip|$cat") {
                                        FilterChip(
                                            selected = category == cat,
                                            onClick = { category = if (category == cat) null else cat },
                                            label = { Text(cat) },
                                            colors = chipColors()
                                        )
                                    }
                                }
                            }
                        }
                    }
                    // ---- Pinned favorites ----
                    if (favorites.isNotEmpty()) {
                        item(key = "live|favs") {
                            SectionTitle("Pinned channels", "long-press any channel to pin")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                itemsIndexed(
                                    favorites,
                                    key = { _, pair -> "fav|${channelKey(pair.first, pair.second)}" }
                                ) { _, (row, meta) -> ChannelCard(row, meta) }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    // ---- Recently watched ----
                    if (recents.isNotEmpty()) {
                        item(key = "live|recent") {
                            SectionTitle("Recently watched", null)
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                itemsIndexed(
                                    recents,
                                    key = { _, pair -> "rec|${channelKey(pair.first, pair.second)}" }
                                ) { _, (row, meta) -> ChannelCard(row, meta) }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    // ---- Channel rows ----
                    if (visibleRows.isEmpty() && (q.isNotBlank() || category != null)) {
                        item(key = "live|noresult") {
                            EmptyState(
                                title = "No channels found",
                                message = "Nothing matches this filter."
                            )
                        }
                    }
                    visibleRows.forEach { row ->
                        item(key = "live|${row.addonId}|${row.type}|${row.catalogId}") {
                            SectionTitle(row.catalogName, row.addonName)
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                itemsIndexed(
                                    row.items,
                                    key = { index, meta -> "live_${row.addonId}_${meta.id}_$index" }
                                ) { _, meta -> ChannelCard(row, meta) }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }

        if (showM3uDialog) {
            M3uImportDialog(
                playlists = (vm.uiState as? LiveUiState.Content)?.playlists ?: emptyList(),
                onDismiss = { showM3uDialog = false },
                onAdd = { url ->
                    // Belt-and-braces: addM3uPlaylist returns Result instead of
                    // throwing, but a throw here would still kill the dialog's
                    // coroutine — never let it escape.
                    try {
                        repository.addM3uPlaylist(url).also { result ->
                            if (result.isSuccess) vm.load()
                        }
                    } catch (e: Exception) {
                        Result.failure<Int>(e)
                    }
                },
                onRemove = { url ->
                    scope.launch {
                        repository.removeM3uPlaylist(url)
                        vm.load()
                    }
                }
            )
        }
    }
}

/**
 * M3U/IPTV playlist import dialog (top-right button of the Live tab).
 * The playlist is fetched + parsed immediately, so a bad URL fails here
 * with a readable error instead of silently adding nothing.
 */
@Composable
private fun M3uImportDialog(
    playlists: List<String>,
    onDismiss: () -> Unit,
    onAdd: suspend (String) -> Result<Int>,
    onRemove: (String) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LocalPalette.current.panel,
        title = { Text("Import M3U playlist", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (success != null) {
                    Text(success!!, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        "Paste an M3U / IPTV playlist URL — its channels appear grouped " +
                            "in the Live tab and play in the Plavio player.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it; error = null },
                        placeholder = { Text("https://…/playlist.m3u", color = TextSecondary) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LocalPalette.current.accent,
                            unfocusedBorderColor = GlassBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = LocalPalette.current.accent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (error != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            error!!,
                            color = Color(0xFFFF6B6B),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (playlists.isNotEmpty() && success == null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Imported playlists",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(4.dp))
                    playlists.forEach { pl ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                runCatching { java.net.URI(pl).host ?: pl }.getOrDefault(pl),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onRemove(pl) }) {
                                Icon(
                                    Icons.Filled.Delete, "Remove playlist",
                                    tint = TextSecondary, modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (success == null) {
                TextButton(
                    onClick = {
                        val u = url.trim()
                        if (u.isBlank()) {
                            error = "Paste a playlist URL first."
                            return@TextButton
                        }
                        adding = true
                        error = null
                        scope.launch {
                            val result = onAdd(u)
                            adding = false
                            if (result.isSuccess) {
                                val count = result.getOrNull() ?: 0
                                success = "Imported $count channel${if (count == 1) "" else "s"} ✓"
                            } else {
                                error = result.exceptionOrNull()?.message
                                    ?: "Couldn't import that playlist."
                            }
                        }
                    },
                    enabled = !adding && url.isNotBlank()
                ) {
                    Text(
                        if (adding) "Importing…" else "Import",
                        color = LocalPalette.current.accent,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Done", color = LocalPalette.current.accent, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (success == null) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
            }
        }
    )
}

/** FilterChip colors matching the Plavio dark aesthetic. */
@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = LocalPalette.current.accent.copy(alpha = 0.25f),
    selectedLabelColor = TextPrimary,
    containerColor = GlassWhite,
    labelColor = TextSecondary,
    selectedLeadingIconColor = LocalPalette.current.accent
)

/** Live indicator: a red dot with a white center, gently pulsing. */
@Composable
private fun LiveDot(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "livedot")
    val alpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "livedotAlpha"
    )
    Box(
        modifier.size(15.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(15.dp)
                .clip(CircleShape)
                .background(Color(0xFFEF4444).copy(alpha = alpha))
        )
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}
