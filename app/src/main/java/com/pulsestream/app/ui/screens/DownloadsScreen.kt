package com.pulsestream.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pulsestream.app.data.db.DownloadItem
import com.pulsestream.app.data.download.DownloadManager
import com.pulsestream.app.ui.components.GlassCard
import com.pulsestream.app.ui.components.GlassIconButton
import com.pulsestream.app.ui.components.WifiOnlyDownloadsControl
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBack: () -> Unit = {},
    onPlayFile: (filePath: String, title: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val manager = remember { DownloadManager.get(context) }
    val scope = rememberCoroutineScope()

    val downloads by manager.observeAll().collectAsState(initial = emptyList())
    val activeProgress by manager.activeDownloads.collectAsState()
    var storageUsed by remember { mutableStateOf(0L) }
    var confirmDelete by remember { mutableStateOf<DownloadItem?>(null) }
    var wifiOnly by remember { mutableStateOf(manager.isWifiOnlyDownloads()) }

    LaunchedEffect(Unit) { manager.recoverInterruptedDownloads() }
    LaunchedEffect(downloads) {
        storageUsed = manager.storageUsedBytes()
    }

    val active = downloads.filter { it.status in listOf("QUEUED", "DOWNLOADING", "PAUSED", "FAILED") }
    val completed = downloads.filter { it.status == "COMPLETED" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = {
                    GlassIconButton(
                        image = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        onClick = onBack,
                        tint = TextPrimary
                    )
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            WifiOnlyDownloadsControl(
                checked = wifiOnly,
                onCheckedChange = { enabled ->
                    wifiOnly = enabled
                    manager.setWifiOnlyDownloads(enabled)
                    if (!enabled) {
                        manager.resumeQueuedDownloads()
                        DownloadServiceStarter.start(context)
                    }
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (downloads.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Icon(
                            Icons.Filled.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = TextSecondary.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("No downloads yet", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Tap the download icon next to any stream to save it here.",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Storage, contentDescription = null, tint = TextSecondary)
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Storage used: ${manager.formatBytes(storageUsed)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                    if (active.isNotEmpty()) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            TextButton(onClick = { manager.pauseAll() }) { Text("Pause queue") }
                                            TextButton(onClick = {
                                                manager.resumeQueuedDownloads()
                                                DownloadServiceStarter.start(context)
                                            }) { Text("Resume queue") }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (active.isNotEmpty()) {
                        item {
                            Text(
                                "Active",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        items(active, key = { it.id }) { item ->
                            val progress = activeProgress[item.id]
                            DownloadRow(
                                item = item,
                                progress = progress,
                                manager = manager,
                                onPause = { manager.pauseDownload(item.id) },
                                onResume = {
                                    manager.resumeDownload(item.id)
                                    DownloadServiceStarter.start(context)
                                },
                                onCancel = {
                                    scope.launch { manager.cancelDownload(item.id) }
                                },
                                onDelete = { confirmDelete = item }
                            )
                        }
                    }

                    if (completed.isNotEmpty()) {
                        item {
                            Text(
                                "Completed",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        items(completed, key = { it.id }) { item ->
                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.Movie,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            item.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            if (manager.isAdaptiveDownloadUrl(item.url)) "Manifest only — not a complete offline download"
                                            else manager.formatBytes(item.totalBytes.takeIf { it > 0 } ?: File(item.filePath).length()),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (manager.isAdaptiveDownloadUrl(item.url)) MaterialTheme.colorScheme.error else TextSecondary
                                        )
                                    }
                                    if (!manager.isAdaptiveDownloadUrl(item.url)) {
                                        GlassIconButton(
                                            image = Icons.Filled.PlayArrow,
                                            contentDescription = "Play",
                                            onClick = { onPlayFile(item.filePath, item.title) },
                                            tint = TextPrimary
                                        )
                                    }
                                    GlassIconButton(
                                        image = Icons.Filled.Delete,
                                        contentDescription = "Delete",
                                        onClick = { confirmDelete = item },
                                        tint = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation
    confirmDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete download?") },
            text = { Text("\"${item.title}\" will be removed from your device.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { manager.deleteDownload(item.id) }
                    confirmDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DownloadRow(
    item: DownloadItem,
    progress: DownloadManager.DownloadProgress?,
    manager: DownloadManager,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    val downloaded = progress?.downloadedBytes ?: item.downloadedBytes
    val total = progress?.totalBytes?.takeIf { it > 0 } ?: item.totalBytes.takeIf { it > 0 } ?: -1L
    val fraction = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
    val status = progress?.status ?: item.status

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        when (status) {
                            "DOWNLOADING" -> if (total > 0)
                                "${manager.formatBytes(downloaded)} of ${manager.formatBytes(total)}"
                            else manager.formatBytes(downloaded)
                            "PAUSED" -> "Paused • ${manager.formatBytes(downloaded)}" +
                                (if (total > 0) " of ${manager.formatBytes(total)}" else "")
                            "QUEUED" -> item.error ?: "Waiting…"
                            "FAILED" -> "Failed${item.error?.let { ": $it" } ?: ""}"
                            else -> status
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                when (status) {
                    "DOWNLOADING", "QUEUED" -> {
                        GlassIconButton(
                            image = Icons.Filled.Pause,
                            contentDescription = "Pause",
                            onClick = onPause,
                            tint = TextPrimary
                        )
                    }
                    "PAUSED", "FAILED" -> {
                        GlassIconButton(
                            image = Icons.Filled.PlayArrow,
                            contentDescription = "Resume",
                            onClick = onResume,
                            tint = TextPrimary
                        )
                    }
                }
                GlassIconButton(
                    image = Icons.Filled.Close,
                    contentDescription = "Cancel",
                    onClick = onDelete,
                    tint = TextSecondary
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Starts the foreground download service (no-op helper to keep call sites tidy). */
object DownloadServiceStarter {
    fun start(context: android.content.Context) {
        com.pulsestream.app.data.download.DownloadService.start(context)
    }
}
