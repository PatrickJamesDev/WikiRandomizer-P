package org.nsh07.wikireader.shared.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.nsh07.wikireader.shared.library.LibraryEntry
import org.nsh07.wikireader.shared.library.SearchQuery
import org.nsh07.wikireader.shared.ui.languageName

@Composable
fun LibrarySearchScreen(
    query: String,
    saved: List<LibraryEntry>,
    history: List<LibraryEntry>,
    recentSearches: List<SearchQuery>,
    onQueryChange: (String) -> Unit,
    onSearchCommitted: (String) -> Unit,
    onRecentSearch: (String) -> Unit,
    onClearRecent: () -> Unit,
    onOpen: (LibraryEntry) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val normalized = query.trim()
    val savedHits = remember(normalized, saved) { saved.matching(normalized) }
    val historyHits = remember(normalized, history) { history.matching(normalized) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search saved and history") },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearchCommitted(query) }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .focusRequester(focusRequester)
        )

        when {
            normalized.length < 2 && recentSearches.isNotEmpty() -> LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "recent-header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent searches",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )
                        TextButton(onClick = onClearRecent) { Text("Clear") }
                    }
                }
                items(recentSearches, key = { it.key }) { search ->
                    RecentSearchRow(
                        search = search,
                        onClick = { onRecentSearch(search.query) }
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }

            normalized.length < 2 -> SearchMessage(
                title = "Search your library",
                description = "Type at least two characters to find saved and recently opened articles.",
                modifier = Modifier.weight(1f)
            )

            savedHits.isEmpty() && historyHits.isEmpty() -> SearchMessage(
                title = "No results",
                description = "Nothing matched \"$normalized\" in saved articles or history.",
                modifier = Modifier.weight(1f)
            )

            else -> LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                resultSection("Saved", savedHits, "saved", onOpen)
                resultSection("History", historyHits, "history", onOpen)
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

private fun LazyListScope.resultSection(
    title: String,
    entries: List<LibraryEntry>,
    keyPrefix: String,
    onOpen: (LibraryEntry) -> Unit
) {
    if (entries.isEmpty()) return
    item(key = "$keyPrefix-header") {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
    items(entries, key = { "$keyPrefix-${it.key}" }) { entry ->
        LibraryRow(
            entry = entry,
            onOpen = { onOpen(entry) },
            onRemove = null
        )
    }
}

@Composable
private fun RecentSearchRow(search: SearchQuery, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = search.query,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = languageName(search.language),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun List<LibraryEntry>.matching(query: String): List<LibraryEntry> {
    if (query.length < 2) return emptyList()
    return filter { entry ->
        entry.article.title.contains(query, ignoreCase = true) ||
            entry.article.summaryDescription?.contains(query, ignoreCase = true) == true ||
            languageName(entry.language).contains(query, ignoreCase = true)
    }
}

@Composable
private fun SearchMessage(
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
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
