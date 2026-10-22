package com.pulsestream.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulsestream.app.data.MetaPreview
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.ui.components.EmptyState
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun WatchlistScreen(
    repository: StreamRepository,
    onOpenDetail: (addonId: String, type: String, metaId: String) -> Unit
) {
    val watchlist by repository.watchlist.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(LibrarySort.RecentlyAdded) }
    var selecting by remember { mutableStateOf(false) }
    var selectedKeys by remember { mutableStateOf(setOf<String>()) }
    val snackbar = remember { SnackbarHostState() }
    val displayed = filterAndSortLibrary(watchlist, query, { it.name }, { it.addedAt }, sort)

    GlowBackground {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Text("Watchlist", style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black),
                    color = TextPrimary, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp))
                Text(if (watchlist.isEmpty()) "" else "${watchlist.size} saved", style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
                if (watchlist.isNotEmpty()) {
                    LibraryControls(
                        query = query, onQueryChange = { query = it }, sort = sort, onSortChange = { sort = it },
                        selectionCount = selectedKeys.size, selectionMode = selecting,
                        onToggleSelection = { selecting = !selecting; if (!selecting) selectedKeys = emptySet() },
                        onRemoveSelected = {
                            val removed = watchlist.filter { it.key in selectedKeys }
                            scope.launch {
                                removed.forEach { repository.toggleWatchlist(it.addonId, it.type, it.metaId, it.name, it.poster) }
                                selectedKeys = emptySet(); selecting = false
                                if (snackbar.showSnackbar("Removed ${removed.size} titles", "Undo") == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                    removed.forEach { repository.toggleWatchlist(it.addonId, it.type, it.metaId, it.name, it.poster) }
                                }
                            }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                if (watchlist.isEmpty()) {
                    EmptyState(title = "Nothing saved yet", message = "Bookmark movies and shows to build your watchlist.")
                } else if (displayed.isEmpty()) {
                    Text("No saved titles match ‘$query’. Your watchlist is unchanged.", color = TextSecondary,
                        modifier = Modifier.padding(24.dp))
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(140.dp), modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(displayed, key = { it.key }) { item ->
                            PosterWithRemoveAction(
                                meta = MetaPreview(id = item.metaId, type = item.type, name = item.name, poster = item.poster),
                                actionLabel = "Remove ${item.name} from watchlist",
                                onClick = {
                                    if (selecting) selectedKeys = if (item.key in selectedKeys) selectedKeys - item.key else selectedKeys + item.key
                                    else onOpenDetail(item.addonId, item.type, item.metaId)
                                },
                                onLongClick = { selecting = true; selectedKeys = selectedKeys + item.key },
                                onRemove = { scope.launch { repository.toggleWatchlist(item.addonId, item.type, item.metaId, item.name, item.poster) } }
                            )
                        }
                    }
                }
            }
            SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}
