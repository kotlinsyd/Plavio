package com.pulsestream.app.ui.screens

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulsestream.app.data.CustomListsRepository
import com.pulsestream.app.data.MetaPreview
import com.pulsestream.app.ui.components.EmptyState
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun ListDetailScreen(
    listsRepository: CustomListsRepository,
    listId: String,
    onBack: () -> Unit,
    onOpenDetail: (addonId: String, type: String, metaId: String) -> Unit
) {
    val items by listsRepository.observeItems(listId).collectAsState(initial = emptyList())
    var listName by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(LibrarySort.RecentlyAdded) }
    var selecting by remember { mutableStateOf(false) }
    var selectedKeys by remember { mutableStateOf(setOf<String>()) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val displayed = filterAndSortLibrary(items, query, { it.name }, { it.addedAt }, sort)

    LaunchedEffect(listId) {
        val list = listsRepository.getList(listId)
        if (list == null) onBack() else listName = list.name
    }

    GlowBackground {
      Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary) }
                Column(Modifier.weight(1f)) {
                    Text(text = listName ?: "List", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black), color = TextPrimary)
                    if (items.isNotEmpty()) Text("${items.size} ${if (items.size == 1) "title" else "titles"}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "List options", tint = TextSecondary) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Rename list") }, leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) }, onClick = { menuOpen = false; showRename = true })
                        DropdownMenuItem(text = { Text("Delete list") }, leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) }, onClick = { menuOpen = false; showDelete = true })
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (items.isNotEmpty()) LibraryControls(
                query = query, onQueryChange = { query = it }, sort = sort, onSortChange = { sort = it },
                selectionCount = selectedKeys.size, selectionMode = selecting,
                onToggleSelection = { selecting = !selecting; if (!selecting) selectedKeys = emptySet() },
                modifier = Modifier.padding(horizontal = 16.dp),
                onRemoveSelected = {
                    val removed = items.filter { it.itemKey in selectedKeys }
                    scope.launch {
                        removed.forEach { listsRepository.removeFromList(listId, it.itemKey) }
                        selectedKeys = emptySet(); selecting = false
                        if (snackbar.showSnackbar("Removed ${removed.size} titles", "Undo") == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                            removed.forEach { listsRepository.addToList(listId, it.addonId, it.type, it.metaId, it.name, it.poster) }
                        }
                    }
                }
            )
            if (items.isEmpty()) {
                EmptyState(title = "List is empty", message = "Add titles from any detail screen with the \"Add to list\" button.")
            } else if (displayed.isEmpty()) {
                Text("No titles match ‘$query’. This list is unchanged.", color = TextSecondary, modifier = Modifier.padding(24.dp))
            } else {
                LazyVerticalGrid(columns = GridCells.Adaptive(140.dp), modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 72.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(displayed, key = { "${it.listId}|${it.itemKey}" }) { item ->
                        PosterWithRemoveAction(
                            meta = MetaPreview(id = item.metaId, type = item.type, name = item.name, poster = item.poster),
                            actionLabel = "Remove ${item.name} from list",
                            onClick = { if (selecting) selectedKeys = if (item.itemKey in selectedKeys) selectedKeys - item.itemKey else selectedKeys + item.itemKey else onOpenDetail(item.addonId, item.type, item.metaId) },
                            onLongClick = { selecting = true; selectedKeys = selectedKeys + item.itemKey },
                            onRemove = { scope.launch { listsRepository.removeFromList(listId, item.itemKey) } }
                        )
                    }
                }
            }
        }
        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
      }
    }

    if (showRename) {
        var name by remember { mutableStateOf(listName ?: "") }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Rename list", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 60) name = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            listsRepository.renameList(listId, name)
                            listName = name.trim()
                            showRename = false
                        }
                    },
                    enabled = name.trim().isNotEmpty()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("Cancel") }
            }
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete \"${listName ?: "list"}\"?", color = TextPrimary) },
            text = {
                Text(
                    "The list and its saved titles will be removed. This can't be undone.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        listsRepository.deleteList(listId)
                        showDelete = false
                        onBack()
                    }
                }) { Text("Delete", color = Color(0xFFF87171)) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Cancel") }
            }
        )
    }
}
