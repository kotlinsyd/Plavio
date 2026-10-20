package com.pulsestream.app.ui.screens

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulsestream.app.data.PersonDetail
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.components.PosterCard
import com.pulsestream.app.ui.components.SafePosterImage
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary

/**
 * Actor filmography page (v0.8.31+): photo + name at the top, then grids of
 * their movies and series. Tapping a title opens the normal detail page.
 * Backed by PlavioMeta /person (needs TMDB server-side; empty when keyless).
 */
@Composable
fun ActorScreen(
    repository: StreamRepository,
    personId: Int,
    onBack: () -> Unit,
    onOpenDetail: (type: String, metaId: String) -> Unit
) {
    var person by remember(personId) { mutableStateOf<PersonDetail?>(null) }
    var loading by remember(personId) { mutableStateOf(true) }
    val tmdbKey = remember { repository.settings.tmdbApiKey.takeIf { it.isNotBlank() } }

    LaunchedEffect(personId) {
        loading = true
        // Try TMDB directly first (user's key), fall back to PlavioMeta
        person = if (!tmdbKey.isNullOrBlank()) {
            runCatching { com.pulsestream.app.data.TmdbClient.getPerson(tmdbKey, personId) }.getOrNull()
        } else {
            runCatching { repository.getPerson(personId) }.getOrNull()
        }
        loading = false
    }

    GlowBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item(key = "header") {
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
                        text = "Actor",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }
            when {
                loading -> item(key = "loading") {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = LocalPalette.current.accent)
                    }
                }
                person == null -> item(key = "empty") {
                    Text(
                        "Couldn't load this actor.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                else -> {
                    val p = person!!
                    item(key = "info") {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SafePosterImage(
                                model = p.photo,
                                contentDescription = p.name,
                                modifier = Modifier
                                    .size(88.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = p.name,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(Modifier.height(4.dp))
                                val total = p.movies.size + p.series.size
                                Text(
                                    text = "$total titles",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (p.movies.isNotEmpty()) {
                        item(key = "movies_header") {
                            Text(
                                "Movies",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        item(key = "movies_grid") {
                            FilmographyGrid(
                                metas = p.movies,
                                onOpenDetail = onOpenDetail
                            )
                        }
                    }
                    if (p.series.isNotEmpty()) {
                        item(key = "series_header") {
                            Text(
                                "Series",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        item(key = "series_grid") {
                            FilmographyGrid(
                                metas = p.series,
                                onOpenDetail = onOpenDetail
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilmographyGrid(
    metas: List<com.pulsestream.app.data.MetaPreview>,
    onOpenDetail: (type: String, metaId: String) -> Unit
) {
    // Fixed-height grid inside the outer LazyColumn.
    val columns = adaptiveColumnCount(LocalConfiguration.current.screenWidthDp - 48)
    val rows = (metas.size + columns - 1) / columns
    val gridHeight = (rows * 244).dp
    LazyVerticalGrid(
        columns = GridCells.Adaptive(140.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(gridHeight),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false
    ) {
        items(metas, key = { it.type + "|" + it.id }) { meta ->
            PosterCard(
                meta = meta,
                onClick = { onOpenDetail(meta.type, meta.id) }
            )
        }
    }
}
