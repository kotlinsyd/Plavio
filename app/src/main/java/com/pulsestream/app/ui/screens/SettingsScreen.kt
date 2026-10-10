package com.pulsestream.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulsestream.app.BuildConfig
import com.pulsestream.app.R
import com.pulsestream.app.data.InstallCounter
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.data.simkl.SimklApi
import com.pulsestream.app.data.update.UpdateChecker
import com.pulsestream.app.data.update.UpdateInfo
import com.pulsestream.app.ui.components.GlassCard
import com.pulsestream.app.ui.components.UpdatePrompt
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.AccentTheme
import com.pulsestream.app.ui.theme.GlassBorder
import com.pulsestream.app.ui.theme.GlassWhite
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    repository: StreamRepository,
    onThemeChanged: () -> Unit = {},
    onGoToAddons: () -> Unit = {},
    onGoToHomeCatalogs: () -> Unit = {},
    onGoToPlayback: () -> Unit = {},
    onGoToAppearance: () -> Unit = {},
    onGoToAbout: () -> Unit = {},
    onGoToAccount: () -> Unit = {}
) {
    GlowBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 40.dp)
        ) {
            item {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
                )
            }

            item {
                SettingsNavRow(
                    icon = Icons.Filled.Person,
                    title = "Account",
                    subtitle = "Trakt and Simkl sync",
                    onClick = onGoToAccount
                )
            }

            item {
                val installedAddons by repository.addons.collectAsState(initial = emptyList())
                val enabledAddons = remember(installedAddons) { installedAddons.count { it.enabled } }
                SettingsStaticRow(
                    icon = Icons.Filled.Extension,
                    title = "Manage addons",
                    subtitle = "${installedAddons.size} installed • $enabledAddons enabled",
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
                    icon = Icons.Filled.PlayArrow,
                    title = "Playback",
                    subtitle = "Autoplay, keep screen on, skip button",
                    onClick = onGoToPlayback
                )
            }

            item {
                SettingsNavRow(
                    icon = Icons.Filled.Contrast,
                    title = "Appearance",
                    subtitle = "Accent color and AMOLED black",
                    onClick = onGoToAppearance
                )
            }

            item {
                SettingsNavRow(
                    icon = Icons.Filled.GridView,
                    title = "Home catalogs",
                    subtitle = "Choose and arrange catalogs on the Home screen",
                    onClick = onGoToHomeCatalogs
                )
            }

            item {
                SettingsNavRow(
                    icon = Icons.Filled.Info,
                    title = "About",
                    subtitle = "Version, website and app updates",
                    onClick = onGoToAbout
                )
            }

            item(key = "credit") {
                val context = LocalContext.current
                var installCount by remember { mutableStateOf<Int?>(null) }
                LaunchedEffect(Unit) {
                    installCount = InstallCounter.getInstallCount(context)
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Made with ❤️ by Najam",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    if (installCount != null) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Filled.Download,
                                contentDescription = null,
                                tint = LocalPalette.current.accent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            val n = installCount!!
                            Text(
                                text = "${"%,d".format(n)} ${if (n == 1) "install" else "installs"}",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = TextSecondary
                            )
                        }
                    }
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
            .clip(RoundedCornerShape(14.dp))
            .background(GlassWhite)
            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp)),
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

@Composable
fun ComingSoonChip() {
    Box(
        modifier = Modifier
            .border(1.dp, LocalPalette.current.accent.copy(alpha = 0.5f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            "Coming soon",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = LocalPalette.current.accent
        )
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
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://plaviomedia.vercel.app"))
                        )
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
                        val info = UpdateChecker.checkNow(appContext)
                        checking = false
                        if (info != null) {
                            updateInfo = info
                        } else {
                            status = "No updates found — you're on the latest version."
                        }
                    }
                },
                enabled = !checking,
                modifier = Modifier.fillMaxWidth(),
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
