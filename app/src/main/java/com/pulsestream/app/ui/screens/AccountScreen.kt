package com.pulsestream.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.data.cloud.CloudManager
import com.pulsestream.app.ui.components.GlassCard
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.screens.SettingsStaticRow
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * Account screen (v0.11.2+): Plavio Cloud login + profiles.
 * Login is OPTIONAL — the app works fully without an account.
 * When logged in: backup/restore watchlist, continue watching, settings, addons.
 * Profiles: multiple profiles per account (e.g. for family members).
 * The last used profile is remembered — no login prompt on every launch.
 */
@Composable
fun AccountScreen(
    repository: StreamRepository,
    onBack: () -> Unit = {}
) {
    val settings = repository.settings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cloud = remember { CloudManager(context, settings, repository) }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoginMode by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var cloudTick by remember { mutableStateOf(0) }

    val isLoggedIn = remember(cloudTick) { cloud.isLoggedIn }
    val userEmail = remember(cloudTick) { cloud.userEmail }
    val profiles = remember(cloudTick) { cloud.getProfiles() }
    val activeProfileId = remember(cloudTick) { cloud.activeProfileId }

    fun doAuth() {
        if (email.isBlank() || password.isBlank()) {
            error = "Enter your email and password."
            return
        }
        if (password.length < 6) {
            error = "Password must be at least 6 characters."
            return
        }
        busy = true
        error = null
        scope.launch {
            val result = if (isLoginMode) {
                cloud.signIn(email.trim(), password)
            } else {
                cloud.signUp(email.trim(), password)
            }
            busy = false
            result.onFailure {
                val msg = it.message ?: "Something went wrong. Try again."
                if (msg.startsWith("VERIFY_EMAIL:")) {
                    // Show as info, not error - user needs to verify email
                    error = msg.removePrefix("VERIFY_EMAIL:")
                    // Switch to login mode so they can log in after verifying
                    isLoginMode = true
                } else {
                    error = msg
                }
            }.onSuccess {
                // Auto-sync: restore cloud backup on login
                if (isLoginMode) {
                    syncing = true
                    cloud.autoSyncOnLogin()
                    syncing = false
                }
                cloudTick++
                email = ""
                password = ""
            }
        }
    }

    GlowBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item(key = "header") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Account",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Optional. Log in to back up your data and use profiles.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 56.dp, end = 8.dp)
                    )
                }
            }

            if (!isLoggedIn) {
                // Login / signup form
                item(key = "cloud_login") {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.CloudUpload,
                                    contentDescription = null,
                                    tint = LocalPalette.current.accent,
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Plavio Cloud",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        "Back up watchlist, progress, settings & addons",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                            Spacer(Modifier.height(16.dp))

                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it.trim(); error = null },
                                label = { Text("Email") },
                                singleLine = true,
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(50),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = LocalPalette.current.accent,
                                    focusedLabelColor = LocalPalette.current.accent
                                )
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it; error = null },
                                label = { Text("Password") },
                                singleLine = true,
                                enabled = !busy,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(50),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = LocalPalette.current.accent,
                                    focusedLabelColor = LocalPalette.current.accent
                                )
                            )
                            if (error != null) {
                                Spacer(Modifier.height(8.dp))
                                Text(error!!, color = Color(0xFFF87171), style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { doAuth() },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = LocalPalette.current.accent
                                )
                            ) {
                                if (busy || syncing) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                                Text(
                                    when {
                                        syncing -> "Syncing..."
                                        isLoginMode -> "Log in"
                                        else -> "Create account"
                                    },
                                    color = Color.White
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            TextButton(
                                onClick = { isLoginMode = !isLoginMode; error = null },
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Text(
                                    if (isLoginMode) "New here? Create an account"
                                    else "Already have an account? Log in",
                                    color = LocalPalette.current.accent
                                )
                            }
                        }
                    }
                }

                item(key = "cloud_note") {
                    Text(
                        "Your data stays on your device unless you log in. " +
                            "Login is optional — the app works fully without it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
                    )
                }
            } else {
                // Logged in: show user + profiles + backup
                item(key = "user_info") {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.AccountCircle,
                                contentDescription = null,
                                tint = LocalPalette.current.accent,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    userEmail ?: "Plavio Cloud",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Text(
                                    "Logged in",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LocalPalette.current.accent
                                )
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    cloud.signOut()
                                    cloudTick++
                                }
                            }) {
                                Text("Log out", color = TextSecondary)
                            }
                        }
                    }
                }

                item(key = "profiles_header") {
                    Row(
                        Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Profiles",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            scope.launch {
                                cloud.createProfile("Profile ${profiles.size + 1}")
                                cloudTick++
                            }
                        }) {
                            Icon(Icons.Filled.Add, contentDescription = "Add profile", tint = LocalPalette.current.accent)
                            Text("Add", color = LocalPalette.current.accent)
                        }
                    }
                }

                profiles.forEach { profile ->
                    item(key = "profile_${profile.id}") {
                        val isActive = profile.id == activeProfileId
                        var showDeleteDialog by remember { mutableStateOf(false) }
                        SettingsStaticRow(
                            icon = Icons.Filled.AccountCircle,
                            title = profile.name,
                            subtitle = if (isActive) "Active profile" else "Tap to switch",
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isActive) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = "Active",
                                            tint = LocalPalette.current.accent
                                        )
                                    }
                                    if (profiles.size > 1) {
                                        IconButton(
                                            onClick = { showDeleteDialog = true },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.Delete,
                                                contentDescription = "Delete profile",
                                                tint = TextSecondary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            },
                            onClick = {
                                if (!isActive) {
                                    scope.launch {
                                        cloud.switchProfile(profile.id)
                                        cloudTick++
                                    }
                                }
                            }
                        )
                        if (showDeleteDialog) {
                            AlertDialog(
                                onDismissRequest = { showDeleteDialog = false },
                                title = { Text("Delete ${profile.name}?", color = TextPrimary) },
                                text = {
                                    Text(
                                        "This will delete the profile and its cloud backup. This cannot be undone.",
                                        color = TextSecondary
                                    )
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                cloud.deleteProfile(profile.id)
                                                cloudTick++
                                            }
                                            showDeleteDialog = false
                                        }
                                    ) {
                                        Text("Delete", color = Color.Red)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDeleteDialog = false }) {
                                        Text("Cancel", color = TextSecondary)
                                    }
                                }
                            )
                        }
                    }
                }

                item(key = "backup") {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "Backup",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Automatically backs up watchlist, continue watching, custom lists, settings, and installed addons.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    Icons.Filled.Sync,
                                    contentDescription = null,
                                    tint = LocalPalette.current.accent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Auto-sync is on",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            val lastBackup = remember(cloudTick) { cloud.lastBackupTime }
                            Text(
                                if (lastBackup != null) "Last synced: $lastBackup" else "Will sync automatically",
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
