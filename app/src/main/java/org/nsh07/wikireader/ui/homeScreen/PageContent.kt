package org.nsh07.wikireader.ui.homeScreen

import org.nsh07.wikireader.data.DebugLog
import org.nsh07.wikireader.ui.image.ImageAspectCache
import org.nsh07.wikireader.ui.image.ArticleGalleryBuilder
import org.nsh07.wikireader.ui.image.ArticleGalleryItem
import org.nsh07.wikireader.ui.image.ImagePrefetcher
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.snapshotFlow
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.motionScheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.LoadingIndicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import org.nsh07.wikireader.data.WikiLang
import org.nsh07.wikireader.ui.common.readingMaxWidth
import org.nsh07.wikireader.ui.homeScreen.viewModel.HomeAction
import org.nsh07.wikireader.ui.homeScreen.viewModel.HomeSubscreen
import org.nsh07.wikireader.ui.image.ImageCard
import org.nsh07.wikireader.ui.settingsScreen.viewModel.PreferencesState
import org.nsh07.wikireader.ui.theme.isDark
import org.nsh07.wikireader.ui.theme.rememberReadingFontFamily

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun PageContent(
    content: HomeSubscreen.Article,
    sharedScope: SharedTransitionScope,
    preferencesState: PreferencesState,
    insets: PaddingValues,
    showLanguageSheet: Boolean,
    recentLangs: List<String>,
    languageSearchStr: String,
    languageSearchQuery: String,
    setShowArticleLanguageSheet: (Boolean) -> Unit,
    onImageClick: () -> Unit,
    /** Opens the full-screen gallery with every picture of the article, at the given position. */
    onOpenGallery: (List<org.nsh07.wikireader.ui.image.ArticleGalleryItem>, Int) -> Unit,
    setSearchStr: (String) -> Unit,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
    onReadingProgress: (Float) -> Unit = {},
    onSaveHighlight: (String) -> Unit = {},
    onSearchInBrowser: (String) -> Unit = {},
    /** Saved passage texts for this article, passed down to tint the rendered body. */
    highlights: List<String> = emptyList()
) {
    val photo = content.photo
    val photoDesc = content.photoDesc
    val fontSize = preferencesState.fontSize
    val fontFamily = rememberReadingFontFamily(preferencesState.fontStyle)
    val lang = content.currentLang ?: preferencesState.lang
    val pageId = content.pageId

    val pullToRefreshState = rememberPullToRefreshState()

    var isRefreshing by remember { mutableStateOf(false) }

    if (showLanguageSheet)
        ArticleLanguageBottomSheet(
            langs = content.langs ?: emptyList(),
            recentLangs = recentLangs,
            currentLang = WikiLang(content.currentLang ?: preferencesState.lang, content.title),
            searchStr = languageSearchStr,
            searchQuery = languageSearchQuery,
            setShowSheet = setShowArticleLanguageSheet,
            loadPage = { title, lang -> onAction(HomeAction.LoadPage(title, lang = lang)) },
            setSearchStr = setSearchStr
        )

    LaunchedEffect(isRefreshing) {
        delay(3000)
        isRefreshing = false
    } // hide refresh indicator after a delay

    // Remember how far the user scrolled (personalization is opt-in; without it the callback is
    // a no-op). One value per visible-item change, not per scroll pixel.
    LaunchedEffect(content.uid) {
        snapshotFlow {
            val total = content.listState.layoutInfo.totalItemsCount
            if (total <= 0) 0f
            else (content.listState.firstVisibleItemIndex.toFloat() / total).coerceIn(0f, 1f)
        }.collect { onReadingProgress(it) }
    }

    // Persist the exact reading place per tab (and per history entry). Debounced so ordinary
    // scrolling does not write to disk on every frame; the value only leaves after scrolling stops.
    LaunchedEffect(content.uid) {
        snapshotFlow {
            content.listState.firstVisibleItemIndex to content.listState.firstVisibleItemScrollOffset
        }
            .debounce(500)
            .collect { (index, offset) ->
                onAction(HomeAction.UpdateScrollPosition(index, offset))
            }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        state = pullToRefreshState,
        onRefresh = {
            // Pulling down jumps to a new random article (setting can switch back to reload).
            if (preferencesState.pullForRandom) onAction(HomeAction.LoadRandom)
            else onAction(HomeAction.ReloadPage(true))
            isRefreshing = true
        },
        indicator = {
            LoadingIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = insets.calculateTopPadding()),
                isRefreshing = isRefreshing,
                state = pullToRefreshState
            )
        }
    ) {
        // Load every image of the article in the background once the user starts reading (or after
        // a moment), and again as more sections finish parsing: scrolling and the gallery then show
        // pictures instantly. Skipped in data-saver mode.
        val prefetchContext = LocalContext.current
        org.nsh07.wikireader.data.TrackRecompositions("PageContent")
        val prefetched = remember(content.uid) { HashSet<String>() }
        // Performance log: what the user is looking at / doing when a slow frame is reported.
        LaunchedEffect(content.uid) {
            DebugLog.crumb("article shown '${content.title}' (${content.extract.size} sections)")
            DebugLog.d(
                "Scroll",
                "PageContent uid=${content.uid} title='${content.title}' " +
                        "listState=${System.identityHashCode(content.listState)} " +
                        "items=${content.extract.size + 3}"
            )
            org.nsh07.wikireader.data.PerfMonitor.listInfo = {
                val li = content.listState.layoutInfo
                "list: ${li.visibleItemsInfo.size} items on screen [${li.visibleItemsInfo.joinToString(",") { it.key.toString().substringAfterLast('#') }}] of ${li.totalItemsCount}"
            }
            snapshotFlow { content.listState.isScrollInProgress }.collect { scrolling ->
                org.nsh07.wikireader.data.PerfMonitor.setScrolling(scrolling)
                val ls = content.listState
                DebugLog.crumb("scroll ${if (scrolling) "start" else "stop"} '${content.title}' at item ${ls.firstVisibleItemIndex}+${ls.firstVisibleItemScrollOffset}px")
            }
        }
        // One-shot scroll request from search. This has to run here, inside the composition that
        // owns the LazyListState: animateScrollToItem needs the Compose MonotonicFrameClock, and
        // viewModelScope does not provide one. Waiting for the item count also covers a restored
        // article whose list has not been measured on the first composition.
        LaunchedEffect(content.uid, content.pendingScrollExtractIndex) {
            val extractIndex = content.pendingScrollExtractIndex ?: return@LaunchedEffect
            // LazyColumn layout: 0 = title, 1 = intro/description, then one item per heading
            // (extract[1] is the first heading, extract[3] the second, and so on).
            val itemIndex = if (extractIndex <= 0) {
                1
            } else {
                val headingIndex = if (extractIndex % 2 == 0) extractIndex - 1 else extractIndex
                2 + (headingIndex - 1) / 2
            }
            snapshotFlow { content.listState.layoutInfo.totalItemsCount }
                .first { it > itemIndex }
            DebugLog.d(
                "Scroll",
                "in-article scroll uid=${content.uid} extractIndex=$extractIndex item=$itemIndex"
            )
            content.listState.animateScrollToItem(itemIndex.coerceAtLeast(0))
            DebugLog.d(
                "Scroll",
                "in-article scroll done uid=${content.uid} first=${content.listState.firstVisibleItemIndex} " +
                        "offset=${content.listState.firstVisibleItemScrollOffset}"
            )
            onAction(HomeAction.ConsumeScrollToSection(content.uid))
        }

        // Picture sizes first, immediately: reserves each picture's real height so text does not jump
        // when pictures load. A small request (no image data), skipped in data-saver mode.
        LaunchedEffect(content.uid, content.extract.size, preferencesState.dataSaver) {
            if (!preferencesState.dataSaver) ImageAspectCache.fetchFor(prefetchContext, content.extract)
        }
        LaunchedEffect(content.uid, content.extract.size, preferencesState.dataSaver) {
            if (preferencesState.dataSaver) return@LaunchedEffect
            withTimeoutOrNull(2_500) {
                snapshotFlow {
                    content.listState.firstVisibleItemIndex > 0 || content.listState.firstVisibleItemScrollOffset > 0
                }.first { it }
            }
            delay(300)
            ImagePrefetcher.prefetch(prefetchContext, content.currentLang ?: preferencesState.lang, content.extract, prefetched)
        }

        // Long sections are composed in slices over a few frames, but only if the article opens at
        // the top: a restored scroll position inside a section needs that section at full height.
        val stageSections = remember(content.uid) {
            // Not observed: reading scroll state during composition would recompose on every scroll frame.
            Snapshot.withoutReadObservation {
                content.listState.firstVisibleItemIndex == 0 && content.listState.firstVisibleItemScrollOffset == 0
            }
        }

        // Single SelectionContainer wrapping the whole column — per-item SelectionContainers
        // disable Compose's layout-result caching for their entire subtree.
        // Links ignore touches while this article scrolls and for a moment after it stops.
        val linkGuard = remember(content.listState) {
            LinkTapGuard { content.listState.isScrollInProgress }
        }
        LaunchedEffect(linkGuard) {
            var wasScrolling = false
            snapshotFlow { content.listState.isScrollInProgress }.collect { scrolling ->
                if (wasScrolling && !scrolling) linkGuard.scrollEnded()
                wasScrolling = scrolling
            }
        }

        // Wikimedia Commons gallery for this article, shown before the References section.
        val commonsGalleryLoaded = rememberCommonsGallery(
            title = content.title,
            lang = lang,
            enabled = !preferencesState.dataSaver && content.title.isNotBlank()
        )
        val referencesIndex = remember(content.uid, content.extract.size) {
            content.extract.indices.firstOrNull { index ->
                index % 2 == 1 &&
                        isReferencesTitle(content.extract[index].joinToString("") { part -> part.text })
            } ?: -1
        }
        // Only heading slots become LazyColumn items: the even slots are extract bodies, and the
        // heading already renders its following body. The old list emitted an empty item for every
        // body slot, doubling the item count and making scroll/layout work larger than necessary.
        val sectionHeadingIndices = remember(content.extract) {
            content.extract.indices.filter { it % 2 == 1 }
        }
        // The article may still be growing section by section; without a References heading the
        // gallery waits until the section count stops changing so it does not jump around.
        var extractSettled by remember(content.uid) { mutableStateOf(false) }
        LaunchedEffect(content.uid, content.extract.size) {
            extractSettled = false
            delay(1200)
            extractSettled = true
        }
        val commonsGallery =
            if (referencesIndex >= 0 || extractSettled) commonsGalleryLoaded else null

        // ---- One gallery for the whole article -------------------------------------------------
        // Any picture tapped (lead picture, body, <gallery>, infobox, Commons strip) opens the same
        // viewer, which can swipe from the first picture of the article to the last. The list is
        // built when a picture is tapped, from what is then on the page.
        val latestContent by rememberUpdatedState(content)
        val latestCommons by rememberUpdatedState(commonsGallery)
        val latestOpenGallery by rememberUpdatedState(onOpenGallery)
        val leadSource = (content.thumbnail ?: photo)?.source
        val leadFullUrl = remember(leadSource, lang) {
            leadSource?.let { src ->
                imageNameFromUrl(src)?.let { ImagePrefetcher.fileUrl(lang, it, 1920) } ?: src
            }
        }
        val openGallery: (String, String) -> Unit = remember(lang) {
            { uri, description ->
                val article = latestContent
                val src = (article.thumbnail ?: article.photo)?.source
                val items = ArticleGalleryBuilder.build(
                    lang = lang,
                    leadSource = src,
                    leadCaption = article.title,
                    extract = article.extract,
                    commons = latestCommons
                )
                val index = ArticleGalleryBuilder.indexOf(items, uri)
                if (index >= 0) latestOpenGallery(items, index)
                else {
                    // A picture the page scan does not know (e.g. one inside an infobox): show it
                    // first, the rest of the article's pictures follow.
                    latestOpenGallery(listOf(ArticleGalleryItem(uri, description)) + items, 0)
                }
            }
        }
        val openLeadImage: () -> Unit = remember(openGallery, lang) {
            {
                val article = latestContent
                val src = (article.thumbnail ?: article.photo)?.source
                if (src == null) onImageClick()
                else {
                    val full = imageNameFromUrl(src)?.let { ImagePrefetcher.fileUrl(lang, it, 1920) } ?: src
                    openGallery(full, article.title)
                }
            }
        }

        // The first pictures are loaded at full-screen size immediately, whatever the scroll state;
        // every thumbnail of the Commons strip is cached as soon as the strip is known.
        LaunchedEffect(content.uid, content.extract.size, preferencesState.dataSaver) {
            if (preferencesState.dataSaver) return@LaunchedEffect
            ImagePrefetcher.preloadFirstFullRes(prefetchContext, lang, leadSource, content.extract, prefetched)
        }
        LaunchedEffect(commonsGalleryLoaded) {
            commonsGalleryLoaded?.let { ImagePrefetcher.preloadCommons(prefetchContext, it) }
        }

        CompositionLocalProvider(LocalLinkTapGuard provides linkGuard) {
        HighlightSelectionContainer(
            onSaveHighlight = onSaveHighlight,
            onSearchInBrowser = onSearchInBrowser,
            onDefineWord = { onAction(HomeAction.DefineWord(it)) },
            onAddVocabulary = { onAction(HomeAction.AddVocabularyWord(it)) }
        ) {
            SelectionContainer {
                // On tablets, desktop windows and unfolded foldables a full-width article is
                // uncomfortable to read. Keep the column centred at a readable width, while
                // phones still use the whole screen. This also reflows cleanly when a foldable
                // changes posture and the window size class changes under the app.
                val readingWidth = currentWindowAdaptiveInfo().windowSizeClass.readingMaxWidth()
                Box(
                    modifier = modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    LazyColumn( // The article
                        state = content.listState,
                        contentPadding = insets,
                        modifier = Modifier
                            .widthIn(max = readingWidth)
                            .fillMaxSize()
                    ) {
                item(key = content.title + "#--header--") { // Title + Image/description
                    with(sharedScope) {
                        Column {
                            Text(
                                text = content.title,
                                style = MaterialTheme.typography.displaySmallEmphasized,
                                fontFamily = fontFamily,
                                modifier = Modifier
                                    .sharedBounds(
                                        sharedContentState = rememberSharedContentState(
                                            content.title
                                        ),
                                        animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                                        zIndexInOverlay = 1f
                                    )
                                    .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                                    .animateContentSize(motionScheme.defaultSpatialSpec())
                            )
                            if (photoDesc != null) {
                                Text(
                                    text = photoDesc,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colorScheme.onSurfaceVariant,
                                    fontFamily = fontFamily,
                                    modifier = Modifier
                                        .sharedBounds(
                                            sharedContentState = rememberSharedContentState(
                                                photoDesc
                                            ),
                                            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                                            zIndexInOverlay = 1f
                                        )
                                        .padding(
                                            start = 16.dp,
                                            end = 16.dp,
                                            top = 4.dp,
                                            bottom = 16.dp
                                        )
                                        .fillMaxWidth()
                                )
                            }
                            // Show the lead image even when the article has no short description
                            // (previously such articles silently lost their picture).
                            if (photo != null || photoDesc != null) {
                                ImageCard(
                                    photo = content.thumbnail ?: photo,
                                    title = content.title,
                                    animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                                    showPhoto = !preferencesState.dataSaver,
                                    onClick = openLeadImage,
                                    downloadUrl = leadFullUrl,
                                    background = preferencesState.imageBackground,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                        }
                    }
                }
                item(key = content.title + "#--desc--") { // Main description
                    if (content.extract.isNotEmpty())
                        ParsedBodyText(
                            body = content.extract[0],
                            lang = content.currentLang ?: preferencesState.lang,
                            fontSize = fontSize,
                            fontFamily = fontFamily,
                            renderMath = preferencesState.renderMath,
                            sharedScope = sharedScope,
                            darkTheme = colorScheme.isDark(),
                            dataSaver = preferencesState.dataSaver,
                            background = preferencesState.imageBackground,
                            checkFirstImage = true,
                            onLinkClick = {
                                onAction(HomeAction.LoadPage(it, lang = content.currentLang))
                            },
                            onLinkLongClick = {
                                onAction(
                                    HomeAction.OpenInNewTab(
                                        it,
                                        content.currentLang ?: preferencesState.lang
                                    )
                                )
                            },
                            onGalleryImageClick = openGallery,
                            showRef = { onAction(HomeAction.UpdateRef(it)) },
                            // Use the same file the lead card actually draws, so the duplicate
                            // check below it also works when only a thumbnail is available.
                            pageImageUri = (content.thumbnail ?: photo)?.source,
                            boldText = preferencesState.boldArticleText,
                            highlights = highlights,
                            missingLinks = content.missingLinks,
                            staged = stageSections
                        )
                }
                items(
                    sectionHeadingIndices,
                    key = { i -> "$pageId.$lang#$i" }
                ) { i ->
                    Column {
                        // The Commons gallery sits at the end of the article, right before References.
                        if (commonsGallery != null && i == referencesIndex)
                            CommonsCategoryGalleryStrip(
                                gallery = commonsGallery,
                                onImageClick = openGallery
                            )
                        ExpandableSection(
                            title = content.extract[i],
                            body = content.extract.getOrElse(i + 1) { emptyList() },
                            lang = content.currentLang ?: preferencesState.lang,
                            fontSize = fontSize,
                            fontFamily = fontFamily,
                            sharedScope = sharedScope,
                            expanded = preferencesState.expandedSections,
                            darkTheme = colorScheme.isDark(),
                            dataSaver = preferencesState.dataSaver,
                            renderMath = preferencesState.renderMath,
                            imageBackground = preferencesState.imageBackground,
                            onLinkClick = {
                                onAction(HomeAction.LoadPage(it, lang = content.currentLang))
                            },
                            onLinkLongClick = {
                                onAction(
                                    HomeAction.OpenInNewTab(
                                        it,
                                        content.currentLang ?: preferencesState.lang
                                    )
                                )
                            },
                            onGalleryImageClick = openGallery,
                            showRef = { onAction(HomeAction.UpdateRef(it)) },
                            boldText = preferencesState.boldArticleText,
                            highlights = highlights,
                            missingLinks = content.missingLinks,
                            staged = stageSections,
                            checkFirstImage = true,
                            pageImageUri = (content.thumbnail ?: photo)?.source
                        )
                    }
                }
                // Articles without a References section: the gallery closes the article instead.
                if (commonsGallery != null && referencesIndex < 0 && content.extract.size > 1) {
                    item(key = "$pageId.$lang#commons-gallery") {
                        CommonsCategoryGalleryStrip(
                            gallery = commonsGallery,
                            onImageClick = openGallery
                        )
                    }
                }
                item {
                    Spacer(Modifier.height(156.dp))
                }
                    }
                }
            }
        }
        }
    }
}