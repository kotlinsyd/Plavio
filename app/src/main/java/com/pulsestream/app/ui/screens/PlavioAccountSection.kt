package com.pulsestream.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.data.cloud.PlavioAccountManager
import com.pulsestream.app.ui.components.GlassCard
import com.pulsestream.app.ui.components.OrbitDots
import com.pulsestream.app.ui.components.ProfileAvatarImage
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Plavio account section (owner's order + approved samples 2026-10-25):
 * email + password sign-in; the email code is only for password resets.
 * Syncs the same full backup as Google Drive (which stays as a second
 * door below). Renders nothing until the backend is configured.
 */
@Composable
fun PlavioAccountSection(
    repository: StreamRepository,
    onChangePicture: () -> Unit = {},
    googleExpanded: Boolean = false,
    onToggleGoogle: () -> Unit = {},
    onAccountChanged: () -> Unit = {}
) {
    if (!PlavioAccountManager.isConfigured()) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { PlavioAccountManager(context, repository) }
    val palette = LocalPalette.current

    var mode by remember {
        mutableStateOf(if (manager.isSignedIn()) Mode.SignedIn else Mode.SignIn)
    }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var resetCode by remember { mutableStateOf("") }
    var resendIn by remember { mutableStateOf(0) }
    var refreshTick by remember { mutableStateOf(0) }
    val signedEmail = remember(refreshTick) { manager.accountEmail() }.orEmpty()
    val lastSync = remember(refreshTick) { manager.lastSyncTime() }

    LaunchedEffect(resendIn) {
        if (resendIn > 0) {
            delay(1000)
            resendIn--
        }
    }

    fun fail(e: Throwable) {
        busy = false
        error = e.message ?: "Something went wrong. Please try again."
    }

    fun afterAuth() {
        scope.launch {
            manager.syncAfterSignIn()
                .onSuccess { restored ->
                    busy = false
                    notice = null
                    mode = if (restored) Mode.WelcomeBack else Mode.SignedIn
                    refreshTick++
                    onAccountChanged()
                }
                .onFailure { fail(it) }
        }
    }

    if (mode == Mode.SignedIn) {
        PlavioSignedInC(
            repository = repository,
            busy = busy,
            signedEmail = signedEmail,
            lastSync = lastSync,
            notice = notice,
            error = error,
            onSyncNow = {
                error = null
                busy = true
                scope.launch {
                    manager.syncNow()
                        .onSuccess {
                            busy = false
                            notice = "Synced just now."
                            refreshTick++
                        }
                        .onFailure { fail(it) }
                }
            },
            onChangePassword = { newPassword, done ->
                error = null
                scope.launch {
                    manager.changePassword(newPassword)
                        .onSuccess { done(null) }
                        .onFailure { done(it.message) }
                }
            },
            onSignOut = {
                scope.launch {
                    manager.signOut()
                    mode = Mode.SignIn
                    notice = null
                    refreshTick++
                    onAccountChanged()
                }
            },
            onChangePicture = onChangePicture,
            googleExpanded = googleExpanded,
            onToggleGoogle = onToggleGoogle
        )
        return
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (mode) {
                Mode.SignIn, Mode.Create -> {
                    Text(
                        if (mode == Mode.SignIn) "Welcome to Plavio" else "Create your account",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        if (mode == Mode.SignIn) {
                            "Sign in to sync your library, progress and addons on every device."
                        } else {
                            "We'll never share your email."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it.trim() },
                        label = { Text("Email") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (mode == Mode.Create) {
                        OutlinedTextField(
                            value = confirm,
                            onValueChange = { confirm = it },
                            label = { Text("Confirm password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth()
                        )
                        PasswordRules(password)
                    }
                    if (mode == Mode.SignIn) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            TextButton(
                                onClick = {
                                    error = null
                                    mode = Mode.ResetRequest
                                },
                                enabled = !busy
                            ) { Text("Forgot password?") }
                        }
                    }
                    Button(
                        onClick = {
                            error = null
                            notice = null
                            if (mode == Mode.SignIn) {
                                busy = true
                                scope.launch {
                                    manager.signIn(email, password)
                                        .onSuccess { afterAuth() }
                                        .onFailure { fail(it) }
                                }
                            } else {
                                when {
                                    password.length < 8 -> error = "Password needs at least 8 characters."
                                    password != confirm -> error = "Passwords don't match."
                                    else -> {
                                        busy = true
                                        scope.launch {
                                            manager.signUp(email, password)
                                                .onSuccess { sessionNow ->
                                                    if (sessionNow) {
                                                        afterAuth()
                                                    } else {
                                                        busy = false
                                                        mode = Mode.SignIn
                                                        notice = "Account created — check your email to confirm it, then sign in."
                                                    }
                                                }
                                                .onFailure { fail(it) }
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !busy && email.contains("@") && password.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (busy) {
                            OrbitDots(modifier = Modifier.size(20.dp))
                        } else {
                            Text(if (mode == Mode.SignIn) "Sign in" else "Create account")
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (mode == Mode.SignIn) "New here?" else "Already have an account?",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        TextButton(
                            onClick = {
                                error = null
                                notice = null
                                mode = if (mode == Mode.SignIn) Mode.Create else Mode.SignIn
                            },
                            enabled = !busy
                        ) {
                            Text(if (mode == Mode.SignIn) "Create account" else "Sign in")
                        }
                    }
                    Text(
                        "Free forever. Your data stays yours.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                Mode.ResetRequest -> {
                    Text(
                        "Reset password",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        "Enter your email and we'll send you a 6-digit code.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it.trim() },
                        label = { Text("Email") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            error = null
                            busy = true
                            scope.launch {
                                manager.requestPasswordReset(email)
                                    .onSuccess {
                                        busy = false
                                        resendIn = 60
                                        mode = Mode.ResetVerify
                                    }
                                    .onFailure { fail(it) }
                            }
                        },
                        enabled = !busy && email.contains("@"),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (busy) "Sending…" else "Send code") }
                    TextButton(
                        onClick = { mode = Mode.SignIn },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Back to sign in") }
                }

                Mode.ResetVerify -> {
                    Text(
                        "Reset password",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        "We emailed a 6-digit code to $email",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = resetCode,
                        onValueChange = { resetCode = it.filter { c -> c.isDigit() }.take(6) },
                        label = { Text("6-digit code") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("New password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            error = null
                            busy = true
                            scope.launch {
                                manager.verifyResetAndSetPassword(email, resetCode, password)
                                    .onSuccess {
                                        busy = false
                                        password = ""
                                        resetCode = ""
                                        mode = Mode.SignIn
                                        notice = "Password updated — sign in with your new password."
                                    }
                                    .onFailure { fail(it) }
                            }
                        },
                        enabled = !busy && resetCode.length == 6 && password.length >= 8,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (busy) "Saving…" else "Set new password") }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { mode = Mode.SignIn },
                            enabled = !busy
                        ) { Text("Back to sign in") }
                        TextButton(
                            onClick = {
                                error = null
                                scope.launch {
                                    manager.requestPasswordReset(email)
                                        .onSuccess { resendIn = 60 }
                                        .onFailure { error = it.message }
                                }
                            },
                            enabled = !busy && resendIn == 0
                        ) {
                            Text(if (resendIn > 0) "Resend code (0:${"%02d".format(resendIn)})" else "Resend code")
                        }
                    }
                    Text(
                        "Didn't get it? Check your spam folder.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                Mode.SignedIn -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(palette.gradient),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                signedEmail.firstOrNull()?.uppercaseChar()?.toString() ?: "P",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Plavio account",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                signedEmail,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                    Text(
                        if (lastSync != null) "Synced · $lastSync" else "Not synced yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Button(
                        onClick = {
                            error = null
                            busy = true
                            scope.launch {
                                manager.syncNow()
                                    .onSuccess {
                                        busy = false
                                        notice = "Synced just now."
                                        refreshTick++
                                    }
                                    .onFailure { fail(it) }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (busy) "Syncing…" else "Sync now")
                    }
                    Text(
                        "What syncs: Library, progress, addons, settings & keys",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    ChangePasswordBlock(
                        busy = busy,
                        onChange = { newPassword, done ->
                            error = null
                            scope.launch {
                                manager.changePassword(newPassword)
                                    .onSuccess { done(null) }
                                    .onFailure { done(it.message) }
                            }
                        }
                    )
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                manager.signOut()
                                mode = Mode.SignIn
                                notice = null
                                refreshTick++
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Sign out")
                    }
                    Text(
                        "Profiles are coming later — they'll live under this account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                Mode.WelcomeBack -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = palette.accent,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Welcome back",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Text(
                        "Everything restored from your account:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    listOf(
                        "Library",
                        "Continue Watching progress",
                        "Addons with your settings",
                        "App settings & API keys"
                    ).forEach { line ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = palette.accent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(line, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        }
                    }
                    Button(
                        onClick = { mode = Mode.SignedIn },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Start watching") }
                }
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            notice?.let {
                Text(it, color = palette.accent, style = MaterialTheme.typography.bodySmall)
            }
            if (mode == Mode.SignIn || mode == Mode.Create) {
                Spacer(Modifier.height(2.dp))
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

private enum class Mode { SignIn, Create, ResetRequest, ResetVerify, SignedIn, WelcomeBack }

@Composable
private fun PasswordRules(password: String) {
    val rules = listOf(
        "8+ characters" to (password.length >= 8),
        "A number" to password.any { it.isDigit() },
        "A symbol" to password.any { !it.isLetterOrDigit() }
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rules.forEach { (label, ok) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = if (ok) LocalPalette.current.accent else TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ok) TextPrimary else TextSecondary
                )
            }
        }
    }
}

@Composable
private fun ChangePasswordBlock(
    busy: Boolean,
    onChange: (String, (String?) -> Unit) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    var newPassword by remember { mutableStateOf("") }
    var localBusy by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    var doneNote by remember { mutableStateOf(false) }
    if (!open) {
        TextButton(
            onClick = { open = true },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Change password") }
        if (doneNote) {
            Text(
                "Password changed.",
                style = MaterialTheme.typography.bodySmall,
                color = LocalPalette.current.accent
            )
        }
        return
    }
    OutlinedTextField(
        value = newPassword,
        onValueChange = { newPassword = it },
        label = { Text("New password") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth()
    )
    Button(
        onClick = {
            localBusy = true
            localError = null
            onChange(newPassword) { err ->
                localBusy = false
                if (err == null) {
                    open = false
                    newPassword = ""
                    doneNote = true
                } else {
                    localError = err
                }
            }
        },
        enabled = !localBusy && newPassword.length >= 8,
        modifier = Modifier.fillMaxWidth()
    ) { Text(if (localBusy) "Saving…" else "Update password") }
    localError?.let {
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Signed-in account area — Sample C "Clean List" (owner's pick
 * 2026-10-25), drawn in the Onyx language: centered identity with a
 * synced pill, then labeled groups of icon rows (the shared settings
 * group structure). The Google door stays below in AccountScreen,
 * collapsed behind the "Sync with Google" row while signed in here.
 */
@Composable
private fun PlavioSignedInC(
    repository: StreamRepository,
    busy: Boolean,
    signedEmail: String,
    lastSync: String?,
    notice: String?,
    error: String?,
    onSyncNow: () -> Unit,
    onChangePassword: (String, (String?) -> Unit) -> Unit,
    onSignOut: () -> Unit,
    onChangePicture: () -> Unit,
    googleExpanded: Boolean,
    onToggleGoogle: () -> Unit
) {
    val palette = LocalPalette.current
    val settings = repository.settings
    val profileName = settings.profileUserName
    var changeOpen by remember { mutableStateOf(false) }
    var newPassword by remember { mutableStateOf("") }
    var pwBusy by remember { mutableStateOf(false) }
    var pwError by remember { mutableStateOf<String?>(null) }
    var pwDone by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // ---- Centered identity ----
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ProfileAvatarImage(
                avatarSetting = settings.profileAvatar,
                photoFile = settings.profilePhotoFile(),
                modifier = Modifier
                    .size(76.dp)
                    .clickable { onChangePicture() },
                placeholderLetter = (profileName.ifBlank { signedEmail })
                    .firstOrNull()?.uppercase()
            )
            Spacer(Modifier.height(10.dp))
            Text(
                profileName.ifBlank { "Plavio account" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                signedEmail,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(Modifier.height(9.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(lerp(palette.panel, Color.White, 0.10f))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(palette.accent)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (lastSync != null) "Synced \u00b7 $lastSync" else "Not synced yet",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
            }
        }

        notice?.let {
            Text(
                it,
                color = palette.accent,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        // ---- Plavio account group ----
        SettingsSectionHeader("Plavio account")
        SettingsGroup(
            {
                SettingsNavRow(
                    icon = Icons.Filled.CloudSync,
                    title = if (busy) "Syncing\u2026" else "Sync now",
                    subtitle = if (lastSync != null) "Last synced $lastSync" else "Upload your latest backup",
                    onClick = onSyncNow
                )
            },
            {
                SettingsStaticRow(
                    icon = Icons.Filled.CloudDone,
                    title = "What syncs",
                    subtitle = "Library, progress, addons, settings & keys",
                    trailing = {}
                )
            },
            {
                SettingsNavRow(
                    icon = Icons.Filled.Lock,
                    title = "Change password",
                    subtitle = if (pwDone) "Password changed" else "Keep your account secure",
                    onClick = { changeOpen = !changeOpen }
                )
            }
        )

        if (changeOpen) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 5.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            pwBusy = true
                            pwError = null
                            onChangePassword(newPassword) { err ->
                                pwBusy = false
                                if (err == null) {
                                    changeOpen = false
                                    newPassword = ""
                                    pwDone = true
                                } else {
                                    pwError = err
                                }
                            }
                        },
                        enabled = !pwBusy && newPassword.length >= 8,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (pwBusy) "Saving\u2026" else "Update password") }
                    pwError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // ---- More group ----
        SettingsSectionHeader("More")
        SettingsGroup(
            {
                SettingsStaticRow(
                    icon = Icons.Filled.Group,
                    title = "Profiles",
                    subtitle = "Coming later \u2014 they'll live under this account",
                    trailing = {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(lerp(palette.panel, Color.White, 0.12f))
                                .padding(horizontal = 9.dp, vertical = 3.dp)
                        ) {
                            Text(
                                "SOON",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary
                            )
                        }
                    }
                )
            },
            {
                SettingsNavRow(
                    icon = Icons.Filled.PhotoLibrary,
                    title = "Profile picture",
                    subtitle = "Photo or a preset avatar",
                    onClick = onChangePicture
                )
            },
            {
                SettingsNavRow(
                    icon = Icons.Filled.CloudDone,
                    title = "Sync with Google",
                    subtitle = if (googleExpanded) "Google panel is open below" else "Second door \u2014 same backup in your Drive",
                    onClick = onToggleGoogle
                )
            },
            {
                // Sign out — bespoke red row (same grouped geometry).
                Box(Modifier.fillMaxWidth().clickable { onSignOut() }) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color(0xFFF43F5E).copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Logout,
                                contentDescription = null,
                                tint = Color(0xFFFB7185),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(
                            "Sign out",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFFB7185)
                        )
                    }
                }
            }
        )
    }
}
