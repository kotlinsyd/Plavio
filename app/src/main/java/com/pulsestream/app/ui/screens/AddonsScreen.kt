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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.data.db.InstalledAddon
import com.pulsestream.app.ui.components.EmptyState
import com.pulsestream.app.ui.components.GlassCard
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.components.DotsLoader
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.GlassBorder
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

sealed interface AddUiState {
    data object Idle : AddUiState
    data object Adding : AddUiState
    data class Error(val message: String) : AddUiState
}

class AddonsViewModel(private val repo: StreamRepository) : ViewModel() {
    var addState by mutableStateOf<AddUiState>(AddUiState.Idle)
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

    fun remove(addon: InstalledAddon) {
        viewModelScope.launch { repo.removeAddon(addon.id) }
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
fun AddonsScreen(repository: StreamRepository, onBack: () -> Unit = {}) {
    val vm: AddonsViewModel = viewModel(factory = vmFactory { AddonsViewModel(repository) })
    val addons by repository.addons.collectAsState(initial = emptyList())
    var showDialog by remember { mutableStateOf(false) }
    // Single list in the user's manual order — toggling on/off never moves a row.
    val ordered = remember(addons) { addons.sortedBy { it.sortOrder } }
    val enabledCount = remember(addons) { addons.count { it.enabled } }

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
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Addons",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
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

            item(key = "install-card") {
                InstallAddonCard(onClick = { vm.reset(); showDialog = true })
            }

            if (ordered.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = "No addons installed",
                        message = "Tap the card above and paste an addon manifest URL to get started.",
                        actionLabel = "Add your first addon",
                        onAction = { vm.reset(); showDialog = true }
                    )
                }
            } else {
                items(ordered, key = { it.id }) { addon ->
                    val idx = ordered.indexOfFirst { it.id == addon.id }
                    AddonRow(
                        addon = addon,
                        onToggle = { vm.setEnabled(addon, it) },
                        onRemove = { vm.remove(addon) },
                        onStar = { vm.toggleStar(addon) },
                        onUp = { vm.moveUp(addon, ordered) },
                        onDown = { vm.moveDown(addon, ordered) },
                        canUp = idx > 0,
                        canDown = idx < ordered.size - 1
                    )
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
            onDismiss = { showDialog = false; vm.reset() },
            onConfirm = { url -> vm.addAddon(url) { showDialog = false } }
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

/** Prominent install affordance at the top of the list. */
@Composable
private fun InstallAddonCard(onClick: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(LocalPalette.current.gradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    tint = Color(0xFF0A0A12),
                    modifier = Modifier.size(26.dp)
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
                    "Paste a manifest URL — it usually ends with /manifest.json",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Compact addon row: logo, name, star, cute on/off pill, reorder + actions. */
@Composable
private fun AddonRow(
    addon: InstalledAddon,
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onStar: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    canUp: Boolean,
    canDown: Boolean
) {
    val context = LocalContext.current
    GlassCard(
        modifier = Modifier.fillMaxWidth()
            .alpha(if (addon.enabled) 1f else 0.55f)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            AddonLogo(addon, size = 54.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                // Name + version.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        addon.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (addon.version.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "v${addon.version}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = LocalPalette.current.accent,
                            maxLines = 1
                        )
                    }
                    if (addon.preinstalled) {
                        Spacer(Modifier.width(6.dp))
                        PreinstalledBadge()
                    }
                }
                // Description, like a proper addon listing.
                if (addon.description.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        addon.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(4.dp))
                val caps = buildList {
                    if (addon.hasCatalog) add("Catalogs")
                    if (addon.hasStream) add("Streams")
                    if (addon.hasSubtitles) add("Subtitles")
                }
                if (caps.isNotEmpty()) {
                    Text(
                        caps.joinToString(" • "),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(8.dp))
                // Actions.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OnOffPill(enabled = addon.enabled, onToggle = onToggle)
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = onStar, modifier = Modifier.size(36.dp)) {
                        Icon(
                            if (addon.starred) Icons.Filled.Star else Icons.Outlined.Star,
                            contentDescription = if (addon.starred) "Unstar" else "Star for priority",
                            tint = if (addon.starred) Color(0xFFFBBF24) else TextSecondary.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    TinyIconButton(onClick = onUp, enabled = canUp) {
                        Icon(Icons.Filled.KeyboardArrowUp, "Move up", modifier = Modifier.size(18.dp))
                    }
                    TinyIconButton(onClick = onDown, enabled = canDown) {
                        Icon(Icons.Filled.KeyboardArrowDown, "Move down", modifier = Modifier.size(18.dp))
                    }
                    if (addon.configurable) {
                        TinyIconButton(onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("${addon.baseUrl}/configure")
                            )
                            context.startActivity(intent)
                        }) {
                            Icon(
                                Icons.Filled.Settings,
                                "Configure addon",
                                tint = LocalPalette.current.accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TinyIconButton(onClick = onRemove) {
                        Icon(
                            Icons.Filled.Delete,
                            "Remove",
                            tint = Color(0xFFFF6B6B),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
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
private fun PreinstalledBadge() {
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
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var url by remember { mutableStateOf("") }
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
