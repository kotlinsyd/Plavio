package com.pulsestream.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.data.simkl.SimklApi
import com.pulsestream.app.data.simkl.SimklManager
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AccountScreen(
    repository: StreamRepository,
    onBack: () -> Unit = {}
) {
    val settings = repository.settings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val simkl = remember { SimklManager(settings) }
    // Bumped on Simkl login/logout to recompose the account row.
    var simklTick by remember { mutableIntStateOf(0) }
    var showPinDialog by remember { mutableStateOf(false) }
    var deviceData by remember { mutableStateOf<SimklApi.DeviceData?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    fun startPinLogin() {
        pinError = null
        deviceData = null
        showPinDialog = true
        scope.launch {
            val device = withContext(Dispatchers.IO) { simkl.requestDeviceCode() }
            if (device == null) {
                pinError = "Couldn't reach Simkl. Check your connection and try again."
            } else {
                deviceData = device
            }
        }
    }

    // Poll for device approval while the dialog is open.
    if (showPinDialog && deviceData != null) {
        LaunchedEffect(deviceData) {
            val device = deviceData ?: return@LaunchedEffect
            var intervalMs = device.intervalSec * 1000L
            val deadline = SystemClock.elapsedRealtime() + device.expiresInSec * 1000L
            while (SystemClock.elapsedRealtime() < deadline) {
                delay(intervalMs)
                when (val result = withContext(Dispatchers.IO) { simkl.pollDeviceToken(device.deviceCode) }) {
                    is SimklApi.PollResult.Approved -> {
                        val name = withContext(Dispatchers.IO) { simkl.fetchUsername(result.accessToken) }
                        simkl.saveLogin(result.accessToken, name)
                        simklTick++
                        showPinDialog = false
                        deviceData = null
                        break
                    }
                    SimklApi.PollResult.SlowDown -> intervalMs += 5000L
                    SimklApi.PollResult.Expired -> {
                        pinError = "That code expired. Please try again."
                        deviceData = null
                        break
                    }
                    SimklApi.PollResult.Pending, SimklApi.PollResult.Failed -> {
                        // Keep polling; transient failures ride out the deadline.
                    }
                }
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
                        "Sync your watched history and progress.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 56.dp, end = 8.dp)
                    )
                }
            }
            item(key = "trakt") {
                SettingsStaticRow(
                    icon = Icons.Filled.Sync,
                    title = "Trakt",
                    subtitle = "Sync watched history and progress",
                    trailing = { ComingSoonChip() },
                    dimmed = true
                )
            }
            item(key = "simkl") {
                val linked = remember(simklTick) { simkl.isLoggedIn }
                val linkedName = remember(simklTick) { simkl.username }
                SettingsStaticRow(
                    icon = Icons.Filled.CloudQueue,
                    title = "Simkl",
                    subtitle = if (linked) "Linked as ${linkedName ?: "Simkl user"}"
                               else "Sync watched history and progress",
                    trailing = {
                        if (linked) {
                            Text(
                                "Linked",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = LocalPalette.current.accent
                            )
                        } else {
                            Icon(
                                Icons.Filled.ChevronRight,
                                contentDescription = "Link Simkl",
                                tint = TextSecondary
                            )
                        }
                    },
                    onClick = { if (linked) showLogoutDialog = true else startPinLogin() }
                )
            }
        }
    }

    if (showPinDialog) {
        SimklPinDialog(
            deviceData = deviceData,
            error = pinError,
            onOpenSite = {
                val uri = deviceData?.verificationUriComplete
                    ?: deviceData?.verificationUri
                    ?: "https://simkl.com/pin"
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                )
            },
            onDismiss = {
                showPinDialog = false
                deviceData = null
                pinError = null
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Unlink Simkl?", color = TextPrimary) },
            text = {
                Text(
                    "Pulse will stop syncing your watched history to Simkl.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    simkl.logout()
                    simklTick++
                    showLogoutDialog = false
                }) { Text("Unlink", color = LocalPalette.current.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = Color(0xFF14141F)
        )
    }
}
