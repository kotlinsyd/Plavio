package com.pulsestream.app.ui.screens.player

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import com.pulsestream.app.ui.components.OrbitDots
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pulsestream.app.data.SettingsStore
import com.pulsestream.app.data.StreamItem
import com.pulsestream.app.ui.components.AddonTabs
import com.pulsestream.app.ui.components.QualityPills
import com.pulsestream.app.ui.components.SafePosterImage
import com.pulsestream.app.ui.components.rememberStreamBrowserState
import com.pulsestream.app.ui.components.streamListItems
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary

/**
 * Design I (user's approved design): full-width banner (artwork + title +
 * source count) at the top, then addon tabs, quality pills and stream cards
 * sharing one scroll area.
 */
@Composable
internal fun PlayerSourcesContent(
    title: String,
    streams: List<StreamItem>,
    selectedUrl: String,
    loading: Boolean,
    onPick: (StreamItem) -> Unit,
    onDownload: (StreamItem) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    settings: SettingsStore? = null,
    poster: String? = null
) {
    val browser = rememberStreamBrowserState(streams, settings, limitPerSource = false)
    LazyColumn(
        modifier = modifier.fillMaxWidth().testTag("player-source-list"),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        item {
            // Full-width banner: artwork with a gradient into the sheet colour,
            // title + source count at the bottom, close button top-right.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                SafePosterImage(
                    model = poster,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0xFF14141F).copy(alpha = 0.7f),
                                    Color(0xFF14141F)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${streams.size} sources available",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.align(Alignment.TopEnd).size(48.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close sources", tint = TextPrimary)
                }
            }
        }
        if (streams.isNotEmpty()) {
            item { AddonTabs(browser) }
            item { QualityPills(browser) }
            item { Text("${browser.filtered.size} sources", style = MaterialTheme.typography.labelSmall,
                color = TextSecondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) }
            if (browser.filtered.isEmpty()) item {
                Text("No sources match these filters.", color = TextSecondary, modifier = Modifier.padding(16.dp))
            }
            streamListItems(streams = browser.filtered, onPick = onPick, selectedUrl = selectedUrl,
                onDownload = onDownload, compact = true)
        } else {
            item {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (loading) OrbitDots(modifier = Modifier.size(26.dp))
                    Text(if (loading) "Finding sources…" else "No sources loaded for this title.", color = TextSecondary)
                }
            }
            if (!loading) item { TextButton(onClick = onRetry, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Load sources") } }
        }
    }
}
