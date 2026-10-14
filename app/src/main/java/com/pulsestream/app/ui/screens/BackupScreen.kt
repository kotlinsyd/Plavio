package com.pulsestream.app.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulsestream.app.data.BackupManager
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * Backup & Restore screen (v0.9.0+): export everything to a local JSON file
 * or import one back. No account, no cloud — the file is yours.
 *
 * Export covers: all settings, installed addons, watchlist and playback
 * states (Continue Watching). Transient caches are skipped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    repository: StreamRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val palette = LocalPalette.current

    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    fun fail(msg: String) {
        busy = false
        status = false to msg
    }

    // Export: user picks where the file goes (Storage Access Framework).
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) {
            busy = false
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val payload = repository.exportBackupJson()
            val err = payload.fold(
                onSuccess = { BackupManager.writeToUri(context, uri, it) },
                onFailure = { it.message ?: "Couldn't build the backup." }
            )
            if (err == null) {
                busy = false
                status = true to "Backup saved."
            } else fail(err)
        }
    }

    // Import: user picks a previously exported file.
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            busy = false
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val text = BackupManager.readFromUri(context, uri)
            if (text == null) {
                fail("Couldn't read that file.")
                return@launch
            }
            val result = repository.importBackupJson(text)
            result.fold(
                onSuccess = {
                    busy = false
                    status = true to "Backup restored — restarting…"
                    // Recreate so every screen picks up the restored state.
                    (context as? Activity)?.recreate()
                },
                onFailure = { fail(it.message ?: "That file isn't a valid Plavio backup.") }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & Restore", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        GlowBackground {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Keep a copy of everything on your device — settings, addons, " +
                        "watchlist and Continue Watching. No account, no cloud; " +
                        "the file stays with you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(28.dp))

                Button(
                    onClick = {
                        busy = true
                        status = null
                        exportLauncher.launch(BackupManager.suggestedFileName())
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Icon(Icons.Filled.FileUpload, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("Export backup", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        busy = true
                        status = null
                        importLauncher.launch(arrayOf("application/json"))
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.panel)
                ) {
                    Icon(Icons.Filled.FileDownload, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("Import backup", style = MaterialTheme.typography.titleSmall)
                }

                Spacer(Modifier.height(20.dp))
                if (busy) {
                    CircularProgressIndicator(color = palette.accent)
                }
                status?.let { (ok, msg) ->
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (ok) Icons.Filled.CheckCircle else Icons.Filled.Error,
                            contentDescription = null,
                            tint = if (ok) palette.accent else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            msg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (ok) TextPrimary else MaterialTheme.colorScheme.error
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))
                Text(
                    "Import replaces your current settings, addons, watchlist and " +
                        "playback history with the file's contents.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}
