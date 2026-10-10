package com.pulsestream.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pulsestream.app.data.update.ApkInstaller
import com.pulsestream.app.data.update.UpdateChecker
import com.pulsestream.app.data.update.UpdateInfo
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary

/**
 * Shows the update dialog for [info] (if non-null) and handles the
 * download → install flow plus the "Later" dismissal. Shared by the
 * automatic launch popup and the manual Settings check.
 */
@Composable
fun UpdatePrompt(
    info: UpdateInfo?,
    onDismiss: () -> Unit
) {
    var downloading by remember(info) { mutableStateOf(false) }
    val appContext = LocalContext.current.applicationContext
    if (info != null) {
        UpdateDialog(
            info = info,
            downloading = downloading,
            onUpdate = {
                downloading = true
                ApkInstaller.download(appContext, info.apkUrl, info.version) {
                    downloading = false
                    onDismiss()
                }
            },
            onLater = {
                UpdateChecker.skip(appContext, info.version)
                onDismiss()
            }
        )
    }
}

/** Popup shown when a newer Plavio release is available. */
@Composable
fun UpdateDialog(
    info: UpdateInfo,
    downloading: Boolean,
    onUpdate: () -> Unit,
    onLater: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!downloading) onLater() },
        title = {
            Text(
                "Update available",
                color = TextPrimary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "v${info.version}",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = LocalPalette.current.accent,
                    textAlign = TextAlign.Center
                )
                if (info.notes.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        info.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (downloading) {
                    CircularProgressIndicator(color = LocalPalette.current.accent)
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    if (downloading) "Downloading… the installer opens when it's done."
                    else "Installs right over your current version — nothing is wiped.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onUpdate, enabled = !downloading) {
                Text(if (downloading) "Downloading…" else "Update", color = LocalPalette.current.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onLater, enabled = !downloading) {
                Text("Later", color = TextSecondary)
            }
        },
        containerColor = Color(0xFF14141F)
    )
}
