package com.pulsestream.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulsestream.app.data.db.DownloadItem
import com.pulsestream.app.data.download.DownloadManager
import com.pulsestream.app.ui.components.GlassCard
import com.pulsestream.app.ui.components.GlassIconButton
import com.pulsestream.app.ui.components.WifiOnlyDownloadsControl
import com.pulsestream.app.ui.theme.GlassBorder
import com.pulsestream.app.ui.theme.LocalPalette
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

    // Sample E hero (v0.12.4): the first download whose LIVE status is
    // DOWNLOADING gets the big "Now downloading" card; a second active
    // download stays in the list below with its own progress.
    val heroItem = active.firstOrNull { item ->
        (activeProgress[item.id]?.status ?: item.status) == "DOWNLOADING"
    }
    val heroProgress = heroItem?.let { activeProgress[it.id] }
    val listActive = if (heroItem != null) active.filter { it.id != heroItem.id } else active

    // Transfer speed for the hero, from progress deltas (the manager
    // exposes bytes only): sample on each progress change, ignore
    // windows under 500ms, smooth 60/40 towards the newest sample.
    var speedSample by remember { mutableStateOf<Triple<String, Long, Long>?>(null) }
    var heroSpeed by remember { mutableStateOf(0L) }
    val heroDownloaded = heroProgress?.downloadedBytes ?: heroItem?.downloadedBytes ?: 0L
    LaunchedEffect(heroItem?.id, heroDownloaded) {
        val current = heroItem
        if (current == null) {
            speedSample = null
            heroSpeed = 0L
            return@LaunchedEffect
        }
        val now = System.currentTimeMillis()
        val prev = speedSample
        if (prev == null || prev.first != current.id) {
            speedSample = Triple(current.id, heroDownloaded, now)
        } else {
            val dtMs = now - prev.third
            if (dtMs >= 500) {
                val instant = ((heroDownloaded - prev.second) * 1000L / dtMs).coerceAtLeast(0)
                heroSpeed = if (heroSpeed <= 0) instant
                else (0.6 * heroSpeed + 0.4 * instant).toLong()
                speedSample = Triple(current.id, heroDownloaded, now)
            }
        }
    }

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
                    // Bottom 104dp: last row scrolls clear of the floating dock.
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 104.dp),
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

                    if (heroItem != null) {
                        item(key = "now_downloading_hero") {
                            NowDownloadingHero(
                                item = heroItem,
                                progress = heroProgress,
                                manager = manager,
                                speedBytesPerSec = heroSpeed,
                                onPause = { manager.pauseDownload(heroItem.id) },
                                onCancel = { manager.cancelDownload(heroItem.id) }
                            )
                        }
                    }

                    if (listActive.isNotEmpty() || (heroItem != null && completed.isNotEmpty())) {
                        item {
                            Text(
                                // Sample E: with a hero up top, everything
                                // below is one "Up next & finished" list.
                                if (heroItem != null) "Up next & finished" else "Active",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                    if (listActive.isNotEmpty()) {
                        items(listActive, key = { it.id }) { item ->
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
                                onCancel = { manager.cancelDownload(item.id) }
                            )
                        }
                    }

                    if (completed.isNotEmpty()) {
                        // With a hero, the combined header above already
                        // covers the finished rows (Sample E).
                        if (heroItem == null) {
                            item {
                                Text(
                                    "Completed",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                        items(completed, key = { it.id }) { item ->
                            // v0.12.1: a completed row whose file was deleted
                            // outside the app used to offer a Play button
                            // that could only fail — say so plainly instead.
                            val fileMissing = remember(item.id, item.filePath) { !File(item.filePath).exists() }
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
                                            when {
                                                fileMissing -> "File missing — it may have been deleted outside the app"
                                                manager.isAdaptiveDownloadUrl(item.url) -> "Manifest only — not a complete offline download"
                                                else -> {
                                                    // Sample E row sub (v0.12.4):
                                                    // "1.4 GB · 1080p · Ready" —
                                                    // quality only when the
                                                    // title/file name carries
                                                    // one; never guessed.
                                                    val sizeText = manager.formatBytes(
                                                        item.totalBytes.takeIf { it > 0 }
                                                            ?: File(item.filePath).length()
                                                    )
                                                    val quality = DOWNLOAD_QUALITY_REGEX.find(item.title)?.value
                                                        ?: DOWNLOAD_QUALITY_REGEX.find(item.fileName)?.value
                                                    if (quality != null) "$sizeText · ${quality.lowercase()} · Ready"
                                                    else "$sizeText · Ready"
                                                }
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (fileMissing || manager.isAdaptiveDownloadUrl(item.url)) MaterialTheme.colorScheme.error else TextSecondary
                                        )
                                    }
                                    if (!fileMissing && !manager.isAdaptiveDownloadUrl(item.url)) {
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

/** Quality tag ("1080p") carried in download titles / file names. */
private val DOWNLOAD_QUALITY_REGEX =
    Regex("""\b(2160p|1440p|1080p|720p|480p|360p)\b""", RegexOption.IGNORE_CASE)

/** "~40 sec" / "~12 min" / "~1 h 5 min" for the hero's ETA text. */
private fun formatEta(totalSeconds: Long): String = when {
    totalSeconds < 60 -> "$totalSeconds sec"
    totalSeconds < 3600 -> "${totalSeconds / 60} min"
    else -> "${totalSeconds / 3600} h ${(totalSeconds % 3600) / 60} min"
}

/**
 * Sample E hero card (v0.12.4): gradient navy panel with a mint
 * "NOW DOWNLOADING" kicker, the title, a big percent, the
 * "<done> of <total> · <speed> · ~<eta> left" line, a progress bar and
 * REAL Pause / Cancel buttons wired to the same manager calls the row
 * buttons use. Unknown values are omitted, never faked: with an unknown
 * total the downloaded size stands in for the percent and the bar goes
 * indeterminate; speed/ETA appear only once measured.
 */
@Composable
private fun NowDownloadingHero(
    item: DownloadItem,
    progress: DownloadManager.DownloadProgress?,
    manager: DownloadManager,
    speedBytesPerSec: Long,
    onPause: () -> Unit,
    onCancel: () -> Unit
) {
    val downloaded = progress?.downloadedBytes ?: item.downloadedBytes
    val total = progress?.totalBytes?.takeIf { it > 0 } ?: item.totalBytes.takeIf { it > 0 } ?: -1L
    val fraction = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
    val percent = (fraction * 100).toInt()
    val etaSeconds = if (total > 0 && speedBytesPerSec > 1024) {
        (total - downloaded).coerceAtLeast(0) / speedBytesPerSec
    } else null
    val accent = LocalPalette.current.accent

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(listOf(Color(0xFF14324A), Color(0xFF0B1A2C)))
            )
            .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Column {
            Text(
                "NOW DOWNLOADING",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                fontWeight = FontWeight.ExtraBold,
                color = accent
            )
            Spacer(Modifier.height(6.dp))
            Text(
                item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            if (total > 0) {
                Text(
                    "$percent%",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
            } else {
                Text(
                    manager.formatBytes(downloaded),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
                Text(
                    "downloaded so far",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Spacer(Modifier.height(4.dp))
            val subParts = buildList {
                if (total > 0) {
                    add("${manager.formatBytes(downloaded)} of ${manager.formatBytes(total)}")
                } else {
                    add(manager.formatBytes(downloaded))
                }
                if (speedBytesPerSec > 0) add("${manager.formatBytes(speedBytesPerSec)}/s")
                etaSeconds?.let { add("~${formatEta(it)} left") }
            }
            Text(
                subParts.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(Modifier.height(10.dp))
            if (total > 0) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = accent,
                    trackColor = Color.White.copy(alpha = 0.12f)
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = accent,
                    trackColor = Color.White.copy(alpha = 0.12f)
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onPause,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = Color(0xFF06231B)
                    )
                ) {
                    Icon(
                        Icons.Filled.Pause,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Pause")
                }
                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextPrimary
                    ),
                    border = BorderStroke(1.dp, GlassBorder)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    item: DownloadItem,
    progress: DownloadManager.DownloadProgress?,
    manager: DownloadManager,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    val downloaded = progress?.downloadedBytes ?: item.downloadedBytes
    val total = progress?.totalBytes?.takeIf { it > 0 } ?: item.totalBytes.takeIf { it > 0 } ?: -1L
    val fraction = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
    val percent = (fraction * 100).toInt()
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
                            // v0.12.1 polish: explicit percent + sizes.
                            "DOWNLOADING" -> if (total > 0)
                                "$percent% • ${manager.formatBytes(downloaded)} of ${manager.formatBytes(total)}"
                            else "Downloading • ${manager.formatBytes(downloaded)}"
                            "PAUSED" -> "Paused" + (if (total > 0)
                                " • $percent% • ${manager.formatBytes(downloaded)} of ${manager.formatBytes(total)}"
                            else " • ${manager.formatBytes(downloaded)}")
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
                    "PAUSED" -> {
                        GlassIconButton(
                            image = Icons.Filled.PlayArrow,
                            contentDescription = "Resume",
                            onClick = onResume,
                            tint = TextPrimary
                        )
                    }
                    "FAILED" -> {
                        GlassIconButton(
                            image = Icons.Filled.Refresh,
                            contentDescription = "Retry",
                            onClick = onResume,
                            tint = TextPrimary
                        )
                    }
                }
                // v0.12.1 fix: this ✕ used to open the DELETE dialog
                // (onDelete) despite its "Cancel" label, and the real
                // onCancel callback was never wired. It now cancels.
                GlassIconButton(
                    image = Icons.Filled.Close,
                    contentDescription = "Cancel download",
                    onClick = onCancel,
                    tint = TextSecondary
                )
            }
            Spacer(Modifier.height(8.dp))
            if (status == "DOWNLOADING" && total <= 0) {
                // Unknown total size: an honest indeterminate bar instead
                // of a determinate one stuck at 0%.
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Starts the foreground download service (no-op helper to keep call sites tidy). */
object DownloadServiceStarter {
    fun start(context: android.content.Context) {
        com.pulsestream.app.data.download.DownloadService.start(context)
    }
}
