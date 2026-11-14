package org.nsh07.wikireader.shared.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.wikirandomizerp.app.data.LanguageData
import com.wikirandomizerp.app.data.WikiSearchResult
import com.wikirandomizerp.app.data.WikiTopic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.nsh07.wikireader.shared.ArticleImage
import org.nsh07.wikireader.shared.ArticleSummary
import org.nsh07.wikireader.shared.WikiText
import org.nsh07.wikireader.shared.WikiUrls
import org.nsh07.wikireader.shared.library.CuratorFollowStore
import org.nsh07.wikireader.shared.library.LibraryEntry
import org.nsh07.wikireader.shared.library.LibraryRepository
import org.nsh07.wikireader.shared.library.SeenArticle
import org.nsh07.wikireader.shared.reader.CuratorSource
import org.nsh07.wikireader.shared.reader.FeedSource
import org.nsh07.wikireader.shared.reader.RandomArticleSource
import org.nsh07.wikireader.shared.reader.WikipediaSearchSource
import org.nsh07.wikireader.shared.reader.findTopicByCategory
import org.nsh07.wikireader.shared.settings.SettingsKeys
import org.nsh07.wikireader.shared.settings.SettingsStore
import org.nsh07.wikireader.shared.ui.article.PlatformArticleWebView
import org.nsh07.wikireader.shared.ui.curators.CuratorsScreen
import org.nsh07.wikireader.shared.ui.feed.FeedScreen
import org.nsh07.wikireader.shared.ui.search.WikipediaSearchScreen
import org.nsh07.wikireader.shared.ui.settings.AboutScreen
import org.nsh07.wikireader.shared.ui.library.LibraryScreen
import org.nsh07.wikireader.shared.ui.library.LibrarySearchScreen
import org.nsh07.wikireader.shared.ui.settings.SettingsScreen
import org.nsh07.wikireader.shared.ui.stats.StatisticsScreen
import org.nsh07.wikireader.shared.ui.topics.TopicsScreen
import org.nsh07.wikireader.shared.ui.settings.THEME_DARK
import org.nsh07.wikireader.shared.ui.settings.THEME_LIGHT
import org.nsh07.wikireader.shared.ui.settings.THEME_SYSTEM
import org.nsh07.wikireader.shared.ui.theme.WikiReaderTheme

private enum class ReaderDestination {
    READER, FEED, TOPICS, SAVED, HISTORY, SEARCH, WIKI_SEARCH, CURATORS, STATS, SETTINGS, ABOUT
}

private const val RANDOM_UNSEEN_ATTEMPTS = 8

private data class OpenArticle(
    val language: String,
    val summary: ArticleSummary
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderApp(
    source: RandomArticleSource,
    searchSource: WikipediaSearchSource,
    curatorSource: CuratorSource,
    feedSource: FeedSource,
    settingsStore: SettingsStore,
    modifier: Modifier = Modifier
) {
    var language by remember {
        mutableStateOf(settingsStore.getString(SettingsKeys.LANGUAGE, "en"))
    }
    var themeMode by remember {
        mutableStateOf(settingsStore.getString(SettingsKeys.THEME, THEME_SYSTEM))
    }
    val darkTheme = when (themeMode) {
        THEME_DARK -> true
        THEME_LIGHT -> false
        else -> isSystemInDarkTheme()
    }

    WikiReaderTheme(darkTheme = darkTheme) {
        val scope = rememberCoroutineScope()
        val uriHandler = LocalUriHandler.current
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        var destination by remember { mutableStateOf(ReaderDestination.READER) }
        var article by remember { mutableStateOf<ArticleSummary?>(null) }
        var isLoading by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var requestId by remember { mutableIntStateOf(0) }
        var showLanguagePicker by remember { mutableStateOf(false) }
        val library = remember(settingsStore) { LibraryRepository(settingsStore) }
        val curatorFollows = remember(settingsStore) { CuratorFollowStore(settingsStore) }
        var savedEntries by remember { mutableStateOf(library.saved()) }
        var historyEntries by remember { mutableStateOf(library.history()) }
        var seenArticles by remember { mutableStateOf(library.seenArticles()) }
        var followedCurators by remember { mutableStateOf(curatorFollows.followed()) }
        var browsingHistory by remember {
            mutableStateOf(
                settingsStore.getString(SettingsKeys.BROWSING_HISTORY, "true").toBoolean()
            )
        }
        var openArticle by remember { mutableStateOf<OpenArticle?>(null) }
        var librarySearchQuery by remember { mutableStateOf("") }
        var recentSearches by remember { mutableStateOf(library.recentSearches()) }
        var wikiSearchQuery by remember { mutableStateOf("") }
        var wikiSearchResults by remember { mutableStateOf<List<WikiSearchResult>>(emptyList()) }
        var wikiSearchLoading by remember { mutableStateOf(false) }
        var wikiSearchError by remember { mutableStateOf<String?>(null) }
        var wikiSearchRetry by remember { mutableIntStateOf(0) }
        var selectedTopic by remember {
            mutableStateOf<WikiTopic?>(
                settingsStore.getString(SettingsKeys.TOPIC, "")
                    .takeIf { it.isNotBlank() }
                    ?.let { findTopicByCategory(it) }
            )
        }

        suspend fun loadRandom(code: String, topic: WikiTopic?) {
            val currentRequest = ++requestId
            isLoading = true
            errorMessage = null
            try {
                var candidate: ArticleSummary? = null
                repeat(RANDOM_UNSEEN_ATTEMPTS) {
                    val result = if (topic == null) {
                        source.randomSummary(code)
                    } else {
                        source.randomSummaryForTopic(code, topic.category)
                    }
                    candidate = result
                    if (currentRequest != requestId) return
                    val key = SeenArticle(code, result.title).key
                    if (seenArticles.none { it.key == key }) {
                        if (currentRequest == requestId) {
                            article = result
                            seenArticles = library.markSeen(code, result.title)
                            isLoading = false
                        }
                        return
                    }
                }
                // The source kept returning titles that were already shown. Show one rather than
                // an error page, but do not grow the seen list with another duplicate.
                if (currentRequest == requestId) {
                    article = candidate
                    isLoading = false
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (currentRequest == requestId) {
                    errorMessage = "Could not load Wikipedia. Check your connection and try again."
                    isLoading = false
                }
            }
        }

        fun isSaved(language: String, summary: ArticleSummary): Boolean {
            val key = LibraryEntry(language, summary).key
            return savedEntries.any { it.key == key }
        }

        fun toggleSaved(language: String, summary: ArticleSummary) {
            val entry = LibraryEntry(language, summary)
            savedEntries = if (isSaved(language, summary)) {
                library.removeSaved(entry)
            } else {
                library.addSaved(entry)
            }
        }

        fun openInReader(language: String, summary: ArticleSummary) {
            if (browsingHistory) {
                historyEntries = library.addHistory(LibraryEntry(language, summary))
            }
            seenArticles = library.markSeen(language, summary.title)
            openArticle = OpenArticle(language, summary)
        }

        val openArticleSaved = openArticle?.let { isSaved(it.language, it.summary) } == true

        LaunchedEffect(source) {
            loadRandom(language, selectedTopic)
        }

        LaunchedEffect(wikiSearchQuery, language, wikiSearchRetry) {
            val normalized = wikiSearchQuery.trim()
            if (normalized.length < 2) {
                wikiSearchResults = emptyList()
                wikiSearchLoading = false
                wikiSearchError = null
                return@LaunchedEffect
            }
            delay(350)
            wikiSearchLoading = true
            wikiSearchError = null
            try {
                wikiSearchResults = searchSource.search(language, normalized)
                wikiSearchLoading = false
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                wikiSearchResults = emptyList()
                wikiSearchError = "Could not search Wikipedia. Check your connection and try again."
                wikiSearchLoading = false
            }
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) {
                        Text(
                            text = "WikiRandomizer-P",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Random Wikipedia, your topics",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    NavigationDrawerItem(
                        label = { Text("Reader") },
                        selected = destination == ReaderDestination.READER,
                        onClick = {
                            destination = ReaderDestination.READER
                            openArticle = null
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    NavigationDrawerItem(
                        label = { Text("Discover") },
                        selected = destination == ReaderDestination.FEED,
                        onClick = {
                            destination = ReaderDestination.FEED
                            openArticle = null
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    NavigationDrawerItem(
                        label = { Text("Topics") },
                        selected = destination == ReaderDestination.TOPICS,
                        onClick = {
                            destination = ReaderDestination.TOPICS
                            openArticle = null
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    NavigationDrawerItem(
                        label = { Text("Saved") },
                        selected = destination == ReaderDestination.SAVED,
                        onClick = {
                            destination = ReaderDestination.SAVED
                            openArticle = null
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    NavigationDrawerItem(
                        label = { Text("History") },
                        selected = destination == ReaderDestination.HISTORY,
                        onClick = {
                            destination = ReaderDestination.HISTORY
                            openArticle = null
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    NavigationDrawerItem(
                        label = { Text("Search") },
                        selected = destination == ReaderDestination.SEARCH,
                        onClick = {
                            destination = ReaderDestination.SEARCH
                            openArticle = null
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    NavigationDrawerItem(
                        label = { Text("Wikipedia") },
                        selected = destination == ReaderDestination.WIKI_SEARCH,
                        onClick = {
                            destination = ReaderDestination.WIKI_SEARCH
                            openArticle = null
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    NavigationDrawerItem(
                        label = { Text("Curators") },
                        selected = destination == ReaderDestination.CURATORS,
                        onClick = {
                            destination = ReaderDestination.CURATORS
                            openArticle = null
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    NavigationDrawerItem(
                        label = { Text("Stats") },
                        selected = destination == ReaderDestination.STATS,
                        onClick = {
                            destination = ReaderDestination.STATS
                            openArticle = null
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    NavigationDrawerItem(
                        label = { Text("Settings") },
                        selected = destination == ReaderDestination.SETTINGS,
                        onClick = {
                            destination = ReaderDestination.SETTINGS
                            openArticle = null
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    NavigationDrawerItem(
                        label = { Text("About") },
                        selected = destination == ReaderDestination.ABOUT,
                        onClick = {
                            destination = ReaderDestination.ABOUT
                            openArticle = null
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        ) {
            Scaffold(
                modifier = modifier,
                topBar = {
                    TopAppBar(
                        navigationIcon = {
                            if (openArticle != null) {
                                TextButton(onClick = { openArticle = null }) {
                                    Text("Back")
                                }
                            } else {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    MenuGlyph()
                                }
                            }
                        },
                        title = {
                            val title = openArticle?.summary?.title ?: when (destination) {
                                ReaderDestination.READER -> "WikiRandomizer-P"
                                ReaderDestination.TOPICS -> "Topics"
                                ReaderDestination.SAVED -> "Saved"
                                ReaderDestination.HISTORY -> "History"
                                ReaderDestination.SEARCH -> "Search"
                                ReaderDestination.WIKI_SEARCH -> "Wikipedia"
                                ReaderDestination.CURATORS -> "Curators"
                                ReaderDestination.FEED -> "Discover"
                                ReaderDestination.STATS -> "Stats"
                                ReaderDestination.SETTINGS -> "Settings"
                                ReaderDestination.ABOUT -> "About"
                            }
                            Column {
                                Text(
                                    text = title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleLarge
                                )
                                val subtitle = when {
                                    openArticle != null -> languageName(openArticle?.language ?: language)
                                    destination == ReaderDestination.WIKI_SEARCH -> languageName(language)
                                    destination != ReaderDestination.READER -> null
                                    selectedTopic != null -> "${languageName(language)} · ${selectedTopic?.label}"
                                    else -> languageName(language)
                                }
                                if (subtitle != null) {
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        },
                        actions = {
                            val currentOpen = openArticle
                            if (currentOpen != null) {
                                TextButton(
                                    onClick = {
                                        toggleSaved(currentOpen.language, currentOpen.summary)
                                    }
                                ) {
                                    Text(if (openArticleSaved) "Saved" else "Save")
                                }
                                TextButton(
                                    onClick = {
                                        uriHandler.openUri(
                                            WikiUrls.articleUrl(
                                                currentOpen.language,
                                                currentOpen.summary.title
                                            )
                                        )
                                    }
                                ) {
                                    Text("Browser")
                                }
                            } else if (destination == ReaderDestination.READER) {
                                TextButton(onClick = { showLanguagePicker = true }) {
                                    Text("Language")
                                }
                                TextButton(
                                    enabled = !isLoading,
                                    onClick = { scope.launch { loadRandom(language, selectedTopic) } }
                                ) {
                                    Text("Shuffle")
                                }
                            }
                        }
                    )
                }
            ) { contentPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                ) {
                    val currentOpen = openArticle
                    if (currentOpen != null) {
                        PlatformArticleWebView(
                            url = WikiUrls.articleUrl(
                                currentOpen.language,
                                currentOpen.summary.title
                            ),
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        when (destination) {
                            ReaderDestination.FEED -> FeedScreen(
                                source = feedSource,
                                language = language,
                                languageName = languageName(language),
                                onOpenArticle = { feedLanguage, title ->
                                    if (title.isNotBlank()) {
                                        openInReader(
                                            feedLanguage,
                                            ArticleSummary(
                                                title = title.replace('_', ' '),
                                                extract = ""
                                            )
                                        )
                                    }
                                },
                                onOpenUrl = { uriHandler.openUri(it) }
                            )

                            ReaderDestination.TOPICS -> TopicsScreen(
                                selected = selectedTopic,
                                onSelect = { topic ->
                                    selectedTopic = topic
                                    settingsStore.putString(SettingsKeys.TOPIC, topic.category)
                                    destination = ReaderDestination.READER
                                    scope.launch { loadRandom(language, topic) }
                                },
                                onClear = {
                                    selectedTopic = null
                                    settingsStore.putString(SettingsKeys.TOPIC, "")
                                    destination = ReaderDestination.READER
                                    scope.launch { loadRandom(language, null) }
                                }
                            )

                            ReaderDestination.SAVED -> LibraryScreen(
                                entries = savedEntries,
                                emptyTitle = "No saved articles",
                                emptyDescription = "Tap Save on a random article to keep it here.",
                                onOpen = { entry -> openInReader(entry.language, entry.article) },
                                onRemove = { entry -> savedEntries = library.removeSaved(entry) },
                                onClear = { savedEntries = library.clearSaved() },
                                onEditDetails = { entry, folder, note ->
                                    savedEntries = library.setSavedDetails(entry, folder, note)
                                }
                            )

                            ReaderDestination.HISTORY -> LibraryScreen(
                                entries = historyEntries,
                                emptyTitle = "No history yet",
                                emptyDescription = "Articles you open will appear here.",
                                onOpen = { entry -> openInReader(entry.language, entry.article) },
                                onRemove = { entry ->
                                    historyEntries = library.removeHistory(entry)
                                },
                                onClear = { historyEntries = library.clearHistory() }
                            )

                            ReaderDestination.SEARCH -> LibrarySearchScreen(
                                query = librarySearchQuery,
                                saved = savedEntries,
                                history = historyEntries,
                                recentSearches = recentSearches,
                                onQueryChange = { librarySearchQuery = it },
                                onSearchCommitted = { query ->
                                    recentSearches = library.addSearchQuery(query, language)
                                },
                                onRecentSearch = { query -> librarySearchQuery = query },
                                onClearRecent = { recentSearches = library.clearSearches() },
                                onOpen = { entry ->
                                    recentSearches = library.addSearchQuery(
                                        librarySearchQuery,
                                        language
                                    )
                                    openInReader(entry.language, entry.article)
                                }
                            )

                            ReaderDestination.WIKI_SEARCH -> WikipediaSearchScreen(
                                query = wikiSearchQuery,
                                language = language,
                                results = wikiSearchResults,
                                isLoading = wikiSearchLoading,
                                errorMessage = wikiSearchError,
                                onQueryChange = { wikiSearchQuery = it },
                                onRetry = { wikiSearchRetry++ },
                                onOpen = { result ->
                                    openInReader(
                                        language,
                                        ArticleSummary(
                                            pageid = result.pageId,
                                            title = result.title,
                                            extract = WikiText.plainText(result.snippet),
                                            thumbnail = result.thumbnail?.let { thumbnail ->
                                                ArticleImage(
                                                    source = thumbnail.source,
                                                    width = thumbnail.width,
                                                    height = thumbnail.height
                                                )
                                            }
                                        )
                                    )
                                }
                            )

                            ReaderDestination.CURATORS -> CuratorsScreen(
                                source = curatorSource,
                                followed = followedCurators,
                                interests = remember(savedEntries, historyEntries) {
                                    (savedEntries + historyEntries)
                                        .map { it.article.title.trim().lowercase() }
                                        .toSet()
                                },
                                onToggleFollow = { id ->
                                    followedCurators = curatorFollows.toggle(id)
                                },
                                onOpenArticle = { pickLanguage, title ->
                                    if (title.isNotBlank()) {
                                        openInReader(
                                            pickLanguage,
                                            ArticleSummary(
                                                title = title.replace('_', ' '),
                                                extract = ""
                                            )
                                        )
                                    }
                                }
                            )

                            ReaderDestination.STATS -> StatisticsScreen(
                                saved = savedEntries,
                                history = historyEntries,
                                topicLabel = selectedTopic?.label ?: "All topics",
                                languageLabel = languageName(language)
                            )

                            ReaderDestination.SETTINGS -> SettingsScreen(
                                themeMode = themeMode,
                                languageName = languageName(language),
                                seenCount = seenArticles.size,
                                browsingHistory = browsingHistory,
                                onThemeModeChange = { mode ->
                                    themeMode = mode
                                    settingsStore.putString(SettingsKeys.THEME, mode)
                                },
                                onLanguageClick = { showLanguagePicker = true },
                                onBrowsingHistoryChange = { enabled ->
                                    browsingHistory = enabled
                                    settingsStore.putString(
                                        SettingsKeys.BROWSING_HISTORY,
                                        enabled.toString()
                                    )
                                },
                                onClearSeen = { seenArticles = library.clearSeen() },
                                onAboutClick = { destination = ReaderDestination.ABOUT }
                            )

                            ReaderDestination.ABOUT -> AboutScreen(
                                onOpenRepository = {
                                    uriHandler.openUri(
                                        "https://github.com/PatrickJamesDev/WikiRandomizer-P"
                                    )
                                },
                                onOpenWikipedia = { uriHandler.openUri("https://www.wikipedia.org") }
                            )

                            ReaderDestination.READER -> ReaderContent(
                                article = article,
                                isLoading = isLoading,
                                errorMessage = errorMessage,
                                language = language,
                                isSaved = article?.let { isSaved(language, it) } == true,
                                onToggleSave = { article?.let { toggleSaved(language, it) } },
                                onRetry = { scope.launch { loadRandom(language, selectedTopic) } },
                                topicLabel = selectedTopic?.label,
                                onOpenArticle = { current -> openInReader(language, current) }
                            )
                        }
                    }
                }
            }
        }

        if (showLanguagePicker) {
            LanguagePickerDialog(
                selected = language,
                onDismiss = { showLanguagePicker = false },
                onSelect = { code ->
                    showLanguagePicker = false
                    if (code != language) {
                        language = code
                        openArticle = null
                        settingsStore.putString(SettingsKeys.LANGUAGE, code)
                        scope.launch { loadRandom(code, selectedTopic) }
                    }
                }
            )
        }
    }
}

@Composable
private fun ReaderContent(
    article: ArticleSummary?,
    isLoading: Boolean,
    errorMessage: String?,
    language: String,
    isSaved: Boolean,
    onToggleSave: () -> Unit,
    onRetry: () -> Unit,
    topicLabel: String?,
    onOpenArticle: (ArticleSummary) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            article != null -> ArticleContent(
                article = article,
                language = language,
                isSaved = isSaved,
                onToggleSave = onToggleSave,
                topicLabel = topicLabel,
                onOpenArticle = { onOpenArticle(article) }
            )

            isLoading -> LoadingState()
            errorMessage != null -> MessageState(
                title = "Could not load Wikipedia",
                description = errorMessage,
                actionLabel = "Try again",
                onAction = onRetry
            )

            else -> MessageState(
                title = "No article yet",
                description = "Tap Shuffle to start reading."
            )
        }

        if (isLoading && article != null) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
private fun MenuGlyph() {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = Modifier.size(24.dp)) {
        val stroke = 2.dp.toPx()
        val width = size.width
        val height = size.height
        drawLine(
            color = color,
            start = Offset(0f, height * 0.28f),
            end = Offset(width, height * 0.28f),
            strokeWidth = stroke
        )
        drawLine(
            color = color,
            start = Offset(0f, height * 0.5f),
            end = Offset(width, height * 0.5f),
            strokeWidth = stroke
        )
        drawLine(
            color = color,
            start = Offset(0f, height * 0.72f),
            end = Offset(width, height * 0.72f),
            strokeWidth = stroke
        )
    }
}

@Composable
private fun ArticleContent(
    article: ArticleSummary,
    language: String,
    isSaved: Boolean,
    onToggleSave: () -> Unit,
    topicLabel: String?,
    onOpenArticle: () -> Unit
) {
    val readingMinutes = WikiText.readingMinutes(article.extract)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)
    ) {
        item {
            Text(
                text = article.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        val description = article.summaryDescription
        if (!description.isNullOrBlank()) {
            item {
                Text(
                    text = description,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        val thumbnail = article.thumbnail?.source
        if (!thumbnail.isNullOrBlank()) {
            item {
                AsyncImage(
                    model = thumbnail,
                    contentDescription = article.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(24.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }

        item {
            if (topicLabel != null) {
                Text(
                    text = "Topic: $topicLabel",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = languageName(language),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                if (readingMinutes > 0) {
                    Text(
                        text = "$readingMinutes min read",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
        }

        item {
            Text(
                text = article.extract,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 26.sp
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Read the full article on Wikipedia",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Tables, references and every section open inside the reader.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilledTonalButton(
                            modifier = Modifier.weight(1f),
                            onClick = onToggleSave
                        ) {
                            Text(if (isSaved) "Saved" else "Save")
                        }
                        FilledTonalButton(
                            modifier = Modifier.weight(1f),
                            onClick = onOpenArticle
                        ) {
                            Text("Read article")
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("Finding a random article…")
        }
    }
}

@Composable
private fun MessageState(
    title: String,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun LanguagePickerDialog(
    selected: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val normalizedQuery = query.trim().lowercase()
    val matches = remember(normalizedQuery) {
        LanguageData.langCodes.indices.filter { index ->
            normalizedQuery.isEmpty() ||
                LanguageData.langCodes[index].contains(normalizedQuery) ||
                LanguageData.langNames[index].lowercase().contains(normalizedQuery)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wikipedia language") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Search languages") }
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(matches, key = { LanguageData.langCodes[it] }) { index ->
                        val code = LanguageData.langCodes[index]
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onSelect(code) }
                        ) {
                            Text(
                                text = if (code == selected) {
                                    "${LanguageData.langNames[index]}  ✓"
                                } else {
                                    LanguageData.langNames[index]
                                },
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
