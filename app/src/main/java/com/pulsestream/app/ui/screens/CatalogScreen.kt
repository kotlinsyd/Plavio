package com.pulsestream.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulsestream.app.ui.components.EmptyState
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.components.PosterCard
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary

/**
 * "View all" grid for a Home catalog row. The row's items are handed over
 * in-memory via [repository.pendingCatalog] (same pattern as pendingPlayback).
 */
@Composable
fun CatalogScreen(
    repository: com.pulsestream.app.data.StreamRepository,
    onBack: () -> Unit,
    onOpenDetail: (addonId: String, type: String, metaId: String) -> Unit
) {
    val catalog = repository.pendingCatalog
    GlowBackground {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, "Back", tint = TextPrimary)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = catalog?.catalogName ?: "Catalog",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    if (catalog != null) {
                        Text(
                            text = catalog.addonName,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
            if (catalog == null || catalog.items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        title = "Nothing here",
                        message = "This catalog came back empty.",
                        actionLabel = "Go back",
                        onAction = onBack
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(140.dp),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(
                        catalog.items,
                        key = { index, meta -> "cat_${meta.id}_$index" }
                    ) { _, meta ->
                        PosterCard(
                            meta = meta,
                            onClick = {
                                onOpenDetail(catalog.addonId, meta.type, meta.id)
                            }
                        )
                    }
                }
            }
        }
    }
}
