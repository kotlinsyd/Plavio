package com.pulsestream.app.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.data.db.InstalledAddon
import com.pulsestream.app.ui.components.EmptyState
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.components.GlassCard
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

class HomeCatalogsViewModel(private val repo: StreamRepository) : ViewModel() {
    var addons by mutableStateOf<List<InstalledAddon>>(emptyList())
        private set

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            addons = repo.catalogAddons().sortedBy { it.sortOrder }
        }
    }

    fun setShowOnHome(addon: InstalledAddon, show: Boolean) {
        viewModelScope.launch {
            repo.setAddonShowOnHome(addon.id, show)
            refresh()
        }
    }

    fun moveUp(addon: InstalledAddon) {
        val list = addons
        val idx = list.indexOfFirst { it.id == addon.id }
        if (idx > 0) viewModelScope.launch {
            repo.swapAddonOrder(addon.id, list[idx - 1].id)
            refresh()
        }
    }

    fun moveDown(addon: InstalledAddon) {
        val list = addons
        val idx = list.indexOfFirst { it.id == addon.id }
        if (idx >= 0 && idx < list.size - 1) viewModelScope.launch {
            repo.swapAddonOrder(addon.id, list[idx + 1].id)
            refresh()
        }
    }
}

/**
 * Dedicated screen for everything about Home catalogs: which addon
 * catalogs appear on Home, their order, and metadata source priority.
 * Opened from Settings via the single "Home catalogs" row.
 */
@Composable
fun HomeCatalogsScreen(
    repository: StreamRepository,
    onBack: () -> Unit = {}
) {
    val vm: HomeCatalogsViewModel = viewModel(factory = vmFactory { HomeCatalogsViewModel(repository) })
    val addons = vm.addons

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
                            text = "Home catalogs",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Choose which addons show their catalogs on the Home screen " +
                            "and arrange their order.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 56.dp, end = 8.dp)
                    )
                }
            }

            if (addons.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = "No catalog addons",
                        message = "Install an addon with catalogs first, then arrange them here.",
                    )
                }
            } else {
                items(addons, key = { it.id }) { addon ->
                    val idx = addons.indexOfFirst { it.id == addon.id }
                    HomeCatalogRow(
                        addon = addon,
                        onToggle = { vm.setShowOnHome(addon, it) },
                        onUp = { vm.moveUp(addon) },
                        onDown = { vm.moveDown(addon) },
                        canUp = idx > 0,
                        canDown = idx < addons.size - 1
                    )
                }
            }

            item(key = "default_note") {
                Text(
                    "PlavioMeta is the default catalog for Home. " +
                        "To disable it, simply toggle it off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun HomeCatalogRow(
    addon: InstalledAddon,
    onToggle: (Boolean) -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    canUp: Boolean,
    canDown: Boolean
) {
    val palette = LocalPalette.current
    GlassCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        onClick = { onToggle(!addon.showOnHome) }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    addon.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1
                )
                Spacer(Modifier.padding(top = 1.dp))
                Text(
                    if (addon.showOnHome) "Showing on Home" else "Hidden from Home",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            IconButton(onClick = onUp, enabled = canUp, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = "Move up",
                    tint = if (canUp) TextPrimary else TextSecondary.copy(alpha = 0.35f),
                    modifier = Modifier.size(22.dp)
                )
            }
            IconButton(onClick = onDown, enabled = canDown, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Move down",
                    tint = if (canDown) TextPrimary else TextSecondary.copy(alpha = 0.35f),
                    modifier = Modifier.size(22.dp)
                )
            }
            Switch(
                checked = addon.showOnHome,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = palette.accent,
                    checkedTrackColor = palette.accent.copy(alpha = 0.35f)
                )
            )
        }
    }
}
