package com.wikirandomizerp.app.ui.homeScreen

import android.content.Intent
import android.net.Uri
import com.wikirandomizerp.app.data.ArticleRow
import com.wikirandomizerp.app.data.DebugLog
import com.wikirandomizerp.app.data.LinkPreview
import com.wikirandomizerp.app.ui.image.ArticleImageContext
import com.wikirandomizerp.app.ui.image.ImageAspectCache
import com.wikirandomizerp.app.ui.image.LocalArticleImageContext
import com.wikirandomizerp.app.ui.image.ArticleGalleryBuilder
import com.wikirandomizerp.app.ui.image.ArticleGalleryItem
import com.wikirandomizerp.app.ui.image.ImagePrefetcher
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.snapshotFlow
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.math.abs
import com.wikirandomizerp.app.R
import com.wikirandomizerp.app.data.WikiLang
import com.wikirandomizerp.app.ui.common.readingMaxWidth
import com.wikirandomizerp.app.ui.homeScreen.viewModel.HomeAction
import com.wikirandomizerp.app.ui.homeScreen.viewModel.HomeSubscreen
import com.wikirandomizerp.app.ui.image.ImageCard
import com.wikirandomizerp.app.ui.settingsScreen.viewModel.PreferencesState
import com.wikirandomizerp.app.ui.theme.LocalReadingOptions
import com.wikirandomizerp.app.ui.theme.SepiaBackground
import com.wikirandomizerp.app.ui.theme.isDark
import com.wikirandomizerp.app.ui.theme.rememberReadingFontFamily

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
    onOpenGallery: (List<com.wikirandomizerp.app.ui.image.ArticleGalleryItem>, Int) -> Unit,
    setSearchStr: (String) -> Unit,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
    onReadingProgress: (Float) -> Unit = {},
    onSaveHighlight: (String) -> Unit = {},
    onSearchInBrowser: (String) -> Unit = {},
    /** Saved passage texts for this article, passed down to tint the rendered body. */
    highlights: List<String> = emptyList(),
    /** Related articles from the topic libraries, shown before References. */
    relatedLoader: suspend (String, String) -> List<ArticleRow> = { _, _ -> emptyList() },
    /** REST summary for the long-press link preview (developer preview). */
    previewLoader: suspend (String, String) -> LinkPreview? = { _, _ -> null }
) {
    val photo = content.photo
    val photoDesc = content.photoDesc
    val fontSize = preferencesState.fontSize
    val fontFamily = rememberReadingFontFamily(preferencesState.fontStyle)
    val lang = content.currentLang ?: preferencesState.lang
    val pageId = content.pageId
    // Taxobox / "Species of ..." pages are organisms, so iNaturalist can identify them.
    val organismArticle = remember(content.uid, content.extract.size, photoDesc) {
        isOrganismArticle(content.extract, photoDesc)
    }
    val uriHandler = LocalUriHandler.current
    val appContext = LocalContext.current
    val scope = rememberCoroutineScope()
    var openingINaturalist by remember(content.uid) { mutableStateOf(false) }
    // Asks "iNaturalist app or browser?" (or uses the remembered answer) for every iNaturalist link.
    val inatOpener = rememberINaturalistOpener()
    inatOpener.Dialogs()
    val density = LocalDensity.current
    val swipeEdgePx = with(density) { 48.dp.toPx() }
    val swipeThresholdPx = with(density) { 72.dp.toPx() }

    // Resolve the taxon while the page opens, so tapping the chip hands the exact page to the
    // iNaturalist app without a visible network wait.
    LaunchedEffect(content.uid, organismArticle, preferencesState.inaturalistEnabled) {
        if (organismArticle && preferencesState.inaturalistEnabled) {
            INaturalistTaxa.resolveId(content.title)
        }
    }

    val pullToRefreshState = rememberPullToRefreshState()

    var isRefreshing by remember { mutableStateOf(false) }

    val latestPreviewLoader by rememberUpdatedState(previewLoader)
    var previewLink by remember(content.uid) { mutableStateOf<String?>(null) }
    var previewData by remember(content.uid) { mutableStateOf<LinkPreview?>(null) }
    var previewLoading by remember(content.uid) { mutableStateOf(false) }
    val handleLinkLongPress: (String) -> Unit = { link ->
        if (preferencesState.linkPreviews) {
            previewLink = link
        } else {
            onAction(HomeAction.OpenInNewTab(link, content.currentLang ?: preferencesState.lang))
        }
    }
    LaunchedEffect(previewLink) {
        val target = previewLink ?: return@LaunchedEffect
        previewLoading = true
        previewData = null
        previewData = runCatching {
            latestPreviewLoader(target, content.currentLang ?: preferencesState.lang)
        }.getOrNull()
        previewLoading = false
    }
    if (previewLink != null) {
        LinkPreviewDialog(
            preview = previewData,
            loading = previewLoading,
            onRead = {
                val target = previewLink
                previewLink = null
                previewData = null
                if (target != null) {
                    onAction(
                        HomeAction.LoadPage(
                            target,
                            lang = content.currentLang ?: preferencesState.lang
                        )
                    )
                }
            },
            onOpenTab = {
                val target = previewLink
                previewLink = null
                previewData = null
                if (target != null) {
                    onAction(
                        HomeAction.OpenInNewTab(
                            target,
                            content.currentLang ?: preferencesState.lang
                        )
                    )
                }
            },
            onDismiss = {
                previewLink = null
                previewData = null
            }
        )
    }
    val latestRelatedLoader by rememberUpdatedState(relatedLoader)
    var relatedArticles by remember(content.uid) {
        mutableStateOf<List<ArticleRow>>(emptyList())
    }
    LaunchedEffect(content.uid) {
        if (content.title.isBlank() || content.title == "Error") return@LaunchedEffect
        relatedArticles = runCatching {
            latestRelatedLoader(content.title, lang)
        }.getOrDefault(emptyList())
    }

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
        com.wikirandomizerp.app.data.TrackRecompositions("PageContent") {
            "uid=${content.uid} sections=${content.extract.size} title=${content.title.take(40)}"
        }
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
            com.wikirandomizerp.app.data.PerfMonitor.listInfo = {
                val li = content.listState.layoutInfo
                "list: ${li.visibleItemsInfo.size} items on screen [${li.visibleItemsInfo.joinToString(",") { it.key.toString().substringAfterLast('#') }}] of ${li.totalItemsCount}"
            }
            snapshotFlow { content.listState.isScrollInProgress }.collect { scrolling ->
                com.wikirandomizerp.app.data.PerfMonitor.setScrolling(scrolling)
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
        // iNaturalist community photos of the species, shown right below the Commons gallery.
        val inatGalleryLoaded = rememberINaturalistGallery(
            title = content.title,
            enabled = organismArticle && preferencesState.inaturalistGallery &&
                    !preferencesState.dataSaver && content.title.isNotBlank()
        )
        val inatGallery =
            if (referencesIndex >= 0 || extractSettled) inatGalleryLoaded else null

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
                    commons = latestCommons,
                    articleTitle = article.title,
                    articleLang = lang
                )
                val index = ArticleGalleryBuilder.indexOf(items, uri)
                if (index >= 0) latestOpenGallery(items, index)
                else {
                    // A picture the page scan does not know (e.g. one inside an infobox): show it
                    // first, the rest of the article's pictures follow.
                    latestOpenGallery(
                        listOf(
                            ArticleGalleryItem(
                                uri = uri,
                                caption = description,
                                articleTitle = article.title,
                                articleLang = lang,
                                thumbnailUrl = uri
                            )
                        ) + items,
                        0
                    )
                }
            }
        }
        // iNaturalist observations open in the same in-app viewer: no redirect to the app or
        // browser just to look at a photo. Their observation page remains available in the viewer.
        val openINatGallery: (List<INatObservation>, Int) -> Unit = remember {
            { observations, index ->
                latestOpenGallery(
                    observations.map { obs ->
                        ArticleGalleryItem(
                            uri = obs.largeUrl,
                            caption = obs.attribution?.takeIf { it.isNotBlank() }
                                ?: obs.observer,
                            pageUrl = obs.observationUrl
                        )
                    },
                    index.coerceIn(0, (observations.size - 1).coerceAtLeast(0))
                )
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

        CompositionLocalProvider(
            LocalLinkTapGuard provides linkGuard,
            LocalArticleImageContext provides ArticleImageContext(content.title, lang)
        ) {
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
                            .background(
                                if (LocalReadingOptions.current.sepia) SepiaBackground
                                else Color.Transparent
                            )
                            .then(
                                if (preferencesState.swipeGestures) Modifier.pointerInput(Unit) {
                                    // Only the centre of the article is ours: edge drags stay with
                                    // the navigation drawer (left) and the tabs panel (right).
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        if (down.position.x < swipeEdgePx ||
                                            down.position.x > size.width - swipeEdgePx
                                        ) return@awaitEachGesture
                                        var totalX = 0f
                                        var totalY = 0f
                                        var active = false
                                        val pointerId = down.id
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes
                                                .firstOrNull { it.id == pointerId } ?: break
                                            if (!change.pressed) break
                                            totalX += change.position.x - change.previousPosition.x
                                            totalY += change.position.y - change.previousPosition.y
                                            if (!active &&
                                                abs(totalX) > viewConfiguration.touchSlop &&
                                                abs(totalX) > abs(totalY)
                                            ) {
                                                active = true
                                            }
                                            if (active) change.consume()
                                        }
                                        if (active && abs(totalX) >= swipeThresholdPx) {
                                            if (totalX > 0f) onAction(HomeAction.NavigateBack)
                                            else onAction(HomeAction.NavigateForward)
                                        }
                                    }
                                } else Modifier
                            )
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
                            if (organismArticle && preferencesState.inaturalistEnabled) {
                                AssistChip(
                                    enabled = !openingINaturalist,
                                    onClick = {
                                        if (!openingINaturalist) {
                                            openingINaturalist = true
                                            scope.launch {
                                                val id = INaturalistTaxa.resolveId(content.title)
                                                openingINaturalist = false
                                                // The app opens the exact taxon page when the id is
                                                // known; otherwise the iNaturalist home screen,
                                                // never a search results page.
                                                val url = id?.let(::inaturalistTaxonUrl)
                                                    ?: "https://www.inaturalist.org/"
                                                inatOpener.open(url)
                                            }
                                        }
                                    },
                                    label = {
                                        Text(
                                            stringResource(
                                                if (openingINaturalist) R.string.openingINaturalist
                                                else R.string.viewOnINaturalist
                                            )
                                        )
                                    },
                                    leadingIcon = {
                                        if (openingINaturalist) {
                                            CircularProgressIndicator(
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        } else {
                                            Icon(
                                                painter = painterResource(R.drawable.leaf),
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    },
                                    modifier = Modifier.padding(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = 4.dp,
                                        bottom = 8.dp
                                    )
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
                            onLinkLongClick = handleLinkLongPress,
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
                        if (i == referencesIndex) {
                            if (relatedArticles.isNotEmpty()) {
                                RelatedArticlesStrip(
                                    articles = relatedArticles,
                                    onOpen = { title ->
                                        onAction(
                                            HomeAction.LoadPage(
                                                title,
                                                lang = content.currentLang
                                            )
                                        )
                                    }
                                )
                            }
                            if (commonsGallery != null)
                                CommonsCategoryGalleryStrip(
                                    gallery = commonsGallery,
                                    onImageClick = openGallery
                                )
                            if (inatGallery != null)
                                INaturalistGalleryStrip(
                                    gallery = inatGallery,
                                    opener = inatOpener,
                                    onOpenImage = openINatGallery
                                )
                        }
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
                            onLinkLongClick = handleLinkLongPress,
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
                if ((commonsGallery != null || inatGallery != null) &&
                    referencesIndex < 0 && content.extract.size > 1
                ) {
                    item(key = "$pageId.$lang#commons-gallery") {
                        Column {
                            if (relatedArticles.isNotEmpty()) {
                                RelatedArticlesStrip(
                                    articles = relatedArticles,
                                    onOpen = { title ->
                                        onAction(
                                            HomeAction.LoadPage(
                                                title,
                                                lang = content.currentLang
                                            )
                                        )
                                    }
                                )
                            }
                            if (commonsGallery != null)
                                CommonsCategoryGalleryStrip(
                                    gallery = commonsGallery,
                                    onImageClick = openGallery
                                )
                            if (inatGallery != null)
                                INaturalistGalleryStrip(
                                    gallery = inatGallery,
                                    opener = inatOpener,
                                    onOpenImage = openINatGallery
                                )
                        }
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