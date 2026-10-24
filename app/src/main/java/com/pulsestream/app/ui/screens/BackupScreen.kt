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
import com.pulsestream.app.ui.components.OrbitDots
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pulsestream.app.data.BackupCrypto
import com.pulsestream.app.data.BackupManager
import com.pulsestream.app.data.BackupPreview
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(repository: StreamRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val palette = LocalPalette.current
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var passwordPrompt by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }
    var exportPassword by remember { mutableStateOf<CharArray?>(null) }
    var queuedImport by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<BackupPreview?>(null) }
    fun fail(msg: String) { busy = false; status = false to msg }
    fun showPreview(payload: String) {
        repository.previewBackupJson(payload).fold(
            onSuccess = { queuedImport = payload; preview = it },
            onFailure = { fail(it.message ?: "That file is not a valid Plavio backup.") }
        )
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) { busy = false; exportPassword?.fill('\u0000'); exportPassword = null; return@rememberLauncherForActivityResult }
        scope.launch {
            val result = repository.exportBackupJson()
            val err = result.fold(
                onSuccess = { clear ->
                    try { BackupManager.writeToUri(context, uri, BackupCrypto.encrypt(clear, exportPassword ?: charArrayOf())) }
                    catch (e: Exception) { e.message ?: "Couldn't encrypt the backup." }
                },
                onFailure = { it.message ?: "Couldn't build the backup." }
            )
            exportPassword?.fill('\u0000'); exportPassword = null
            if (err == null) { busy = false; status = true to "Encrypted backup saved. Store the password separately; it cannot be recovered." }
            else fail(err)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) { busy = false; return@rememberLauncherForActivityResult }
        scope.launch {
            val text = BackupManager.readFromUri(context, uri)
            if (text == null) { fail("Couldn't read that file (maximum size is 10 MiB).") }
            else if (BackupCrypto.isEncrypted(text)) { queuedImport = text; password = ""; passwordPrompt = "import" }
            else showPreview(text)
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Backup & Restore", fontWeight = FontWeight.Bold) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
        })
    }) { padding ->
        GlowBackground {
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(12.dp))
                Text("Encrypted portable backup of settings, addons, watchlist, playback history and custom lists. Downloaded media and download records are excluded and must be downloaded again. Integration credentials are never exported. Android device/cloud backup is disabled to protect private addon URLs and account data.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Spacer(Modifier.height(24.dp))
                Button(onClick = { password = ""; passwordPrompt = "export"; status = null }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Icon(Icons.Filled.FileUpload, contentDescription = null); Spacer(Modifier.width(10.dp)); Text("Export encrypted backup")
                }
                Spacer(Modifier.height(14.dp))
                Button(onClick = { busy = true; status = null; importLauncher.launch(arrayOf("application/octet-stream", "application/json", "text/plain")) }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = palette.panel)) {
                    Icon(Icons.Filled.FileDownload, contentDescription = null); Spacer(Modifier.width(10.dp)); Text("Import backup")
                }
                Spacer(Modifier.height(20.dp))
                if (busy) OrbitDots(modifier = Modifier.size(36.dp))
                status?.let { (ok, msg) ->
                    Spacer(Modifier.height(8.dp)); Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (ok) Icons.Filled.CheckCircle else Icons.Filled.Error, contentDescription = null,
                            tint = if (ok) palette.accent else MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp)); Text(msg, style = MaterialTheme.typography.bodyMedium,
                            color = if (ok) TextPrimary else MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text("Import replaces the included sections only. Custom lists and membership are included. Omitted sections in older partial backups are left unchanged. Secrets, cloud sign-in, privacy consent and onboarding are kept local and never imported.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }

    passwordPrompt?.let { purpose ->
        AlertDialog(onDismissRequest = { passwordPrompt = null; busy = false },
            title = { Text(if (purpose == "export") "Set backup password" else "Unlock backup") },
            text = {
                Column {
                    Text(if (purpose == "export") "Choose a password of at least 10 characters. The file is encrypted with AES-256-GCM and PBKDF2. Keep this password safe; Plavio cannot recover it." else "Enter the password used to encrypt this backup.")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = password, onValueChange = { password = it }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), label = { Text("Password") })
                }
            }, confirmButton = {
                TextButton(onClick = {
                    val typed = password.toCharArray()
                    if (purpose == "export" && typed.size < 10) { typed.fill('\u0000'); fail("Use a backup password with at least 10 characters."); passwordPrompt = null }
                    else if (purpose == "import") {
                        val encrypted = queuedImport.orEmpty()
                        try {
                            val decoded = BackupCrypto.decrypt(encrypted, typed)
                            typed.fill('\u0000'); passwordPrompt = null; busy = false; showPreview(decoded)
                        } catch (e: Exception) { typed.fill('\u0000'); fail(e.message ?: "Could not unlock backup."); passwordPrompt = null }
                    } else {
                        exportPassword = typed; passwordPrompt = null; busy = true
                        exportLauncher.launch(BackupManager.suggestedFileName())
                    }
                    password = ""
                }) { Text("Continue") }
            }, dismissButton = { TextButton(onClick = { passwordPrompt = null; busy = false }) { Text("Cancel") } }
        )
    }
    preview?.let { counts ->
        AlertDialog(onDismissRequest = { preview = null; queuedImport = null },
            title = { Text("Review backup before restore") },
            text = {
                Column {
                    Text("Format version ${counts.version}")
                    Text("${counts.addonCount} addons · ${counts.watchlistCount} watchlist entries · ${counts.playbackCount} playback states")
                    Text("${counts.customListCount} custom lists · ${counts.customListItemCount} list items")
                    Spacer(Modifier.height(10.dp))
                    Text("This will replace the sections shown above. Downloads/media are not included; integration credentials and local consent settings remain on this device.")
                }
            }, confirmButton = {
                TextButton(onClick = {
                    val payload = queuedImport ?: return@TextButton
                    preview = null; busy = true
                    scope.launch {
                        repository.importBackupJson(payload).fold(
                            onSuccess = { busy = false; status = true to "Backup restored — restarting…"; (context as? Activity)?.recreate() },
                            onFailure = { fail(it.message ?: "Restore failed; no backup data was applied.") }
                        )
                    }
                }) { Text("Replace included data") }
            }, dismissButton = { TextButton(onClick = { preview = null; queuedImport = null }) { Text("Cancel") } }
        )
    }
}
