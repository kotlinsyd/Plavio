package com.pulsestream.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
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
import com.pulsestream.app.data.SettingsStore
import com.pulsestream.app.data.simkl.SimklApi
import com.pulsestream.app.data.simkl.SimklManager
import com.pulsestream.app.data.trakt.TraktApi
import com.pulsestream.app.data.trakt.TraktManager
import com.pulsestream.app.ui.screens.SettingsStaticRow
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Trakt + Simkl link cards. Moved here from AccountScreen (v0.11.2+)
 * so Account can host the Plavio cloud login instead.
 */
@Composable
fun TraktSimklSection(settings: SettingsStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val simkl = remember { SimklManager(settings) }
    var simklTick by remember { mutableIntStateOf(0) }
    var showPinDialog by remember { mutableStateOf(false) }
    var deviceData by remember { mutableStateOf<SimklApi.DeviceData?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    val trakt = remember { TraktManager(settings) }
    var traktTick by remember { mutableIntStateOf(0) }
    var showTraktDialog by remember { mutableStateOf(false) }
    var traktPkce by remember { mutableStateOf<TraktApi.Pkce?>(null) }
    var traktCode by remember { mutableStateOf("") }
    var traktError by remember { mutableStateOf<String?>(null) }
    var traktBusy by remember { mutableStateOf(false) }
    var showTraktLogoutDialog by remember { mutableStateOf(false) }

    fun startTraktLogin() {
        traktError = null
        traktCode = ""
        traktBusy = false
        val pkce = trakt.newPkce()
        traktPkce = pkce
        showTraktDialog = true
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(trakt.authorizeUrl(pkce)))
        )
    }

    fun submitTraktCode() {
        val pkce = traktPkce ?: return
        val code = traktCode.trim()
        if (code.isEmpty()) {
            traktError = "Paste the code shown on the Trakt page."
            return
        }
        traktBusy = true
        traktError = null
        scope.launch {
            val ok = withContext(Dispatchers.IO) { trakt.loginWithCode(code, pkce.verifier) }
            traktBusy = false
            if (ok) {
                traktTick++
                showTraktDialog = false
                traktPkce = null
                traktCode = ""
            } else {
                traktError = "That code didn't work. Try linking again."
            }
        }
    }

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
                    }
                }
            }
        }
    }

    // Trakt row
    run {
        val linked = remember(traktTick) { trakt.isLoggedIn }
        val linkedName = remember(traktTick) { trakt.username }
        SettingsStaticRow(
            icon = Icons.Filled.Sync,
            title = "Trakt",
            subtitle = if (linked) "Linked as ${linkedName ?: "Trakt user"}"
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
                        contentDescription = "Link Trakt",
                        tint = TextSecondary
                    )
                }
            },
            onClick = { if (linked) showTraktLogoutDialog = true else startTraktLogin() }
        )
    }

    // Simkl row
    run {
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
                    "Plavio will stop syncing your watched history to Simkl.",
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

    if (showTraktDialog) {
        TraktCodeDialog(
            code = traktCode,
            onCodeChange = { traktCode = it },
            error = traktError,
            busy = traktBusy,
            onReopen = { traktPkce?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(trakt.authorizeUrl(it)))) } },
            onSubmit = { submitTraktCode() },
            onDismiss = {
                showTraktDialog = false
                traktPkce = null
                traktCode = ""
                traktError = null
            }
        )
    }

    if (showTraktLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showTraktLogoutDialog = false },
            title = { Text("Unlink Trakt?", color = TextPrimary) },
            text = {
                Text(
                    "Plavio will stop syncing your watched history to Trakt.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    trakt.logout()
                    traktTick++
                    showTraktLogoutDialog = false
                }) { Text("Unlink", color = LocalPalette.current.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showTraktLogoutDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = Color(0xFF14141F)
        )
    }
}

/** Dialog for pasting the Trakt authorization code shown on the callback page. */
@Composable
fun TraktCodeDialog(
    code: String,
    onCodeChange: (String) -> Unit,
    error: String?,
    busy: Boolean,
    onReopen: () -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link Trakt", color = TextPrimary) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Approve Plavio in the browser, then paste the code shown on the page below.",
                    color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = onCodeChange,
                    label = { Text("Trakt code") },
                    singleLine = true,
                    enabled = !busy,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = LocalPalette.current.accent,
                        unfocusedLabelColor = TextSecondary,
                        focusedIndicatorColor = LocalPalette.current.accent,
                        unfocusedIndicatorColor = TextSecondary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = Color(0xFFF87171), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                if (busy) {
                    Spacer(Modifier.height(12.dp))
                    Text("Linking…", color = TextSecondary)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSubmit, enabled = !busy) {
                Text("Link", color = LocalPalette.current.accent)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReopen) {
                    Text("Reopen page", color = TextSecondary)
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        },
        containerColor = Color(0xFF14141F)
    )
}
