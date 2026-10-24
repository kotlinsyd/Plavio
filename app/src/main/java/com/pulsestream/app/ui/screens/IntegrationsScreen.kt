package com.pulsestream.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.pulsestream.app.data.StreamRepository
import kotlinx.coroutines.launch
import com.pulsestream.app.ui.components.GlassCard
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary

/**
 * Integrations screen (v0.9.0+): third-party service connections.
 *
 * Hosts debrid integrations (TorBox, Real-Debrid): paste an API key and
 * torrent infoHash streams resolve to instant direct HTTP links.
 * Debrid-only — the app never downloads torrents itself.
 */
@Composable
fun IntegrationsScreen(
    repository: StreamRepository,
    onBack: () -> Unit = {}
) {
    val settings = repository.settings

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
                            text = "Integrations",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Connect third-party services to unlock extra features.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 56.dp, end = 8.dp)
                    )
                }
            }

            item(key = "torbox") {
                var showTorboxDialog by remember { mutableStateOf(false) }
                var torboxTick by remember { mutableIntStateOf(0) }
                val torboxConfigured = remember(torboxTick) { !settings.torboxApiKey.isNullOrBlank() }
                SettingsStaticRow(
                    icon = Icons.Filled.Bolt,
                    title = "TorBox",
                    subtitle = if (torboxConfigured) "API key saved" else "Add your API key for cached streams",
                    trailing = {
                        if (torboxConfigured) {
                            Text(
                                "Saved",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = LocalPalette.current.accent
                            )
                        } else {
                            Icon(
                                Icons.Filled.ChevronRight,
                                contentDescription = "Configure TorBox",
                                tint = TextSecondary
                            )
                        }
                    },
                    onClick = { showTorboxDialog = true }
                )
                if (showTorboxDialog) {
                    ApiKeyDialog(
                        title = "TorBox API Key",
                        keyLinkText = "Get your API key at torbox.app/settings",
                        keyLinkUrl = "https://torbox.app",
                        currentKey = settings.torboxApiKey,
                        enabled = true,
                        helpText = "Paste your TorBox API key and tap Save. To remove it, clear the field and save. Saving does not validate the key.",
                        onSave = { settings.torboxApiKey = it; torboxTick++ },
                        onDismiss = { showTorboxDialog = false }
                    )
                }
            }

            item(key = "realdebrid") {
                var showRdDialog by remember { mutableStateOf(false) }
                var rdTick by remember { mutableIntStateOf(0) }
                val rdConfigured = remember(rdTick) { !settings.realDebridApiKey.isNullOrBlank() }
                SettingsStaticRow(
                    icon = Icons.Filled.Bolt,
                    title = "Real-Debrid",
                    subtitle = if (rdConfigured) "Connected" else "Under testing — coming soon",
                    trailing = {
                        if (rdConfigured) {
                            Text(
                                "Connected",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = LocalPalette.current.accent
                            )
                        } else {
                            Icon(
                                Icons.Filled.ChevronRight,
                                contentDescription = "Configure Real-Debrid",
                                tint = TextSecondary
                            )
                        }
                    },
                    onClick = { showRdDialog = true }
                )
                if (showRdDialog) {
                    ApiKeyDialog(
                        title = "Real-Debrid API Key",
                        keyLinkText = "Get your API key at real-debrid.com/apitoken",
                        keyLinkUrl = "https://real-debrid.com",
                        currentKey = settings.realDebridApiKey,
                        enabled = false,
                        onSave = { settings.realDebridApiKey = it; rdTick++ },
                        onDismiss = { showRdDialog = false }
                    )
                }
            }

            item(key = "tmdb_key") {
                var showTmdbDialog by remember { mutableStateOf(false) }
                var tmdbTick by remember { mutableIntStateOf(0) }
                val tmdbConfigured = remember(tmdbTick) { settings.tmdbApiKey.isNotBlank() }
                val maskedKey = remember(tmdbTick) {
                    val k = settings.tmdbApiKey
                    if (k.isBlank()) "" else if (k.length <= 4) "••••" else k.take(4) + "••••••••"
                }
                SettingsStaticRow(
                    icon = Icons.Filled.VpnKey,
                    title = "TMDB API Key",
                    subtitle = if (tmdbConfigured) "Key saved: $maskedKey" else "Enables clickable actors and recommendations",
                    trailing = {
                        if (tmdbConfigured) {
                            Text(
                                "Set",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = LocalPalette.current.accent
                            )
                        } else {
                            Icon(
                                Icons.Filled.ChevronRight,
                                contentDescription = "Set TMDB API key",
                                tint = TextSecondary
                            )
                        }
                    },
                    onClick = { showTmdbDialog = true }
                )
                if (showTmdbDialog) {
                    ApiKeyDialog(
                        title = "TMDB API Key",
                        keyLinkText = "Get your free key",
                        keyLinkUrl = "https://www.themoviedb.org/settings/api",
                        currentKey = settings.tmdbApiKey,
                        enabled = true,
                        onSave = { settings.tmdbApiKey = it; tmdbTick++ },
                        onDismiss = { showTmdbDialog = false }
                    )
                }
            }

            item(key = "watch_sync_header") {
                Text(
                    "Watch history sync",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
                )
            }

            item(key = "trakt_simkl") {
                TraktSimklSection(settings = settings)
            }

            item(key = "library_sync") {
                LibrarySyncRow(repository = repository)
            }

            item(key = "note") {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Note",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Cached torrents stream instantly without using download slots. " +
                                "When both are connected, TorBox is tried first, then Real-Debrid.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "⚠️ This feature is under development and testing, so you may experience issues. " +
                                "For any problem, please report it via Settings → Feedback, " +
                                "open an issue on GitHub, or reach out on our Discord channel.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

/**
 * One debrid service card: status indicator, password-masked API key field,
 * link to get a key, and plain Save / Remove buttons.
 */
@Composable
private fun DebridCard(
    title: String,
    description: String,
    keyLinkUrl: String,
    keyLinkText: String,
    currentKey: () -> String?,
    onSaveKey: (String?) -> Unit,
    modifier: Modifier = Modifier,
    tagLabel: String? = null,
    enabled: Boolean = true
) {
    val context = LocalContext.current
    val palette = LocalPalette.current

    // Bumped on save/remove so the "configured" state recomposes.
    var tick by remember { mutableIntStateOf(0) }
    val configured = remember(tick) { !currentKey().isNullOrBlank() }

    var keyInput by remember(tick) { mutableStateOf(currentKey() ?: "") }
    var showKey by remember { mutableStateOf(false) }
    var savedFlash by remember { mutableStateOf(false) }

    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Bolt,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                if (tagLabel != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        tagLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.accent,
                        modifier = Modifier
                            .padding(start = 4.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    if (configured) "Connected" else "Not connected",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (configured) palette.accent else TextSecondary
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = keyInput,
                onValueChange = {
                    keyInput = it
                    savedFlash = false
                },
                label = { Text("$title API key") },
                placeholder = { Text(if (enabled) "Paste your API key" else "Under testing") },
                singleLine = true,
                enabled = enabled,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }, enabled = enabled) {
                        Icon(
                            if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (showKey) "Hide key" else "Show key",
                            tint = TextSecondary
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            TextButton(
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(keyLinkUrl))
                    )
                }
            ) {
                Text(keyLinkText)
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        onSaveKey(keyInput.trim().ifBlank { null })
                        tick++
                        savedFlash = true
                    },
                    enabled = enabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.accent
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (savedFlash) "Saved ✓" else "Save")
                }
                if (configured) {
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            onSaveKey(null)
                            keyInput = ""
                            savedFlash = false
                            tick++
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Remove")
                    }
                }
            }
        }
    }
}

/**
 * Simple API key input dialog, shown when tapping a service row.
 */
@Composable
private fun ApiKeyDialog(
    title: String,
    keyLinkText: String,
    keyLinkUrl: String,
    currentKey: String?,
    enabled: Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    helpText: String? = null
) {
    val context = LocalContext.current
    var keyInput by remember { mutableStateOf(currentKey ?: "") }
    var showKey by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = TextPrimary) },
        text = {
            Column {
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it; saveFailed = false },
                    label = { Text("API key") },
                    singleLine = true,
                    enabled = enabled,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }, enabled = enabled) {
                            Icon(
                                if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showKey) "Hide key" else "Show key",
                                tint = TextSecondary
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(keyLinkUrl))
                        )
                    }
                ) {
                        Text(keyLinkText, color = LocalPalette.current.accent)
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.OpenInNew,
                        contentDescription = null,
                        tint = LocalPalette.current.accent
                    )
                }
                if (helpText != null) {
                    Text(
                        helpText,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                if (saveFailed) {
                    Text(
                        "Could not save the API key securely. Please try again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (!enabled) {
                    Text(
                        "Currently under testing — coming soon.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    try {
                        onSave(keyInput.trim())
                        onDismiss()
                    } catch (_: Exception) {
                        // Never expose the key or a storage exception in UI/logs.
                        saveFailed = true
                    }
                },
                enabled = enabled
            ) {
                Text("Save", color = LocalPalette.current.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

/**
 * Library sync row (issue #3): keeps the in-app library in sync with the
 * linked Trakt watchlist / Simkl library. Manual "Sync" tap, plus a quiet
 * auto-sync when this screen opens and the last run is over 15 minutes old.
 */
@Composable
private fun LibrarySyncRow(repository: StreamRepository) {
    val settings = repository.settings
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val linked = remember(tick) { repository.librarySyncLinked() }
    val lastResult = remember(tick) { settings.librarySyncLastResult }

    fun runSync() {
        if (busy || !linked) return
        busy = true
        scope.launch {
            runCatching { repository.syncLibraryWithServices() }
            busy = false
            tick++
        }
    }

    LaunchedEffect(Unit) {
        if (linked && System.currentTimeMillis() - settings.librarySyncLastAt > 15 * 60_000L) {
            runSync()
        }
    }

    SettingsStaticRow(
        icon = Icons.Filled.Sync,
        title = "Library sync",
        subtitle = when {
            !linked -> "Link Trakt or Simkl above to sync your library"
            busy -> "Syncing…"
            lastResult.isNotBlank() -> lastResult
            else -> "Keep this library in sync with Trakt and Simkl"
        },
        trailing = {
            if (linked) {
                Text(
                    if (busy) "…" else "Sync",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = LocalPalette.current.accent
                )
            } else {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = TextSecondary
                )
            }
        },
        onClick = { runSync() }
    )
}
