package com.wikirandomizerp.app.ui.homeScreen

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarDefaults.vibrantFloatingToolbarColors
import androidx.compose.material3.FloatingToolbarScrollBehavior
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.motionScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.core.text.parseAsHtml
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEvent
import androidx.window.core.layout.WindowSizeClass
import kotlinx.coroutines.launch
import com.wikirandomizerp.app.R
import com.wikirandomizerp.app.data.DebugLog
import com.wikirandomizerp.app.data.SavedStatus
import com.wikirandomizerp.app.data.WikiPhoto
import org.nsh07.wikireader.shared.WikiUrls
import com.wikirandomizerp.app.ui.homeScreen.viewModel.HomeAction
import com.wikirandomizerp.app.ui.homeScreen.viewModel.HomeScreenState
import com.wikirandomizerp.app.ui.homeScreen.viewModel.HomeSubscreen
import com.wikirandomizerp.app.ui.homeScreen.viewModel.articleVoteKey
import com.wikirandomizerp.app.ui.image.FullScreenArticleImage
import com.wikirandomizerp.app.ui.image.FullScreenGallery
import com.wikirandomizerp.app.ui.image.FullScreenImage
import com.wikirandomizerp.app.ui.settingsScreen.LanguageBottomSheet
import com.wikirandomizerp.app.ui.settingsScreen.viewModel.PreferencesState
import com.wikirandomizerp.app.ui.settingsScreen.viewModel.SettingsAction
import com.wikirandomizerp.app.ui.shimmer.AnimatedShimmer
import com.wikirandomizerp.app.ui.shimmer.FeedLoader

/**
 * The main composable function for the app's home screen.
 *
 * @param backStack The navigation back stack for the home screen, managing [HomeSubscreen]s.
 * @param homeScreenState The current state of the home screen, containing article data and UI status.
 * @param preferencesState The current state of user preferences.
 * @param recentLangs A list of recently used language codes.
 * @param floatingToolbarScrollBehaviour The [FloatingToolbarScrollBehavior] for the floating action toolbar.
 * @param languageSearchStr The current search string for languages in the language bottom sheet.
 * @param languageSearchQuery The current search query for languages after debouncing.
 * @param showLanguageSheet A boolean indicating whether the language selection bottom sheet should be shown.
 * @param deepLinkHandled A boolean indicating if a deep link has been processed for the initial feed load.
 * @param setShowArticleLanguageSheet A lambda function to control the visibility of the article language bottom sheet.
 * @param onAction A lambda function to dispatch [HomeAction] events to the ViewModel.
 * @param onSettingsAction A lambda function to dispatch [SettingsAction] events to the SettingsViewModel.
 * @param insets The [PaddingValues] for handling system window insets.
 * This is used to adjust UI elements to avoid overlapping with system bars.
 * @param windowSizeClass The [WindowSizeClass] for adapting the layout to different screen sizes.
 * @param modifier The [Modifier] to be applied to the root container of the home screen.
 */
@OptIn(
    ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalSharedTransitionApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class
)
@Composable
fun AppHomeScreen(
    backStack: SnapshotStateList<HomeSubscreen>,
    homeScreenState: HomeScreenState,
    preferencesState: PreferencesState,
    recentLangs: List<String>,
    floatingToolbarScrollBehaviour: FloatingToolbarScrollBehavior?,
    languageSearchStr: String,
    languageSearchQuery: String,
    showLanguageSheet: Boolean,
    deepLinkHandled: Boolean,
    setShowArticleLanguageSheet: (Boolean) -> Unit,
    onAction: (HomeAction) -> Unit,
    onSettingsAction: (SettingsAction) -> Unit,
    onNavigateToRandomTopics: () -> Unit = {},
    insets: PaddingValues,
    windowSizeClass: WindowSizeClass,
    modifier: Modifier = Modifier,
    relatedLoader: suspend (String, String) -> List<com.wikirandomizerp.app.data.ArticleRow> =
        { _, _ -> emptyList() },
    previewLoader: suspend (String, String) -> com.wikirandomizerp.app.data.LinkPreview? =
        { _, _ -> null }
) {
    val clipboard = LocalClipboard.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val motionScheme = motionScheme

    // Use lightweight stable keys instead of the full Article data class (which contains
    // List<List<AnnotatedString>> and forces a deep equality walk on every remember check).
    val lastArticleTitle = (backStack.lastOrNull() as? HomeSubscreen.Article)?.title ?: ""
    val lastArticleLang = (backStack.lastOrNull() as? HomeSubscreen.Article)?.currentLang ?: preferencesState.lang
    var showSaveDialog by remember { mutableStateOf(false) }
    val hasArticleScreen = backStack.lastOrNull() is HomeSubscreen.Article
    LaunchedEffect(hasArticleScreen) {
        if (!hasArticleScreen) showSaveDialog = false
    }
    // Never let the save dialog drift onto a different article opened behind it.
    LaunchedEffect(lastArticleTitle, lastArticleLang) {
        showSaveDialog = false
    }

    // Restore the saved like/dislike state whenever a different article is shown.
    LaunchedEffect(lastArticleTitle, lastArticleLang) {
        if (lastArticleTitle.isNotEmpty()) {
            onAction(HomeAction.LoadArticleVote(lastArticleTitle, lastArticleLang))
        }
    }

    val sendIntent: Intent = remember(lastArticleTitle, lastArticleLang, preferencesState.lang) {
        Intent()
            .apply {
                val article = backStack.lastOrNull() as? HomeSubscreen.Article
                action = Intent.ACTION_SEND
                putExtra(
                    Intent.EXTRA_TEXT,
                    if (article != null) WikiUrls.articleUrl(lastArticleLang, article.title)
                    else WikiUrls.mainPageUrl(preferencesState.lang)
                )
                type = "text/plain"
            }
    }
    val shareIntent = remember(lastArticleTitle, lastArticleLang) {
        Intent.createChooser(sendIntent, null)
    }

    val context = LocalContext.current
    val systemBars = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()

    if (homeScreenState.showRef && backStack.last() is HomeSubscreen.Article) // Reference bottom sheet
        ModalBottomSheet(
            onDismissRequest = { onAction(HomeAction.HideRef) },
        ) {
            val content = homeScreenState.ref
            SelectionContainer {
                Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.reference),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.weight(1f))
                        IconButton(
                            onClick = {
                                scope.launch {
                                    clipboard.setClipEntry(
                                        ClipEntry(
                                            ClipData.newPlainText(
                                                content,
                                                content
                                            )
                                        )
                                    )
                                }
                            },
                            shapes = IconButtonDefaults.shapes()
                        ) {
                            Icon(
                                painterResource(R.drawable.copy),
                                contentDescription = "Copy reference text"
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .clip(shapes.large)
                            .background(colorScheme.surface)
                            .fillMaxWidth()
                    ) {
                        Text(
                            content, Modifier
                                .padding(16.dp)
                                .fillMaxWidth()
                        )
                    }
                }
            }
        }

    // Back handling for the full-screen image pages.
    // `backStack::removeLastOrNull` popped WHATEVER was on top: a second quick tap on the back
    // arrow (the page is still fading out) removed the article/feed underneath and could empty the
    // stack, which makes NavDisplay throw. Only an image is ever popped here, and never the root.
    val popImage: () -> Unit = remember(backStack) {
        {
            if (backStack.size > 1 && backStack.last() is HomeSubscreen.Image)
                backStack.removeAt(backStack.lastIndex)
        }
    }
    // Two quick taps on a picture used to push two identical entries -> duplicate navigation key.
    val pushImage: (HomeSubscreen.Image) -> Unit = remember(backStack) {
        { image ->
            if (backStack.lastOrNull() !is HomeSubscreen.Image) backStack.add(image)
        }
    }

    // Always created, and the counts are read defensively. They used to be conditional and cast
    // `backStack[0] as Feed` inside the count lambdas: when a failed pull-to-refresh replaced the
    // feed with the logo, the fading-out feed still measured its pager -> ClassCastException /
    // NullPointerException (`pagerState!!`) -> crash.
    val pagerState = rememberPagerState {
        ((backStack.getOrNull(0) as? HomeSubscreen.Feed)?.mostReadArticles?.size ?: 0) / 5
    }
    val newsCarouselState = rememberCarouselState(0) {
        (backStack.getOrNull(0) as? HomeSubscreen.Feed)?.news?.size ?: 0
    }
    val otdCarouselState = rememberCarouselState(0) {
        (backStack.getOrNull(0) as? HomeSubscreen.Feed)?.onThisDay?.size ?: 0
    }

    // Navigation 3 composes every entry in the back stack. When two rapid random loads add the
    // same content key twice, SaveableStateHolder throws far from the actual tap. This line makes
    // the back stack, its content keys and the scene identities visible in the debug log.
    val navKeys = backStack.map { entry ->
        when (entry) {
            is HomeSubscreen.Article -> "article-${entry.uid}"
            is HomeSubscreen.Image.FullScreenImage -> "image-${entry.uid}"
            is HomeSubscreen.Image.FullScreenArticleImage -> "image-${entry.uid}"
            is HomeSubscreen.Image.FullScreenGallery -> "image-${entry.uid}"
            is HomeSubscreen.Feed -> "feed"
            is HomeSubscreen.FeedLoader -> "feed-loader"
            is HomeSubscreen.Logo -> "logo"
        }
    }
    LaunchedEffect(navKeys) {
        DebugLog.i(
            "Navigation",
            "back stack (${backStack.size}) keys=[${navKeys.joinToString(", ")}] " +
                    "scenes=[${backStack.joinToString(" > ") { entry ->
                        when (entry) {
                            is HomeSubscreen.Article -> "Article('${entry.title}', uid=${entry.uid}, ${entry.extract.size} sections)"
                            is HomeSubscreen.Image.FullScreenImage -> "Image('${entry.title}', uid=${entry.uid})"
                            is HomeSubscreen.Image.FullScreenArticleImage -> "ArticleImage(uid=${entry.uid})"
                            is HomeSubscreen.Image.FullScreenGallery -> "Gallery(${entry.items.size} images, start=${entry.startIndex}, uid=${entry.uid})"
                            is HomeSubscreen.Feed -> "Feed"
                            is HomeSubscreen.FeedLoader -> "FeedLoader"
                            is HomeSubscreen.Logo -> "Logo"
                        }
                    }}]"
        )
    }

    Box(modifier = modifier) { // The container for all the composables in the home screen
        SharedTransitionLayout {
            NavDisplay(
                backStack = backStack,
                onBack = {
                    // Articles have their own unlimited history. System back (and predictive back)
                    // first walks that list; only when there is nothing older does it pop the
                    // rendered stack back to the feed/previous screen.
                    if (backStack.lastOrNull() is HomeSubscreen.Article &&
                        homeScreenState.canGoBack
                    ) {
                        onAction(HomeAction.NavigateBack)
                    } else {
                        onAction(HomeAction.StopAll)
                        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                    }
                },
                transitionSpec = { fadeIn().togetherWith(fadeOut()) },
                popTransitionSpec = { fadeIn().togetherWith(fadeOut()) },
                predictivePopTransitionSpec = { edge ->
                    if (backStack.size > 2 && backStack.last() !is HomeSubscreen.Image)
                        if (edge == NavigationEvent.EDGE_LEFT) {
                            (slideInHorizontally(initialOffsetX = { -it / 4 }) + fadeIn())
                                .togetherWith(slideOutHorizontally(targetOffsetX = { it }))
                        } else {
                            (slideInHorizontally(initialOffsetX = { it / 4 }) + fadeIn())
                                .togetherWith(slideOutHorizontally(targetOffsetX = { -it }))
                        }
                    else fadeIn().togetherWith(fadeOut())
                },
                entryProvider = entryProvider {
                    entry<HomeSubscreen.Logo> {
                        Icon(
                            painterResource(R.drawable.ic_launcher_custom_monochrome),
                            contentDescription = null,
                            modifier = modifier
                                .size(400.dp)
                                .align(Alignment.Center)
                        )
                    }

                    entry<HomeSubscreen.FeedLoader> {
                        FeedShimmer(insets = insets)
                    }

                    // Stable content key: the feed is rebuilt (copy) when its sections load, and the
                    // default key (toString of the whole object) would tear the screen down each time.
                    entry<HomeSubscreen.Feed>(clazzContentKey = { "feed" }) { entry ->
                        if (showLanguageSheet)
                            LanguageBottomSheet(
                                recentLangs = recentLangs,
                                lang = preferencesState.lang,
                                searchStr = languageSearchStr,
                                searchQuery = languageSearchQuery,
                                setShowSheet = setShowArticleLanguageSheet,
                                setLang = {
                                    // The Home view model observes the language preference and
                                    // reloads the feed in the new language.
                                    onSettingsAction(SettingsAction.SaveLang(it))
                                },
                                setSearchStr = { onAction(HomeAction.UpdateLanguageSearchStr(it)) }
                            )
                        ArticleFeed(
                            feedContent = entry,
                            pagerState = pagerState,
                            newsCarouselState = newsCarouselState,
                            otdCarouselState = otdCarouselState,
                            insets = insets,
                            loadPage = { title, lang -> onAction(HomeAction.LoadPage(title, lang = lang)) },
                            refreshFeed = { onAction(HomeAction.LoadFeed()) },
                            onImageClick = {
                                pushImage(
                                    HomeSubscreen.Image.FullScreenImage(
                                        photo = WikiPhoto(
                                            source = entry.image?.thumbnail?.source ?: "",
                                            width = entry.image?.thumbnail?.width ?: 1,
                                            height = entry.image?.thumbnail?.height ?: 1
                                        ),
                                        photoDesc = entry.image?.description?.text?.parseAsHtml()
                                            ?.toString().orEmpty(),
                                        title = entry.image?.title ?: "",
                                        link = entry.image?.filePage,
                                        background = preferencesState.imageBackground,
                                        onBack = popImage
                                    )
                                )
                            },
                            windowSizeClass = windowSizeClass,
                            sharedScope = this@SharedTransitionLayout,
                            imageBackground = preferencesState.imageBackground
                        )
                    }

                    // IMPORTANT: give every article a small, stable, Bundle-saveable content key.
                    // The default is key.toString(), i.e. the ENTIRE article text, which changes on
                    // every progressive-parse update and save-state change. The navigation layer
                    // then treats each update as a brand-new screen. During rapid random navigation
                    // that recreated scenes while old ones were leaving, and SaveableStateHolder
                    // threw "Key ... was used multiple times" / the scene animation crashed.
                    // The uid is assigned once per load and never changes, so the scene and the
                    // SaveableStateProvider stay stable while the article content updates in place.
                    entry<HomeSubscreen.Article>(clazzContentKey = { "article-${it.uid}" }) { entry ->
                        PageContent(
                            content = entry,
                            sharedScope = this@SharedTransitionLayout,
                            preferencesState = preferencesState,
                            insets = insets,
                            showLanguageSheet = showLanguageSheet,
                            recentLangs = recentLangs,
                            languageSearchStr = languageSearchStr,
                            languageSearchQuery = languageSearchQuery,
                            setShowArticleLanguageSheet = setShowArticleLanguageSheet,
                            onImageClick = {
                                pushImage(
                                    HomeSubscreen.Image.FullScreenImage(
                                        photo = entry.photo,
                                        photoDesc = entry.photoDesc,
                                        title = entry.title,
                                        background = preferencesState.imageBackground,
                                        link = entry.photo?.source,
                                        onBack = popImage
                                    )
                                )
                            },
                            onReadingProgress = { onAction(HomeAction.ReadingProgress(it)) },
                            onSaveHighlight = { onAction(HomeAction.SaveHighlight(it)) },
                            highlights = entry.highlights,
                            onSearchInBrowser = { text ->
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse(
                                                "https://www.google.com/search?q=" + Uri.encode(text)
                                            )
                                        )
                                    )
                                }
                            },
                            onOpenGallery = { items, startIndex ->
                                pushImage(
                                    HomeSubscreen.Image.FullScreenGallery(
                                        items = items,
                                        startIndex = startIndex,
                                        background = preferencesState.imageBackground,
                                        onBack = popImage
                                    )
                                )
                            },
                            setSearchStr = { onAction(HomeAction.UpdateLanguageSearchStr(it)) },
                            onAction = onAction,
                            relatedLoader = relatedLoader,
                            previewLoader = previewLoader,
                            modifier = Modifier.background(colorScheme.surface)
                        )
                    }

                    entry<HomeSubscreen.Image.FullScreenImage>(clazzContentKey = { "image-${it.uid}" }) {
                        FullScreenImage(
                            photo = it.photo,
                            photoDesc = it.photoDesc,
                            sharedScope = this@SharedTransitionLayout,
                            title = it.title,
                            background = it.background,
                            link = it.link,
                            onBack = it.onBack
                        )
                    }

                    entry<HomeSubscreen.Image.FullScreenGallery>(clazzContentKey = { "image-${it.uid}" }) {
                        with(this@SharedTransitionLayout) {
                            FullScreenGallery(
                                items = it.items,
                                startIndex = it.startIndex,
                                background = it.background,
                                onBack = it.onBack
                            )
                        }
                    }

                    entry<HomeSubscreen.Image.FullScreenArticleImage>(clazzContentKey = { "image-${it.uid}" }) {
                        FullScreenArticleImage(
                            uri = it.uri,
                            description = it.description,
                            sharedScope = this@SharedTransitionLayout,
                            background = it.background,
                            link = it.link,
                            onBack = it.onBack
                        )
                    }
                }
            )
        }

        AnimatedVisibility( // The linear progress bar that shows up when the article is loading
            visible = homeScreenState.isLoading,
            enter = expandVertically(expandFrom = Alignment.Top),
            exit = shrinkVertically(shrinkTowards = Alignment.Top),
            modifier = Modifier.padding(top = (max(systemBars, insets.calculateTopPadding())))
        ) {
            if (homeScreenState.loadingProgress == null)
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                )
            else {
                val animatedProgress by animateFloatAsState(
                    targetValue = homeScreenState.loadingProgress,
                    animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec
                )
                LinearProgressIndicator(
                    { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                )
            }
        }

        AnimatedVisibility(
            backStack.last() !is HomeSubscreen.Image,
            enter = slideInVertically(
                motionScheme.defaultSpatialSpec(),
                initialOffsetY = {
                    with(density) {
                        it + insets.calculateBottomPadding().roundToPx() + 1
                    }
                }
            ),
            exit = slideOutVertically(
                motionScheme.defaultSpatialSpec(),
                targetOffsetY = {
                    with(density) {
                        it + insets.calculateBottomPadding().roundToPx() + 1
                    }
                }
            ),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            HorizontalFloatingToolbar(
                expanded = true,
                scrollBehavior = floatingToolbarScrollBehaviour,
                colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .offset(y = -(insets.calculateBottomPadding()))
            ) {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        TooltipAnchorPosition.Above
                    ),
                    tooltip = { PlainTooltip { Text(stringResource(R.string.settingWikipediaLanguage)) } },
                    state = rememberTooltipState()
                ) {
                    IconButton(
                        onClick = { setShowArticleLanguageSheet(true) },
                        enabled = backStack.last() is HomeSubscreen.Feed ||
                                ((backStack.lastOrNull() as? HomeSubscreen.Article)?.langs?.isEmpty() == false)
                    ) {
                        Icon(
                            painterResource(R.drawable.translate),
                            stringResource(R.string.settingWikipediaLanguage)
                        )
                    }
                }

                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        TooltipAnchorPosition.Above
                    ),
                    tooltip = { PlainTooltip { Text(stringResource(R.string.sharePage)) } },
                    state = rememberTooltipState()
                ) {
                    IconButton(
                        enabled = backStack.last() is HomeSubscreen.Article,
                        // Do not remember on the whole Article: its data class hash walks every
                        // parsed AnnotatedString and showed up as jank. shareIntent is already
                        // remembered by title/language, so a plain lambda is both stable and cheap.
                        onClick = { context.startActivity(shareIntent) }
                    ) {
                        Icon(
                            painterResource(R.drawable.share),
                            contentDescription = stringResource(R.string.sharePage)
                        )
                    }
                }

                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        TooltipAnchorPosition.Above
                    ),
                    tooltip = {
                        PlainTooltip {
                            Text(
                                // Safe cast: the tooltip can be shown (long-press on the disabled
                                // button) while the home feed is on screen, where this crashed.
                                when ((backStack.lastOrNull() as? HomeSubscreen.Article)?.savedStatus) {
                                    SavedStatus.SAVED -> stringResource(R.string.deleteArticle)
                                    else -> stringResource(R.string.downloadArticle)
                                }
                            )
                        }
                    },
                    state = rememberTooltipState()
                ) {
                    FilledTonalIconToggleButton(
                        checked = (backStack.lastOrNull() as? HomeSubscreen.Article)?.savedStatus == SavedStatus.SAVED,
                        enabled = backStack.last() is HomeSubscreen.Article,
                        colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
                            containerColor = vibrantFloatingToolbarColors().toolbarContainerColor,
                            contentColor = vibrantFloatingToolbarColors().toolbarContentColor,
                            checkedContainerColor = colorScheme.surfaceContainer,
                            checkedContentColor = colorScheme.onSurface,
                            disabledContainerColor = vibrantFloatingToolbarColors().toolbarContainerColor,
                            disabledContentColor = colorScheme.onPrimaryContainer.copy(alpha = 0.38f)
                        ),
                        onCheckedChange = {
                            val article = backStack.lastOrNull() as? HomeSubscreen.Article
                            if (article != null &&
                                article.savedStatus == SavedStatus.NOT_SAVED &&
                                (preferencesState.saveFolders || preferencesState.saveNotes)
                            ) {
                                // Optional details are enabled: ask for them before saving.
                                showSaveDialog = true
                            } else if (article?.savedStatus != SavedStatus.SAVING) {
                                onAction(
                                    HomeAction.SaveArticle(
                                        preferencesState.lang,
                                        context.getString(R.string.snackbarUnableToSave),
                                        context.getString(R.string.snackbarUnableToDelete)
                                    )
                                )
                            }
                        }
                    ) {
                        AnimatedContent(
                            (backStack.lastOrNull() as? HomeSubscreen.Article)?.savedStatus
                                ?: SavedStatus.NOT_SAVED,
                            label = "saveAnimation"
                        ) { saved ->
                            when (saved) {
                                SavedStatus.SAVED ->
                                    Icon(
                                        painterResource(R.drawable.download_done),
                                        contentDescription = stringResource(R.string.deleteArticle)
                                    )

                                SavedStatus.SAVING -> LoadingIndicator()

                                else ->
                                    Icon(
                                        painterResource(R.drawable.download),
                                        contentDescription = stringResource(R.string.downloadArticle)
                                    )
                            }
                        }
                    }
                }

                // Tune the topics. These are toggles: tapping the active thumb removes the vote,
                // so an accidental tap never has to be corrected by tapping the other thumb.
                run {
                    val article = backStack.lastOrNull() as? HomeSubscreen.Article
                    val enabled = article != null && article.title != "Error"
                    val articleLang = article?.currentLang ?: preferencesState.lang
                    val vote = article?.let {
                        homeScreenState.articleVotes[articleVoteKey(articleLang, it.title)] ?: 0
                    } ?: 0
                    val toggleColors = IconButtonDefaults.filledTonalIconToggleButtonColors(
                        containerColor = vibrantFloatingToolbarColors().toolbarContainerColor,
                        contentColor = vibrantFloatingToolbarColors().toolbarContentColor,
                        checkedContainerColor = colorScheme.surfaceContainer,
                        checkedContentColor = colorScheme.onSurface,
                        disabledContainerColor = vibrantFloatingToolbarColors().toolbarContainerColor,
                        disabledContentColor = colorScheme.onPrimaryContainer.copy(alpha = 0.38f)
                    )
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                            TooltipAnchorPosition.Above
                        ),
                        tooltip = { PlainTooltip { Text(stringResource(R.string.feedbackMore)) } },
                        state = rememberTooltipState()
                    ) {
                        FilledTonalIconToggleButton(
                            checked = vote > 0,
                            enabled = enabled,
                            colors = toggleColors,
                            onCheckedChange = { checked ->
                                if (article != null) onAction(
                                    HomeAction.Rate(
                                        article.title,
                                        articleLang,
                                        if (checked) 1 else 0,
                                        context.getString(
                                            if (checked) R.string.feedbackThanksMore
                                            else R.string.feedbackVoteRemoved
                                        )
                                    )
                                )
                            }
                        ) {
                            Icon(
                                painterResource(R.drawable.thumb_up),
                                contentDescription = stringResource(R.string.feedbackMore)
                            )
                        }
                    }
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                            TooltipAnchorPosition.Above
                        ),
                        tooltip = { PlainTooltip { Text(stringResource(R.string.feedbackLess)) } },
                        state = rememberTooltipState()
                    ) {
                        FilledTonalIconToggleButton(
                            checked = vote < 0,
                            enabled = enabled,
                            colors = toggleColors,
                            onCheckedChange = { checked ->
                                if (article != null) onAction(
                                    HomeAction.Rate(
                                        article.title,
                                        articleLang,
                                        if (checked) -1 else 0,
                                        context.getString(
                                            if (checked) R.string.feedbackThanksLess
                                            else R.string.feedbackVoteRemoved
                                        )
                                    )
                                )
                            }
                        ) {
                            Icon(
                                painterResource(R.drawable.thumb_down),
                                contentDescription = stringResource(R.string.feedbackLess)
                            )
                        }
                    }
                }

                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        TooltipAnchorPosition.Above
                    ),
                    tooltip = { PlainTooltip { Text(stringResource(R.string.scroll_to_top)) } },
                    state = rememberTooltipState()
                ) {
                    IconButton(
                        onClick = { onAction(HomeAction.ScrollToTop) },
                        enabled = true
                    ) {
                        Icon(
                            painterResource(R.drawable.upward),
                            contentDescription = stringResource(R.string.scroll_to_top)
                        )
                    }
                }

            }
        }
    }

    if (showSaveDialog) {
        val article = backStack.lastOrNull() as? HomeSubscreen.Article
        if (article != null) {
            SaveArticleDialog(
                title = article.title,
                folders = homeScreenState.savedFolders,
                showFolder = preferencesState.saveFolders,
                showNote = preferencesState.saveNotes,
                onDismiss = { showSaveDialog = false },
                onSave = { folder, note ->
                    showSaveDialog = false
                    onAction(
                        HomeAction.SaveArticle(
                            preferencesState.lang,
                            context.getString(R.string.snackbarUnableToSave),
                            context.getString(R.string.snackbarUnableToDelete),
                            folder = folder,
                            note = note
                        )
                    )
                }
            )
        }
    }

    // Keyed by the kind of top entry, not the entry itself (hashing a full article on each update is wasteful)
    LaunchedEffect(backStack.last()::class) {
        if (backStack.last() is HomeSubscreen.Logo && !deepLinkHandled && preferencesState.feedEnabled)
            onAction(
                HomeAction.ShowFeedErrorSnackBar(
                    context.getString(R.string.snackbarUnableToLoadFeed)
                )
            )
    }
}

@Composable
fun FeedShimmer(
    insets: PaddingValues,
    modifier: Modifier = Modifier
) {
    AnimatedShimmer {
        FeedLoader(shimmer = it, insets = insets, modifier = modifier)
    }
}
