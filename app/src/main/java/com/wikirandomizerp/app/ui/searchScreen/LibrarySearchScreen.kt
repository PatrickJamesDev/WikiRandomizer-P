package com.wikirandomizerp.app.ui.searchScreen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wikirandomizerp.app.R
import com.wikirandomizerp.app.ui.searchScreen.viewModel.LibrarySearchHit
import com.wikirandomizerp.app.ui.searchScreen.viewModel.LibrarySearchKind
import com.wikirandomizerp.app.ui.searchScreen.viewModel.LibrarySearchResults
import com.wikirandomizerp.app.ui.searchScreen.viewModel.LibrarySearchViewModel

@Composable
fun LibrarySearchScreenRoot(
    onOpenArticle: (String, String) -> Unit,
    onOpenSavedArticle: (Int, String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibrarySearchViewModel = viewModel(factory = LibrarySearchViewModel.Factory)
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()

    LibrarySearchScreen(
        query = query,
        results = results,
        onQueryChange = viewModel::setQuery,
        onOpenArticle = onOpenArticle,
        onOpenSavedArticle = onOpenSavedArticle,
        onBack = onBack,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySearchScreen(
    query: String,
    results: LibrarySearchResults,
    onQueryChange: (String) -> Unit,
    onOpenArticle: (String, String) -> Unit,
    onOpenSavedArticle: (Int, String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.librarySearch)) },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = colorScheme.surfaceContainer
                        )
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .imePadding()
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.librarySearchHint)) },
                leadingIcon = {
                    Icon(painterResource(R.drawable.search), contentDescription = null)
                },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                painterResource(R.drawable.clear),
                                contentDescription = stringResource(R.string.clearSearchField)
                            )
                        }
                    }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(focusRequester)
            )

            if (results.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            when {
                query.trim().length < 2 -> EmptySearchMessage(Modifier.weight(1f))
                !results.loading && results.hits.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.librarySearchNoResults, query.trim()),
                        style = typography.bodyLarge,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp)
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        section(R.string.librarySearchSaved, results.of(LibrarySearchKind.SAVED)) { hit ->
                            val pageId = hit.pageId
                            if (pageId != null) onOpenSavedArticle(pageId, hit.lang)
                            else onOpenArticle(hit.title, hit.lang)
                        }
                        section(R.string.librarySearchHighlights, results.of(LibrarySearchKind.HIGHLIGHT)) { hit ->
                            onOpenArticle(hit.title, hit.lang)
                        }
                        section(R.string.librarySearchHistory, results.of(LibrarySearchKind.HISTORY)) { hit ->
                            onOpenArticle(hit.title, hit.lang)
                        }
                        section(R.string.librarySearchPast, results.of(LibrarySearchKind.SEARCH)) { hit ->
                            onQueryChange(hit.query ?: hit.title)
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.section(
    titleRes: Int,
    hits: List<LibrarySearchHit>,
    onClick: (LibrarySearchHit) -> Unit
) {
    if (hits.isEmpty()) return
    item(key = "header-$titleRes") {
        Text(
            text = androidx.compose.ui.res.stringResource(titleRes),
            style = typography.titleSmall,
            color = colorScheme.primary,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp)
        )
    }
    items(hits) { hit ->
        ListItem(
            leadingContent = {
                Icon(
                    painterResource(
                        when (hit.kind) {
                            LibrarySearchKind.SAVED -> R.drawable.download_done
                            LibrarySearchKind.HIGHLIGHT -> R.drawable.highlight
                            LibrarySearchKind.HISTORY -> R.drawable.history
                            LibrarySearchKind.SEARCH -> R.drawable.search_history
                        }
                    ),
                    contentDescription = null
                )
            },
            headlineContent = {
                Text(hit.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = if (!hit.preview.isNullOrBlank()) {
                {
                    Text(
                        hit.preview.orEmpty(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            } else null,
            colors = ListItemDefaults.colors(containerColor = colorScheme.surface),
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable { onClick(hit) }
        )
    }
}

@Composable
private fun EmptySearchMessage(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painterResource(R.drawable.search),
            contentDescription = null,
            tint = colorScheme.primary,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.librarySearchEmpty),
            style = typography.bodyLarge,
            color = colorScheme.onSurfaceVariant
        )
    }
}
