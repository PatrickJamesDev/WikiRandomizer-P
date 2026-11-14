package org.nsh07.wikireader.shared.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.nsh07.wikireader.shared.library.LibraryEntry
import org.nsh07.wikireader.shared.ui.languageName

private const val NO_FOLDER_FILTER = "\u0000no-folder"

@Composable
fun LibraryScreen(
    entries: List<LibraryEntry>,
    emptyTitle: String,
    emptyDescription: String,
    onOpen: (LibraryEntry) -> Unit,
    onRemove: (LibraryEntry) -> Unit,
    onClear: () -> Unit,
    onEditDetails: ((LibraryEntry, String?, String?) -> Unit)? = null
) {
    var confirmClear by remember { mutableStateOf(false) }
    var editDetailsFor by remember { mutableStateOf<LibraryEntry?>(null) }
    var folderFilter by remember { mutableStateOf<String?>(null) }

    val folderNames = entries.mapNotNull { entry ->
        entry.folder?.trim()?.takeIf { it.isNotEmpty() }
    }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
    val hasFolderless = entries.any { it.folder.isNullOrBlank() }
    val visibleEntries = when (folderFilter) {
        null -> entries
        NO_FOLDER_FILTER -> entries.filter { it.folder.isNullOrBlank() }
        else -> entries.filter { it.folder.equals(folderFilter, ignoreCase = true) }
    }

    LaunchedEffect(folderNames, hasFolderless) {
        val selected = folderFilter ?: return@LaunchedEffect
        if (selected == NO_FOLDER_FILTER) {
            if (!hasFolderless) folderFilter = null
        } else if (folderNames.none { it.equals(selected, ignoreCase = true) }) {
            folderFilter = null
        }
    }

    if (entries.isEmpty()) {
        EmptyLibrary(title = emptyTitle, description = emptyDescription)
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (folderFilter == null) {
                            "${entries.size} ${if (entries.size == 1) "article" else "articles"}"
                        } else {
                            "${visibleEntries.size} of ${entries.size} articles"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { confirmClear = true }) {
                        Text("Clear all")
                    }
                }
            }

            if (folderNames.isNotEmpty()) {
                item {
                    FolderFilterRow(
                        folders = folderNames,
                        hasFolderless = hasFolderless,
                        selected = folderFilter,
                        onSelect = { folderFilter = it }
                    )
                }
            }

            if (visibleEntries.isEmpty()) {
                item {
                    Text(
                        text = "No saved articles in this folder.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(visibleEntries, key = { it.key }) { entry ->
                    LibraryRow(
                        entry = entry,
                        onOpen = { onOpen(entry) },
                        onRemove = { onRemove(entry) },
                        onEdit = onEditDetails?.let { { editDetailsFor = entry } }
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    editDetailsFor?.let { entry ->
        DetailsDialog(
            entry = entry,
            folders = folderNames,
            onDismiss = { editDetailsFor = null },
            onSave = { folder, note ->
                onEditDetails?.invoke(entry, folder, note)
                editDetailsFor = null
            }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear this list?") },
            text = { Text("Every item in this list will be removed from this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        onClear()
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
internal fun LibraryRow(
    entry: LibraryEntry,
    onOpen: () -> Unit,
    onRemove: (() -> Unit)?,
    onEdit: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val thumbnail = entry.article.thumbnail?.source
                if (!thumbnail.isNullOrBlank()) {
                    AsyncImage(
                        model = thumbnail,
                        contentDescription = entry.article.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = entry.article.title.take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = entry.article.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val description = entry.article.summaryDescription
                if (!description.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = languageName(entry.language),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                val folder = entry.folder?.trim()
                if (!folder.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = folder,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val note = entry.note
                if (!note.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                if (onEdit != null) {
                    TextButton(onClick = onEdit) {
                        Text("Edit")
                    }
                }
                if (onRemove != null) {
                    TextButton(onClick = onRemove) {
                        Text("Remove")
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderFilterRow(
    folders: List<String>,
    hasFolderless: Boolean,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text("All") }
        )
        folders.forEach { folder ->
            val isSelected = selected.equals(folder, ignoreCase = true)
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(if (isSelected) null else folder) },
                label = { Text(folder) }
            )
        }
        if (hasFolderless) {
            val isSelected = selected == NO_FOLDER_FILTER
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(if (isSelected) null else NO_FOLDER_FILTER) },
                label = { Text("No folder") }
            )
        }
    }
}

@Composable
private fun DetailsDialog(
    entry: LibraryEntry,
    folders: List<String>,
    onDismiss: () -> Unit,
    onSave: (String?, String?) -> Unit
) {
    var folder by remember(entry.key) { mutableStateOf(entry.folder.orEmpty()) }
    var note by remember(entry.key) { mutableStateOf(entry.note.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Saved article details") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = folder,
                    onValueChange = { if (it.length <= 80) folder = it },
                    label = { Text("Folder") },
                    placeholder = { Text("Research, Recipes...") },
                    singleLine = true
                )
                if (folders.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        folders.forEach { name ->
                            val isSelected = folder.trim().equals(name, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = { folder = if (isSelected) "" else name },
                                label = { Text(name) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 2_000) note = it },
                    label = { Text("Personal note") },
                    minLines = 3,
                    maxLines = 6
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(folder, note) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun EmptyLibrary(title: String, description: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
