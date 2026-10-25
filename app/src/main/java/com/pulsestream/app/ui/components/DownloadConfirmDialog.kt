package com.pulsestream.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulsestream.app.data.StreamItem
import com.pulsestream.app.data.download.DownloadManager
import com.pulsestream.app.data.download.DownloadService
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * Confirmation dialog shown before starting a download.
 * Probes the file size via HEAD request and warns if it's large (>500MB).
 */
@Composable
fun DownloadConfirmDialog(
    stream: StreamItem,
    title: String,
    onDismiss: () -> Unit,
    onStarted: () -> Unit = {}
) {
    val context = LocalContext.current
    val manager = remember { DownloadManager.get(context) }
    val scope = rememberCoroutineScope()

    var sizeBytes by remember { mutableStateOf<Long?>(null) }
    var probing by remember { mutableStateOf(true) }
    var alreadyDownloaded by remember { mutableStateOf(false) }
    var requestError by remember { mutableStateOf<String?>(null) }
    val dashUnsupported = manager.isDashDownloadUrl(stream.url)
    val isHls = manager.isHlsDownloadUrl(stream.url)

    LaunchedEffect(stream.url) {
        alreadyDownloaded = manager.getByUrl(stream.url)?.status == "COMPLETED"
        if (!dashUnsupported && !isHls) sizeBytes = manager.probeSize(stream.url, stream.headers)
        probing = false
    }

    val enoughStorage = manager.storagePreflight(sizeBytes)
    val isLarge = (sizeBytes ?: -1L) > DownloadManager.LARGE_FILE_WARNING_BYTES
    val displayTitle = stream.title?.takeIf { it.isNotBlank() }
        ?: stream.name?.takeIf { it.isNotBlank() }
        ?: title

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Download", fontWeight = FontWeight.Bold, color = TextPrimary)
        },
        text = {
            Column {
                Text(
                    displayTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
                Spacer(Modifier.height(8.dp))
                when {
                    probing -> {
                        Row {
                            OrbitDots(modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Checking file size…",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                    dashUnsupported -> {
                        Text(
                            "DASH streams can't be saved as offline downloads yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    isHls -> {
                        Text(
                            "This is an HLS stream. It will be downloaded in segments and saved as a single MP4 file, so the size can't be checked beforehand.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    alreadyDownloaded -> {
                        Text(
                            "This file is already downloaded.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    else -> {
                        val sizeText = sizeBytes?.let {
                            if (it > 0) manager.formatBytes(it) else "Unknown size"
                        } ?: "Unknown size"
                        Text(
                            "Size: $sizeText",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        if (sizeBytes != null && sizeBytes!! > 0 && !enoughStorage) {
                            Spacer(Modifier.height(8.dp))
                            Text("Not enough free storage for this file plus a safety reserve.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        if (isLarge) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "⚠️ This file is large (${manager.formatBytes(sizeBytes!!)}). " +
                                    "Make sure you have enough storage and a stable connection.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                requestError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (!alreadyDownloaded) {
                        scope.launch(kotlinx.coroutines.NonCancellable) {
                            try {
                                manager.startDownload(url = stream.url, title = displayTitle, headers = stream.headers)
                                DownloadService.start(context)
                                onStarted()
                                onDismiss()
                            } catch (e: Exception) {
                                requestError = e.message ?: "Could not start this download."
                            }
                        }
                    } else onDismiss()
                },
                enabled = !probing && !alreadyDownloaded && !dashUnsupported && (isHls || enoughStorage)
            ) {
                Text("Download", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
