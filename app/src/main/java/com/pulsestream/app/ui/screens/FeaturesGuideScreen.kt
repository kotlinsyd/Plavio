package com.pulsestream.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulsestream.app.ui.components.GlassCard
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary

private data class FeatureGuide(
    val icon: ImageVector,
    val title: String,
    val description: String
)

private val features = listOf(
    FeatureGuide(
        Icons.Filled.CloudDownload,
        "Debrid Integrations",
        "Connect your TorBox or Real-Debrid API key in Settings → Integrations. " +
            "Cached torrents appear as ⚡ TorBox or ⚡ RD streams and play instantly as direct links. " +
            "No torrents are downloaded in the app."
    ),
    FeatureGuide(
        Icons.Filled.Subtitles,
        "Subtitle Timing Sync",
        "If subtitles are out of sync, open the subtitle menu in the player and use the −500ms / +500ms buttons " +
            "to shift them. Your adjustment applies to the current title. " +
            "Set a global default offset in Settings → Playback → Subtitle offset."
    ),
    FeatureGuide(
        Icons.Filled.PictureInPicture,
        "Picture-in-Picture",
        "Press the PiP button in the player or press Home while watching to float the video. " +
            "The floating window keeps playing and has its own play/pause button. " +
            "Portrait videos float in portrait, just like YouTube."
    ),
    FeatureGuide(
        Icons.Filled.Shuffle,
        "Episode Shuffle",
        "On a series detail page, enable Shuffle to play episodes in random order. " +
            "Auto-next also picks a random episode when shuffle is on."
    ),
    FeatureGuide(
        Icons.Filled.Speed,
        "Buffer & Cache",
        "In Settings → Playback, choose a buffer preset: Low (saves data), Balanced, or Aggressive (fewer stalls). " +
            "You can also set how much disk space the player may use for caching."
    ),
    FeatureGuide(
        Icons.Filled.CloudSync,
        "Account Sync",
        "Open Settings → Account to sync your library, progress and portable settings across devices. " +
            "Optionally encrypt integration keys and full configured addon URLs with a passphrase; on a new device, restore the profile first, then unlock the private data. " +
            "The passphrase has no recovery, and downloaded files, Android permissions and cloud sign-in remain device-only."
    ),
    FeatureGuide(
        Icons.Filled.Info,
        "Stream Info",
        "Each stream shows its source, quality and size where available. " +
            "Debrid-resolved streams are marked with a ⚡ badge."
    ),
    FeatureGuide(
        Icons.Filled.LiveTv,
        "Live TV",
        "Open the Live tab to browse live TV channels from your installed addons."
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeaturesGuideScreen(
    onBack: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Features Guide") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(features) { feature ->
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            feature.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(28.dp)
                                .padding(end = 0.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                feature.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                feature.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
