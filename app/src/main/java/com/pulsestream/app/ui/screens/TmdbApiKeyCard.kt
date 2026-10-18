package com.pulsestream.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.pulsestream.app.ui.components.GlassCard
import com.pulsestream.app.ui.screens.SettingsIconTile
import com.pulsestream.app.ui.theme.GlassBorder
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary

/**
 * TMDB API key input card (Integrations screen).
 * User pastes their free TMDB API key; enables clickable actors and
 * in-app "You May Like" recommendations. Key is masked in the UI.
 * Moved here from SettingsScreen so all integration cards live together.
 */
@Composable
fun TmdbApiKeyCard(settings: com.pulsestream.app.data.SettingsStore) {
    val palette = LocalPalette.current
    var keyInput by remember { mutableStateOf(settings.tmdbApiKey) }
    var saved by remember { mutableStateOf(settings.tmdbApiKey.isNotBlank()) }
    var showSavedTick by remember { mutableStateOf(false) }

    // Masked display: first 4 chars + "..." or "Not set"
    val maskedKey = remember(keyInput, saved) {
        val k = if (saved) settings.tmdbApiKey else keyInput
        if (k.isBlank()) "Not set"
        else if (k.length <= 4) "••••"
        else k.take(4) + "••••••••"
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SettingsIconTile(Icons.Filled.VpnKey, "TMDB API Key")
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "TMDB API Key",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (saved) "Key saved: $maskedKey" else "Not set",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (saved) palette.accent else TextSecondary
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = keyInput,
                onValueChange = {
                    keyInput = it.trim()
                    if (showSavedTick) showSavedTick = false
                },
                label = { Text("Paste your TMDB API key") },
                placeholder = { Text("e.g. a1b2c3d4...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = palette.accent,
                    focusedLabelColor = palette.accent,
                    unfocusedBorderColor = GlassBorder,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )
            Spacer(Modifier.height(8.dp))
            val tmdbContext = LocalContext.current
            Text(
                "Get your free key",
                style = MaterialTheme.typography.bodySmall,
                color = palette.accent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable {
                    tmdbContext.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://www.themoviedb.org/settings/api")
                        )
                    )
                }
            )
            Text(
                "— enables clickable actors and recommendations.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        settings.tmdbApiKey = keyInput.trim()
                        saved = keyInput.isNotBlank()
                        showSavedTick = true
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.accent.copy(alpha = 0.18f),
                        contentColor = palette.accent
                    )
                ) {
                    if (showSavedTick) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Saved")
                    } else {
                        Text("Save Key")
                    }
                }
                OutlinedButton(
                    onClick = {
                        keyInput = ""
                        settings.tmdbApiKey = ""
                        saved = false
                        showSavedTick = false
                    },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Clear", color = TextSecondary)
                }
            }
        }
    }
}
