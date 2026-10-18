package com.pulsestream.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulsestream.app.data.CustomListsRepository
import com.pulsestream.app.data.db.CustomList
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * Bottom sheet for adding the current title to custom lists.
 * Each row toggles membership; a "New list" row creates one inline.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToListSheet(
    listsRepository: CustomListsRepository,
    addonId: String,
    type: String,
    metaId: String,
    name: String,
    poster: String?,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val lists by listsRepository.lists.collectAsState(initial = emptyList())
    val itemKey = listsRepository.itemKey(addonId, type, metaId)
    val containing by listsRepository.observeListsContaining(itemKey)
        .collectAsState(initial = emptyList())
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp)
        ) {
            Text(
                text = "Add to list",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(Modifier.height(12.dp))

            if (lists.isEmpty() && !creating) {
                Text(
                    text = "You don't have any lists yet — create your first one below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }

            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(lists, key = { it.id }) { list ->
                    ListToggleRow(
                        list = list,
                        listsRepository = listsRepository,
                        checked = list.id in containing,
                        onToggle = { isNowIn ->
                            scope.launch {
                                if (isNowIn) {
                                    listsRepository.addToList(
                                        list.id, addonId, type, metaId, name, poster
                                    )
                                } else {
                                    listsRepository.removeFromList(list.id, itemKey)
                                }
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            if (creating) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { if (it.length <= 60) newName = it },
                        placeholder = { Text("List name", color = TextSecondary) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            scope.launch {
                                val id = listsRepository.createList(newName)
                                listsRepository.addToList(
                                    id, addonId, type, metaId, name, poster
                                )
                                newName = ""
                                creating = false
                            }
                        },
                        enabled = newName.trim().isNotEmpty()
                    ) { Text("Create") }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { creating = true }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        tint = LocalPalette.current.accent,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "New list",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = LocalPalette.current.accent
                    )
                }
            }
        }
    }
}

@Composable
private fun ListToggleRow(
    list: CustomList,
    listsRepository: CustomListsRepository,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val count by listsRepository.observeItemCount(list.id).collectAsState(initial = 0)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.FormatListBulleted,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = list.name,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary
            )
            Text(
                text = "$count ${if (count == 1) "title" else "titles"}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        if (checked) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "In list",
                tint = LocalPalette.current.accent,
                modifier = Modifier.size(22.dp)
            )
        } else {
            // Keep alignment consistent with the checked state.
            Spacer(Modifier.size(22.dp))
        }
    }
}
