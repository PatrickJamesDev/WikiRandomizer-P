package com.wikirandomizerp.app.ui.highlightsScreen

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.motionScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wikirandomizerp.app.R
import com.wikirandomizerp.app.data.SavedHighlight
import com.wikirandomizerp.app.ui.highlightsScreen.viewModel.HighlightsViewModel
import com.wikirandomizerp.app.ui.theme.CustomColors.listItemColors
import com.wikirandomizerp.app.ui.theme.CustomColors.topBarColors
import com.wikirandomizerp.app.ui.theme.WRShapeDefaults.middleListItemShape
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun HighlightsScreenRoot(
    openArticle: (title: String, lang: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HighlightsViewModel = viewModel(factory = HighlightsViewModel.Factory)
) {
    val highlights by viewModel.highlightsFlow.collectAsState(emptyList())
    HighlightsScreen(
        highlights = highlights,
        openArticle = openArticle,
        delete = viewModel::delete,
        deleteAll = viewModel::deleteAll,
        onBack = onBack,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HighlightsScreen(
    highlights: List<SavedHighlight>,
    openArticle: (title: String, lang: String) -> Unit,
    delete: (Long) -> Unit,
    deleteAll: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var pendingDelete by remember { mutableStateOf<SavedHighlight?>(null) }
    var showDeleteAll by remember { mutableStateOf(false) }

    val dateFormat = remember {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
    }
    val zone = remember { ZoneId.systemDefault() }

    if (pendingDelete != null) {
        DeleteHighlightDialog(
            onDismiss = { pendingDelete = null },
            onDelete = {
                pendingDelete?.let { delete(it.id) }
                pendingDelete = null
            }
        )
    }
    if (showDeleteAll) {
        DeleteAllHighlightsDialog(
            onDismiss = { showDeleteAll = false },
            onDelete = {
                showDeleteAll = false
                deleteAll()
            }
        )
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.savedHighlights)) },
                subtitle = {
                    Text(
                        if (highlights.isEmpty()) stringResource(R.string.savedHighlightsDesc)
                        else "${highlights.size}"
                    )
                },
                navigationIcon = {
                    FilledTonalIconButton(
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = listItemColors.containerColor
                        ),
                        onClick = onBack
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        enabled = highlights.isNotEmpty(),
                        shapes = IconButtonDefaults.shapes(),
                        onClick = { showDeleteAll = true }
                    ) {
                        Icon(
                            painterResource(R.drawable.delete),
                            contentDescription = stringResource(R.string.deleteAll)
                        )
                    }
                },
                colors = topBarColors,
                scrollBehavior = scrollBehavior
            )
        },
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { insets ->
        if (highlights.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(topBarColors.containerColor)
                    .padding(insets),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        color = colorScheme.primaryContainer,
                        contentColor = colorScheme.onPrimaryContainer,
                        shape = shapes.extraLarge,
                        modifier = Modifier.size(120.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painterResource(R.drawable.highlight),
                                contentDescription = null,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        stringResource(R.string.noHighlights),
                        style = typography.titleLarge
                    )
                    Text(
                        stringResource(R.string.noHighlightsDesc),
                        style = typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 40.dp, vertical = 8.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = insets,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .background(topBarColors.containerColor)
                    .padding(horizontal = 16.dp)
            ) {
                items(highlights, key = { it.id }) { highlight ->
                    val date = remember(highlight.createdAt) {
                        if (highlight.createdAt <= 0) ""
                        else Instant.ofEpochMilli(highlight.createdAt).atZone(zone).format(dateFormat)
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = listItemColors.containerColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .combinedClickable(
                                onClick = { openArticle(highlight.title, highlight.lang) },
                                onLongClick = { pendingDelete = highlight }
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(width = 4.dp, height = 48.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(colorScheme.primary)
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = highlight.text,
                                    style = typography.bodyLarge,
                                    maxLines = 8,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = highlight.title,
                                    style = typography.labelLarge,
                                    color = colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = listOf(highlight.lang.uppercase(Locale.getDefault()), date)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · "),
                                    style = typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                shapes = IconButtonDefaults.shapes(),
                                onClick = { pendingDelete = highlight }
                            ) {
                                Icon(
                                    painterResource(R.drawable.delete),
                                    contentDescription = stringResource(R.string.deleteHighlight)
                                )
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DeleteHighlightDialog(
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = shapes.extraLarge,
            tonalElevation = AlertDialogDefaults.TonalElevation
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    stringResource(R.string.deleteHighlight),
                    style = typography.headlineSmall
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.deleteHighlightDesc),
                    style = typography.bodyMedium
                )
                Spacer(Modifier.height(20.dp))
                Row(Modifier.align(Alignment.End)) {
                    TextButton(
                        shapes = ButtonDefaults.shapes(),
                        onClick = onDismiss
                    ) { Text(stringResource(R.string.cancel)) }
                    TextButton(
                        shapes = ButtonDefaults.shapes(),
                        onClick = onDelete
                    ) { Text(stringResource(R.string.delete)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DeleteAllHighlightsDialog(
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = shapes.extraLarge,
            tonalElevation = AlertDialogDefaults.TonalElevation
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    stringResource(R.string.deleteAllHighlights),
                    style = typography.headlineSmall
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.deleteAllHighlightsDesc),
                    style = typography.bodyMedium
                )
                Spacer(Modifier.height(20.dp))
                Row(Modifier.align(Alignment.End)) {
                    TextButton(
                        shapes = ButtonDefaults.shapes(),
                        onClick = onDismiss
                    ) { Text(stringResource(R.string.cancel)) }
                    TextButton(
                        shapes = ButtonDefaults.shapes(),
                        onClick = onDelete
                    ) { Text(stringResource(R.string.deleteAll)) }
                }
            }
        }
    }
}
