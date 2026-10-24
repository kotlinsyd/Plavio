package com.pulsestream.app.ui.screens

import android.accounts.AccountManager
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.Button
import com.pulsestream.app.ui.components.OrbitDots
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.data.cloud.GoogleDriveSyncManager
import com.pulsestream.app.ui.components.GlassCard
import com.pulsestream.app.ui.components.GlowBackground
import kotlinx.coroutines.launch

/**
 * Account screen (v0.11.9+): Google account + Drive sync.
 * User's order 2026-10-19: remove Supabase entirely, Google login only,
 * auto-backup and auto-restore everything including API keys.
 * Uses system account picker (no GET_ACCOUNTS permission needed).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    repository: StreamRepository,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sync = remember { GoogleDriveSyncManager(context, repository.settings, repository) }

    var busy by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableStateOf(0) }
    // Custom username (owner's order 2026-10-22): a name of the
    // user's own choice, shown in the app instead of the Gmail
    // address acting as the account name.
    var editingName by remember { mutableStateOf(false) }
    var nameDraft by remember { mutableStateOf("") }
    val profileName = remember(refreshTick) { repository.settings.profileUserName }

    val isSignedIn = remember(refreshTick) { sync.isSignedIn() }
    val accountEmail = remember(refreshTick) { sync.getAccountEmail() }
    val lastBackup = remember(refreshTick) { sync.getLastBackupTime() }
    val lastRestore = remember(refreshTick) { sync.getLastRestoreTime() }

    // Auth activity launcher (for when user needs to grant Drive access)
    // Defined first because accountPickerLauncher uses it
    val authLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Retry the pending operation after auth
        refreshTick++
        message = "Authorization granted. Try backup/restore again."
    }

    // System account picker (no permission needed, works on all Android versions)
    val accountPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val accountName = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (accountName != null) {
                scope.launch {
                    busy = true
                    error = null
                    sync.selectAccountByName(accountName)
                    message = "Signed in as $accountName. Restoring..."
                    // Auto-restore on login (user's order)
                    val restoreResult = sync.autoRestoreOnLogin()
                    restoreResult.onSuccess { restored ->
                        message = if (restored) {
                            "Welcome back! Everything restored."
                        } else {
                            "Signed in. No backup found yet - your data will sync automatically."
                        }
                    }.onFailure { e ->
                        if (e is GoogleDriveSyncManager.AuthRequiredException) {
                            authLauncher.launch(e.intent)
                            message = "Please grant Drive access to continue."
                        } else {
                            message = "Signed in, but restore failed: ${e.message}"
                        }
                    }
                    busy = false
                    refreshTick++
                }
            }
        }
    }

    fun doPickAccount() {
        val intent = AccountManager.newChooseAccountIntent(
            null, // selectedAccount
            null, // allowableAccounts
            arrayOf("com.google"), // allowableAccountTypes
            false, // alwaysPromptForAccount
            null, // descriptionOverrideText
            null, // addAccountAuthTokenType
            null, // addAccountRequiredFeatures
            null // addAccountOptions
        )
        accountPickerLauncher.launch(intent)
    }

    fun doSignOut() {
        sync.signOut()
        refreshTick++
        message = "Signed out."
    }

    fun doBackup() {
        if (syncing || busy) return
        scope.launch {
            syncing = true
            error = null
            sync.backupNow().onSuccess {
                message = "Backup saved to Google Drive."
            }.onFailure { e ->
                if (e is GoogleDriveSyncManager.AuthRequiredException) {
                    authLauncher.launch(e.intent)
                    message = "Please grant Drive access to continue."
                } else {
                    error = "Backup failed: ${e.message}"
                }
            }
            syncing = false
            refreshTick++
        }
    }

    fun doRestore() {
        if (syncing || busy) return
        scope.launch {
            syncing = true
            error = null
            sync.restoreFromCloud().onSuccess { restored ->
                message = if (restored) "Everything restored from Google Drive." else "No backup found in Google Drive."
            }.onFailure { e ->
                if (e is GoogleDriveSyncManager.AuthRequiredException) {
                    authLauncher.launch(e.intent)
                    message = "Please grant Drive access to continue."
                } else {
                    error = "Restore failed: ${e.message}"
                }
            }
            syncing = false
            refreshTick++
        }
    }

    GlowBackground {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Account & Sync") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
                message?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }

                if (!isSignedIn) {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Filled.CloudDone,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Sync with Google",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                "Choose your Google account to automatically backup and sync your watchlist, history, addons, settings, and API keys to Google Drive.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = ::doPickAccount,
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (busy) {
                                    OrbitDots(modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Signing in...")
                                } else {
                                    Text("Choose Google Account")
                                }
                            }
                        }
                    }
                } else {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Filled.CloudDone,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        profileName.ifBlank { "Signed in" },
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        accountEmail ?: "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            lastBackup?.let {
                                Text(
                                    "Last backup: $it",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            lastRestore?.let {
                                Text(
                                    "Last restore: $it",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(Modifier.height(4.dp))

                            Button(
                                onClick = ::doBackup,
                                enabled = !syncing && !busy,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (syncing) {
                                    OrbitDots(modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Syncing...")
                                } else {
                                    Icon(Icons.Filled.CloudUpload, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Backup Now")
                                }
                            }

                            OutlinedButton(
                                onClick = ::doRestore,
                                enabled = !syncing && !busy,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.CloudDownload, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Restore from Drive")
                            }

                            OutlinedButton(
                                onClick = {
                                    nameDraft = profileName
                                    editingName = true
                                },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (profileName.isBlank()) "Set Username" else "Edit Username")
                            }

                            OutlinedButton(
                                onClick = ::doSignOut,
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Logout, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Sign Out")
                            }
                        }
                    }

                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Automatic Sync",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Your data syncs automatically to your private Google Drive folder. " +
                                "When you sign in on a new device, everything restores automatically - " +
                                "watchlist, watch history, addons, settings, and API keys.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (editingName) {
                    AlertDialog(
                        onDismissRequest = { editingName = false },
                        title = { Text("Username") },
                        text = {
                            Column {
                                Text(
                                    "This name is shown in the app instead of your Gmail address.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = nameDraft,
                                    onValueChange = { nameDraft = it },
                                    singleLine = true,
                                    label = { Text("Your name") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                repository.settings.profileUserName = nameDraft
                                editingName = false
                                refreshTick++
                            }) { Text("Save") }
                        },
                        dismissButton = {
                            TextButton(onClick = { editingName = false }) { Text("Cancel") }
                        }
                    )
                }
            }
        }
    }
}
