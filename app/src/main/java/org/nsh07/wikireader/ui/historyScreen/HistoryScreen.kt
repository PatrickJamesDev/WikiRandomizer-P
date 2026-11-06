package org.nsh07.wikireader.ui.historyScreen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.motionScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.nsh07.wikireader.R
import org.nsh07.wikireader.data.ViewHistoryItem
import org.nsh07.wikireader.ui.common.CollectionLayout
import org.nsh07.wikireader.ui.common.CollectionSort
import org.nsh07.wikireader.ui.common.topicDisplayName
import org.nsh07.wikireader.ui.historyScreen.viewModel.HistoryAction
import org.nsh07.wikireader.ui.historyScreen.viewModel.HistoryViewModel
import org.nsh07.wikireader.ui.image.FeedImage
import org.nsh07.wikireader.ui.theme.CustomColors.listItemColors
import org.nsh07.wikireader.ui.theme.CustomColors.topBarColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun HistoryScreenRoot(
    imageBackground: Boolean,
    openArticle: (String, String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory)
) {
    val viewHistory by viewModel.viewHistoryFlow.collectAsStateWithLifecycle(emptyList())
    val sortName by viewModel.sortName.collectAsStateWithLifecycle()
    val layoutName by viewModel.layoutName.collectAsStateWithLifecycle()

    HistoryScreen(
        viewHistory = viewHistory,
        imageBackground = imageBackground,
        openArticle = openArticle,
        onAction = viewModel::onAction,
        onBack = onBack,
        sortName = sortName,
        layoutName = layoutName,
        onSortChange = viewModel::setSort,
        onLayoutChange = viewModel::setLayout,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HistoryScreen(
    viewHistory: List<ViewHistoryItem>,
    imageBackground: Boolean,
    openArticle: (String, String) -> Unit,
    onAction: (HistoryAction) -> Unit,
    onBack: () -> Unit,
    sortName: String = CollectionSort.DATE_NEW.name,
    layoutName: String = CollectionLayout.LIST.name,
    onSortChange: (String) -> Unit = {},
    onLayoutChange: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val locale = LocalConfiguration.current.locales.get(0)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val snackbarHostState = remember { SnackbarHostState() }
    var sortMenu by remember { mutableStateOf(false) }
    val sort = CollectionSort.entries.firstOrNull { it.name == sortName } ?: CollectionSort.DATE_NEW
    val layout = CollectionLayout.entries.firstOrNull { it.name == layoutName } ?: CollectionLayout.LIST

    var lastDeleted: ViewHistoryItem?
    var deletedItems: List<ViewHistoryItem>?

    val dtf = remember {
        DateTimeFormatter
            .ofLocalizedDate(FormatStyle.LONG)
            .withLocale(locale)
    }
    val tf = remember {
        DateTimeFormatter
            .ofLocalizedTime(FormatStyle.SHORT)
            .withLocale(locale)
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val sortedHistory = remember(viewHistory, sort) {
        when (sort) {
            CollectionSort.DATE_NEW -> viewHistory.sortedWith(
                compareByDescending<ViewHistoryItem> { it.time }.thenBy { it.title }
            )
            CollectionSort.DATE_OLD -> viewHistory.sortedWith(
                compareBy<ViewHistoryItem> { it.time }.thenBy { it.title }
            )
            CollectionSort.TOPIC_AZ -> viewHistory.sortedWith(
                compareBy<ViewHistoryItem> { topicDisplayName(it.topic) ?: "\uFFFF" }.thenBy { it.title }
            )
            CollectionSort.TOPIC_ZA -> viewHistory.sortedWith(
                compareByDescending<ViewHistoryItem> { topicDisplayName(it.topic) ?: "" }.thenByDescending { it.title }
            )
        }
    }
    fun groupLabel(item: ViewHistoryItem): String = if (sort.isDate) {
        Instant.ofEpochMilli(item.time).atZone(zone).format(dtf)
    } else {
        topicDisplayName(item.topic) ?: "Other topics"
    }
    val groupedHistory = remember(sortedHistory, sort) {
        sortedHistory.groupBy { groupLabel(it) }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.history)) },
                subtitle = { Text(stringResource(R.string.items, viewHistory.size)) },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = listItemColors.containerColor),
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = { sortMenu = true },
                            shapes = IconButtonDefaults.shapes()
                        ) {
                            Icon(painterResource(R.drawable.sort), stringResource(R.string.sort))
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            listOf(
                                CollectionSort.DATE_NEW to R.string.sortDateNewest,
                                CollectionSort.DATE_OLD to R.string.sortDateOldest,
                                CollectionSort.TOPIC_AZ to R.string.sortTopicAZ,
                                CollectionSort.TOPIC_ZA to R.string.sortTopicZA
                            ).forEach { (mode, label) ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(label)) },
                                    onClick = {
                                        sortMenu = false
                                        onSortChange(mode.name)
                                    },
                                    trailingIcon = {
                                        if (sort == mode) Icon(
                                            painterResource(R.drawable.check),
                                            contentDescription = null
                                        )
                                    }
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = { onLayoutChange(layout.next().name) },
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(
                            painterResource(
                                if (layout.isGrid) R.drawable.grid_view else R.drawable.view_list
                            ),
                            contentDescription = stringResource(
                                when (layout) {
                                    CollectionLayout.LIST -> R.string.layoutList
                                    CollectionLayout.GRID_2 -> R.string.layoutGrid2
                                    CollectionLayout.GRID_3 -> R.string.layoutGrid3
                                    CollectionLayout.GRID_4 -> R.string.layoutGrid4
                                }
                            )
                        )
                    }
                    IconButton(
                        onClick = {
                            deletedItems = viewHistory
                            onAction(HistoryAction.RemoveAll)
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = context.getString(R.string.allHistoryDeleted),
                                    actionLabel = context.getString(R.string.undo)
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    deletedItems.fastForEach {
                                        onAction(HistoryAction.InsertItem(it))
                                        delay(10)
                                    }
                                }
                            }
                        },
                        enabled = viewHistory.isNotEmpty(),
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(painterResource(R.drawable.delete), stringResource(R.string.deleteAll))
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = topBarColors
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { insets ->
        if (viewHistory.isNotEmpty()) {
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
                    items(sortedHistory, key = { it.time }) { item ->
                        HistoryGridCard(
                            item = item,
                            imageBackground = imageBackground,
                            subtitle = if (sort.isDate) {
                                topicDisplayName(item.topic) ?: item.description ?: item.lang
                            } else {
                                Instant.ofEpochMilli(item.time).atZone(zone).format(tf)
                            },
                            onClick = { openArticle(item.title, item.lang) },
                            onLongClick = {
                                lastDeleted = item
                                val removed = item
                                onAction(HistoryAction.RemoveItem(item))
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = context.getString(
                                            R.string.deletedFromHistory,
                                            item.title
                                        ),
                                        actionLabel = context.getString(R.string.undo),
                                        duration = SnackbarDuration.Long
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        onAction(HistoryAction.InsertItem(removed))
                                    }
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
                        .fillMaxSize()
                        .background(topBarColors.containerColor)
                ) {
                    groupedHistory.forEach { item ->
                        val items = item.value.size
                        item(key = item.key + "-group") {
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
                            key = { index: Int, it: ViewHistoryItem -> it.time }
                        ) { index, it ->
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
                                            loadingIndicator = true,
                                            background = imageBackground,
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(RoundedCornerShape(imageCorners))
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
                                    Text(
                                        topicDisplayName(it.topic)
                                            ?: it.description
                                            ?: it.lang,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                trailingContent = {
                                    Text(
                                        remember {
                                            Instant.ofEpochMilli(it.time).atZone(zone).format(tf)
                                        }
                                    )
                                },
                                colors = listItemColors,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = top,
                                            topEnd = top,
                                            bottomStart = bottom,
                                            bottomEnd = bottom
                                        )
                                    )
                                    .combinedClickable(
                                        onClick = { openArticle(it.title, it.lang) },
                                        onLongClick = {
                                            lastDeleted = it
                                            val removed = it
                                            onAction(HistoryAction.RemoveItem(it))
                                            scope.launch {
                                                val result = snackbarHostState.showSnackbar(
                                                    message = context.getString(
                                                        R.string.deletedFromHistory,
                                                        it.title
                                                    ),
                                                    actionLabel = context.getString(R.string.undo),
                                                    duration = SnackbarDuration.Long
                                                )
                                                if (result == SnackbarResult.ActionPerformed) {
                                                    onAction(HistoryAction.InsertItem(removed))
                                                }
                                            }
                                        },
                                        interactionSource = interactionSource
                                    )
                                    .animateItem()
                            )
                        }
                    }
                }
            }
        } else {
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
                            painterResource(R.drawable.history_huge),
                            contentDescription = null,
                            tint = colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .padding(32.dp)
                                .size(100.dp)
                        )
                    }
                    Text(
                        stringResource(R.string.noHistoryItems),
                        textAlign = TextAlign.Center,
                        style = typography.titleLarge,
                        modifier = Modifier.padding(8.dp)
                    )
                    Text(
                        stringResource(R.string.noHistoryItemsDesc),
                        textAlign = TextAlign.Center,
                        style = typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 48.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryGridCard(
    item: ViewHistoryItem,
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
            if (item.thumbnail != null) {
                FeedImage(
                    source = item.thumbnail,
                    description = item.description,
                    loadingIndicator = true,
                    background = imageBackground,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )
            }
            Text(
                item.title,
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
                    modifier = Modifier.padding(start = 10.dp, top = 2.dp, end = 10.dp, bottom = 10.dp)
                )
            }
        }
    }
}