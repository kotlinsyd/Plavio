package com.pulsestream.app.ui.screens

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pulsestream.app.data.MetaPreview
import com.pulsestream.app.ui.components.PosterCard
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary

internal enum class LibrarySort { RecentlyAdded, Title }

/** Pure filtering/sorting logic shared by the watchlist and custom-list screens. */
internal fun <T> filterAndSortLibrary(
    items: List<T>,
    query: String,
    name: (T) -> String,
    addedAt: (T) -> Long,
    sort: LibrarySort
): List<T> {
    val needle = query.trim()
    val filtered = if (needle.isEmpty()) items else items.filter { name(it).contains(needle, ignoreCase = true) }
    return when (sort) {
        LibrarySort.RecentlyAdded -> filtered.sortedByDescending(addedAt)
        LibrarySort.Title -> filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { name(it) })
    }
}

internal fun adaptiveColumnCount(availableWidthDp: Int, minCellWidthDp: Int = 140): Int =
    (availableWidthDp / minCellWidthDp).coerceAtLeast(1)

/** Android's animator switch is the platform accessibility setting for reduced motion. */
internal fun systemAnimationsEnabled(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()

@Composable
internal fun LibraryControls(
    query: String,
    onQueryChange: (String) -> Unit,
    sort: LibrarySort,
    onSortChange: (LibrarySort) -> Unit,
    selectionCount: Int,
    onToggleSelection: () -> Unit,
    selectionMode: Boolean = selectionCount > 0,
    onRemoveSelected: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Filter saved titles") },
            singleLine = true
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = sort == LibrarySort.RecentlyAdded,
                onClick = { onSortChange(LibrarySort.RecentlyAdded) },
                label = { Text("Recently added") }
            )
            FilterChip(
                selected = sort == LibrarySort.Title,
                onClick = { onSortChange(LibrarySort.Title) },
                label = { Text("Title A–Z") }
            )
            FilterChip(
                selected = selectionMode,
                onClick = onToggleSelection,
                label = { Text(if (selectionMode) "Cancel selection" else "Select") }
            )
            if (selectionCount > 0 && onRemoveSelected != null) {
                FilterChip(
                    selected = false,
                    onClick = onRemoveSelected,
                    label = { Text("Remove $selectionCount") }
                )
            }
        }
        if (selectionCount > 0) {
            Text("$selectionCount selected · tap titles to change selection", color = TextSecondary,
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Reserve a full, independent 48 dp row for the removal action rather than overlaying a poster. */
@Composable
internal fun PosterWithRemoveAction(
    meta: MetaPreview,
    actionLabel: String,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier.widthIn(max = 160.dp)) {
        Box(Modifier.fillMaxWidth().height(48.dp)) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.align(Alignment.CenterEnd).size(48.dp)
                    .semantics { contentDescription = actionLabel }
            ) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = TextPrimary)
            }
        }
        PosterCard(meta = meta, onClick = onClick, onLongClick = onLongClick,
            modifier = Modifier.fillMaxWidth())
    }
}
