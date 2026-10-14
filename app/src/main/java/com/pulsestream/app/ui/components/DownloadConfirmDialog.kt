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

    LaunchedEffect(stream.url) {
        alreadyDownloaded = manager.getByUrl(stream.url)?.status == "COMPLETED"
        sizeBytes = manager.probeSize(stream.url, stream.headers)
        probing = false
    }

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
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Checking file size…",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
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
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (!alreadyDownloaded) {
                        scope.launch {
                            manager.startDownload(
                                url = stream.url,
                                title = displayTitle,
                                headers = stream.headers
                            )
                            DownloadService.start(context)
                            onStarted()
                        }
                    }
                    onDismiss()
                },
                enabled = !probing && !alreadyDownloaded
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
