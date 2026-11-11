package com.wikirandomizerp.app.ui.savedArticlesScreen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.motionScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.wikirandomizerp.app.R
import com.wikirandomizerp.app.data.ArticleInfo
import com.wikirandomizerp.app.data.LanguageInfo
import com.wikirandomizerp.app.data.WRStatus
import com.wikirandomizerp.app.ui.common.CollectionLayout
import com.wikirandomizerp.app.ui.common.CollectionSort
import com.wikirandomizerp.app.ui.common.topicDisplayName
import com.wikirandomizerp.app.ui.image.FeedImage
import com.wikirandomizerp.app.ui.image.ImagePrefetcher
import com.wikirandomizerp.app.ui.savedArticlesScreen.viewModel.SavedArticlesAction
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import com.wikirandomizerp.app.ui.savedArticlesScreen.viewModel.SavedArticlesViewModel
import com.wikirandomizerp.app.ui.theme.CustomColors.listItemColors
import com.wikirandomizerp.app.ui.theme.CustomColors.topBarColors

/** Folder filter value for saved articles that have no folder (folder names cannot contain NUL). */
private const val NO_FOLDER_FILTER = "\u0000no-folder"

@Composable
fun SavedArticlesScreenRoot(
    imageBackground: Boolean,
    openSavedArticle: (Int, String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    saveFolders: Boolean = false,
    saveNotes: Boolean = false,
    viewModel: SavedArticlesViewModel = viewModel(factory = SavedArticlesViewModel.Factory)
) {
    val savedArticles by viewModel.savedArticlesFlow.collectAsState(emptyList())
    val savedArticleLangs by viewModel.savedArticleLangsFlow.collectAsState(emptyList())
    val savedFolders by viewModel.savedFoldersFlow.collectAsState(emptyList())
    val sortName by viewModel.sortName.collectAsStateWithLifecycle()
    val layoutName by viewModel.layoutName.collectAsStateWithLifecycle()

    // Keep every saved cover pinned in the image cache: opening Saved is instant even after
    // old article body images have been evicted.
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(savedArticles) {
        // Off the main thread: the first call may build Coil's image loader (SSL setup).
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            ImagePrefetcher.warmThumbnails(context, savedArticles.map { it.thumbnail })
        }
    }

    SavedArticlesScreen(
        savedArticles = savedArticles,
        savedArticleLangs = savedArticleLangs,
        savedFolders = savedFolders,
        imageBackground = imageBackground,
        openSavedArticle = openSavedArticle,
        onAction = viewModel::onAction,
        onBack = onBack,
        sortName = sortName,
        layoutName = layoutName,
        onSortChange = viewModel::setSort,
        onLayoutChange = viewModel::setLayout,
        saveFolders = saveFolders,
        saveNotes = saveNotes,
        modifier = modifier
    )
}

@OptIn(
    ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun SavedArticlesScreen(
    savedArticles: List<ArticleInfo>,
    savedArticleLangs: List<LanguageInfo>,
    imageBackground: Boolean,
    openSavedArticle: (Int, String) -> Unit,
    onAction: (SavedArticlesAction) -> WRStatus,
    onBack: () -> Unit,
    savedFolders: List<String> = emptyList(),
    sortName: String = CollectionSort.DATE_NEW.name,
    layoutName: String = CollectionLayout.LIST.name,
    onSortChange: (String) -> Unit = {},
    onLayoutChange: (String) -> Unit = {},
    saveFolders: Boolean = false,
    saveNotes: Boolean = false,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackBarHostState = remember { SnackbarHostState() }
    var toDelete: Pair<Int, String>? by remember { mutableStateOf(null) }
    var toDeleteTitle: String? by remember { mutableStateOf(null) }
    var showArticleDeleteDialog by remember { mutableStateOf(false) }
    var detailsArticle by remember { mutableStateOf<ArticleInfo?>(null) }
    var folderFilter by remember { mutableStateOf<String?>(null) }
    var ct by remember { mutableIntStateOf(0) }
    val sort = CollectionSort.entries.firstOrNull { it.name == sortName } ?: CollectionSort.DATE_NEW
    val layout = CollectionLayout.entries.firstOrNull { it.name == layoutName } ?: CollectionLayout.LIST

    val languageFilters = remember(savedArticleLangs) {
        savedArticleLangs.map { LanguageFilterOption(it.langName, it.lang) }
    }

    val selectedLanguages = remember(ct, languageFilters) {
        languageFilters.fastFilter { it.selected }.map { it.option }
    }

    // Folders actually present in the library. "No folder" is offered separately when at least
    // one article has no folder. The savedFolders flow keeps this correct after an edit.
    val folderNames = remember(savedFolders) { savedFolders }
    val hasFolderless = remember(savedArticles) { savedArticles.any { it.folder.isNullOrBlank() } }

    val filtered = remember(savedArticles, selectedLanguages, folderFilter) {
        savedArticles.filter { article ->
            val languageOk = selectedLanguages.isEmpty() || article.langName in selectedLanguages
            val folderOk = when (folderFilter) {
                null -> true
                NO_FOLDER_FILTER -> article.folder.isNullOrBlank()
                else -> article.folder == folderFilter
            }
            languageOk && folderOk
        }
    }
    val sorted = remember(filtered, sort) {
        when (sort) {
            CollectionSort.DATE_NEW -> filtered.sortedWith(
                compareByDescending<ArticleInfo> { it.savedAt }.thenBy { it.title }
            )
            CollectionSort.DATE_OLD -> filtered.sortedWith(
                compareBy<ArticleInfo> { it.savedAt }.thenBy { it.title }
            )
            CollectionSort.TOPIC_AZ -> filtered.sortedWith(
                compareBy<ArticleInfo> { topicDisplayName(it.topic) ?: "\uFFFF" }.thenBy { it.title }
            )
            CollectionSort.TOPIC_ZA -> filtered.sortedWith(
                compareByDescending<ArticleInfo> { topicDisplayName(it.topic) ?: "" }.thenByDescending { it.title }
            )
        }
    }

    val zone = remember { ZoneId.systemDefault() }
    val dateFormat = remember {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
    }
    fun groupLabel(article: ArticleInfo): String = if (sort.isDate) {
        if (article.savedAt <= 0) "Older"
        else Instant.ofEpochMilli(article.savedAt).atZone(zone).format(dateFormat)
    } else {
        topicDisplayName(article.topic) ?: "Other topics"
    }
    val groupedArticles = remember(sorted, sort) { sorted.groupBy { groupLabel(it) } }

    if (showArticleDeleteDialog)
        DeleteArticleDialog(
            pageId = toDelete?.first,
            lang = toDelete?.second,
            title = toDeleteTitle,
            setShowDeleteDialog = { showArticleDeleteDialog = it },
            deleteArticle = { pageId, lang ->
                onAction(SavedArticlesAction.Delete(pageId, lang))
            },
            deleteAll = { onAction(SavedArticlesAction.DeleteAll) },
            showSnackbar = { coroutineScope.launch { snackBarHostState.showSnackbar(it) } }
        )

    detailsArticle?.let { article ->
        SavedArticleDetailsDialog(
            article = article,
            folders = folderNames,
            showFolder = saveFolders,
            showNote = saveNotes,
            onDismiss = { detailsArticle = null },
            onSave = { folder, note ->
                detailsArticle = null
                onAction(
                    SavedArticlesAction.SetDetails(
                        pageId = article.pageId,
                        lang = article.lang,
                        folder = folder,
                        note = note
                    )
                )
                coroutineScope.launch {
                    snackBarHostState.showSnackbar(
                        context.getString(R.string.savedArticleUpdated)
                    )
                }
            },
            onDelete = {
                // Keep the familiar confirmation before actually deleting a saved article.
                detailsArticle = null
                toDelete = Pair(article.pageId, article.lang)
                toDeleteTitle = article.title
                showArticleDeleteDialog = true
            }
        )
    }

    Scaffold(
        topBar = {
            SavedArticlesTopBar(
                articlesInfo = stringResource(
                    R.string.articlesSize,
                    savedArticles.size,
                    remember(groupedArticles) { groupedArticles.size }
                ),
                scrollBehavior = scrollBehavior,
                deleteEnabled = savedArticles.isNotEmpty(),
                sort = sort,
                layout = layout,
                onSortChange = { onSortChange(it.name) },
                onLayoutChange = { onLayoutChange(it.name) },
                onBack = onBack,
                onDeleteAll = {
                    toDelete = null
                    toDeleteTitle = null
                    showArticleDeleteDialog = true
                }
            )
        },
        snackbarHost = { SnackbarHost(snackBarHostState) },
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { insets ->
        if (savedArticles.isNotEmpty()) {
            if (layout.isGrid) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(layout.columns),
                    contentPadding = insets,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .background(topBarColors.containerColor)
                        .padding(horizontal = 16.dp)
                ) {
                    if (savedArticleLangs.size > 1 || folderNames.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SavedFilterChips(
                                languageFilters = languageFilters,
                                showLanguages = savedArticleLangs.size > 1,
                                folders = folderNames,
                                hasFolderless = hasFolderless,
                                selectedFolder = folderFilter,
                                onToggleLanguage = { ct++ },
                                onSelectFolder = { folderFilter = it }
                            )
                        }
                    }
                    items(
                        sorted,
                        key = { it.pageId.toString() + it.lang }
                    ) { article ->
                        SavedGridCard(
                            article = article,
                            imageBackground = imageBackground,
                            subtitle = groupLabel(article),
                            onClick = { openSavedArticle(article.pageId, article.lang) },
                            onLongClick = {
                                if (saveFolders || saveNotes) {
                                    detailsArticle = article
                                } else {
                                    toDelete = Pair(article.pageId, article.lang)
                                    toDeleteTitle = article.title
                                    showArticleDeleteDialog = true
                                }
                            }
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(Modifier.height(16.dp))
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = insets,
                    modifier = Modifier
                        .fillMaxHeight()
                        .background(topBarColors.containerColor)
                ) {
                    item {
                        if (savedArticleLangs.size > 1 || folderNames.isNotEmpty()) {
                            SavedFilterChips(
                                languageFilters = languageFilters,
                                showLanguages = savedArticleLangs.size > 1,
                                folders = folderNames,
                                hasFolderless = hasFolderless,
                                selectedFolder = folderFilter,
                                onToggleLanguage = { ct++ },
                                onSelectFolder = { folderFilter = it }
                            )
                        }
                    }
                    groupedArticles.forEach { item ->
                        val items = item.value.size
                        if (groupedArticles.size > 1) item(key = item.key + "-group") {
                            Text(
                                item.key,
                                style = typography.titleSmall,
                                modifier = Modifier
                                    .padding(horizontal = 32.dp, vertical = 14.dp)
                                    .animateItem()
                            )
                        }
                        itemsIndexed(
                            item.value,
                            key = { index: Int, it: ArticleInfo -> it.pageId.toString() + it.lang }
                        ) { index: Int, it: ArticleInfo ->
                            val interactionSource = remember { MutableInteractionSource() }
                            val isPressed by interactionSource.collectIsPressedAsState()

                            val top by animateDpAsState(
                                if (isPressed) 48.dp
                                else {
                                    if (items == 1 || index == 0) 20.dp
                                    else 4.dp
                                },
                                motionScheme.fastSpatialSpec()
                            )
                            val bottom by animateDpAsState(
                                if (isPressed) 48.dp
                                else {
                                    if (items == 1 || index == items - 1) 20.dp
                                    else 4.dp
                                },
                                motionScheme.fastSpatialSpec()
                            )
                            val imageCorners by animateDpAsState(
                                if (isPressed) 32.dp
                                else 16.dp
                            )

                            ListItem(
                                leadingContent = if (it.thumbnail != null) {
                                    {
                                        FeedImage(
                                            source = it.thumbnail,
                                            description = it.description,
                                            background = imageBackground,
                                            pinnedThumbnail = true,
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(RoundedCornerShape(imageCorners.coerceAtLeast(0.dp)))
                                        )
                                    }
                                } else {
                                    {
                                        Spacer(Modifier.width(64.dp))
                                    }
                                },
                                headlineContent = {
                                    Text(
                                        it.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                supportingContent = {
                                    Column {
                                        Text(
                                            topicDisplayName(it.topic)
                                                ?: it.description
                                                ?: it.langName,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (!it.folder.isNullOrBlank()) {
                                            Text(
                                                stringResource(R.string.savedFolder, it.folder),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = typography.bodySmall,
                                                color = colorScheme.primary
                                            )
                                        }
                                        if (!it.note.isNullOrBlank()) {
                                            Text(
                                                it.note,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                style = typography.bodySmall,
                                                color = colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
                                colors = listItemColors,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = top.coerceAtLeast(0.dp),
                                            topEnd = top.coerceAtLeast(0.dp),
                                            bottomStart = bottom.coerceAtLeast(0.dp),
                                            bottomEnd = bottom.coerceAtLeast(0.dp)
                                        )
                                    )
                                    .combinedClickable(
                                        onClick = { openSavedArticle(it.pageId, it.lang) },
                                        onLongClick = {
                                            if (saveFolders || saveNotes) {
                                                detailsArticle = it
                                            } else {
                                                toDelete = Pair(it.pageId, it.lang)
                                                toDeleteTitle = it.title
                                                showArticleDeleteDialog = true
                                            }
                                        },
                                        interactionSource = interactionSource
                                    )
                                    .animateItem()
                            )
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        } else
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(topBarColors.containerColor),
                contentAlignment = Alignment.Center
            ) {
                val transition = rememberInfiniteTransition(
                    label = "Cookie rotate"
                )
                val angle by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 10000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "Cookie animation"
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.Center) {
                        Spacer(
                            Modifier
                                .graphicsLayer {
                                    rotationZ = angle
                                }
                                .clip(MaterialShapes.Cookie12Sided.toShape())
                                .background(colorScheme.primaryContainer)
                                .padding(32.dp)
                                .size(100.dp)
                        )
                        Icon(
                            painterResource(R.drawable.save),
                            contentDescription = null,
                            tint = colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .padding(32.dp)
                                .size(100.dp)
                        )
                    }
                    Text(
                        stringResource(R.string.noSavedArticles),
                        textAlign = TextAlign.Center,
                        style = typography.titleLarge,
                        modifier = Modifier.padding(8.dp)
                    )
                    Text(
                        stringResource(R.string.noSavedArticlesDesc),
                        textAlign = TextAlign.Center,
                        style = typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 48.dp)
                    )
                }
            }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SavedFilterChips(
    languageFilters: List<LanguageFilterOption>,
    showLanguages: Boolean,
    folders: List<String>,
    hasFolderless: Boolean,
    selectedFolder: String?,
    onToggleLanguage: () -> Unit,
    onSelectFolder: (String?) -> Unit
) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        if (showLanguages) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                languageFilters.fastForEach { filterOption ->
                    FilterChip(
                        selected = filterOption.selected,
                        onClick = {
                            filterOption.selected = !filterOption.selected
                            onToggleLanguage()
                        },
                        label = { Text(filterOption.option) },
                        leadingIcon = if (filterOption.selected) {
                            {
                                Icon(painterResource(R.drawable.check), contentDescription = null)
                            }
                        } else null
                    )
                }
            }
        }
        if (folders.isNotEmpty() || hasFolderless) {
            FlowRow(
                modifier = Modifier.padding(top = if (showLanguages) 6.dp else 0.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFolder == null,
                    onClick = { onSelectFolder(null) },
                    label = { Text(stringResource(R.string.allFolders)) }
                )
                folders.fastForEach { folder ->
                    FilterChip(
                        selected = selectedFolder == folder,
                        onClick = { onSelectFolder(folder) },
                        label = { Text(folder) }
                    )
                }
                if (hasFolderless) {
                    FilterChip(
                        selected = selectedFolder == NO_FOLDER_FILTER,
                        onClick = { onSelectFolder(NO_FOLDER_FILTER) },
                        label = { Text(stringResource(R.string.noFolder)) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SavedGridCard(
    article: ArticleInfo,
    imageBackground: Boolean,
    subtitle: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = listItemColors.containerColor),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Column {
            if (article.thumbnail != null) {
                FeedImage(
                    source = article.thumbnail,
                    description = article.description,
                    background = imageBackground,
                    pinnedThumbnail = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )
            }
            Text(
                article.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = typography.titleSmall,
                modifier = Modifier.padding(start = 10.dp, top = 10.dp, end = 10.dp)
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 10.dp, top = 2.dp, end = 10.dp)
                )
            }
            if (!article.folder.isNullOrBlank()) {
                Text(
                    stringResource(R.string.savedFolder, article.folder),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = typography.bodySmall,
                    color = colorScheme.primary,
                    modifier = Modifier.padding(start = 10.dp, top = 2.dp, end = 10.dp)
                )
            }
            if (!article.note.isNullOrBlank()) {
                Text(
                    article.note,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 10.dp, top = 2.dp, end = 10.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}