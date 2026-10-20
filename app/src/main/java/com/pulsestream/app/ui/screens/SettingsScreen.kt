package com.pulsestream.app.ui.screens

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulsestream.app.BuildConfig
import com.pulsestream.app.R
import com.pulsestream.app.data.BadgePacks
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.data.simkl.SimklApi
import com.pulsestream.app.data.update.UpdateChecker
import com.pulsestream.app.data.update.UpdateInfo
import com.pulsestream.app.ui.components.GlassCard
import com.pulsestream.app.ui.components.liquidGlass
import com.pulsestream.app.ui.components.UpdatePrompt
import com.pulsestream.app.ui.components.BadgePackPreview
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.AccentTheme
import com.pulsestream.app.ui.theme.GlassBorder
import com.pulsestream.app.ui.theme.GlassWhite
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import com.pulsestream.app.ui.tv.tvFocusRing
import kotlinx.coroutines.launch

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
    onGoToFeaturesGuide: () -> Unit = {},
    onGoToAccount: () -> Unit = {},
    onGoToIntegrations: () -> Unit = {}
) {
    val settings = repository.settings
    val context = LocalContext.current
    var badgeStyleKey by remember { mutableStateOf(settings.streamCardStyle) }
    val badgeStyleLabel = BadgePacks.labelForKey(context, badgeStyleKey)
    var showBadgeStyleDialog by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var telemetryEnabled by remember { mutableStateOf(com.pulsestream.app.data.TelemetryConsent.enabled(context)) }

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

    if (showBadgeStyleDialog) {
        BadgeStyleDialog(
            initial = badgeStyleKey,
            onDismiss = { showBadgeStyleDialog = false },
            onSelect = { key ->
                badgeStyleKey = key
                settings.streamCardStyle = key
                showBadgeStyleDialog = false
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


            // ---- Account ----
            item { SettingsSectionHeader(stringResource(R.string.settings_section_account)) }
            item {
                SettingsNavRow(
                    icon = Icons.Filled.Person,
                    title = stringResource(R.string.settings_account_title),
                    subtitle = stringResource(R.string.settings_account_sub),
                    onClick = onGoToAccount
                )
            }
            item {
                SettingsNavRow(
                    icon = Icons.Filled.Link,
                    title = "Integrations",
                    subtitle = "TorBox debrid and more",
                    onClick = onGoToIntegrations
                )
            }
            // ---- Playback: everything that affects how video plays ----
            item { SettingsSectionHeader(stringResource(R.string.settings_section_playback)) }
            item {
                SettingsNavRow(
                    icon = Icons.Filled.PlayArrow,
                    title = stringResource(R.string.settings_playback_title),
                    subtitle = stringResource(R.string.settings_playback_sub),
                    onClick = onGoToPlayback
                )
            }
            item {
                SettingsNavRow(
                    icon = Icons.Filled.VideoLibrary,
                    title = stringResource(R.string.settings_streams_title),
                    subtitle = stringResource(R.string.settings_streams_sub),
                    onClick = onGoToStreams
                )
            }
            item {
                SettingsNavRow(
                    icon = Icons.Filled.ClosedCaption,
                    title = stringResource(R.string.settings_subtitles_title),
                    subtitle = stringResource(R.string.settings_subtitles_sub),
                    onClick = onGoToSubtitles
                )
            }

            // ---- Content: addons and home catalogs ----
            item { SettingsSectionHeader(stringResource(R.string.settings_section_content)) }
            item {
                val installedAddons by repository.addons.collectAsState(initial = emptyList())
                val enabledAddons = remember(installedAddons) { installedAddons.count { it.enabled } }
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
                    onClick = onGoToAddons
                )
            }
            item {
                SettingsNavRow(
                    icon = Icons.Filled.GridView,
                    title = stringResource(R.string.settings_home_catalogs_title),
                    subtitle = stringResource(R.string.settings_home_catalogs_sub),
                    onClick = onGoToHomeCatalogs
                )
            }

            // ---- Appearance ----
            item { SettingsSectionHeader(stringResource(R.string.settings_section_appearance)) }
            item {
                SettingsNavRow(
                    icon = Icons.Filled.Contrast,
                    title = stringResource(R.string.settings_appearance_title),
                    subtitle = stringResource(R.string.settings_appearance_sub),
                    onClick = onGoToAppearance
                )
            }
            item {
                val reduced by com.pulsestream.app.data.MotionPreferences.reduced.collectAsState()
                SettingsToggleRow(icon = Icons.Filled.Contrast, title = "Reduced motion",
                    subtitle = "Pause automatic hero slides and decorative pulses; also respects Android animation settings.",
                    checked = reduced, onCheckedChange = { com.pulsestream.app.data.MotionPreferences.setReduced(context, it) })
            }
            item(key = "badgestyle") {
                SettingsNavRow(
                    icon = Icons.Filled.Style,
                    title = stringResource(R.string.settings_badges_title),
                    subtitle = badgeStyleLabel,
                    onClick = { showBadgeStyleDialog = true }
                )
            }

            // ---- Help & About ----
            item { SettingsSectionHeader("Help & About") }
            item {
                SettingsNavRow(icon = Icons.Filled.Info, title = "Source health",
                    subtitle = "Redacted status and timing for this session", onClick = { showDiagnostics = true })
            }
            item {
                SettingsToggleRow(icon = Icons.Filled.Info, title = "Optional analytics & crash reports",
                    subtitle = "Off by default. Share usage and crash data with Firebase.",
                    checked = telemetryEnabled, onCheckedChange = {
                        com.pulsestream.app.data.TelemetryConsent.setEnabled(context, it)
                        telemetryEnabled = it
                    })
            }
            item {
                SettingsNavRow(
                    icon = Icons.Filled.Book,
                    title = "Features Guide",
                    subtitle = "Learn what Plavio can do",
                    onClick = onGoToFeaturesGuide
                )
            }
            item {
                SettingsNavRow(
                    icon = Icons.Filled.Feedback,
                    title = stringResource(R.string.settings_feedback_title),
                    subtitle = stringResource(R.string.settings_feedback_sub),
                    onClick = onGoToFeedback
                )
            }
            item {
                SettingsNavRow(
                    icon = Icons.Filled.Info,
                    title = stringResource(R.string.settings_about_title),
                    subtitle = stringResource(R.string.settings_about_sub),
                    onClick = onGoToAbout
                )
            }

            item(key = "credit") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Made with 💜 by Kotlinsyd",
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
    onCheckedChange: (Boolean) -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        onClick = { onCheckedChange(!checked) }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsIconTile(icon, title)
            Spacer(Modifier.width(14.dp))
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
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        onClick = onClick
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsIconTile(icon, title)
            Spacer(Modifier.width(14.dp))
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
    onClick: (() -> Unit)? = null
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .alpha(if (dimmed) 0.6f else 1f),
        onClick = onClick
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsIconTile(icon, title)
            Spacer(Modifier.width(14.dp))
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
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Plavio",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Black,
                    brush = LocalPalette.current.gradient
                )
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary
            )
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
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://plaviomedia.vercel.app"))
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
                    text = "plaviomedia.vercel.app",
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
/** Single-select dialog for the stream badge pack, with live previews + custom import. */
@Composable
private fun BadgeStyleDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(initial) }
    var customPacks by remember { mutableStateOf(BadgePacks.listCustomPacks(context)) }
    var importError by remember { mutableStateOf<String?>(null) }
    val accent = LocalPalette.current.accent

    // System file picker for a badge-pack JSON.
    val pickJson = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (text.isNullOrBlank()) {
            importError = "Couldn't read that file."
            return@rememberLauncherForActivityResult
        }
        val name = runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
            }
        }.getOrNull() ?: "custom.json"
        val err = BadgePacks.importCustomPack(context, name, text)
        if (err != null) {
            importError = err
        } else {
            importError = null
            customPacks = BadgePacks.listCustomPacks(context)
        }
    }

    // If the selected pack was deleted, clear the selection.
    LaunchedEffect(customPacks) {
        if (selected.startsWith(BadgePacks.CUSTOM_PREFIX) &&
            customPacks.none { it.key == selected }) {
            selected = ""
        }
    }

    // URL import state
    var urlInput by remember { mutableStateOf("") }
    var isDownloading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stream Badges", color = TextPrimary) },
        text = {
            Column {
                Text(
                    "Badge pack used for stream info badges.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                if (importError != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        importError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFEF4444)
                    )
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    if (customPacks.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                "No badge packs yet. Import one via file or link below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                    items(customPacks, key = { it.key }) { pack ->
                        BadgePackRow(
                            packKey = pack.key,
                            label = pack.name,
                            sublabel = "${pack.filterCount} badges · imported",
                            isSelected = pack.key == selected,
                            onSelect = { selected = pack.key },
                            onDelete = {
                                BadgePacks.deleteCustomPack(context, pack.key)
                                customPacks = BadgePacks.listCustomPacks(context)
                            }
                        )
                    }
                    item(key = "import-file") {
                        OutlinedButton(
                            onClick = { pickJson.launch("application/json") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(50)
                        ) {
                            Icon(
                                Icons.Filled.Upload,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Upload badge JSON file", color = TextPrimary)
                        }
                    }
                    item(key = "import-url") {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = urlInput,
                                onValueChange = { urlInput = it },
                                label = { Text("Paste badge JSON link") },
                                placeholder = { Text("https://example.com/badges.json") },
                                singleLine = true,
                                enabled = !isDownloading,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(50),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = accent,
                                    focusedLabelColor = accent
                                )
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    val url = urlInput.trim()
                                    if (url.isBlank()) {
                                        importError = "Paste a link first."
                                        return@Button
                                    }
                                    isDownloading = true
                                    importError = null
                                    scope.launch {
                                        val err = BadgePacks.importCustomPackFromUrl(context, url)
                                        isDownloading = false
                                        if (err != null) {
                                            importError = err
                                        } else {
                                            importError = null
                                            urlInput = ""
                                            customPacks = BadgePacks.listCustomPacks(context)
                                        }
                                    }
                                },
                                enabled = !isDownloading,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(50)
                            ) {
                                if (isDownloading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        Icons.Filled.Link,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(if (isDownloading) "Downloading..." else "Import from link")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(selected) }) {
                Text("Done", color = LocalPalette.current.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@Composable
private fun BadgePackRow(
    packKey: String,
    label: String,
    sublabel: String?,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val accent = LocalPalette.current.accent
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                if (isSelected) 1.5.dp else 1.dp,
                if (isSelected) accent else GlassBorder,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onSelect)
            .tvFocusRing(RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = isSelected,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(selectedColor = accent)
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    color = TextPrimary,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
                if (sublabel != null) {
                    Text(
                        sublabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Remove badge pack",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        // Sample badges from the pack — non-interactive.
        BadgePackPreview(packKey = packKey, label = "", modifier = Modifier.fillMaxWidth())
    }
}
