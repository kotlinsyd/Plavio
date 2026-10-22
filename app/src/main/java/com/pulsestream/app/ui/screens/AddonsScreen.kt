package com.pulsestream.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.pulsestream.app.data.AddonShare
import com.pulsestream.app.data.AddonRefreshOutcome
import com.pulsestream.app.data.SharedAddon
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.data.StremioApi
import com.pulsestream.app.data.db.InstalledAddon
import com.pulsestream.app.ui.components.EmptyState
import com.pulsestream.app.ui.components.GlassCard
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.components.DotsLoader
import com.pulsestream.app.ui.tv.TvFocusGreen
import com.pulsestream.app.ui.tv.tvFocusRing
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.GlassBorder
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

sealed interface AddUiState {
    data object Idle : AddUiState
    data object Adding : AddUiState
    data class Error(val message: String) : AddUiState
}

/** One-shot result of a manual addon refresh, shown under the addon row. */
data class RefreshNote(val message: String, val isError: Boolean)

class AddonsViewModel(private val repo: StreamRepository) : ViewModel() {
    var addState by mutableStateOf<AddUiState>(AddUiState.Idle)
        private set

    /** Addon ids currently being refreshed (spinner on their row). */
    var refreshingIds by mutableStateOf(setOf<String>())
        private set

    /** Latest refresh result per addon id, shown under the row. */
    var refreshNotes by mutableStateOf(mapOf<String, RefreshNote>())
        private set

    fun addAddon(url: String, onDone: () -> Unit) {
        viewModelScope.launch {
            addState = AddUiState.Adding
            val result = repo.addAddonFromUrl(url)
            result.onSuccess {
                addState = AddUiState.Idle
                onDone()
            }.onFailure {
                addState = AddUiState.Error(it.message ?: "Couldn't add this addon")
            }
        }
    }

    fun reset() { addState = AddUiState.Idle }

    /** Import a shared addon bundle: installs every addon in it, one by one. */
    fun importBundle(
        code: String,
        onDone: (installed: Int, failed: Int, alreadyInstalled: Int) -> Unit
    ) {
        viewModelScope.launch {
            val shared = AddonShare.decode(code)
            if (shared.isNullOrEmpty()) {
                addState = AddUiState.Error("That doesn't look like a Plavio addon bundle.")
                onDone(0, 0, 0)
                return@launch
            }
            addState = AddUiState.Adding
            // Dedup by normalized manifest URL: re-importing the same bundle
            // skips what's already installed instead of "installing" it
            // again (addAddonFromUrl upserts and would bump sortOrder).
            val known = repo.addons.first()
                .mapTo(mutableSetOf()) { StremioApi.normalizeBaseUrl(it.baseUrl) }
            var ok = 0
            var failed = 0
            var skipped = 0
            for (s in shared) {
                val norm = runCatching { StremioApi.normalizeBaseUrl(s.url) }.getOrNull()
                if (norm != null && norm in known) {
                    skipped++
                    continue
                }
                val r = repo.addAddonFromUrl(s.url)
                if (r.isSuccess) {
                    ok++
                    if (norm != null) known.add(norm)
                } else {
                    failed++
                }
            }
            addState = AddUiState.Idle
            onDone(ok, failed, skipped)
        }
    }

    fun remove(addon: InstalledAddon) {
        viewModelScope.launch { repo.removeAddon(addon.id) }
    }

    /**
     * Re-fetch the addon's manifest.json and update its stored metadata.
     * The stored record is only touched on success — a failed refresh
     * keeps the old data and reports the reason under the row.
     */
    fun refreshAddon(addon: InstalledAddon) {
        if (addon.id in refreshingIds) return
        viewModelScope.launch {
            refreshingIds = refreshingIds + addon.id
            refreshNotes = refreshNotes - addon.id
            val result = repo.refreshAddon(addon.id)
            refreshingIds = refreshingIds - addon.id
            val note = result.fold(
                onSuccess = { outcome ->
                    when (outcome) {
                        is AddonRefreshOutcome.Updated -> RefreshNote(
                            "${addon.name} updated: v${outcome.oldVersion} → v${outcome.newVersion}",
                            isError = false
                        )
                        is AddonRefreshOutcome.UpToDate -> RefreshNote(
                            if (outcome.version.isNotBlank())
                                "${addon.name} is already up to date (v${outcome.version})"
                            else "${addon.name} is already up to date",
                            isError = false
                        )
                    }
                },
                onFailure = {
                    RefreshNote(
                        "Couldn't refresh ${addon.name}: ${it.message ?: "network error"}",
                        isError = true
                    )
                }
            )
            refreshNotes = refreshNotes + (addon.id to note)
        }
    }

    fun setEnabled(addon: InstalledAddon, enabled: Boolean) {
        viewModelScope.launch { repo.setAddonEnabled(addon.id, enabled) }
    }

    fun toggleStar(addon: InstalledAddon) {
        viewModelScope.launch { repo.setAddonStarred(addon.id, !addon.starred) }
    }

    fun moveUp(addon: InstalledAddon, siblings: List<InstalledAddon>) {
        val idx = siblings.indexOfFirst { it.id == addon.id }
        if (idx > 0) viewModelScope.launch {
            repo.swapAddonOrder(addon.id, siblings[idx - 1].id)
        }
    }

    fun moveDown(addon: InstalledAddon, siblings: List<InstalledAddon>) {
        val idx = siblings.indexOfFirst { it.id == addon.id }
        if (idx >= 0 && idx < siblings.size - 1) viewModelScope.launch {
            repo.swapAddonOrder(addon.id, siblings[idx + 1].id)
        }
    }
}

@Composable
fun AddonsScreen(
    repository: StreamRepository,
    onBack: () -> Unit = {},
    onGoToTvSetup: () -> Unit = {}
) {
    val vm: AddonsViewModel = viewModel(factory = vmFactory { AddonsViewModel(repository) })
    val addons by repository.addons.collectAsState(initial = emptyList())
    var showDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    // Pre-fill for the import dialog when a plavio-addons:// deep link opened
    // the app (MainActivity stashed the link on the repository).
    var importPrefill by remember { mutableStateOf<String?>(null) }
    // Pre-fill for the single-addon dialog when a stremio:// deep link opened
    // the app (MainActivity stashed the manifest URL on the repository).
    var deepLinkPrefill by remember { mutableStateOf<String?>(null) }
    val pendingBundle by repository.pendingAddonBundle.collectAsState()
    val pendingManifestUrl by repository.pendingManifestUrl.collectAsState()
    LaunchedEffect(pendingBundle) {
        if (pendingBundle != null) {
            repository.clearPendingAddonBundle()
            importPrefill = pendingBundle
            showImportDialog = true
        }
    }
    // stremio:// deep link: open the single-addon install dialog with the
    // manifest URL pre-filled (not the bundle import dialog).
    LaunchedEffect(pendingManifestUrl) {
        if (pendingManifestUrl != null) {
            repository.clearPendingManifestUrl()
            deepLinkPrefill = pendingManifestUrl
            vm.reset()
            showDialog = true
        }
    }
    // Bundle-share selection mode: tick addons, export as one shareable link.
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    val context = LocalContext.current
    // Single list in the user's manual order — toggling on/off never moves a row.
    val ordered = remember(addons) { addons.sortedBy { it.sortOrder } }
    val enabledCount = remember(addons) { addons.count { it.enabled } }
    val isTv = com.pulsestream.app.ui.tv.rememberIsTv()

    GlowBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "header") {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        if (selectionMode) {
                            IconButton(
                                onClick = {
                                    selectionMode = false
                                    selectedIds = emptySet()
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Cancel selection",
                                    tint = TextPrimary
                                )
                            }
                        } else {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = TextPrimary
                                )
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (selectionMode) "${selectedIds.size} selected" else "Addons",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        if (!selectionMode) {
                            // Import a shared bundle.
                            IconButton(
                                onClick = { vm.reset(); showImportDialog = true },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Download,
                                    contentDescription = "Import addon bundle",
                                    tint = TextSecondary
                                )
                            }
                            // Enter bundle-share selection mode.
                            IconButton(
                                onClick = { selectionMode = true },
                                modifier = Modifier.size(40.dp),
                                enabled = addons.isNotEmpty()
                            ) {
                                Icon(
                                    Icons.Filled.Share,
                                    contentDescription = "Share addons",
                                    tint = TextSecondary
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatPill("$enabledCount enabled", LocalPalette.current.accent)
                        StatPill("${addons.size} installed", TextSecondary)
                        val starred = addons.count { it.starred }
                        if (starred > 0) StatPill("$starred starred", Color(0xFFFBBF24))
                    }
                }
            }

            item(key = "overview") {
                Text(
                    "Overview",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )
            }

            item(key = "stats-card") {
                val totalCatalogs = remember(addons) {
                    // Estimate from addon types; actual catalog count comes from manifests
                    addons.sumOf { addon ->
                        try {
                            val types = org.json.JSONArray(addon.types ?: "[]")
                            types.length() * 3 // rough estimate
                        } catch (e: Exception) { 3 }
                    }
                }
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatColumn(
                            value = addons.size.toString(),
                            label = "Addons"
                        )
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(48.dp)
                                .background(GlassBorder)
                        )
                        StatColumn(
                            value = enabledCount.toString(),
                            label = "Active"
                        )
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(48.dp)
                                .background(GlassBorder)
                        )
                        StatColumn(
                            value = totalCatalogs.toString(),
                            label = "Catalogs"
                        )
                    }
                }
            }

            item(key = "add-addon-header") {
                Text(
                    "Add Addon",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
            }

            item(key = "install-card") {
                var urlInput by remember { mutableStateOf("") }
                var installing by remember { mutableStateOf(false) }
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            label = { Text("Addon URL") },
                            placeholder = { Text("Paste manifest URL...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = LocalPalette.current.accent,
                                focusedLabelColor = LocalPalette.current.accent,
                                unfocusedBorderColor = GlassBorder,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (urlInput.isNotBlank()) {
                                    installing = true
                                    vm.addAddon(urlInput.trim()) {
                                        installing = false
                                        urlInput = ""
                                    }
                                } else {
                                    vm.reset()
                                    showDialog = true
                                }
                            },
                            enabled = !installing,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LocalPalette.current.accent.copy(alpha = 0.2f),
                                contentColor = LocalPalette.current.accent,
                                disabledContainerColor = Color.White.copy(alpha = 0.06f),
                                disabledContentColor = TextSecondary
                            )
                        ) {
                            if (installing) {
                                CircularProgressIndicator(
                                    color = LocalPalette.current.accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Text(
                                    if (urlInput.isBlank()) "Browse to Install" else "Install Addon",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // TV only: push addons from the phone over home Wi-Fi instead of
            // typing long URLs with the remote.
            if (isTv) {
                item(key = "tv-setup-card") {
                    TvSetupCard(onClick = onGoToTvSetup)
                }
            }

            if (ordered.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = "No addons installed",
                        message = "Paste an addon manifest URL above to get started.",
                        actionLabel = "Browse addons",
                        onAction = { vm.reset(); showDialog = true }
                    )
                }
            } else {
                item(key = "installed-header") {
                    Text(
                        "Installed Addons",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                }
                items(ordered, key = { it.id }) { addon ->
                    val idx = ordered.indexOfFirst { it.id == addon.id }
                    AddonCard(
                        addon = addon,
                        selectionMode = selectionMode,
                        selected = addon.id in selectedIds,
                        onSelect = {
                            selectedIds = if (addon.id in selectedIds) {
                                selectedIds - addon.id
                            } else {
                                selectedIds + addon.id
                            }
                        },
                        onToggle = { vm.setEnabled(addon, it) },
                        onRemove = { vm.remove(addon) },
                        onStar = { vm.toggleStar(addon) },
                        onRefresh = { vm.refreshAddon(addon) },
                        refreshing = addon.id in vm.refreshingIds,
                        refreshNote = vm.refreshNotes[addon.id],
                        onUp = { vm.moveUp(addon, ordered) },
                        onDown = { vm.moveDown(addon, ordered) },
                        canUp = idx > 0,
                        canDown = idx < ordered.size - 1
                    )
                }
            }

            // Bundle-share action bar (selection mode only).
            if (selectionMode) {
                item(key = "share-bar") {
                    val chosen = ordered.filter { it.id in selectedIds }
                    Surface(
                        onClick = {
                            if (chosen.isNotEmpty()) {
                                val link = try {
                                    AddonShare.encode(chosen.map { SharedAddon(it.name, it.baseUrl) })
                                } catch (_: com.pulsestream.app.data.UnsafeAddonShareException) {
                                    android.widget.Toast.makeText(context,
                                        "Sharing blocked: an addon URL may contain a password, token or API key. Share its public install link instead.",
                                        android.widget.Toast.LENGTH_LONG).show()
                                    return@Surface
                                }
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "My Plavio addons (${
                                            chosen.joinToString(", ") { it.name }
                                        }):\n$link"
                                    )
                                }
                                context.startActivity(
                                    Intent.createChooser(intent, "Share addon bundle")
                                )
                            }
                        },
                        shape = RoundedCornerShape(50),
                        color = if (chosen.isNotEmpty()) LocalPalette.current.accent
                        else Color.White.copy(alpha = 0.06f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Filled.Share,
                                contentDescription = null,
                                tint = if (chosen.isNotEmpty()) Color(0xFF0A0A12)
                                else TextSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (chosen.isEmpty()) "Tick addons to share"
                                else "Share ${chosen.size} addon${if (chosen.size == 1) "" else "s"}",
                                fontWeight = FontWeight.Bold,
                                color = if (chosen.isNotEmpty()) Color(0xFF0A0A12)
                                else TextSecondary.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }

            item(key = "star-hint") {
                if (addons.any { it.starred }) {
                    Text(
                        "★ Starred addons are tried first for streams and autoplay.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }

    if (showDialog) {
        AddAddonDialog(
            state = vm.addState,
            initialUrl = deepLinkPrefill ?: "",
            onDismiss = { showDialog = false; deepLinkPrefill = null; vm.reset() },
            onConfirm = { url -> vm.addAddon(url) { showDialog = false; deepLinkPrefill = null } }
        )
    }

    if (showImportDialog) {
        ImportBundleDialog(
            state = vm.addState,
            initialCode = importPrefill ?: "",
            onDismiss = { showImportDialog = false; importPrefill = null; vm.reset() },
            onImport = { code, onResult -> vm.importBundle(code, onResult) }
        )
    }
}

@Composable
private fun StatPill(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/** Small stat column for the Overview card (value on top, label below). */
@Composable
private fun StatColumn(value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

/** Slim capsule install affordance at the top of the list. */
@Composable
private fun InstallAddonCard(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(LocalPalette.current.gradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    tint = Color(0xFF0A0A12),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Add addon",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    "Paste a manifest URL",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = TextSecondary
            )
        }
    }
}

/** TV only: open the QR phone-pairing setup instead of typing URLs. */
@Composable
private fun TvSetupCard(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = TvFocusGreen.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, TvFocusGreen.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusRing(RoundedCornerShape(50))
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(TvFocusGreen),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.PhoneAndroid,
                    contentDescription = null,
                    tint = Color(0xFF0A0A12),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Add from phone",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    "Scan a QR code, paste URLs on your phone",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = TextSecondary
            )
        }
    }
}

@Composable
private fun AddonCard(
    addon: InstalledAddon,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onSelect: () -> Unit = {},
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onStar: () -> Unit,
    onRefresh: () -> Unit,
    refreshing: Boolean = false,
    refreshNote: RefreshNote? = null,
    onUp: () -> Unit,
    onDown: () -> Unit,
    canUp: Boolean,
    canDown: Boolean
) {
    val palette = LocalPalette.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (addon.enabled) 1f else 0.55f)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectionMode) {
                    androidx.compose.material3.Checkbox(
                        checked = selected,
                        onCheckedChange = { onSelect() },
                        colors = androidx.compose.material3.CheckboxDefaults.colors(
                            checkedColor = palette.accent
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                }
                AddonLogo(addon, size = 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        addon.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (addon.version.isNotBlank()) {
                        Text(
                            "Version ${addon.version}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                androidx.compose.material3.Switch(
                    checked = addon.enabled,
                    onCheckedChange = onToggle,
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = palette.accent,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFF3A3A4A)
                    )
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (canUp) {
                    IconButton(onClick = onUp, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Filled.KeyboardArrowUp,
                            contentDescription = "Move up",
                            tint = TextSecondary
                        )
                    }
                }
                if (canDown) {
                    IconButton(onClick = onDown, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Move down",
                            tint = TextSecondary
                        )
                    }
                }
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(40.dp),
                    enabled = !refreshing
                ) {
                    if (refreshing) {
                        CircularProgressIndicator(
                            color = palette.accent,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Refresh",
                            tint = TextSecondary
                        )
                    }
                }
                IconButton(onClick = onStar, modifier = Modifier.size(40.dp)) {
                    Icon(
                        if (addon.starred) Icons.Filled.Star else Icons.Outlined.Star,
                        contentDescription = "Star",
                        tint = if (addon.starred) Color(0xFFFBBF24) else TextSecondary
                    )
                }
                if (addon.configurable) {
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    IconButton(
                        onClick = {
                            val base = addon.baseUrl.trimEnd('/')
                            runCatching {
                                ctx.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("$base/configure")
                                    )
                                )
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = "Configure", tint = TextSecondary)
                    }
                }
                IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xFFF87171)
                    )
                }
            }

            if (refreshNote != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    refreshNote.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (!refreshNote.isError) palette.accent else Color(0xFFF87171)
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetaPill(text = if (addon.enabled) "Active" else "Disabled")
                val resourceCount = try {
                    org.json.JSONArray(addon.resources ?: "[]").length()
                } catch (e: Exception) { 0 }
                if (resourceCount > 0) {
                    MetaPill(text = "$resourceCount resource${if (resourceCount == 1) "" else "s"}")
                }
                val catalogEstimate = try {
                    org.json.JSONArray(addon.types ?: "[]").length() * 3
                } catch (e: Exception) { 0 }
                if (catalogEstimate > 0) {
                    MetaPill(text = "$catalogEstimate catalogs")
                }
            }

            if (addon.description.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    addon.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val typesText = try {
                val types = org.json.JSONArray(addon.types ?: "[]")
                (0 until types.length()).joinToString(" / ") { types.getString(it) }
            } catch (e: Exception) { "" }
            if (typesText.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    typesText,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary.copy(alpha = 0.7f)
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete ${addon.name}?", color = TextPrimary) },
            text = {
                Text(
                    "This will remove the addon from Plavio.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemove()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete", color = Color(0xFFF87171))
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

/** Small pill tag for addon metadata. */
@Composable
private fun MetaPill(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/** Cute little on/off pill instead of a full switch. */
@Composable
private fun OnOffPill(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    val bg = if (enabled) LocalPalette.current.accent else Color(0xFF2A2A36)
    val fg = if (enabled) Color(0xFF0A0A12) else TextSecondary
    Surface(
        color = bg,
        shape = RoundedCornerShape(50),
        modifier = Modifier.clickable { onToggle(!enabled) }
    ) {
        Text(
            if (enabled) "ON" else "OFF",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = fg,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun TinyIconButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(32.dp)
    ) {
        Box(modifier = Modifier.alpha(if (enabled) 1f else 0.3f)) {
            content()
        }
    }
}

/** Addon logo from the manifest, falling back to a gradient initial tile. */
@Composable
private fun AddonLogo(addon: InstalledAddon, size: androidx.compose.ui.unit.Dp) {
    val initial = addon.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (addon.enabled) LocalPalette.current.gradient
                else androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(Color(0xFF3A3A48), Color(0xFF2A2A36))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            initial,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = if (addon.enabled) Color(0xFF0A0A12) else TextSecondary
        )
        if (addon.logoUrl.isNotBlank()) {
            AsyncImage(
                model = addon.logoUrl,
                contentDescription = "${addon.name} logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
            )
        }
    }
}

@Composable
private fun PreinstalledPill() {
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(50),
        border = androidx.compose.foundation.BorderStroke(1.dp, LocalPalette.current.accent2.copy(alpha = 0.6f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = LocalPalette.current.accent2,
                modifier = Modifier.size(11.dp)
            )
            Spacer(Modifier.width(3.dp))
            Text(
                "Pre-installed",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = LocalPalette.current.accent2
            )
        }
    }
}

@Composable
private fun AddAddonDialog(
    state: AddUiState,
    initialUrl: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LocalPalette.current.panel,
        title = { Text("Add addon", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Paste the addon manifest URL (it usually ends with /manifest.json).",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = { Text("https://…/manifest.json", color = TextSecondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LocalPalette.current.accent,
                        unfocusedBorderColor = GlassBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = LocalPalette.current.accent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (state is AddUiState.Adding) {
                    Spacer(Modifier.height(12.dp))
                    DotsLoader()
                }
                if (state is AddUiState.Error) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        state.message,
                        color = Color(0xFFFF6B6B),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(url) },
                enabled = state !is AddUiState.Adding && url.isNotBlank()
            ) {
                Text(
                    if (state is AddUiState.Adding) "Adding…" else "Add",
                    color = LocalPalette.current.accent,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}

/** Paste-a-bundle dialog: one shared link installs every addon in it. */
@Composable
private fun ImportBundleDialog(
    state: AddUiState,
    initialCode: String = "",
    onDismiss: () -> Unit,
    onImport: (String, (installed: Int, failed: Int, alreadyInstalled: Int) -> Unit) -> Unit
) {
    var code by remember(initialCode) { mutableStateOf(initialCode) }
    var result by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LocalPalette.current.panel,
        title = { Text("Import addon bundle", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (result != null) {
                    Text(result!!, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        "Paste a bundle link someone shared with you — every addon in it installs at once.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        placeholder = { Text("plavio-addons://…", color = TextSecondary) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LocalPalette.current.accent,
                            unfocusedBorderColor = GlassBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = LocalPalette.current.accent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (state is AddUiState.Adding) {
                        Spacer(Modifier.height(12.dp))
                        DotsLoader()
                    }
                    if (state is AddUiState.Error) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            state.message,
                            color = Color(0xFFFF6B6B),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (result == null) {
                TextButton(
                    onClick = {
                        onImport(code) { installed, failed, alreadyInstalled ->
                            val plural = { n: Int -> if (n == 1) "" else "s" }
                            result = when {
                                installed == 0 && failed == 0 && alreadyInstalled == 0 ->
                                    "That doesn't look like a Plavio addon bundle."
                                installed == 0 && failed == 0 ->
                                    "All $alreadyInstalled addon${plural(alreadyInstalled)} " +
                                        "already installed — nothing new."
                                failed == 0 ->
                                    "Installed $installed addon${plural(installed)} ✓" +
                                        (if (alreadyInstalled > 0)
                                            " ($alreadyInstalled already installed)"
                                        else "")
                                else -> "Installed $installed, $failed failed (offline or dead link)."
                            }
                        }
                    },
                    enabled = state !is AddUiState.Adding && code.isNotBlank()
                ) {
                    Text(
                        if (state is AddUiState.Adding) "Installing…" else "Install all",
                        color = LocalPalette.current.accent,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Done", color = LocalPalette.current.accent, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (result == null) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
            }
        }
    )
}
