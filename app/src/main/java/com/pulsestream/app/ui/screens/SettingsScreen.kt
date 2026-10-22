package com.pulsestream.app.ui.screens

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulsestream.app.BuildConfig
import com.pulsestream.app.R
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.data.cloud.GoogleDriveSyncManager
import com.pulsestream.app.data.simkl.SimklApi
import com.pulsestream.app.data.update.UpdateChecker
import com.pulsestream.app.data.update.UpdateInfo
import com.pulsestream.app.ui.components.GlassCard
import com.pulsestream.app.ui.components.liquidGlass
import com.pulsestream.app.ui.components.UpdatePrompt
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.AccentTheme
import com.pulsestream.app.ui.theme.GlassBorder
import com.pulsestream.app.ui.theme.GlassWhite
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import com.pulsestream.app.ui.tv.tvFocusRing
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    repository: StreamRepository,
    onThemeChanged: () -> Unit = {},
    onGoToAddons: () -> Unit = {},
    onGoToHomeCatalogs: () -> Unit = {},
    onGoToPlayback: () -> Unit = {},
    onGoToSubtitles: () -> Unit = {},
    onGoToAppearance: () -> Unit = {},
    onGoToStreams: () -> Unit = {},
    onGoToAbout: () -> Unit = {},
    onGoToFeedback: () -> Unit = {},
    onGoToAccount: () -> Unit = {},
    onGoToIntegrations: () -> Unit = {}
) {
    val settings = repository.settings
    val context = LocalContext.current
    var showDiagnostics by remember { mutableStateOf(false) }
    var telemetryEnabled by remember { mutableStateOf(com.pulsestream.app.data.AppStats.enabled(context)) }

    // ---- Sample F (v0.12.4): searchable compact root screen ----
    // The account card reads the same sync manager the Account screen
    // uses; this screen is recreated on every visit, so the values are
    // fresh each time it opens.
    var settingsQuery by rememberSaveable { mutableStateOf("") }
    val syncManager = remember { GoogleDriveSyncManager(context, repository.settings, repository) }
    val signedIn = remember { syncManager.isSignedIn() }
    val accountEmail = remember { syncManager.getAccountEmail() }
    val lastBackup = remember { syncManager.getLastBackupTime() }
    // The user's own chosen username (set in Account) wins over the
    // email-derived name (owner's order 2026-10-22).
    val profileUserName = remember { settings.profileUserName }
    val accountName = remember(accountEmail, profileUserName) {
        profileUserName.takeIf { it.isNotBlank() } ?: accountEmail?.let { deriveDisplayName(it) }
    }
    val installedAddons by repository.addons.collectAsState(initial = emptyList())
    val enabledAddons = remember(installedAddons) { installedAddons.count { it.enabled } }
    val reducedMotion by com.pulsestream.app.data.MotionPreferences.reduced.collectAsState()

    val sectionAccount = stringResource(R.string.settings_section_account)
    val sectionPlayback = stringResource(R.string.settings_section_playback)
    val sectionContent = stringResource(R.string.settings_section_content)
    val sectionAppearance = stringResource(R.string.settings_section_appearance)
    val sectionHelp = "Help & About"

    // Every root entry as data, so the search field filters the very
    // rows the sections render. Destinations and behaviour are exactly
    // the pre-restructure ones — presentation only. The old Account nav
    // row is gone: the account card above the list IS the account entry.
    val entries = listOf(
        SettingsEntry(sectionAccount, "Integrations",
            "integrations torbox debrid real-debrid alldebrid premiumize link accounts api keys") {
            SettingsNavRow(
                icon = Icons.Filled.Link,
                title = "Integrations",
                subtitle = "TorBox debrid and more",
                onClick = onGoToIntegrations,
                compact = true
            )
        },
        SettingsEntry(sectionPlayback, stringResource(R.string.settings_playback_title),
            "playback autoplay next episode screen skip intro audio resume buffer") {
            SettingsNavRow(
                icon = Icons.Filled.PlayArrow,
                title = stringResource(R.string.settings_playback_title),
                subtitle = stringResource(R.string.settings_playback_sub),
                onClick = onGoToPlayback,
                compact = true
            )
        },
        SettingsEntry(sectionPlayback, stringResource(R.string.settings_streams_title),
            "streams sources source limits quality order autoplay rule providers") {
            SettingsNavRow(
                icon = Icons.Filled.VideoLibrary,
                title = stringResource(R.string.settings_streams_title),
                subtitle = stringResource(R.string.settings_streams_sub),
                onClick = onGoToStreams,
                compact = true
            )
        },
        SettingsEntry(sectionPlayback, stringResource(R.string.settings_subtitles_title),
            "subtitles captions preferred languages appearance font size") {
            SettingsNavRow(
                icon = Icons.Filled.ClosedCaption,
                title = stringResource(R.string.settings_subtitles_title),
                subtitle = stringResource(R.string.settings_subtitles_sub),
                onClick = onGoToSubtitles,
                compact = true
            )
        },
        SettingsEntry(sectionContent, stringResource(R.string.settings_addons_title),
            "addons manage extensions plugins install sources manifest") {
            SettingsStaticRow(
                icon = Icons.Filled.Extension,
                title = stringResource(R.string.settings_addons_title),
                subtitle = stringResource(
                    R.string.settings_addons_sub,
                    installedAddons.size,
                    enabledAddons
                ),
                trailing = {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = "Open addons",
                        tint = TextSecondary
                    )
                },
                onClick = onGoToAddons,
                compact = true
            )
        },
        SettingsEntry(sectionContent, stringResource(R.string.settings_home_catalogs_title),
            "home catalogs rows arrange visibility home screen order") {
            SettingsNavRow(
                icon = Icons.Filled.GridView,
                title = stringResource(R.string.settings_home_catalogs_title),
                subtitle = stringResource(R.string.settings_home_catalogs_sub),
                onClick = onGoToHomeCatalogs,
                compact = true
            )
        },
        SettingsEntry(sectionAppearance, stringResource(R.string.settings_appearance_title),
            "appearance accent color amoled black theme dark light") {
            SettingsNavRow(
                icon = Icons.Filled.Contrast,
                title = stringResource(R.string.settings_appearance_title),
                subtitle = stringResource(R.string.settings_appearance_sub),
                onClick = onGoToAppearance,
                compact = true
            )
        },
        SettingsEntry(sectionAppearance, "Reduced motion",
            "reduced motion animations accessibility pause hero slides pulses") {
            SettingsToggleRow(
                icon = Icons.Filled.Contrast,
                title = "Reduced motion",
                subtitle = "Pause automatic hero slides and decorative pulses; also respects Android animation settings.",
                checked = reducedMotion,
                onCheckedChange = { com.pulsestream.app.data.MotionPreferences.setReduced(context, it) },
                compact = true
            )
        },
        SettingsEntry(sectionHelp, "Source health",
            "source health diagnostics session status timing report providers") {
            SettingsNavRow(
                icon = Icons.Filled.Info,
                title = "Source health",
                subtitle = "Redacted status and timing for this session",
                onClick = { showDiagnostics = true },
                compact = true
            )
        },
        SettingsEntry(sectionHelp, "Anonymous usage counting",
            "anonymous counting installs online users privacy usage data own server") {
            SettingsToggleRow(
                icon = Icons.Filled.Info,
                title = "Anonymous usage counting",
                subtitle = "On by default. Counts installs and active users on Plavio's own server — a random ID only, no personal data, no Google.",
                checked = telemetryEnabled,
                onCheckedChange = {
                    com.pulsestream.app.data.AppStats.setEnabled(context, it)
                    telemetryEnabled = it
                },
                compact = true
            )
        },
        SettingsEntry(sectionHelp, stringResource(R.string.settings_feedback_title),
            "feedback send bug report feature request support") {
            SettingsNavRow(
                icon = Icons.Filled.Feedback,
                title = stringResource(R.string.settings_feedback_title),
                subtitle = stringResource(R.string.settings_feedback_sub),
                onClick = onGoToFeedback,
                compact = true
            )
        },
        SettingsEntry(sectionHelp, stringResource(R.string.settings_about_title),
            "about version website updates app info check") {
            SettingsNavRow(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.settings_about_title),
                subtitle = stringResource(R.string.settings_about_sub),
                onClick = onGoToAbout,
                compact = true
            )
        }
    )
    val normalizedQuery = settingsQuery.trim().lowercase()
    val visibleEntries = if (normalizedQuery.isEmpty()) entries
        else entries.filter { entry ->
            entry.title.lowercase().contains(normalizedQuery) ||
                entry.searchText.contains(normalizedQuery)
        }

    if (showDiagnostics) {
        val entries by com.pulsestream.app.data.SourceDiagnostics.entries.collectAsState()
        AlertDialog(
            onDismissRequest = { showDiagnostics = false },
            title = { Text("Source health") },
            text = {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    item { Text("This session only. Hashed source IDs, status and request time; no URLs, credentials or viewing history.") }
                    if (entries.isEmpty()) item { Text("No source requests recorded yet.") }
                    items(entries.reversed()) { entry ->
                        Text("${entry.provider}: ${entry.outcome.replace('_', ' ')} · ${entry.durationMs} ms",
                            modifier = Modifier.padding(vertical = 6.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Plavio source diagnostics",
                        com.pulsestream.app.data.SourceDiagnostics.export()))
                }) { Text("Copy safe report") }
            },
            dismissButton = {
                TextButton(onClick = { com.pulsestream.app.data.SourceDiagnostics.clear(); showDiagnostics = false }) { Text("Clear & close") }
            }
        )
    }

    GlowBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // Bottom padding ensures the floating nav capsule never covers the last card,
            // including on smaller phones (capsule ~80dp + system nav ~32dp + margin)
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }


            // ---- Settings search (Sample F) ----
            item(key = "settings_search") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .liquidGlass(RoundedCornerShape(50))
                ) {
                    OutlinedTextField(
                        value = settingsQuery,
                        onValueChange = { settingsQuery = it },
                        placeholder = { Text("Search settings", color = TextSecondary) },
                        leadingIcon = { Icon(Icons.Filled.Search, null, tint = TextSecondary) },
                        trailingIcon = if (settingsQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { settingsQuery = "" }) {
                                    Icon(Icons.Filled.Close, "Clear search", tint = TextSecondary)
                                }
                            }
                        } else null,
                        singleLine = true,
                        shape = RoundedCornerShape(50),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = LocalPalette.current.accent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ---- Account card (Sample F): signed-in Google account,
            // name + email only, plus sync status. This card IS the
            // account/backup entry — it opens the same Account screen
            // the old nav row did, with every backup/restore action
            // intact there. No product name for the sync feature is
            // shown anywhere in Settings (owner's order, v0.12.4).
            item(key = "account_card") {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 5.dp),
                    onClick = onGoToAccount
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(LocalPalette.current.accent.copy(alpha = 0.20f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (signedIn) {
                                Text(
                                    (accountName?.firstOrNull()?.uppercase() ?: "P"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = LocalPalette.current.accent
                                )
                            } else {
                                Icon(
                                    Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = LocalPalette.current.accent
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (signedIn) (accountName ?: "Signed in") else "Not signed in",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                when {
                                    signedIn && accountEmail != null -> accountEmail
                                    signedIn -> "Google account"
                                    else -> "Tap to sign in and enable backup & sync"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                if (signedIn) {
                                    lastBackup?.let { "Last backup: $it" }
                                        ?: "Signed in · backup & sync on"
                                } else "Backup & sync off",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = TextSecondary
                        )
                    }
                }
            }

            // ---- Entries: sticky section headers when browsing; a
            // flat filtered list while searching (Sample F) ----
            if (normalizedQuery.isEmpty()) {
                entries.groupBy { it.section }.forEach { (section, sectionEntries) ->
                    stickyHeader(key = "section_$section") {
                        SettingsSectionHeader(section)
                    }
                    items(sectionEntries, key = { "entry_${it.title}" }) { entry ->
                        entry.content()
                    }
                }
            } else {
                items(visibleEntries, key = { "entry_${it.title}" }) { entry ->
                    entry.content()
                }
                if (visibleEntries.isEmpty()) {
                    item(key = "no_settings_match") {
                        Text(
                            "No settings match \"${settingsQuery.trim()}\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp, vertical = 24.dp)
                        )
                    }
                }
            }

            item(key = "credit") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Made with 💚 by Kotlinsyd",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    SocialIconRow()
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

/** Device-code dialog for linking a Simkl account. */
@Composable
fun SimklPinDialog(
    deviceData: SimklApi.DeviceData?,
    error: String?,
    onOpenSite: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link Simkl", color = TextPrimary) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when {
                    error != null -> {
                        Text(error, color = TextSecondary, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "You can retry from the Simkl row.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                    deviceData == null -> {
                        CircularProgressIndicator(color = LocalPalette.current.accent)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Talking to Simkl…",
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                    else -> {
                        Text(
                            "Enter this code at simkl.com/pin — or tap Open Simkl to pre-fill it",
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            deviceData.userCode,
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 6.sp
                            ),
                            color = LocalPalette.current.accent,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "This window closes automatically once you approve the code.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenSite, enabled = deviceData != null) {
                Text("Open Simkl", color = LocalPalette.current.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = Color(0xFF14141F)
    )
}

/** One root Settings entry for the Sample F searchable list (v0.12.4). */
private data class SettingsEntry(
    val section: String,
    val title: String,
    /** Extra lowercase keywords the settings search matches against. */
    val searchText: String,
    val content: @Composable () -> Unit
)

/**
 * Display name for the Settings account card, derived from the account
 * email's local part ("syed.najam" → "Syed Najam") — the sync layer
 * exposes only the email address, so the name is derived at display
 * time and never stored.
 */
private fun deriveDisplayName(email: String): String =
    email.substringBefore('@')
        .split('.', '_', '-', '+')
        .filter { it.isNotBlank() }
        .joinToString(" ") { part -> part.replaceFirstChar { c -> c.uppercase() } }
        .ifBlank { email }

/** Small-caps section header, Pulse style. */
@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        ),
        color = TextSecondary,
        modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
    )
}

/** Leading icon in a rounded glass tile. */
@Composable
fun SettingsIconTile(icon: ImageVector, contentDescription: String?) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .liquidGlass(RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = LocalPalette.current.accent, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    compact: Boolean = false
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = if (compact) 3.dp else 5.dp),
        onClick = { onCheckedChange(!checked) }
    ) {
        Row(
            Modifier.fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = if (compact) 9.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (compact) {
                Icon(
                    icon,
                    contentDescription = title,
                    tint = LocalPalette.current.accent,
                    modifier = Modifier.size(21.dp)
                )
                Spacer(Modifier.width(12.dp))
            } else {
                SettingsIconTile(icon, title)
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = LocalPalette.current.accent,
                    checkedTrackColor = LocalPalette.current.accent.copy(alpha = 0.35f)
                )
            )
        }
    }
}

/** Navigation row: icon tile + title/subtitle + chevron, opens a sub-screen. */
@Composable
fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    compact: Boolean = false
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = if (compact) 3.dp else 5.dp),
        onClick = onClick
    ) {
        Row(
            Modifier.fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = if (compact) 9.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (compact) {
                Icon(
                    icon,
                    contentDescription = title,
                    tint = LocalPalette.current.accent,
                    modifier = Modifier.size(21.dp)
                )
                Spacer(Modifier.width(12.dp))
            } else {
                SettingsIconTile(icon, title)
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Spacer(Modifier.width(12.dp))
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = TextSecondary
            )
        }
    }
}

/** Non-interactive row (used for "coming soon" placeholders). */
@Composable
fun SettingsStaticRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
    dimmed: Boolean = false,
    onClick: (() -> Unit)? = null,
    compact: Boolean = false
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = if (compact) 3.dp else 5.dp)
            .alpha(if (dimmed) 0.6f else 1f),
        onClick = onClick
    ) {
        Row(
            Modifier.fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = if (compact) 9.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (compact) {
                Icon(
                    icon,
                    contentDescription = title,
                    tint = LocalPalette.current.accent,
                    modifier = Modifier.size(21.dp)
                )
                Spacer(Modifier.width(12.dp))
            } else {
                SettingsIconTile(icon, title)
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** Script font used for the "Najam Syyd" signature in About. */

@Composable
fun AboutCard() {
    val context = LocalContext.current
    GlassCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.mipmap.app_logo),
                contentDescription = "Plavio logo",
                modifier = Modifier.size(88.dp).clip(RoundedCornerShape(26.dp))
            )
            // Name + version text REMOVED (owner's order 2026-10-22):
            // the logo already says Plavio, and the updater card below
            // already shows the version — the pair only duplicated both.
            Spacer(Modifier.height(14.dp))
            Text(
                text = "A beautiful open-source streaming app with a built-in " +
                    "player. Add your favorite addons and enjoy your movies " +
                    "and shows in one glowing place.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable {
                        // Devices with no browser must not force-close.
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.plavio.site"))
                            )
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    Icons.Filled.Language,
                    contentDescription = null,
                    tint = LocalPalette.current.accent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "plavio.site",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = LocalPalette.current.accent,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Filled.Link,
                    contentDescription = null,
                    tint = LocalPalette.current.accent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Data: Cinemeta • OpenSubtitles • IntroDB",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** Settings > About > App Updater: auto-check toggle + manual update check. */
@Composable
fun AppUpdaterCard() {
    val appContext = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var autoCheck by remember { mutableStateOf(UpdateChecker.isAutoCheckEnabled(appContext)) }
    var checking by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }

    GlassCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SettingsIconTile(Icons.Filled.SystemUpdate, "App updater")
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "App Updater",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "You're on v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Check automatically",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                    Text(
                        "Pop up when a new version is available",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = autoCheck,
                    onCheckedChange = {
                        autoCheck = it
                        UpdateChecker.setAutoCheckEnabled(appContext, it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = LocalPalette.current.accent,
                        checkedTrackColor = LocalPalette.current.accent.copy(alpha = 0.35f)
                    )
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    checking = true
                    status = null
                    scope.launch {
                        // Classified outcome: tell offline apart from
                        // up-to-date instead of "latest version" for all of it.
                        when (val result = UpdateChecker.checkNowDetailed(appContext)) {
                            is UpdateChecker.UpdateCheckResult.Update -> {
                                updateInfo = result.info
                            }
                            UpdateChecker.UpdateCheckResult.UpToDate -> {
                                status = "You're on the latest version."
                            }
                            UpdateChecker.UpdateCheckResult.Offline -> {
                                status = "Couldn't check — you're offline."
                            }
                            is UpdateChecker.UpdateCheckResult.Error -> {
                                status = "Couldn't check for updates: ${result.message}"
                            }
                        }
                        checking = false
                    }
                },
                enabled = !checking,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LocalPalette.current.accent.copy(alpha = 0.18f),
                    contentColor = LocalPalette.current.accent,
                    disabledContainerColor = LocalPalette.current.accent.copy(alpha = 0.08f)
                )
            ) {
                if (checking) {
                    CircularProgressIndicator(
                        color = LocalPalette.current.accent,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Checking…")
                } else {
                    Text("Check for update now")
                }
            }
            status?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    UpdatePrompt(
        info = updateInfo,
        onDismiss = { updateInfo = null }
    )
}

/**
 * Icon-only social/community links, placed directly below the App Installs
 * counter. Icons only, no text labels (content descriptions for a11y).
 */
@Composable
private fun SocialIconRow() {
    val context = LocalContext.current
    val palette = LocalPalette.current
    val links = listOf(
        Triple(R.drawable.ic_discord, "Discord", "https://discord.gg/pbNwJKU3p"),
        Triple(R.drawable.ic_github, "GitHub", "https://github.com/kotlinsyd/Plavio"),
        Triple(R.drawable.ic_gitlab, "GitLab", "https://gitlab.com/KotlinJS5/plavio"),
        Triple(R.drawable.ic_reddit, "Reddit", "https://www.reddit.com/r/Plavio/"),
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        links.forEach { (iconRes, label, url) ->
            IconButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = palette.panel.copy(alpha = 0.7f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = label,
                    // No tint — show icons in their native white for clean look
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
