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
import androidx.compose.material.icons.filled.OpenInNew
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
import androidx.compose.runtime.getValue
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
                DebridCard(
                    title = "TorBox",
                    description = "Resolves cached torrent streams to instant HTTP links. " +
                        "Debrid-only — nothing is downloaded in the app.",
                    keyLinkUrl = "https://torbox.app",
                    keyLinkText = "Get your API key at torbox.app/settings",
                    currentKey = { settings.torboxApiKey },
                    onSaveKey = { settings.torboxApiKey = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            item(key = "realdebrid") {
                DebridCard(
                    title = "Real-Debrid",
                    description = "Resolves cached torrent streams to instant HTTP links. " +
                        "Debrid-only — nothing is downloaded in the app.",
                    keyLinkUrl = "https://real-debrid.com",
                    keyLinkText = "Get your API key at real-debrid.com/apitoken",
                    currentKey = { settings.realDebridApiKey },
                    onSaveKey = { settings.realDebridApiKey = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
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
    modifier: Modifier = Modifier
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
                placeholder = { Text("Paste your API key") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
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
