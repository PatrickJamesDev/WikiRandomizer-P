package org.nsh07.wikireader.ui.homeScreen.search

import androidx.compose.foundation.background
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.shape.CircleShape
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.motionScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.util.fastForEach
import androidx.window.core.layout.WindowSizeClass
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.nsh07.wikireader.R
import org.nsh07.wikireader.data.SearchHistoryItem
import org.nsh07.wikireader.data.UserLanguage
import org.nsh07.wikireader.data.WikiPrefixSearchResult
import org.nsh07.wikireader.ui.homeScreen.viewModel.AppSearchBarState
import org.nsh07.wikireader.ui.homeScreen.viewModel.ArticleTextMatch
import org.nsh07.wikireader.ui.homeScreen.viewModel.HomeAction
import org.nsh07.wikireader.ui.image.FeedImage
import org.nsh07.wikireader.ui.settingsScreen.LanguageBottomSheet
import org.nsh07.wikireader.ui.settingsScreen.viewModel.PreferencesState
import org.nsh07.wikireader.ui.settingsScreen.viewModel.SettingsAction

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun AppSearchBar(
    appSearchBarState: AppSearchBarState,
    searchBarState: SearchBarState,
    preferencesState: PreferencesState,
    textFieldState: TextFieldState,
    userLangs: List<UserLanguage>,
    recentLangs: List<String>,
    searchHistory: List<SearchHistoryItem>,
    searchBarEnabled: Boolean,
    searchListState: LazyListState,
    windowSizeClass: WindowSizeClass,
    scrollBehavior: TopAppBarScrollBehavior?,
    languageSearchStr: String,
    languageSearchQuery: String,
    onAction: (HomeAction) -> Unit,
    onSettingsAction: (SettingsAction) -> Unit,
    onSearchBarExpandedChange: (Boolean) -> Unit,
    onNavigateToRandomTopics: () -> Unit = {},
    removeHistoryItem: (SearchHistoryItem) -> Unit,
    clearHistory: () -> Unit,
    onMenuIconClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = appSearchBarState.focusRequester
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val colorScheme = colorScheme
    val size = searchHistory.size
    val inArticleListState = rememberLazyListState()
    val compactWindow =
        !windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    val (showLanguageSheet, setShowLanguageSheet) = remember { mutableStateOf(false) }

    var enabled by remember { mutableStateOf(Build.VERSION.SDK_INT >= 29) }

    if (Build.VERSION.SDK_INT < 29) {
        LaunchedEffect(Unit) {
            scope.launch {
                delay(1000)
                enabled = true
            }
        }
    }

    LaunchedEffect(textFieldState.text) {
        onAction(HomeAction.LoadSearchResultsDebounced(textFieldState.text.toString()))
    }

    // The bottom reading toolbar asks for search by bumping focusRequest. Expand first:
    // focusing the field while the SearchBar is still collapsed did nothing on some devices.
    LaunchedEffect(appSearchBarState.focusRequest) {
        if (appSearchBarState.focusRequest > 0) {
            if (searchBarState.currentValue != SearchBarValue.Expanded) {
                searchBarState.animateToExpanded()
            }
            focusRequester.requestFocus()
        }
    }

    val inputField =
        @Composable {
            SearchBarDefaults.InputField(
                textFieldState = textFieldState,
                searchBarState = searchBarState,
                onSearch = {
                    onSearchBarExpandedChange(false)
                    onAction(HomeAction.LoadSearch(it))
                },
                placeholder = {
                    val alignment by animateHorizontalAlignmentAsState(
                        if (searchBarState.targetValue == SearchBarValue.Expanded) -1f else 0f,
                        animationSpec = motionScheme.defaultSpatialSpec()
                    )
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
                        Text(stringResource(R.string.searchWikipedia), style = typography.bodyLarge)
                    }
                },
                trailingIcon =
                    if (textFieldState.text.isNotEmpty()) {
                        {
                            IconButton(
                                shapes = IconButtonDefaults.shapes(),
                                onClick = {
                                    onAction(HomeAction.SetQuery(""))
                                    focusRequester.requestFocus()
                                }
                            ) {
                                Icon(
                                    painterResource(R.drawable.clear),
                                    contentDescription = stringResource(R.string.clearSearchField)
                                )
                            }
                        }
                    } else null,
                enabled = searchBarEnabled && enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        }

    val textStyle = LocalTextStyle.current
    TopAppBar(
        title = {
            CompositionLocalProvider(
                LocalTextStyle provides textStyle
            ) {
                BoxWithConstraints {
                    SearchBar(
                        state = searchBarState,
                        inputField = inputField,
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .padding(
                                top = TopAppBarDefaults.windowInsets.asPaddingValues()
                                    .calculateTopPadding()
                            )
                            .width(max(320.dp, this@BoxWithConstraints.maxWidth / 2))
                    )
                }
            }
        },
        subtitle = {},
        navigationIcon = {
            AnimatedContent(
                searchBarState.targetValue == SearchBarValue.Collapsed &&
                        compactWindow,
                modifier = Modifier.padding(
                    top = TopAppBarDefaults.windowInsets.asPaddingValues()
                        .calculateTopPadding(),
                    start = 4.dp,
                    end = 4.dp
                )
            ) { currentValue ->
                when (currentValue) {
                    true ->
                        IconButton(
                            shapes = IconButtonDefaults.shapes(),
                            onClick = onMenuIconClicked
                        ) {
                            Icon(
                                painterResource(R.drawable.menu),
                                contentDescription = stringResource(R.string.moreOptions)
                            )
                        }

                    else ->
                        IconButton(
                            shapes = IconButtonDefaults.shapes(),
                            onClick = {
                                if (searchBarState.currentValue == SearchBarValue.Expanded) {
                                    onSearchBarExpandedChange(false)
                                    onAction(HomeAction.LoadSearch(textFieldState.text.toString()))
                                }
                                else
                                    focusRequester.requestFocus()
                            }
                        ) {
                            Icon(
                                painterResource(R.drawable.search),
                                contentDescription = null
                            )
                        }
                }
            }
        },
        actions = {
                val topicsLabel = stringResource(R.string.randomTopicsTitle)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(
                        top = TopAppBarDefaults.windowInsets.asPaddingValues()
                            .calculateTopPadding(),
                        start = 4.dp,
                        end = 4.dp
                    )
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colorScheme.secondaryContainer)
                    .combinedClickable(
                        role = Role.Button,
                        onClick = { onAction(HomeAction.LoadRandom) },
                        onLongClick = { onNavigateToRandomTopics() },
                        onLongClickLabel = topicsLabel
                    )
            ) {
                Icon(
                    painterResource(R.drawable.shuffle),
                    contentDescription = stringResource(R.string.randomArticle),
                    tint = colorScheme.onSecondaryContainer
                )
            }
        },
        scrollBehavior = scrollBehavior,
        windowInsets =
            if (!compactWindow)
                TopAppBarDefaults.windowInsets
                    .only(WindowInsetsSides.Bottom + WindowInsetsSides.End)
            else
                TopAppBarDefaults.windowInsets
                    .only(WindowInsetsSides.Bottom + WindowInsetsSides.End + WindowInsetsSides.Start),
        titleHorizontalAlignment = Alignment.CenterHorizontally,
        colors = TopAppBarDefaults.topAppBarColors(scrolledContainerColor = colorScheme.surface),
        modifier = modifier
    )

    ExpandedFullScreenSearchBar(
        state = searchBarState,
        inputField = inputField
    ) {
        if (showLanguageSheet)
            LanguageBottomSheet(
                lang = preferencesState.lang,
                recentLangs = recentLangs,
                searchStr = languageSearchStr,
                searchQuery = languageSearchQuery,
                setShowSheet = setShowLanguageSheet,
                setLang = {
                    onSettingsAction(SettingsAction.SaveLang(it))
                    onAction(HomeAction.LoadSearchResultsDebounced(textFieldState.text.toString()))
                },
                setSearchStr = { onAction(HomeAction.UpdateLanguageSearchStr(it)) },
                userLanguageSelectionMode = true,
                insertUserLanguage = { onAction(HomeAction.InsertUserLanguage(it)) },
                deleteUserLanguage = { onAction(HomeAction.DeleteUserLanguage(it)) }
            )

        if (Build.VERSION.SDK_INT < 29) { // Workaround to fix the infinite search bar loop bug
            BackHandler(true) {
                scope.launch {
                    enabled = false
                    searchBarState.animateToCollapsed()
                    delay(1000)
                    enabled = true
                }
            }
        } else {
            // SearchBar does not install its own back handler on modern Android. Without this,
            // Back was swallowed by the expanded overlay and the only way out was the tiny
            // clear button, which made search feel stuck.
            BackHandler(enabled = searchBarState.targetValue == SearchBarValue.Expanded) {
                onSearchBarExpandedChange(false)
            }
        }

        AnimatedContent(textFieldState.text.trim().isEmpty()) { targetState ->
            when (targetState) {
                true ->
                    if (preferencesState.searchHistory) {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 32.dp)
                                ) {
                                    Text(
                                        stringResource(R.string.history),
                                        style = typography.labelLarge
                                    )
                                    Spacer(Modifier.weight(1f))
                                    TextButton(
                                        shapes = ButtonDefaults.shapes(),
                                        onClick = clearHistory,
                                        enabled = size > 0
                                    ) {
                                        Text(stringResource(R.string.clear))
                                    }
                                }
                            }
                            items(size, key = { searchHistory[it].time }) {
                                SearchHistoryListItem(
                                    index = it,
                                    items = size,
                                    currentItem = searchHistory[it],
                                    haptic = haptic,
                                    onAction = onAction,
                                    removeHistoryItem = removeHistoryItem,
                                    onClick = {
                                        textFieldState.setTextAndPlaceCursorAtEnd(searchHistory[it].query)
                                        onSearchBarExpandedChange(false)
                                        onAction(HomeAction.LoadSearch(searchHistory[it].query))
                                    }
                                )
                            }
                            item {
                                Spacer(
                                    Modifier.height(
                                        WindowInsets.systemBars.asPaddingValues()
                                            .calculateBottomPadding() + 152.dp
                                    )
                                )
                            }
                        }
                    }

                else -> {
                    var tabIndex by rememberSaveable { mutableStateOf(0) }
                    Column(Modifier.fillMaxSize()) {
                        PrimaryTabRow(selectedTabIndex = tabIndex) {
                            Tab(
                                selected = tabIndex == 0,
                                onClick = { tabIndex = 0 },
                                text = { Text(stringResource(R.string.searchTabArticles)) }
                            )
                            Tab(
                                selected = tabIndex == 1,
                                onClick = { tabIndex = 1 },
                                text = { Text(stringResource(R.string.searchTabInArticle)) }
                            )
                        }
                        if (tabIndex == 0) {
                            ArticlesSearchResults(
                                appSearchBarState = appSearchBarState,
                                preferencesState = preferencesState,
                                userLangs = userLangs,
                                searchListState = searchListState,
                                textFieldState = textFieldState,
                                setShowLanguageSheet = setShowLanguageSheet,
                                onSearchBarExpandedChange = onSearchBarExpandedChange,
                                onAction = onAction
                            )
                        } else {
                            InArticleSearchResults(
                                matches = appSearchBarState.articleMatches,
                                hasCurrentArticle = appSearchBarState.hasCurrentArticle,
                                listState = inArticleListState,
                                onSearchBarExpandedChange = onSearchBarExpandedChange,
                                onAction = onAction
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArticlesSearchResults(
    appSearchBarState: AppSearchBarState,
    preferencesState: PreferencesState,
    userLangs: List<UserLanguage>,
    searchListState: LazyListState,
    textFieldState: TextFieldState,
    setShowLanguageSheet: (Boolean) -> Unit,
    onSearchBarExpandedChange: (Boolean) -> Unit,
    onAction: (HomeAction) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        FlowRow(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            userLangs.fastForEach { filterOption ->
                val filterChipInteractionSource = remember { MutableInteractionSource() }
                Box {
                    FilterChip(
                        selected = filterOption.selected,
                        onClick = {},
                        label = { Text(filterOption.langName) },
                        leadingIcon =
                            if (filterOption.selected) {
                                {
                                    Icon(
                                        painterResource(R.drawable.check),
                                        contentDescription = null
                                    )
                                }
                            } else null,
                        interactionSource = filterChipInteractionSource
                    )
                    Box( // Workaround to enable long-press on a FilterChip
                        modifier = Modifier
                            .matchParentSize()
                            .combinedClickable(
                                onClick = {
                                    scope.launch {
                                        onAction(
                                            HomeAction.MarkUserLanguageSelected(
                                                filterOption.lang
                                            )
                                        )
                                        haptic.performHapticFeedback(
                                            HapticFeedbackType.ToggleOn
                                        )
                                        onAction(
                                            HomeAction.LoadSearchResultsDebounced(
                                                textFieldState.text.toString()
                                            )
                                        )
                                    }
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(
                                        HapticFeedbackType.LongPress
                                    )
                                    if (userLangs.size > 1)
                                        onAction(
                                            HomeAction.DeleteUserLanguage(
                                                filterOption.lang
                                            )
                                        )
                                },
                                interactionSource = filterChipInteractionSource,
                                indication = null,
                            )
                    )
                }
            }
            InputChip(
                onClick = { setShowLanguageSheet(true) },
                label = {},
                leadingIcon = {
                    Icon(painterResource(R.drawable.add), "Add language")
                },
                selected = false,
                modifier = Modifier.width(40.dp)
            )
        }

        LazyColumn(
            state = searchListState,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // The user's own history and saved articles are the fastest way back to
            // something they already read, so they come first.
            item(key = "hdr-local") {
                SearchSectionHeader(stringResource(R.string.historyAndSaved))
            }
            if (appSearchBarState.localMatches.isEmpty()) {
                item(key = "empty-local") {
                    Text(
                        text = stringResource(R.string.noLocalMatches),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        style = typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            } else {
                itemsIndexed(
                    appSearchBarState.localMatches,
                    key = { _, match -> "local-${match.lang}-${match.title}-${match.saved}" }
                ) { index, match ->
                    LocalSearchResultListItem(
                        item = match,
                        index = index,
                        items = appSearchBarState.localMatches.size,
                        dataSaver = preferencesState.dataSaver,
                        imageBackground = preferencesState.imageBackground,
                        onSearchBarExpandedChange = onSearchBarExpandedChange,
                        onAction = onAction
                    )
                }
            }

            item(key = "hdr-titles") {
                SearchSectionHeader(stringResource(R.string.titleMatches))
            }
            val titles = appSearchBarState.prefixSearchResults
            if (titles.isNullOrEmpty()) {
                item(key = "empty-titles") {
                    Text(
                        text = stringResource(R.string.noTitleMatches),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        style = typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            } else {
                itemsIndexed(
                    titles,
                    key = { _, it: WikiPrefixSearchResult -> "prefix-${it.title}" }
                ) { index: Int, it: WikiPrefixSearchResult ->
                    PrefixSearchResultListItem(
                        item = it,
                        dataSaver = preferencesState.dataSaver,
                        imageBackground = preferencesState.imageBackground,
                        onSearchBarExpandedChange = onSearchBarExpandedChange,
                        onAction = onAction,
                        items = titles.size,
                        index = index
                    )
                }
            }

            item(key = "bottom-spacer") {
                Spacer(
                    Modifier.height(
                        WindowInsets.systemBars.asPaddingValues()
                            .calculateBottomPadding() + 152.dp
                    )
                )
            }
        }
    }
}

@Composable
private fun InArticleSearchResults(
    matches: List<ArticleTextMatch>,
    hasCurrentArticle: Boolean,
    listState: LazyListState,
    onSearchBarExpandedChange: (Boolean) -> Unit,
    onAction: (HomeAction) -> Unit
) {
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (matches.isEmpty()) {
            item {
                Text(
                    text = stringResource(
                        if (hasCurrentArticle) R.string.noInArticleMatches
                        else R.string.inArticleSearchHint
                    ),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    style = typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant
                )
            }
        } else {
            itemsIndexed(
                matches,
                key = { index, match -> "in-${match.sectionIndex}-${match.sectionTitle}-$index" }
            ) { _, match ->
                ArticleMatchListItem(
                    match = match,
                    onSearchBarExpandedChange = onSearchBarExpandedChange,
                    onAction = onAction
                )
            }
        }
        item {
            Spacer(
                Modifier.height(
                    WindowInsets.systemBars.asPaddingValues()
                        .calculateBottomPadding() + 152.dp
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArticleMatchListItem(
    match: ArticleTextMatch,
    onSearchBarExpandedChange: (Boolean) -> Unit,
    onAction: (HomeAction) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    ListItem(
        headlineContent = {
            Text(
                text = match.sectionTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = match.snippet,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        },
        colors = ListItemDefaults.colors(containerColor = SearchBarDefaults.colors().containerColor),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shapes.large)
            .clickable {
                // Drop IME focus first: an expanded SearchBar whose text field still owns focus
                // can immediately re-expand after the collapse animation, leaving the result
                // overlay on screen even though the article already scrolled.
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
                onSearchBarExpandedChange(false)
                onAction(HomeAction.ScrollToSection(match.sectionIndex))
            }
    )
}

@Composable
private fun animateHorizontalAlignmentAsState(
    targetBiasValue: Float,
    animationSpec: AnimationSpec<Float>
): State<BiasAlignment.Horizontal> {
    val bias by animateFloatAsState(targetBiasValue, animationSpec)
    return remember { derivedStateOf { BiasAlignment.Horizontal(bias) } }
}
