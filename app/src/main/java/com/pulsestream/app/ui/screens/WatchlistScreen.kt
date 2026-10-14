package com.pulsestream.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulsestream.app.data.MetaPreview
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.ui.components.EmptyState
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.components.PosterCard
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

    GlowBackground {
        Column(Modifier.fillMaxSize()) {
            Text(
                text = "Watchlist",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black),
                color = TextPrimary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
            )
            Text(
                text = if (watchlist.isEmpty()) "" else "${watchlist.size} saved",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            )
            if (watchlist.isEmpty()) {
                EmptyState(
                    title = "Nothing saved yet",
                    message = "Bookmark movies and shows to build your watchlist."
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(watchlist, key = { it.key }) { item ->
                        // Visible remove button per item (the old path was
                        // Detail → bookmark toggle only).
                        Box {
                            PosterCard(
                                meta = MetaPreview(
                                    id = item.metaId,
                                    type = item.type,
                                    name = item.name,
                                    poster = item.poster
                                ),
                                onClick = { onOpenDetail(item.addonId, item.type, item.metaId) }
                            )
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        // The item is in the list, so toggle = remove.
                                        repository.toggleWatchlist(
                                            item.addonId,
                                            item.type,
                                            item.metaId,
                                            item.name,
                                            item.poster
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.65f))
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Remove from watchlist",
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
