package com.pulsestream.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulsestream.app.data.CatalogRow
import com.pulsestream.app.data.MetaPreview
import com.pulsestream.app.data.PlaybackRequest
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.ui.components.EmptyState
import com.pulsestream.app.ui.components.ErrorState
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.components.LoadingState
import com.pulsestream.app.ui.components.PosterCard
import com.pulsestream.app.ui.components.SectionTitle
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed interface LiveUiState {
    data object Loading : LiveUiState
    data object Empty : LiveUiState
    data class Content(val rows: List<CatalogRow>) : LiveUiState
    data class Error(val message: String) : LiveUiState
}

class LiveViewModel(private val repo: StreamRepository) : ViewModel() {
    var uiState by mutableStateOf<LiveUiState>(LiveUiState.Loading)
        private set

    /**
     * Cache-first: cached channels show instantly, fresh listings replace
     * them quietly in the background. Falls back to the old blocking path
     * on first run when nothing is cached yet.
     */
    fun load() {
        viewModelScope.launch {
            val cached = repo.loadTvRowsCached()
            if (cached.isNotEmpty()) {
                uiState = LiveUiState.Content(cached)
            } else {
                uiState = LiveUiState.Loading
            }
            try {
                val rows = repo.loadTvRows()
                uiState = when {
                    rows.isNotEmpty() -> LiveUiState.Content(rows)
                    cached.isNotEmpty() -> LiveUiState.Content(cached)
                    else -> LiveUiState.Empty
                }
            } catch (e: Exception) {
                if (cached.isEmpty()) {
                    uiState = LiveUiState.Error(e.message ?: "Couldn't load live channels")
                }
            }
        }
    }
}

/**
 * Live TV tab: aggregates "tv" catalogs from enabled addons.
 * Pulse bundles no channels — they come only from addons the user installs.
 */
@Composable
fun LiveScreen(
    repository: StreamRepository,
    onPlay: () -> Unit,
    onGoToAddons: () -> Unit
) {
    val vm: LiveViewModel = viewModel(factory = vmFactory { LiveViewModel(repository) })
    val scope = rememberCoroutineScope()
    var resolvingKey by remember { mutableStateOf<String?>(null) }
    var resolveError by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.load() }

    fun playChannel(row: CatalogRow, meta: MetaPreview) {
        if (resolvingKey != null) return
        val key = "${row.addonId}|${meta.id}"
        resolvingKey = key
        resolveError = null
        scope.launch {
            try {
                val result = withTimeoutOrNull(20_000) {
                    repository.streamResultsFlow("tv", meta.id)
                        .firstOrNull { it.streams.isNotEmpty() }
                }
                val stream = result?.streams?.firstOrNull()
                if (stream == null) {
                    resolveError = "No playable stream found for ${meta.name}."
                } else {
                    repository.pendingPlayback = PlaybackRequest(
                        streamUrl = stream.url,
                        title = meta.name,
                        poster = meta.poster,
                        videoId = meta.id,
                        subtitles = result?.subtitles ?: emptyList(),
                        imdbId = null,
                        isMovie = false
                    )
                    onPlay()
                }
            } catch (e: Exception) {
                resolveError = e.message ?: "Couldn't load the stream."
            } finally {
                resolvingKey = null
            }
        }
    }

    GlowBackground {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Live",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        LiveDot()
                    }
                    Text(
                        text = "Live TV channels from your addons",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                IconButton(onClick = { searching = !searching }) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = "Search channels",
                        tint = if (searching) LocalPalette.current.accent else TextSecondary
                    )
                }
            }

            // Live-only channel search.
            AnimatedVisibility(visible = searching) {
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
                        "with its manifest URL and its channels will appear here.",                    actionLabel = "Add an addon",
                    onAction = onGoToAddons
                )
                is LiveUiState.Content -> {
                    resolveError?.let { err ->
                        Text(
                            err,
                            color = Color(0xFFFF6B6B),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                    val q = query.trim()
                    val visibleRows = remember(state.rows, q) {
                        if (q.isBlank()) state.rows
                        else state.rows.map { row ->
                            row.copy(items = row.items.filter {
                                it.name.contains(q, ignoreCase = true)
                            })
                        }.filter { it.items.isNotEmpty() }
                    }
                    if (visibleRows.isEmpty() && q.isNotBlank()) {
                        EmptyState(
                            title = "No channels found",
                            message = "No live channels match \"$q\"."
                        )
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
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
                                    ) { _, meta ->
                                        val busy = resolvingKey == "${row.addonId}|${meta.id}"
                                        Box(contentAlignment = Alignment.Center) {
                                            PosterCard(
                                                meta = meta,
                                                badge = "LIVE",
                                                onClick = { playChannel(row, meta) }
                                            )
                                            if (busy) {
                                                CircularProgressIndicator(
                                                    color = LocalPalette.current.accent,
                                                    modifier = Modifier.padding(8.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

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
