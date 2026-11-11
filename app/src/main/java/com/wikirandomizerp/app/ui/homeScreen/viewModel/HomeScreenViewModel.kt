package com.wikirandomizerp.app.ui.homeScreen.viewModel

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.util.fastAny
import androidx.core.text.parseAsHtml
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import com.wikirandomizerp.app.R
import retrofit2.HttpException
import java.io.IOException
import java.util.LinkedHashMap
import java.util.Locale
import com.wikirandomizerp.app.data.RelevanceModel
import com.wikirandomizerp.app.data.SEARCH_TOPIC_PREFIX
import com.wikirandomizerp.app.data.TopicHint
import com.wikirandomizerp.app.WikiRandomizerPApplication
import com.wikirandomizerp.app.data.ALL_TOPICS
import com.wikirandomizerp.app.data.AppDatabaseRepository
import com.wikirandomizerp.app.data.ArticleCache
import com.wikirandomizerp.app.ui.image.ImagePrefetcher
import com.wikirandomizerp.app.data.ArticleRow
import com.wikirandomizerp.app.data.CacheConfig
import com.wikirandomizerp.app.data.DebugLog
import com.wikirandomizerp.app.data.DeveloperMode
import com.wikirandomizerp.app.data.IndexKeepAliveService
import com.wikirandomizerp.app.data.InterestMatch
import com.wikirandomizerp.app.data.InterestResolver
import com.wikirandomizerp.app.data.LibraryExhaustedException
import com.wikirandomizerp.app.data.LanguagePinnedWikipediaRepository
import com.wikirandomizerp.app.data.BackupManager
import com.wikirandomizerp.app.data.CommonsImage
import com.wikirandomizerp.app.data.LibraryStats
import com.wikirandomizerp.app.data.ReadingStats
import com.wikirandomizerp.app.data.VocabularyWord
import com.wikirandomizerp.app.data.TopicBuildResult
import com.wikirandomizerp.app.data.TopicIndex
import com.wikirandomizerp.app.data.TopicIndexBuilder
import com.wikirandomizerp.app.data.kiwix.KiwixRepository
import com.wikirandomizerp.app.data.kiwix.KiwixStatus
import com.wikirandomizerp.app.data.AppPreferencesRepository
import com.wikirandomizerp.app.data.AppStatus
import com.wikirandomizerp.app.data.SavedArticle
import com.wikirandomizerp.app.data.SavedHighlight
import com.wikirandomizerp.app.data.SavedStatus
import com.wikirandomizerp.app.data.SearchHistoryItem
import com.wikirandomizerp.app.data.UserLanguage
import com.wikirandomizerp.app.data.ViewHistoryItem
import com.wikirandomizerp.app.data.WRStatus
import com.wikirandomizerp.app.data.WikiApiPageData
import com.wikirandomizerp.app.data.WikiApiPage
import com.wikirandomizerp.app.data.WikiPrefixSearchResult
import com.wikirandomizerp.app.data.WikipediaRepository
import com.wikirandomizerp.app.data.langCodeToName
import com.wikirandomizerp.app.data.parseSections
import com.wikirandomizerp.app.network.HostGate
import com.wikirandomizerp.app.network.HostSelectionInterceptor
import com.wikirandomizerp.app.network.NetworkException
import com.wikirandomizerp.app.parser.HTML_INFOBOX_MARKER
import com.wikirandomizerp.app.parser.ReferenceData.infoboxTemplates
import com.wikirandomizerp.app.parser.ReferenceData.refCount
import com.wikirandomizerp.app.parser.ReferenceData.refList
import com.wikirandomizerp.app.parser.ReferenceData.refListCount
import com.wikirandomizerp.app.parser.ReferenceData.refListIndex
import com.wikirandomizerp.app.parser.blockTemplateAt
import com.wikirandomizerp.app.parser.extractCladeBlocks
import com.wikirandomizerp.app.parser.imagemapBlock
import com.wikirandomizerp.app.parser.isBlockTemplateStart
import com.wikirandomizerp.app.parser.buildRefList
import com.wikirandomizerp.app.parser.cleanUpWikitext
import com.wikirandomizerp.app.parser.absolutizeHtmlLinks
import com.wikirandomizerp.app.parser.paragraphizeHtml
import com.wikirandomizerp.app.parser.splitHtmlCladeBlocks
import com.wikirandomizerp.app.parser.splitHtmlMediaBlocks
import com.wikirandomizerp.app.parser.splitHtmlSections
import com.wikirandomizerp.app.parser.splitWikitextBlocks
import com.wikirandomizerp.app.parser.substringMatchingParen
import com.wikirandomizerp.app.parser.wikitextFileLinkBlock
import com.wikirandomizerp.app.parser.toWikitextAnnotatedStringSafe
import com.wikirandomizerp.app.ui.settingsScreen.viewModel.PreferencesState
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.min
import kotlin.random.Random

// ---------------------------------------------------------------------------
// File-level cached regexes 窶・compiled once, never inside hot loops.
// ---------------------------------------------------------------------------
private val imageExtRegexVm =
    ".*\\.(jpg|jpeg|png|svg|gif|webp|tif|tiff|bmp|jfif|apng).*"
        .toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val thumbFilterRegexVm =
    "thumb|thumbnail|frame|frameless|border|baseline|class=.*|center|left|right|upright.*|.+px|alt=.*"
        .toRegex()
private val htmlImageSrcRegex = Regex("""(?is)<img\b[^>]*\bsrc\s*=\s*["']([^"']+)["']""")

// Hard safety limits for the topic-based random article picker. These bound worst-case
// latency regardless of how many topics are selected or how sparse some of them turn out
// to be 窶・a handful of "dud" categories (typo'd names, near-empty leaf categories) can no
// longer chain into a long stall, and the user always gets a result within a bounded time.
private const val MAX_TOPICS_PER_ATTEMPT = 5       // try at most this many selected topics per tap
private const val TOPIC_RANDOM_TIMEOUT_MS = 8_000L // hard ceiling on the whole topic-based attempt
private const val MAX_BACKSTACK_ENTRIES = 8        // bounds memory when chaining random articles
private const val MAX_CANDIDATE_FETCH_ATTEMPTS = 3 // retries if a chosen candidate's fetch fails
private const val MAX_RECENTLY_CLOSED = 10         // closed tabs that can still be brought back
private const val PREFETCH_TARGET = 12             // random articles kept ready in the background
private const val PREFETCH_WARM_IMAGES = 8         // body pictures warmed for a background page
private const val PREFETCH_WARM_FULL_RES = 2       // full-screen renditions warmed in the background

// Keep the text and body pictures of this many most-recently-read articles; older ones are
// evicted from the caches (saved articles, offline downloads and open tabs are exempt).
private const val RETENTION_READS = 10

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
class HomeScreenViewModel(
    private val appStatusFlow: MutableStateFlow<AppStatus>,
    private val preferencesStateMutableFlow: MutableStateFlow<PreferencesState>,
    private val interceptor: HostSelectionInterceptor,
    private val wikipediaRepository: WikipediaRepository,
    private val appPreferencesRepository: AppPreferencesRepository,
    private val appDatabaseRepository: AppDatabaseRepository,
    private val topicIndexProvider: (String) -> TopicIndex,
    private val articleCache: ArticleCache,
    private val appContext: android.content.Context,
    /** True when the user already supported the app, so donation prompts stay hidden. */
    private val donationSupporterProvider: () -> Boolean = { false }
) : ViewModel() {
    private val _appSearchBarState = MutableStateFlow(AppSearchBarState())
    val appSearchBarState: StateFlow<AppSearchBarState> = _appSearchBarState.asStateFlow()

    val backStack = mutableStateListOf<HomeSubscreen>(HomeSubscreen.FeedLoader)

    private val _homeScreenState = MutableStateFlow(HomeScreenState())
    val homeScreenState: StateFlow<HomeScreenState> = _homeScreenState.asStateFlow()

    /**
     * Optional Kiwix/ZIM database. It is created lazily: users who never pick an archive never
     * load the native libzim libraries.
     */
    val kiwixRepository: KiwixRepository by lazy { KiwixRepository(appContext) }
    val kiwixStatus: StateFlow<KiwixStatus> get() = kiwixRepository.status

    fun selectKiwixDatabase(uri: android.net.Uri, displayName: String?) {
        viewModelScope.launch(Dispatchers.IO + errorHandler) {
            kiwixRepository.select(uri, displayName)
        }
    }

    fun removeKiwixDatabase() {
        viewModelScope.launch(Dispatchers.IO + errorHandler) {
            kiwixRepository.remove()
        }
    }

    fun setKiwixForRandom(enabled: Boolean) {
        kiwixRepository.setUseForRandom(enabled)
    }

    private val preferencesState: StateFlow<PreferencesState> =
        preferencesStateMutableFlow.asStateFlow()

    private val _searchListState = MutableStateFlow(LazyListState(0, 0))

    val searchListState: StateFlow<LazyListState> = _searchListState.asStateFlow()
    private val _languageSearchStr = MutableStateFlow("")

    val languageSearchStr: StateFlow<String> = _languageSearchStr.asStateFlow()

    val textFieldState: TextFieldState = TextFieldState()
    val snackBarHostState: SnackbarHostState = SnackbarHostState()

    /**
     * A link was held and its article went into the tab list. [first] is true only the very first
     * time ever: the UI explains it with a message once, afterwards only the short animation plays.
     * [alreadyOpen] means the article was in the tabs already (same animation, tab list unchanged).
     */
    data class TabAddedEvent(val first: Boolean, val tabCount: Int, val alreadyOpen: Boolean)

    private val _tabAdded = MutableSharedFlow<TabAddedEvent>(extraBufferCapacity = 4)
    val tabAdded: SharedFlow<TabAddedEvent> = _tabAdded.asSharedFlow()

    // The confirmation for a vote is useful the first time, but repeating it after every tap
    // covered the reading toolbar. Show each message once per app session.
    private val feedbackNoticesShown = mutableSetOf<Int>()

    val searchHistoryFlow = appDatabaseRepository.getSearchHistory().distinctUntilChanged()
    val recentLangsFlow = appDatabaseRepository.getRecentLanguages().distinctUntilChanged()
    val userLangsFlow = appDatabaseRepository.getUserLanguages().distinctUntilChanged()

    @OptIn(FlowPreview::class)
    val languageSearchQuery = languageSearchStr.debounce(500L)

    var lastQuery: Pair<String, String>? = null
    private var filesDir: String = ""
    // Previously a self-healing property (custom getter that swapped in a fresh Job()
    // whenever the old one was found cancelled). That avoided reusing a dead Job, but the
    // replacement Jobs were root jobs with no parent, so in-flight work here wasn't
    // guaranteed to be cancelled if the ViewModel itself were cleared. Reassigning the
    // actual Job returned by launch() keeps it a proper child of viewModelScope instead.
    private var loaderJob: Job? = null

    /**
     * Safety net for every background launch: an exception that escapes a coroutine used to
     * kill the whole app (e.g. a database error while saving history after tapping Random).
     * Now it is logged, the spinner is cleared and the app keeps running.
     */
    private val errorHandler = CoroutineExceptionHandler { _, t ->
        DebugLog.e("VM", "UNCAUGHT coroutine exception (app kept alive)", t)
        _homeScreenState.update { it.copy(isLoading = false) }
    }
    /** Titles already shown by the random button this session (works even with history off). */
    private val sessionSeen: MutableSet<String> = java.util.Collections.synchronizedSet(HashSet())
    private val seenMutex = Mutex()

    // 笏笏 Search result caches 笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏
    // Search-as-you-type sends a request for nearly every prefix ("a", "ab", "abc"). The
    // network results for a prefix do not change within a session, and local history/saved
    // queries are cheap but repeated on every keystroke. Both caches are small LRUs with a
    // short TTL so a newly saved article still shows up promptly.
    private data class TimedSearchEntry<T>(val value: T, val atNanos: Long)
    private class SearchLruCache<T> : LinkedHashMap<String, TimedSearchEntry<T>>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, TimedSearchEntry<T>>
        ) = size > 32
    }

    private val prefixSearchCache = SearchLruCache<List<WikiPrefixSearchResult>>()
    private val localSearchCache = SearchLruCache<List<LocalArticleMatch>>()

    private fun <T> readSearchCache(
        cache: SearchLruCache<T>,
        key: String,
        ttlMs: Long
    ): T? = synchronized(cache) {
        val entry = cache[key] ?: return@synchronized null
        if (System.nanoTime() - entry.atNanos > ttlMs * 1_000_000L) {
            cache.remove(key)
            null
        } else entry.value
    }

    private fun <T> writeSearchCache(cache: SearchLruCache<T>, key: String, value: T) {
        synchronized(cache) {
            cache[key] = TimedSearchEntry(value, System.nanoTime())
        }
    }

    // 笏笏 Unlimited, lightweight article history 笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏
    // The rendered back stack is capped at 8 entries because every entry holds parsed article
    // text. This list holds only title + language, so it can keep the whole session without a
    // meaningful memory cost and powers the back/forward controls in the tab panel.
    private data class HistoryEntry(
    val title: String,
    val lang: String,
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0
)
    private val articleHistory = ArrayList<HistoryEntry>()
    private var historyIndex = -1

    // 笏笏 Article tabs 笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏
    private var activeTabId: Long? = null
    private var tabIdCounter = System.currentTimeMillis()
    private var tabsPrefetchJob: Job? = null
    /** Pending debounced write of the per-tab reading positions. */
    private var scrollPersistJob: Job? = null

    // 笏笏 Next random article, prepared in the background 笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏

    private class PrefetchedRandom(
        val page: WikiApiPage,
        val libraryTitle: String,
        val content: String,
        val key: String
    )

    /** The next few random articles, already downloaded (text on disk, pictures in the image cache). */
    private val prefetchQueue = ArrayList<PrefetchedRandom>()
    private val prefetchLock = Any()
    private val persistMutex = Mutex()
    private var prefetchJob: Job? = null
    @Volatile private var libraryVersion = 0

    /** One article currently on screen; finished when the user leaves it (opt-in personalization). */
    private class ReadingSession(
        val title: String,
        val lang: String,
        val topic: String?,
        val startedMs: Long
    ) {
        @Volatile var maxProgress: Double = 0.0
    }
    @Volatile private var readingSession: ReadingSession? = null

    private val _dictionaryLookup = MutableStateFlow<DictionaryLookup?>(null)
    val dictionaryLookup: StateFlow<DictionaryLookup?> = _dictionaryLookup.asStateFlow()

    private fun queuedCount(): Int = synchronized(prefetchLock) { prefetchQueue.size }

    /**
     * Anything that could change which article should come next changes this key. Only settings
     * that affect picks are included: a font/theme change used to throw away the prepared queue.
     */
    private suspend fun prefetchKey(): String {
        val p = preferencesState.value
        return "${selectedTopicList().joinToString(",")}|images=${p.onlyArticlesWithImages}" +
                "|stubs=${p.avoidStubs}|quality=${p.preferQuality}|bias=${p.popularityBias}" +
                "|strict=${p.topicStrictness}|personal=${p.personalizationEnabled}" +
                "|mode=${p.renderMode}|$libraryVersion"
    }

    private fun invalidatePrefetch() {
        libraryVersion++
        synchronized(prefetchLock) { prefetchQueue.clear() }
        prefetchJob?.cancel()
        persistPrefetchQueue()
        // Topic/settings changes must begin refilling immediately, otherwise the next Random tap
        // has nothing prepared and falls back to a loading screen.
        schedulePrefetch()
    }

    /** Remembers which articles are queued, so they are instantly available after a restart too. */
    private fun persistPrefetchQueue() {
        viewModelScope.launch(Dispatchers.IO + errorHandler) {
            persistMutex.withLock {
                val snapshot = synchronized(prefetchLock) { prefetchQueue.toList() }
                val lang = preferencesState.value.lang
                val lines = snapshot.joinToString("\n") {
                    listOf(it.key, it.libraryTitle, it.page.title).joinToString("\u0001")
                }
                appPreferencesRepository.saveStringPreference("prefetch_queue", lang + "\n" + lines)
            }
        }
    }

    /** Puts the articles prepared in an earlier session back in the queue (read from disk, no network). */
    private suspend fun restorePrefetchQueue() {
        try {
            val raw = appPreferencesRepository.readStringPreference("prefetch_queue") ?: return
            val lines = raw.split('\n')
            val lang = lines.firstOrNull() ?: return
            if (lang != preferencesState.value.lang) return
            val currentKey = prefetchKey()
            val currentMode = preferencesState.value.renderMode
            val restored = ArrayList<PrefetchedRandom>()
            for (line in lines.drop(1)) {
                val parts = line.split('\u0001')
                if (parts.size < 3 || parts[0] != currentKey) continue
                val entry = articleCache.get(lang, parts[2], 0L) ?: continue
                // Old cache files default to wikitext, so an HTML queue can never consume one by
                // accident. A mode change also changes the queue key, but this is defence in depth.
                if (entry.contentKind != currentMode) continue
                val page = entry.page ?: continue
                restored.add(PrefetchedRandom(page, parts[1], entry.wikitext, parts[0]))
            }
            if (restored.isNotEmpty()) {
                synchronized(prefetchLock) { prefetchQueue.addAll(0, restored) }
                DebugLog.i("Prefetch", "restored ${restored.size} prepared article(s) from the previous session (mode=$currentMode)")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DebugLog.w("Prefetch", "could not restore the prepared articles: ${e.message}")
        }
    }

    // 笏笏 Article history (unlimited) 笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏

    private fun updateHistoryFlags() {
        // An error page is a dead end stacked above the article that was being read (or above the
        // feed). It is not part of the light history, so the normal historyIndex test would leave
        // the Back button disabled on it. Back must always be able to close the error page.
        val onErrorPage =
            (backStack.lastOrNull() as? HomeSubscreen.Article)?.title == "Error"
        _homeScreenState.update {
            it.copy(
                canGoBack = historyIndex > 0 || (onErrorPage && backStack.size > 1),
                canGoForward = !onErrorPage && historyIndex in 0 until articleHistory.lastIndex
            )
        }
    }

    private fun initializeHistory(title: String, lang: String) {
        articleHistory.clear()
        articleHistory.add(HistoryEntry(title, lang))
        historyIndex = 0
        updateHistoryFlags()
    }

    private fun recordHistory(title: String, lang: String) {
        if (title.isBlank() || title == "Error") return
        val current = articleHistory.getOrNull(historyIndex)
        if (current?.title == title && current.lang == lang) {
            updateHistoryFlags()
            return
        }
        // Loading a new article after going back discards the forward branch, exactly like a
        // browser. Only the current branch is kept.
        if (historyIndex < articleHistory.lastIndex) {
            articleHistory.subList(historyIndex + 1, articleHistory.size).clear()
        }
        articleHistory.add(HistoryEntry(title, lang))
        // Two strings per entry: this cap is effectively unlimited for a reading session and only
        // protects against a pathological automated loop.
        if (articleHistory.size > 2_000) articleHistory.removeAt(0)
        historyIndex = articleHistory.lastIndex
        updateHistoryFlags()
    }

    private fun revertHistoryToCurrentArticle() {
        val top = backStack.lastOrNull() as? HomeSubscreen.Article ?: return
        val lang = top.currentLang ?: return
        val index = articleHistory.indexOfLast { it.title == top.title && it.lang == lang }
        if (index >= 0) {
            historyIndex = index
            updateHistoryFlags()
        }
    }

    private fun navigateHistory(delta: Int) {
        // An error page is a dead end, not a real article: Back from it must return to the
        // article that was open before the failed link, without stepping one entry too far in
        // the light history (which used to skip the previous article entirely).
        val top = backStack.lastOrNull()
        if (top is HomeSubscreen.Article && top.title == "Error") {
            if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
            updateHistoryFlags()
            DebugLog.d("History", "closed the error page instead of walking history")
            return
        }
        val target = historyIndex + delta
        if (target !in articleHistory.indices) return
        if (backStack.lastOrNull() !is HomeSubscreen.Article) return
        // Save where the reader was before leaving, so going forward again returns to that spot.
        captureCurrentScroll()
        val entry = articleHistory[target]
        historyIndex = target
        updateHistoryFlags()
        DebugLog.d(
            "History",
            "navigate ${if (delta < 0) "back" else "forward"} to '${entry.title}' (${entry.lang}), " +
                    "index=$historyIndex/${articleHistory.lastIndex} " +
                    "scroll=${entry.scrollIndex}+${entry.scrollOffset}px"
        )
        loadPage(
            title = entry.title,
            lang = entry.lang,
            replaceBackstackEntry = true,
            fromHistory = true,
            initialScroll = entry.scrollIndex to entry.scrollOffset
        )
    }

    /**
     * Keeps the first non-article screen (feed/logo) and the newest article. Older parsed articles
     * are removed from the render stack; the lightweight history above still knows all of them.
     */
    private fun pruneArticleBackstack() {
        var i = 0
        while (i < backStack.lastIndex) {
            if (backStack[i] is HomeSubscreen.Article) backStack.removeAt(i) else i++
        }
    }

    // 笏笏 Article tabs 笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏

    private fun nextTabId(): Long {
        tabIdCounter = maxOf(tabIdCounter + 1, System.currentTimeMillis())
        return tabIdCounter
    }

    /**
     * Collapses tabs that point at the same article. Wikipedia link targets and page titles can
     * differ only by capitalisation ("plant reproduction" vs "Plant reproduction"), so the key is
     * language plus a case-insensitive title. The most recently used duplicate wins, which keeps
     * the id of a tab that was just opened or navigated. If the active tab was the duplicate that
     * lost, the active id is remapped to the kept tab.
     */
    private fun normalizeTabs(
        tabs: List<ArticleTab>,
        active: Long?
    ): Pair<List<ArticleTab>, Long?> {
        if (tabs.isEmpty()) return emptyList<ArticleTab>() to null
        val keptByKey = LinkedHashMap<Pair<String, String>, ArticleTab>()
        tabs.sortedWith(
            compareByDescending<ArticleTab> { it.id == active }
                .thenByDescending { it.lastUsedAt }
        ).forEach { tab ->
            keptByKey.putIfAbsent(
                tab.lang to tab.title.lowercase(Locale.ROOT),
                tab
            )
        }
        val normalized = keptByKey.values.toList()
        val activeTab = tabs.firstOrNull { it.id == active }
        val normalizedActive = activeTab?.let { tab ->
            keptByKey[tab.lang to tab.title.lowercase(Locale.ROOT)]?.id
        } ?: normalized.maxByOrNull { it.lastUsedAt }?.id
        return normalized to normalizedActive
    }

    private fun publishTabs(tabs: List<ArticleTab>, active: Long?) {
        val (normalized, normalizedActive) = normalizeTabs(tabs, active)
        activeTabId = normalizedActive
        _homeScreenState.update { it.copy(tabs = normalized, activeTabId = normalizedActive) }
        persistTabs(normalized, normalizedActive)
    }

    private fun persistTabs(tabs: List<ArticleTab>, active: Long?) {
        scrollPersistJob?.cancel()
        viewModelScope.launch(Dispatchers.IO + errorHandler) { writeTabs(tabs, active) }
    }

    private suspend fun writeTabs(tabs: List<ArticleTab>, active: Long?) {
        val raw = buildString {
            append(active ?: "")
            append('\n')
            tabs.forEach { tab ->
                append(tab.id).append('\u0001')
                append(tab.title).append('\u0001')
                append(tab.lang).append('\u0001')
                append(tab.lastUsedAt).append('\u0001')
                append(tab.scrollIndex).append('\u0001')
                append(tab.scrollOffset).append('\u0001')
                append(tab.thumbnail.orEmpty()).append('\n')
            }
        }
        appPreferencesRepository.saveStringPreference("article_tabs", raw)
    }

    private suspend fun restoreTabs() {
        try {
            val raw = appPreferencesRepository.readStringPreference("article_tabs") ?: return
            val lines = raw.split('\n')
            val savedActive = lines.firstOrNull()?.toLongOrNull()
            val tabs = lines.drop(1).mapNotNull { line ->
                val parts = line.split('\u0001')
                if (parts.size < 4) return@mapNotNull null
                val id = parts[0].toLongOrNull() ?: return@mapNotNull null
                if (parts[1].isBlank() || parts[2].isBlank()) return@mapNotNull null
                ArticleTab(
                    id = id,
                    title = parts[1],
                    lang = parts[2],
                    lastUsedAt = parts[3].toLongOrNull() ?: 0L,
                    // Older sessions only stored four fields; default to the top of the article.
                    scrollIndex = parts.getOrNull(4)?.toIntOrNull() ?: 0,
                    scrollOffset = parts.getOrNull(5)?.toIntOrNull() ?: 0,
                    thumbnail = parts.getOrNull(6)?.takeIf { it.isNotBlank() }
                )
            }.distinctBy { it.id }
            if (tabs.isEmpty()) return
            tabIdCounter = maxOf(tabIdCounter, tabs.maxOf { it.id })
            val active = savedActive?.takeIf { id -> tabs.any { it.id == id } }
                ?: tabs.maxByOrNull { it.lastUsedAt }?.id
            publishTabs(tabs, active)
            DebugLog.i("Tabs", "restored ${tabs.size} tab(s) from the previous session")
            prefetchTabs()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DebugLog.w("Tabs", "could not restore tabs: ${e.message}")
        }
    }

    private fun onArticleLoaded(
        title: String,
        lang: String,
        fromRestore: Boolean,
        forceNewTab: Boolean,
        thumbnail: String? = null
    ) {
        if (title.isBlank() || title == "Error") return
        val tabs = homeScreenState.value.tabs
        val now = System.currentTimeMillis()
        if (fromRestore) {
            val matching = tabs.firstOrNull {
                it.title.equals(title, ignoreCase = true) && it.lang == lang
            }
            if (matching != null) {
                publishTabs(tabs, matching.id)
            } else {
                val tab = ArticleTab(nextTabId(), title, lang, now, thumbnail = thumbnail)
                publishTabs(listOf(tab) + tabs, tab.id)
            }
            prefetchTabs()
            return
        }
        if (forceNewTab) {
            val tab = ArticleTab(nextTabId(), title, lang, now, thumbnail = thumbnail)
            publishTabs(listOf(tab) + tabs, tab.id)
            prefetchTabs()
            return
        }
        val active = activeTabId
        val existing = tabs.firstOrNull { it.id == active }
        if (existing == null) {
            val tab = ArticleTab(nextTabId(), title, lang, now, thumbnail = thumbnail)
            publishTabs(listOf(tab) + tabs, tab.id)
        } else {
            val updated = tabs.map {
                if (it.id == active) it.copy(
                    title = title,
                    lang = lang,
                    lastUsedAt = now,
                    // Never keep the cover of the previous article when the tab navigated elsewhere.
                    thumbnail = thumbnail ?: it.thumbnail.takeIf { _ ->
                        it.title.equals(title, ignoreCase = true) && it.lang == lang
                    }
                ) else it
            }.sortedByDescending { it.lastUsedAt }
            publishTabs(updated, active)
        }
        prefetchTabs()
    }

    private fun revertActiveTabToCurrentArticle() {
        val top = backStack.lastOrNull() as? HomeSubscreen.Article ?: return
        val lang = top.currentLang ?: return
        val tab = homeScreenState.value.tabs.firstOrNull {
            it.title.equals(top.title, ignoreCase = true) && it.lang == lang
        }
        if (tab != null) publishTabs(homeScreenState.value.tabs, tab.id)
    }

    private fun openInNewTab(title: String, lang: String) {
        if (title.isBlank()) return
        val tabs = homeScreenState.value.tabs
        if (tabs.any { it.title.equals(title, ignoreCase = true) && it.lang == lang }) {
            // Nothing to add, but the hold must still answer: same animation, no text.
            _tabAdded.tryEmit(TabAddedEvent(first = false, tabCount = tabs.size, alreadyOpen = true))
            return
        }
        val tab = ArticleTab(nextTabId(), title, lang, System.currentTimeMillis())
        publishTabs(listOf(tab) + tabs, activeTabId)
        prefetchTabs()
        DebugLog.i("Tabs", "added '$title' ($lang) in the background; ${tabs.size + 1} tab(s)")
        // The explanation ("Added to tabs", with a shortcut to the panel) is shown exactly once, ever.
        val prefs = appContext.getSharedPreferences("ui_hints", 0)
        val first = !prefs.getBoolean("tab_added_notice_shown", false)
        if (first) prefs.edit().putBoolean("tab_added_notice_shown", true).apply()
        _tabAdded.tryEmit(TabAddedEvent(first = first, tabCount = tabs.size + 1, alreadyOpen = false))
    }

    private fun switchToTab(id: Long) {
        captureCurrentScroll()
        val tabs = homeScreenState.value.tabs
        val tab = tabs.firstOrNull { it.id == id } ?: return
        val updated = tabs.map {
            if (it.id == id) it.copy(lastUsedAt = System.currentTimeMillis()) else it
        }.sortedByDescending { it.lastUsedAt }
        publishTabs(updated, id)
        val top = backStack.lastOrNull() as? HomeSubscreen.Article
        if (top?.title?.equals(tab.title, ignoreCase = true) == true &&
            top.currentLang == tab.lang
        ) {
            prefetchTabs()
            return
        }
        loadPage(
            title = tab.title,
            lang = tab.lang,
            replaceBackstackEntry = true,
            fromTabSwitch = true,
            initialScroll = tab.scrollIndex to tab.scrollOffset
        )
    }

    private fun closeTab(id: Long) {
        val tabs = homeScreenState.value.tabs
        val closed = tabs.firstOrNull { it.id == id } ?: return
        // Remember it (with its scroll position) so the tab panel can offer to undo the close.
        _homeScreenState.update {
            it.copy(
                recentlyClosed = (it.recentlyClosed.filterNot { old ->
                    old.lang == closed.lang && old.title.equals(closed.title, ignoreCase = true)
                } + closed).takeLast(MAX_RECENTLY_CLOSED)
            )
        }
        val remaining = tabs.filterNot { it.id == id }
        if (activeTabId != id) {
            publishTabs(remaining, activeTabId)
            return
        }
        val next = remaining.maxByOrNull { it.lastUsedAt }
        if (next == null) {
            publishTabs(emptyList(), null)
            return
        }
        publishTabs(remaining, next.id)
        val top = backStack.lastOrNull() as? HomeSubscreen.Article
        if (top?.title?.equals(next.title, ignoreCase = true) != true ||
            top.currentLang != next.lang
        ) {
            loadPage(
                title = next.title,
                lang = next.lang,
                replaceBackstackEntry = true,
                fromTabSwitch = true
            )
        } else {
            prefetchTabs()
        }
    }

    /** Undo for [closeTab]: puts the most recently closed tab back and switches to it. */
    private fun reopenClosedTab() {
        val closed = homeScreenState.value.recentlyClosed.lastOrNull() ?: return
        _homeScreenState.update { it.copy(recentlyClosed = it.recentlyClosed.dropLast(1)) }
        val tabs = homeScreenState.value.tabs
        val already = tabs.firstOrNull {
            it.lang == closed.lang && it.title.equals(closed.title, ignoreCase = true)
        }
        if (already != null) {
            switchToTab(already.id)
            return
        }
        // Same id, same scroll position and cover as when it was closed.
        publishTabs(tabs + closed, activeTabId)
        switchToTab(closed.id)
    }

    /** Fills in a tab's cover picture once the article's data is known (background-opened tabs have none). */
    private fun setTabThumbnail(tabId: Long, source: String?) {
        if (source.isNullOrBlank()) return
        val tabs = homeScreenState.value.tabs
        if (tabs.none { it.id == tabId && it.thumbnail.isNullOrBlank() }) return
        val updated = tabs.map { if (it.id == tabId) it.copy(thumbnail = source) else it }
        _homeScreenState.update { it.copy(tabs = updated) }
        persistTabs(updated, activeTabId)
    }

    /**
     * Keeps up to twelve recently used tabs ready on disk (raw text + page metadata). This is the
     * "10+ tabs always loaded" promise: switching to one of them is a cache hit and needs no
     * network. Requests are deliberately serial with a small gap so background work never competes
     * with the article the reader is waiting for.
     */
    private fun prefetchTabs() {
        val tabs = homeScreenState.value.tabs
            .sortedByDescending { it.lastUsedAt }
            .take(12)
        if (tabs.isEmpty()) return
        tabsPrefetchJob?.cancel()
        tabsPrefetchJob = viewModelScope.launch(Dispatchers.IO + errorHandler) {
            val kind = if (preferencesState.value.renderMode == "html") "html" else "wikitext"
            val current = (backStack.lastOrNull() as? HomeSubscreen.Article)?.let {
                it.title to it.currentLang
            }
            // Cover pictures of every tab go into the disk cache first, so the tab panel shows them
            // at once; tabs that are still missing a cover get it from their cached/fetched page.
            if (!preferencesState.value.dataSaver) {
                ImagePrefetcher.warmThumbnails(appContext, tabs.map { it.thumbnail })
            }
            for (tab in tabs) {
                coroutineContext.ensureActive()
                if (tab.title.isBlank()) continue
                if (tab.thumbnail.isNullOrBlank()) {
                    articleCache.get(tab.lang, tab.title)?.page?.let { cachedPage ->
                        val cover = cachedPage.thumbnail?.source ?: cachedPage.photo?.source
                        setTabThumbnail(tab.id, cover)
                        if (!preferencesState.value.dataSaver) {
                            ImagePrefetcher.warmThumbnails(appContext, listOf(cover))
                        }
                    }
                }
                if (current?.first == tab.title && current.second == tab.lang) continue
                val cached = articleCache.get(tab.lang, tab.title)
                    ?.takeIf { it.contentKind == kind }
                if (cached != null) continue
                try {
                    withHost(tab.lang) {
                        val pageData = wikipediaRepository.getPageData(tab.title)
                        val info = pageData.query?.pages?.get(0)
                        val content = if (kind == "html") wikipediaRepository.getPageHtml(tab.title)
                        else wikipediaRepository.getPageContent(tab.title)
                        if (info != null) {
                            articleCache.put(
                                tab.lang,
                                info.title,
                                info,
                                content,
                                tab.title,
                                kind,
                                imageUrls = imageUrlsFor(tab.lang, info, content, kind == "html")
                            )
                            val cover = info.thumbnail?.source ?: info.photo?.source
                            setTabThumbnail(tab.id, cover)
                            if (!preferencesState.value.dataSaver) {
                                ImagePrefetcher.warmThumbnails(appContext, listOf(cover))
                                warmImages(tab.lang, info, content, full = false)
                            }
                        }
                    }
                    DebugLog.d("Tabs", "prefetched '${tab.title}' (${tab.lang}, $kind)")
                    delay(250)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    DebugLog.w("Tabs", "prefetch '${tab.title}' failed: ${e.message}")
                }
            }
        }
    }

    /**
     * Starts loading a page's pictures so they are on screen the moment the article opens.
     *
     * A page the user actually reads warms every body picture at 960 px and all high-resolution
     * 1920 px renditions. Background-prefetched pages warm only a handful, otherwise a dozen
     * queued articles would download thousands of pictures and compete with the visible page.
     */
    private fun warmImages(
        lang: String,
        page: WikiApiPage,
        content: String,
        full: Boolean = true
    ) {
        // Scanning the article and building the Coil loader must not run on the caller's thread
        // (article open / tab switch); it is pure background work.
        viewModelScope.launch(Dispatchers.IO + errorHandler) {
            try {
                val urls = ArrayList<String>()
                // Both the thumbnail (article/infobox) and the original (full-screen viewer) so the
                // viewer can open the lead picture instantly even before the body is scrolled.
                ImagePrefetcher.standardThumbnailUrl(page.thumbnail?.source)?.let { urls.add(it) }
                page.photo?.source?.let { urls.add(it) }
                val limit = if (full) CacheConfig.MAX_ARTICLE_IMAGES else PREFETCH_WARM_IMAGES
                if (preferencesState.value.renderMode == "html") {
                    // REST HTML is converted to [[File:...]] blocks before rendering, so warm
                    // exactly those file names (the raw <img> src is not what the renderer
                    // requests).
                    val media = splitHtmlMediaBlocks(content)
                        .filter { it.contains("[[File:", ignoreCase = true) }
                        .joinToString("\n")
                    urls.addAll(ImagePrefetcher.firstImageUrls(lang, media, limit))
                } else {
                    urls.addAll(ImagePrefetcher.firstImageUrls(lang, content, limit))
                }
                ImagePrefetcher.warm(
                    appContext,
                    urls,
                    maxFullRes = if (full) CacheConfig.MAX_HIGH_RES_IMAGES
                    else PREFETCH_WARM_FULL_RES
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                DebugLog.w("Prefetch", "could not warm pictures: ${e.message}")
            }
        }
    }

    /** Saves a downloaded article for instant re-opening (also under the title that was typed/linked). */
    private fun cacheArticle(
        lang: String,
        page: WikiApiPage,
        content: String,
        requestedTitle: String?,
        contentKind: String
    ) {
        viewModelScope.launch(Dispatchers.IO + errorHandler) {
            // Remember every picture that belongs to this article so the retention policy can
            // evict exactly these body images later, without touching pinned thumbnails. The
            // scan can walk the whole page, so it runs off the main thread too.
            val imageUrls = imageUrlsFor(lang, page, content, contentKind == "html")
            articleCache.put(
                lang = lang,
                title = page.title,
                page = page,
                content = content,
                alsoAs = requestedTitle,
                contentKind = contentKind,
                imageUrls = imageUrls
            )
        }
        // Start warming the article's pictures immediately: no waiting for a scroll or for the
        // progressive renderer to reach the first image.
        if (!preferencesState.value.dataSaver) warmImages(lang, page, content)
    }

    /** All picture addresses in [content] (HTML <img> tags or wikitext [[File:]] blocks). */
    private fun imageUrlsFor(
        lang: String,
        page: WikiApiPage,
        content: String,
        html: Boolean
    ): List<String> {
        val urls = LinkedHashSet<String>()
        ImagePrefetcher.standardThumbnailUrl(page.thumbnail?.source)?.let(urls::add)
        page.photo?.source?.let(urls::add)
        try {
            if (html) {
                // The rendered article requests FilePath URLs built from the [[File:...]] names
                // produced by splitHtmlMediaBlocks(), so record those for retention/warming.
                val media = splitHtmlMediaBlocks(content)
                    .filter { it.contains("[[File:", ignoreCase = true) }
                    .joinToString("\n")
                urls.addAll(ImagePrefetcher.firstImageUrls(lang, media, max = 200))
                // Keep raw <img> sources too for pictures the parser leaves as plain HTML.
                htmlImageSrcRegex.findAll(content)
                    .map { it.groupValues[1].trim() }
                    .filter { it.isNotBlank() && !it.startsWith("data:") }
                    .map { if (it.startsWith("//")) "https:$it" else it }
                    .mapNotNull { ImagePrefetcher.standardThumbnailUrl(it) }
                    .forEach(urls::add)
            } else {
                urls.addAll(ImagePrefetcher.firstImageUrls(lang, content, max = 200))
            }
        } catch (e: Throwable) {
            DebugLog.w("Prefetch", "could not collect picture URLs: ${e.message}")
        }
        return urls.toList()
    }

    /**
     * Cache retention: keep the full text and body pictures of the last [RETENTION_READS]
     * articles. When an article falls further behind, its cached text and body images are
     * deleted again so the library never grows without bound. Saved articles, offline downloads
     * and articles that are open in a tab are never evicted. Pinned History/Saved thumbnails are
     * stored under different keys and always survive.
     */
    private fun recordReadForRetention(lang: String, title: String) {
        viewModelScope.launch(Dispatchers.IO + errorHandler) {
            try {
                val reads = articleCache.recentReads()
                reads.removeAll { it.first == lang && it.second == title }
                reads.add(lang to title)
                // Entries that are `RETENTION_READS` positions older than the newest one are
                // candidates. Each article is considered once and then dropped from the list.
                val excess = (reads.size - RETENTION_READS).coerceAtLeast(0)
                val candidates = if (excess > 0) reads.subList(0, excess).toList() else emptyList()
                if (excess > 0) reads.subList(0, excess).clear()
                // Bound the bookkeeping itself.
                if (reads.size > RETENTION_READS * 4) {
                    reads.subList(0, reads.size - RETENTION_READS * 4).clear()
                }
                articleCache.setRecentReads(reads)

                if (candidates.isEmpty()) return@launch
                val openTabs = homeScreenState.value.tabs
                    .map { it.lang to it.title.lowercase(Locale.ROOT) }
                    .toHashSet()
                for ((candidateLang, candidateTitle) in candidates) {
                    val key = candidateLang to candidateTitle.lowercase(Locale.ROOT)
                    if (key in openTabs) continue
                    if (articleCache.isOffline(candidateLang, candidateTitle)) continue
                    val saved = try {
                        appDatabaseRepository.isTitleSaved(candidateTitle, candidateLang)
                    } catch (e: Exception) {
                        false
                    }
                    if (saved) continue
                    val entry = articleCache.removeNormal(candidateLang, candidateTitle)
                    ImagePrefetcher.evict(appContext, entry?.imageUrls.orEmpty())
                    if (entry != null) {
                        DebugLog.i(
                            "Retention",
                            "evicted '$candidateTitle' ($candidateLang) after $RETENTION_READS newer reads"
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DebugLog.w("Retention", "could not prune old article caches: ${e.message}")
            }
        }
    }

    // 笏笏 opt-in behaviour learning 笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏

    /** Starts a reading session for a shown article; only when the user opted in. */
    private fun startReading(title: String, lang: String) {
        readingSession = null
        // Statistics always count reading time. Personalization (which needs a topic) is still
        // opt-in, but turning it off must not stop the local "time read" counter.
        val topic = if (preferencesState.value.personalizationEnabled) try {
            topicIndexProvider(lang).topicsForTitle(title).firstOrNull()
        } catch (e: Exception) {
            DebugLog.w("Personalization", "could not find topics for '$title': ${e.message}")
            null
        } else null
        readingSession = ReadingSession(title, lang, topic, System.currentTimeMillis())
    }

    /** Called from the article scroll position (0 = top, 1 = end). */
    private fun onReadingProgress(progress: Float) {
        val session = readingSession ?: return
        val p = progress.toDouble().coerceIn(0.0, 1.0)
        if (p > session.maxProgress) session.maxProgress = p
    }

    /**
     * Records the exact reading place of the visible article into the active tab and the current
     * history entry. Called on a debounce while scrolling and right before any tab/history switch,
     * so switching back always restores the same spot instead of jumping to the top.
     */
    private fun onScrollPosition(index: Int, offset: Int) {
        val top = backStack.lastOrNull() as? HomeSubscreen.Article ?: return
        val title = top.title
        val lang = top.currentLang ?: return

        articleHistory.getOrNull(historyIndex)?.let { entry ->
            if (entry.title.equals(title, ignoreCase = true) && entry.lang == lang) {
                articleHistory[historyIndex] = entry.copy(
                    scrollIndex = index,
                    scrollOffset = offset
                )
            }
        }

        val active = activeTabId ?: return
        val tabs = homeScreenState.value.tabs
        val updated = tabs.map {
            if (it.id == active) it.copy(scrollIndex = index, scrollOffset = offset) else it
        }
        if (updated == tabs) return
        _homeScreenState.update { it.copy(tabs = updated) }
        // Debounced disk write: scrolling should not cause one preference write per item.
        scrollPersistJob?.cancel()
        scrollPersistJob = viewModelScope.launch(Dispatchers.IO + errorHandler) {
            delay(1_200)
            val current = homeScreenState.value
            writeTabs(current.tabs, current.activeTabId)
        }
    }

    /** Reads the live LazyListState of the visible article (used before switching away). */
    private fun captureCurrentScroll() {
        val top = backStack.lastOrNull() as? HomeSubscreen.Article ?: return
        onScrollPosition(
            top.listState.firstVisibleItemIndex,
            top.listState.firstVisibleItemScrollOffset
        )
    }

    /** Records the finished session (time on page + furthest scroll) and re-ranks the next pick. */
    private fun finishReading() {
        val session = readingSession ?: return
        readingSession = null
        val seconds = (System.currentTimeMillis() - session.startedMs) / 1000.0
        if (seconds < 2.0) return
        viewModelScope.launch(Dispatchers.IO + errorHandler) {
            try {
                // Always keep local totals, even when personalization is disabled.
                val oldSeconds = appPreferencesRepository.readIntPreference("stats-reading-seconds") ?: 0
                appPreferencesRepository.saveIntPreference(
                    "stats-reading-seconds",
                    oldSeconds + seconds.toInt()
                )
                val oldSessions = appPreferencesRepository.readIntPreference("stats-reading-sessions") ?: 0
                appPreferencesRepository.saveIntPreference("stats-reading-sessions", oldSessions + 1)
                // Per-day time and the longest single session, for the statistics screen.
                val dayKey = statsDayKey()
                val daySeconds = appPreferencesRepository.readIntPreference("stats-day-seconds-$dayKey") ?: 0
                appPreferencesRepository.saveIntPreference(
                    "stats-day-seconds-$dayKey",
                    daySeconds + seconds.toInt()
                )
                val longest = appPreferencesRepository.readIntPreference("stats-longest-session") ?: 0
                if (seconds.toInt() > longest) {
                    appPreferencesRepository.saveIntPreference("stats-longest-session", seconds.toInt())
                }
                if (session.topic != null) {
                    topicIndexProvider(session.lang).recordReading(
                        session.title, session.topic, seconds, session.maxProgress
                    )
                    DebugLog.d(
                        "Personalization",
                        "read '${session.title}' (${session.topic}) for ${seconds.toInt()}s, ${(session.maxProgress * 100).toInt()}%"
                    )
                }
                // Refill the queue instead of clearing it. Clearing used to throw away the
                // already-downloaded next articles after every finished read, so with no network
                // the user could only read the article on screen (the "1-2 articles offline" bug).
                schedulePrefetch()
            } catch (e: Exception) {
                DebugLog.w("Personalization", "could not record the reading session: ${e.message}")
            }
        }
    }

    fun onAppForegrounded() {
        schedulePrefetch()
    }

    fun onAppBackgrounded() {
        prefetchJob?.cancel()
        prefetchJob = null
    }

    /**
     * While the user reads, quietly pick the next random articles and download their text and first
     * pictures, so the Random button feels instant. Nothing is marked as seen until an article is
     * really shown. Keeps up to [PREFETCH_TARGET] articles ready.
     */
    private fun schedulePrefetch() {
        val prefs = preferencesState.value
        if (prefs.dataSaver || !prefs.preloadArticles) return
        // A library build is already using the network; adding prefetch requests would compete
        // with it and help trigger 429s.
        if (_indexState.value.running) return
        if (prefetchJob?.isActive == true) return // a refill is already running
        prefetchJob = viewModelScope.launch(Dispatchers.IO + errorHandler) {
            try {
                var failures = 0
                var attempts = 0
                // Duplicates are cheap (one title lookup) and should not exhaust the refill, so
                // they have their own streak limit instead of counting as network failures.
                var duplicateStreak = 0
                while (failures < 8 && attempts < PREFETCH_TARGET * 6 &&
                    queuedCount() < PREFETCH_TARGET
                ) {
                    attempts++
                    val key = prefetchKey()
                    val t0 = System.nanoTime()
                    // Topic libraries give better picks, but preloading must also work for plain
                    // "random article" users: otherwise they had nothing prepared offline.
                    val picked = pickPrefetchCandidate()
                    if (picked == null) {
                        failures++
                        continue
                    }
                    val title = picked.first.title
                    val duplicate = title in sessionSeen ||
                            synchronized(prefetchLock) { prefetchQueue.any { it.page.title == title } }
                    if (duplicate) {
                        duplicateStreak++
                        if (duplicateStreak >= 12) failures++
                        continue
                    }
                    duplicateStreak = 0
                    val mode = preferencesState.value.renderMode
                    val prefetchLang = preferencesState.value.lang
                    val content = withHost(prefetchLang) {
                        if (mode == "html") wikipediaRepository.getPageHtml(title)
                        else wikipediaRepository.getPageContent(title)
                    }
                    if (key != prefetchKey()) return@launch // settings changed meanwhile: start over later
                    val lang = prefetchLang
                    articleCache.put(lang, title, picked.first, content, contentKind = mode)
                    warmImages(lang, picked.first, content, full = false)
                    synchronized(prefetchLock) {
                        prefetchQueue.add(PrefetchedRandom(picked.first, picked.second, content, key))
                    }
                    persistPrefetchQueue()
                    DebugLog.i(
                        "Prefetch",
                        "next random article ready: '$title' (${content.length} chars, mode=$mode) in ${(System.nanoTime() - t0) / 1_000_000} ms"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: LibraryExhaustedException) {
                DebugLog.d("Prefetch", "library exhausted: nothing to prepare")
            } catch (e: Exception) {
                DebugLog.w("Prefetch", "could not prepare the next article: ${e.message}")
            }
        }
    }

    /**
     * Picks one article for the background queue. Uses the on-device topic library when it is
     * ready, and otherwise asks Wikipedia for a plain random article, so the next articles are
     * always preloaded (the offline reading guarantee does not depend on building a library).
     */
    private suspend fun pickPrefetchCandidate(): Pair<WikiApiPage, String>? {
        if (hasCompleteTopicLibrary()) {
            pickFromIndexWithTitle(markSeen = false)?.let { return it }
        }
        val page = randomArticleFromWikipedia() ?: return null
        return page to page.title
    }

    /** The first prepared article that is still valid; marks it as seen. */
    private suspend fun takePrefetched(): PrefetchedRandom? {
        val currentKey = prefetchKey()
        val index = topicIndexProvider(preferencesState.value.lang)
        while (true) {
            val pre = synchronized(prefetchLock) {
                if (prefetchQueue.isEmpty()) null else prefetchQueue.removeAt(0)
            } ?: return null
            persistPrefetchQueue()
            if (pre.key != currentKey) {
                DebugLog.d("Prefetch", "discarded a stale prepared article ('${pre.page.title}')")
                continue
            }
            if (index.isSeen(pre.libraryTitle)) continue
            // Do not mark as seen yet: the caller may still fail to fetch/show it. loadPage()
            // records it once it is actually on screen.
            return pre
        }
    }

    // 笏笏 On-device topic index 笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏

    private val _indexState = MutableStateFlow(IndexBuildState())
    val indexState: StateFlow<IndexBuildState> = _indexState.asStateFlow()
    private var indexJob: Job? = null

    // Additive builds: saving another topic while one is already building must never cancel the
    // work in progress. New topics are queued and picked up by the same builder after the current
    // topic finishes. The set also deduplicates repeated taps of Save/Add.
    private val indexBuildLock = Any()
    private val pendingIndexBuilds = ConcurrentLinkedQueue<Pair<String, Boolean>>()
    private val scheduledIndexTopics: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * Selected topics = topics enabled in the on-device library (non-destructive: unchecking a
     * topic only turns it off, its downloaded articles stay) plus topics saved in the old
     * `random_topics` preference that do not have a library row yet (queued for building).
     */
    private suspend fun selectedTopicList(): List<String> = withContext(Dispatchers.IO) {
        val index = topicIndexProvider(preferencesState.value.lang)
        val enabled = index.enabledTopics()
        val saved = getRandomTopics()
        (enabled + saved.filter { !index.isBuilt(it) }).distinct()
    }

    private suspend fun hasSelectedTopics(): Boolean = selectedTopicList().isNotEmpty()

    /** True when at least one selected topic can actually serve articles right now. */
    private suspend fun hasCompleteTopicLibrary(): Boolean {
        val topics = selectedTopicList()
        if (topics.isEmpty()) return false
        val index = topicIndexProvider(preferencesState.value.lang)
        return topics.any { index.isComplete(it) }
    }

    /**
     * v7 migration: the old database stored only one flat preference and every topic row was
     * always active. Reconcile the new `topics.enabled` column once, so existing users keep
     * exactly the selection they had.
     */
    private suspend fun migrateTopicSelection() {
        try {
            if (appPreferencesRepository.readBooleanPreference("topics-enabled-v7") == true) return
            val topics = topicIndexProvider(preferencesState.value.lang)
            topics.enableTopics(getRandomTopics())
            appPreferencesRepository.saveBooleanPreference("topics-enabled-v7", true)
            DebugLog.i("IndexBuild", "migrated topic selection to v7 (${getRandomTopics().size} topics)")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DebugLog.w("IndexBuild", "could not migrate topic selection: ${e.message}")
        }
    }

    /**
     * Starts building the index for [topics] that aren't indexed yet (or all of them if [force]).
     * @return true if a build is running (the UI should show progress), false if nothing to do.
     */
    suspend fun startIndexBuild(topics: Set<String>, force: Boolean = false): Boolean =
        withContext(Dispatchers.IO) {
            val index = topicIndexProvider(preferencesState.value.lang)
            val todo = topics.filter { force || index.needsBuild(it) }
            val labels = ALL_TOPICS.associate { it.category to it.label }
            val hints = readTopicHints()
            synchronized(indexBuildLock) {
                if (indexJob?.isActive == true) {
                    // The user added more topics while a build is running. Keep the current build
                    // alive and append the new topics to its work queue.
                    var queued = 0
                    todo.forEach { topic ->
                        if (scheduledIndexTopics.add(topic)) {
                            pendingIndexBuilds.add(topic to force)
                            queued++
                        }
                    }
                    if (queued > 0) {
                        _indexState.update { it.copy(topicTotal = scheduledIndexTopics.size) }
                        DebugLog.i("IndexBuild", "queued $queued topic(s) behind the running build: $todo")
                    }
                    return@synchronized true
                }
                if (todo.isEmpty()) return@synchronized false
                // A previous job has finished: its queued additions are already covered by the
                // fresh todo computed above, so drop any stale queue entries.
                pendingIndexBuilds.clear()
                scheduledIndexTopics.clear()
                scheduledIndexTopics.addAll(todo)
                val now = System.currentTimeMillis()
                _indexState.value = IndexBuildState(
                    running = true, topicTotal = todo.size, startedAtMs = now, lastActivityMs = now
                )
                IndexKeepAliveService.start(appContext)
                indexJob = viewModelScope.launch(Dispatchers.IO + errorHandler) {
                    runIndexBuild(index, todo, labels, hints, force)
                }.also { job ->
                    job.invokeOnCompletion { cause ->
                        IndexKeepAliveService.stop(appContext)
                        // If a topic was queued in the tiny window between the builder's last
                        // drain and its completion, start it as a follow-up job instead of
                        // losing it. Cancellation intentionally drops the queue.
                        if (cause == null) {
                            val queued = mutableListOf<Pair<String, Boolean>>()
                            while (true) {
                                val next = pendingIndexBuilds.poll() ?: break
                                queued.add(next)
                            }
                            if (queued.isNotEmpty()) {
                                viewModelScope.launch(Dispatchers.IO + errorHandler) {
                                    startIndexBuild(
                                        queued.map { it.first }.toSet(),
                                        queued.any { it.second }
                                    )
                                }
                            }
                        }
                    }
                }
                true
            }
        }

    fun cancelIndexBuild() {
        pendingIndexBuilds.clear()
        indexJob?.cancel()
    }

    private suspend fun runIndexBuild(
        index: TopicIndex,
        todo: List<String>,
        labels: Map<String, String>,
        hints: Map<String, TopicHint>,
        refresh: Boolean
    ) {
        val builder = TopicIndexBuilder(
            // The library belongs to one Wikipedia language. Pin the build to it so changing
            // the language setting mid-build cannot mix articles from two Wikipedias.
            LanguagePinnedWikipediaRepository(wikipediaRepository, interceptor, index.lang),
            index,
            includePageViews = preferencesState.value.popularityBias != 0
        )
        val results = mutableListOf<TopicBuildResult>()
        val topicHints = hints.toMutableMap()
        val queue = ArrayDeque<Pair<String, Boolean>>()
        todo.forEach { queue.add(it to refresh) }
        var processed = 0
        val t0 = System.currentTimeMillis()
        DebugLog.i("IndexBuild", "######## LIBRARY BUILD START: ${todo.size} topics $todo refresh=$refresh ${DebugLog.memory()}")
        var cancelled = false
        try {
            while (true) {
                while (queue.isNotEmpty()) {
                    val (topic, topicRefresh) = queue.removeFirst()
                    val label = labels[topic] ?: topic.removePrefix(SEARCH_TOPIC_PREFIX).replace('_', ' ')
                    _indexState.update {
                        it.copy(
                            currentTopic = label, topicIndex = processed,
                            topicTotal = scheduledIndexTopics.size,
                            progress = null, lastActivityMs = System.currentTimeMillis()
                        )
                    }
                    val result = builder.buildTopic(
                        rootCategory = topic,
                        label = label,
                        refresh = topicRefresh,
                        hint = topicHints[topic],
                        progress = { p ->
                            _indexState.update {
                                it.copy(progress = p, lastActivityMs = System.currentTimeMillis())
                            }
                        },
                        onUsable = { usable ->
                            // The builder can spend a little longer on optional page views. Keep the
                            // completed topic in the results/start-browsing state immediately; if the
                            // optional phase is cancelled, the downloaded library remains usable.
                            val existing = results.indexOfFirst { it.topic == usable.topic }
                            if (existing >= 0) results[existing] = usable else results.add(usable)
                            _indexState.update { it.copy(results = results.toList()) }
                            invalidatePrefetch()
                        }
                    )
                    val existing = results.indexOfFirst { it.topic == result.topic }
                    if (existing >= 0) results[existing] = result else results.add(result)
                    processed++
                    _indexState.update {
                        it.copy(
                            results = results.toList(), topicIndex = processed,
                            topicTotal = scheduledIndexTopics.size
                        )
                    }

                    // Pick up topics added while this topic was building. Reading the hints again
                    // makes sure a natural-language category added mid-build gets its exclusions.
                    var queuedAny = false
                    while (true) {
                        val next = pendingIndexBuilds.poll() ?: break
                        queue.add(next)
                        queuedAny = true
                    }
                    if (queuedAny) {
                        topicHints.putAll(readTopicHints())
                        DebugLog.i("IndexBuild", "picked up ${queue.size} queued topic(s) for the active build")
                    }
                }

                // Close the small race where a topic was queued just after the last poll.
                var queuedAny = false
                while (true) {
                    val next = pendingIndexBuilds.poll() ?: break
                    queue.add(next)
                    queuedAny = true
                }
                if (queue.isEmpty()) break
                if (queuedAny) topicHints.putAll(readTopicHints())
            }
        } catch (e: CancellationException) {
            cancelled = true
            throw e
        } finally {
            DebugLog.i(
                "IndexBuild",
                "######## LIBRARY BUILD END: cancelled=$cancelled, ${results.count { it.ok }}/$processed topics ok, " +
                        "${results.sumOf { it.indexed }} articles, took ${(System.currentTimeMillis() - t0) / 1000}s ${DebugLog.memory()}"
            )
            _indexState.update {
                it.copy(
                    running = false, finished = true, cancelled = cancelled,
                    finishedAtMs = System.currentTimeMillis(), topicIndex = processed,
                    topicTotal = scheduledIndexTopics.size, results = results.toList()
                )
            }
            pendingIndexBuilds.clear()
            scheduledIndexTopics.clear()
            // After running=false so the refill is not skipped by the "build in progress" guard.
            invalidatePrefetch() // the library changed
        }
    }

    // 笏笏 Natural-language interests 笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏

    suspend fun resolveInterests(text: String, onStatus: (String) -> Unit): List<InterestMatch> =
        withContext(Dispatchers.IO) { InterestResolver(wikipediaRepository).resolve(text, onStatus) }

    suspend fun getInterestText(): String =
        appPreferencesRepository.readStringPreference("interest_text") ?: ""

    suspend fun saveInterestText(text: String) {
        appPreferencesRepository.saveStringPreference("interest_text", text)
    }

    // 笏笏 Topic hints: the user's own words behind a natural-language topic 笏笏笏笏笏笏笏笏笏

    private suspend fun readTopicHints(): Map<String, TopicHint> =
        (appPreferencesRepository.readStringPreference("topic_hints") ?: "")
            .lines().mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size < 2 || parts[0].isBlank()) null
                else parts[0] to TopicHint(
                    parts[1],
                    if (parts.size > 2 && parts[2].isNotBlank()) parts[2].split(";;").filter { it.isNotBlank() } else emptyList()
                )
            }.toMap()

    /** Remembers, per category, the user's words and exclusions it was found for. */
    suspend fun saveTopicHints(hints: Map<String, TopicHint>) {
        if (hints.isEmpty()) return
        val merged = readTopicHints() + hints
        appPreferencesRepository.saveStringPreference(
            "topic_hints",
            merged.entries.joinToString("\n") {
                "${it.key}\t${it.value.phrase.replace('\n', ' ').replace('\t', ' ')}\t" +
                        it.value.exclusions.joinToString(";;") { e -> e.replace('\n', ' ').replace('\t', ' ') }
            }
        )
    }

    /**
     * "More like this" / "Not interested": stored per article and applied to every later pick by
     * the relevance model. A "not interested" article also disappears and the next one loads.
     */
    private suspend fun rateArticle(action: HomeAction.Rate) {
        DebugLog.i("Feedback", "vote ${action.vote} for '${action.title}' (${action.lang})")
        DebugLog.crumb("vote ${action.vote} '${action.title}'")
        val index = topicIndexProvider(action.lang)
        index.setVote(action.title, action.vote)
        if (preferencesState.value.personalizationEnabled) {
            index.recordVote(action.title, action.vote)
        }
        val key = articleVoteKey(action.lang, action.title)
        _homeScreenState.update { state ->
            state.copy(
                articleVotes = if (action.vote == 0) state.articleVotes - key
                else state.articleVotes + (key to action.vote)
            )
        }
        invalidatePrefetch()
        // A dislike still teaches the picker, but it must not yank the article away while the
        // reader is looking at it (or deciding whether to undo the tap).
        if (action.vote < 0) index.markSeen(action.title)
        if (feedbackNoticesShown.add(action.vote)) {
            snackBarHostState.showSnackbar(action.message)
        }
    }

    /** "Start over": forget which articles were already shown. */
    suspend fun resetSeenArticles() = withContext(Dispatchers.IO) {
        DebugLog.i("Random", "user pressed Start over: clearing the seen list")
        val lang = preferencesState.value.lang
        topicIndexProvider(lang).clearSeen()
        // Clear every other layer that can suppress or replay an article: the old preferences
        // list (still consulted by the legacy picker), the in-memory set and prepared entries.
        appPreferencesRepository.saveStringPreference("random_seen_$lang", "")
        synchronized(sessionSeen) { sessionSeen.clear() }
        invalidatePrefetch()
    }

    /** Removes one topic (and articles only it used) from the on-device library. */
    suspend fun removeTopicFromLibrary(topic: String) = withContext(Dispatchers.IO) {
        DebugLog.i("IndexBuild", "removing topic '$topic' from library")
        topicIndexProvider(preferencesState.value.lang).deleteTopic(topic)
        // Also forget the selection; otherwise it would look like a pending, unbuilt topic and
        // keep hasSelectedTopics() true forever.
        val saved = getRandomTopics().toMutableSet()
        if (saved.remove(topic)) saveRandomTopics(saved)
        invalidatePrefetch()
    }

    /**
     * Unchecking a topic must not throw away its downloaded library: re-downloading hundreds of
     * articles costs minutes and mobile data. It is only switched off (and on again instantly).
     */
    suspend fun setTopicEnabled(topic: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        val index = topicIndexProvider(preferencesState.value.lang)
        index.setTopicEnabled(topic, enabled)
        val saved = getRandomTopics().toMutableSet()
        if (enabled) saved.add(topic) else saved.remove(topic)
        saveRandomTopics(saved)
        // Turning a topic on resumes an interrupted download immediately; a complete library is a
        // no-op because needsBuild() is false. Turning a topic off never deletes anything.
        if (enabled) startIndexBuild(setOf(topic))
        // The prepared queue may contain articles from the topic that was just hidden. Dropping it
        // makes Settings -> topic switch take effect on the very next random pick.
        invalidatePrefetch()
        DebugLog.i("IndexBuild", "topic '$topic' ${if (enabled) "enabled" else "disabled (library kept)"}")
    }

    /** Saves the selection and mirrors it into the database's `enabled` flags. */
    suspend fun saveTopicSelection(topics: Set<String>) = withContext(Dispatchers.IO) {
        topicIndexProvider(preferencesState.value.lang).enableTopics(topics)
        saveRandomTopics(topics)
        invalidatePrefetch()
    }

    /** Totals and the per-topic breakdown of the on-device library. */
    suspend fun getLibraryStats(): LibraryStats = withContext(Dispatchers.IO) {
        topicIndexProvider(preferencesState.value.lang).libraryStats()
    }

    // ── Explicit offline downloads ──────────────────────────────────────────

    /**
     * Chooses up to [count] articles, taking the same number from every enabled topic so a
     * library with a huge and a tiny topic does not download only from the huge one.
     *
     * Only never-seen articles are eligible: the random queue is the user's reading list, and
     * downloading ahead must not silently use up articles they have not read yet. Articles that
     * are already downloaded are skipped too, so pressing Download twice does not re-download
     * the same text.
     */
    private fun balancedOfflineRows(
        count: Int,
        topics: List<String>,
        lang: String,
        index: TopicIndex
    ): List<ArticleRow> {
        if (count <= 0 || topics.isEmpty()) return emptyList()
        val out = ArrayList<ArticleRow>()
        val pickedTitles = HashSet<String>()

        fun pickFor(
            pickTopics: List<String>,
            want: Int
        ) {
            if (want <= 0 || out.size >= count) return
            var added = 0
            // pick() applies ORDER BY RANDOM() before we can reject downloaded rows, so ask for
            // a few extra candidates. Three passes are enough even if most of a small topic has
            // already been downloaded.
            repeat(3) {
                if (added >= want || out.size >= count) return
                index.pick(
                    topics = pickTopics,
                    imagesOnly = false,
                    minBytes = 0,
                    qualityOnly = false,
                    limit = (want - added) * 4,
                    includeSeen = false
                ).forEach { row ->
                    if (added < want && out.size < count &&
                        pickedTitles.add(row.title) &&
                        !articleCache.isOffline(lang, row.title)
                    ) {
                        out.add(row)
                        added++
                    }
                }
            }
        }

        val base = count / topics.size
        val remainder = count % topics.size
        topics.forEachIndexed { i, topic ->
            val want = base + if (i < remainder) 1 else 0
            pickFor(listOf(topic), want)
        }
        // Some tiny topics may not have enough unseen articles: fill from every enabled topic.
        pickFor(topics, count - out.size)
        return out
    }

    /** Size estimate for [count] offline articles, without downloading anything. */
    suspend fun estimateOfflineDownload(count: Int): ArticleCache.OfflineSelection =
        withContext(Dispatchers.IO) {
            val index = topicIndexProvider(preferencesState.value.lang)
            val topics = index.libraryStats().topics.filter { it.enabled }.map { it.topic }
            val rows = balancedOfflineRows(count, topics, preferencesState.value.lang, index)
            // Stored JSON includes page metadata and captions, roughly 1.25× the raw article text.
            val bytes = rows.sumOf { it.bytes.toLong() } + rows.size * 2_048L
            ArticleCache.OfflineSelection(rows.size, bytes)
        }

    /** Downloads [count] articles into the persistent offline cache. Returns how many were stored. */
    suspend fun downloadOfflineArticles(
        count: Int,
        onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Int = withContext(Dispatchers.IO) {
        val lang = preferencesState.value.lang
        val index = topicIndexProvider(lang)
        val topics = index.libraryStats().topics.filter { it.enabled }.map { it.topic }
        val rows = balancedOfflineRows(count, topics, lang, index)
        val kind = if (preferencesState.value.renderMode == "html") "html" else "wikitext"
        var stored = 0
        rows.forEachIndexed { i, row ->
            try {
                if (!articleCache.isOffline(lang, row.title)) {
                    val pageData = withHost(lang) { wikipediaRepository.getPageData(row.title) }
                    val page = pageData.query?.pages?.firstOrNull()
                    if (page != null) {
                        val content = withHost(lang) {
                            if (kind == "html") wikipediaRepository.getPageHtml(page.title)
                            else wikipediaRepository.getPageContent(page.title)
                        }
                        if (content.isNotBlank()) {
                            articleCache.put(
                                lang = lang,
                                title = page.title,
                                page = page,
                                content = content,
                                contentKind = kind,
                                offline = true,
                                // The topic index may spell the title slightly differently (case or
                                // underscores). Store the row title as an alias so the next download
                                // recognises this article as already downloaded.
                                alsoAs = row.title
                            )
                            stored++
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DebugLog.w("Offline", "could not download '${row.title}': ${e.message}")
            }
            onProgress(i + 1, rows.size)
        }
        DebugLog.i("Offline", "downloaded $stored of ${rows.size} articles for offline reading")
        stored
    }

    suspend fun getOfflineStats(): ArticleCache.OfflineStats = withContext(Dispatchers.IO) {
        articleCache.offlineStats()
    }

    suspend fun deleteOfflineArticles() = withContext(Dispatchers.IO) {
        articleCache.deleteAllOffline()
    }

    /** Fraction of the known articles that have fewer views than [v] (0 = least read, 1 = most read). */
    private fun viewsPercentile(v: Int, sorted: IntArray): Double {
        var lo = 0
        var hi = sorted.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (sorted[mid] < v) lo = mid + 1 else hi = mid
        }
        return lo.toDouble() / sorted.size
    }

    private fun weightedPick(
        items: List<ArticleRow>,
        boostQuality: Boolean,
        bias: Int = 0,
        sortedViews: IntArray? = null,
        feedback: Map<String, Double> = emptyMap(),
        affinity: Map<String, Double> = emptyMap()
    ): ArticleRow {
        fun weight(a: ArticleRow): Double {
            var w = kotlin.math.ln(maxOf(a.bytes, 1_000).toDouble())
            if (boostQuality) w *= when (a.tier) { 2 -> 4.0; 1 -> 3.0; else -> 1.0 }
            if (a.hasImage) w *= 1.3
            // Articles close to the topic's own category are more on-topic than deep ones.
            w *= 1.0 / (1.0 + 0.6 * a.depth)
            // Articles whose title/description match the topic's key words are more on-topic.
            if (a.rel > 0) w *= 1.0 + 0.5 * minOf(a.rel, 200) / 100.0   // rel is a percent: 100 = typical best match
            w *= feedback[a.title] ?: 1.0
            // Learned topic affinity (opt-in): read more from topics the user actually reads.
            if (affinity.isNotEmpty() && a.topic.isNotEmpty()) w *= affinity[a.topic] ?: 1.0
            // Popularity preference: favour the least- or most-read end of the library.
            if (bias != 0 && sortedViews != null && a.views >= 0) {
                val p = viewsPercentile(a.views, sortedViews)
                val strength = 1.5 * kotlin.math.abs(bias)
                w *= if (bias < 0) Math.pow(1.05 - p, strength) else Math.pow(p + 0.05, strength)
            }
            return w
        }
        val total = items.sumOf { weight(it) }
        var r = Random.nextDouble() * total
        for (a in items) {
            r -= weight(a)
            if (r <= 0) return a
        }
        return items.last()
    }

    /**
     * Picks a never-shown article from the pre-built library.
     * @return null if no selected topic is built yet or the network fetch failed
     * @throws LibraryExhaustedException when every matching article has already been shown
     */
    private suspend fun pickFromIndex(): WikiApiPage? = pickFromIndexWithTitle(markSeen = true)?.first

    /**
     * Like [pickFromIndex] but also returns the library title, and [markSeen] = false only
     * *chooses* (used to prepare the next article in the background; it is marked as seen when
     * it is actually shown).
     */
    private suspend fun pickFromIndexWithTitle(markSeen: Boolean): Pair<WikiApiPage, String>? {
        val topics = selectedTopicList()
        if (topics.isEmpty()) return null
        val prefs = preferencesState.value
        val index = topicIndexProvider(prefs.lang)
        // Only complete libraries: a topic whose crawl finished but whose details/relevance are
        // still being fetched would otherwise expose raw, unrelated category members.
        val built = topics.filter { index.isComplete(it) }
        DebugLog.crumb("pickFromIndex topics=${topics.size} built=${built.size}")
        DebugLog.d("Random", "selected topics=${topics.size} indexed=${built.size} ${topics.filter { it !in built }.let { if (it.isEmpty()) "" else "NOT indexed: $it" }}")
        if (built.isEmpty()) return null

        val minBytes = if (prefs.avoidStubs) 5_000 else 0
        val bias = prefs.popularityBias
        // A bigger sample when a popularity preference is set: percentiles need enough articles.
        val sampleSize = if (bias != 0) 1200 else 400
        // Thresholds follow the audit: coverage is the absolute check that was missing. A high
        // relative score can still mean "shares no words with the topic at all" when the whole
        // field is weak, so strictness raises both gates.
        //
        // Small/odd libraries can have no article at the requested gate even though they do have
        // perfectly readable articles. Falling through progressively lower gates keeps Balanced /
        // Strict / Very strict usable instead of showing "library exhausted" for every level except
        // Broad. The fallback is explicit in the debug log.
        val tiers = when (prefs.topicStrictness) {
            0 -> listOf(0 to 0)
            1 -> listOf(25 to 20, 10 to 5, 0 to 0)
            2 -> listOf(45 to 35, 25 to 20, 10 to 5, 0 to 0)
            else -> listOf(65 to 50, 45 to 35, 25 to 20, 0 to 0)
        }
        var minRel = tiers.first().first
        var minCov = tiers.first().second
        var pool = emptyList<ArticleRow>()
        for ((relGate, covGate) in tiers) {
            val attempt = index.pick(
                built, prefs.onlyArticlesWithImages, minBytes, false, sampleSize, relGate, covGate
            )
            if (attempt.isNotEmpty() || (relGate == 0 && covGate == 0)) {
                minRel = relGate
                minCov = covGate
                pool = attempt
            }
            DebugLog.d(
                "Random",
                "pool=${attempt.size} (strictness=${prefs.topicStrictness}, rel>=$relGate, cov>=$covGate, " +
                        "images=${prefs.onlyArticlesWithImages}, minBytes=$minBytes)"
            )
            if (attempt.isNotEmpty()) break
            if (relGate != 0 || covGate != 0) {
                DebugLog.w(
                    "Random",
                    "no article reaches strictness=${prefs.topicStrictness} (rel>=$relGate, cov>=$covGate); " +
                            "trying a lower gate instead of failing"
                )
            }
        }
        if (pool.isEmpty()) {
            // Nothing at the requested strictness/images/stub filters. Before giving up, relax
            // those filters: a readable article from the topic is far better than an error page.
            val relaxed = index.pick(built, false, 0, false, sampleSize, 0, 0)
            if (relaxed.isNotEmpty()) {
                DebugLog.w(
                    "Random",
                    "no article matched the current filters; relaxing them for this pick (${relaxed.size} candidates)"
                )
                pool = relaxed
                minRel = 0
                minCov = 0
            } else {
                // Every article in the library has already been shown. Repeat the least-recently
                // relevant one rather than failing: the user can always press Start over.
                val repeats = index.pick(
                    built, false, 0, false, sampleSize, 0, 0, includeSeen = true
                )
                if (repeats.isEmpty()) {
                    throw LibraryExhaustedException(index.unseenCount(built))
                }
                DebugLog.i("Random", "all topic articles were seen: offering a repeat (${repeats.size} candidates)")
                notifyAllSeenRepeat()
                pool = repeats
                minRel = 0
                minCov = 0
            }
        }
        val quality = if (prefs.preferQuality)
            index.pick(built, prefs.onlyArticlesWithImages, minBytes, true, 100, minRel, minCov)
        else emptyList()

        var candidates = (pool + quality).distinctBy { it.title }

        // Popularity preference (views of the last 30 days). Percentiles are measured within the
        // candidates themselves, which are a random sample of the library.
        val sortedViews = candidates.filter { it.views >= 0 }.map { it.views }.sorted().toIntArray()
        val haveViews = sortedViews.size >= 20
        if (bias != 0 && !haveViews) {
            DebugLog.w("Random", "popularity preference $bias set but only ${sortedViews.size} candidates have page-view data (rebuild the library)")
        }
        if (haveViews && (bias == -2 || bias == 2)) {
            val limited = candidates.filter {
                it.views >= 0 && (viewsPercentile(it.views, sortedViews).let { p -> if (bias < 0) p <= 0.25 else p >= 0.75 })
            }
            DebugLog.d("Random", "popularity-only filter ($bias): ${candidates.size} -> ${limited.size} candidates")
            if (limited.isNotEmpty()) candidates = limited
        }
        // Done after the popularity filter: scoring 1200 candidates cost ~240 ms of CPU per pick,
        // scoring the ~90 that survive costs a few ms (less CPU contention with the UI thread).
        // Learn from the user's votes: articles that read like ones they rejected are removed or
        // down-weighted, ones like those they liked are favoured. Local, instant, no network.
        var feedback: Map<String, Double> = emptyMap()
        val liked = index.votedTitles(1)
        val disliked = index.votedTitles(-1)
        if (liked.isNotEmpty() || disliked.isNotEmpty()) {
            val t0 = System.nanoTime()
            // Only 1-3 articles are picked, so a random 400 is plenty to choose from (percentiles above
            // were measured on the whole sample); this bounds the scoring cost when no popularity
            // filter shrank the pool.
            if (candidates.size > 400) candidates = candidates.shuffled().take(400)
            val voteDocs = index.docsFor(liked + disliked).associateBy { it.key }
            feedback = RelevanceModel.feedbackMultipliers(
                index.docsFor(candidates.map { it.title }),
                liked.mapNotNull { voteDocs[it] },
                disliked.mapNotNull { voteDocs[it] }
            )
            val kept = candidates.filter { (feedback[it.title] ?: 1.0) > 0.0 }
            DebugLog.d("Random", "PERF feedback (${liked.size} liked, ${disliked.size} disliked): ${candidates.size} -> ${kept.size} candidates in ${(System.nanoTime() - t0) / 1_000_000} ms")
            if (kept.isNotEmpty()) candidates = kept
        }

        val affinity = if (prefs.personalizationEnabled) index.topicAffinity() else emptyMap()
        val remaining = candidates.toMutableList()
        repeat(3) {
            if (remaining.isEmpty()) return null
            val chosen = weightedPick(
                remaining, prefs.preferQuality, bias,
                if (haveViews) sortedViews else null, feedback, affinity
            )
            remaining.remove(chosen)
            if (markSeen) index.markSeen(chosen.title)
            DebugLog.i("Random", "CHOSEN '${chosen.title}' bytes=${chosen.bytes} tier=${chosen.tier} img=${chosen.hasImage} depth=${chosen.depth} rel=${chosen.rel} (candidates=${remaining.size + 1})")
            val page = try {
                // The index is per language: make sure the metadata request goes to the same
                // Wikipedia as the titles, not to whichever article happened to be open before.
                withHost(prefs.lang) {
                    wikipediaRepository.getPageData(chosen.title).query?.pages?.getOrNull(0)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) { null }
            // A deleted/renamed article comes back without a page id: try another one.
            if (page != null && page.pageId != null) {
                // Also remember the canonical title: if the library title was an alias of
                // another entry, that entry must not be offered later either.
                if (page.title != chosen.title) {
                    DebugLog.i("Random", "'${chosen.title}' resolved to '${page.title}': marking both as seen")
                    if (markSeen) index.markSeen(page.title)
                }
                return Pair(page, chosen.title)
            }
            DebugLog.w("Random", "'${chosen.title}' returned no usable page (missing?), trying another")
        }
        return null
    }

    private suspend fun persistedSeen(lang: String): List<String> =
        appPreferencesRepository.readStringPreference("random_seen_$lang")
            ?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()

    /** Remembers [title] across launches so Random never re-serves it (capped at 1500). */
    private fun rememberSeen(lang: String, title: String) {
        viewModelScope.launch(Dispatchers.IO + errorHandler) {
            seenMutex.withLock {
                val updated = (persistedSeen(lang).filter { it != title } + title).takeLast(1500)
                appPreferencesRepository.saveStringPreference(
                    "random_seen_$lang", updated.joinToString("\n")
                )
            }
        }
    }
    private var searchDebounceJob: Job? = null

    val appStatus = appStatusFlow

    private var sections = 0
    private var currentSection = 0

    private var colorScheme: ColorScheme = lightColorScheme()
    private var typography: Typography = Typography()
    private var fromLink: Boolean = false

    /**
     * The interceptor has one global host, but feed/article/search requests can overlap (for
     * example a German feed loading while an English article is restored). Serialising just the
     * host-sensitive network calls and setting the host inside the lock prevents one request from
     * silently travelling to another language's Wikipedia. The same gate is shared with the
     * background library builder.
     */
    private suspend fun <T> withHost(lang: String, block: suspend () -> T): T =
        HostGate.withHost(interceptor, lang, block)

    init {
        // Open a previously selected Kiwix archive in the background. Nothing is loaded when no
        // archive has ever been chosen, so the native library stays out of the normal start-up.
        viewModelScope.launch(Dispatchers.IO + errorHandler) {
            runCatching { kiwixRepository.refresh() }
        }

        // Folders already in use, for the save dialog's quick picks. A Flow keeps this current
        // after creating a folder from another screen.
        viewModelScope.launch(Dispatchers.IO + errorHandler) {
            try {
                appDatabaseRepository.getSavedArticleFolders().collect { folders ->
                    _homeScreenState.update { it.copy(savedFolders = folders) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DebugLog.w("Save", "could not read saved folders: ${e.message}")
            }
        }

        // Changing the Wikipedia language must not leave an article (or an old error page) from
        // the previous language on screen. Reset to the feed in the new language. This observer
        // lives in the view model, not the composable, so it also runs while Settings is open.
        viewModelScope.launch(errorHandler) {
            var lastLang = preferencesState.value.lang
            preferencesState.map { it.lang }.distinctUntilChanged().collect { lang ->
                if (lang != lastLang) {
                    lastLang = lang
                    DebugLog.i("Language", "Wikipedia language changed to '$lang': reloading feed")
                    loadFeed()
                }
            }
        }

        // What the app was doing, appended to every crash report.
        DebugLog.stateProvider = {
            val top = backStack.lastOrNull()
            "backStack=${backStack.size} " + backStack.joinToString(" > ") {
                if (it is HomeSubscreen.Article) "Article('${it.title}', sections=${it.extract.size})" else it.javaClass.simpleName
            } + "\nisLoading=${_homeScreenState.value.isLoading} loaderJobActive=${loaderJob?.isActive == true} " +
                    "indexRunning=${_indexState.value.running} lang=${preferencesState.value.lang} top=${top?.javaClass?.simpleName}"
        }
        viewModelScope.launch(Dispatchers.IO + errorHandler) {
            var collectAppStatus = true

            userLangsFlow.first().let {
                if (it.isEmpty()) insertUserLanguage(UserLanguage("en", "English", true))
            }

            appStatus.takeWhile { collectAppStatus }.collect {
                if (it == AppStatus.INITIALIZED) {
                    collectAppStatus = false
                    startUp()
                }
            }
        }
    }

    // 笏笏 Welcome tutorial 笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏

    private val _showTutorial = MutableStateFlow(false)
    val showTutorial: StateFlow<Boolean> = _showTutorial.asStateFlow()
    private val _tutorialStep = MutableStateFlow(0)
    val tutorialStep: StateFlow<Int> = _tutorialStep.asStateFlow()

    /** True when every article in the current topics has been shown: offer a reset. */
    private val _topicExhausted = MutableStateFlow(false)
    val topicExhausted: StateFlow<Boolean> = _topicExhausted.asStateFlow()

    private val _donationPromptAt = MutableStateFlow<Int?>(null)
    val donationPromptAt: StateFlow<Int?> = _donationPromptAt.asStateFlow()

    fun dismissDonationPrompt() {
        _donationPromptAt.value = null
    }

    /** Used by the hidden developer menu to preview the optional support prompt. */
    fun showDonationPromptForTest() {
        _donationPromptAt.value = 100
    }

    /** Counts one user-opened article for statistics and the optional donation milestones. */
    private fun noteArticleOpened() {
        viewModelScope.launch(Dispatchers.IO + errorHandler) {
            try {
                val count = (appPreferencesRepository.readStringPreference("article-open-count")
                    ?.toIntOrNull() ?: 0) + 1
                appPreferencesRepository.saveStringPreference(
                    "article-open-count", count.toString()
                )
                val dayKey = statsDayKey()
                val dayOpens = appPreferencesRepository.readIntPreference("stats-day-opens-$dayKey") ?: 0
                appPreferencesRepository.saveIntPreference("stats-day-opens-$dayKey", dayOpens + 1)

                // Optional support prompts, disabled by default and never shown to supporters.
                if (preferencesStateMutableFlow.value.donationPrompts &&
                    !donationSupporterProvider() &&
                    !DeveloperMode.donationPromptsSuppressed(appContext)
                ) {
                    val lastPrompt =
                        appPreferencesRepository.readIntPreference("donation-prompt-last") ?: 0
                    val milestone = DONATION_PROMPT_MILESTONES.firstOrNull {
                        count >= it && it > lastPrompt
                    }
                    if (milestone != null) {
                        appPreferencesRepository.saveIntPreference(
                            "donation-prompt-last", milestone
                        )
                        _donationPromptAt.value = milestone
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DebugLog.w("Donation", "could not update the open counter: ${e.message}")
            }
        }
    }

    // ── Dictionary and vocabulary ───────────────────────────────────────────

    private fun defineWord(word: String) {
        val clean = word.trim().take(80)
        if (clean.isEmpty()) return
        _dictionaryLookup.value = DictionaryLookup(clean)
        viewModelScope.launch(Dispatchers.IO + errorHandler) {
            val definition = try {
                wikipediaRepository.getWiktionaryDefinition(clean, preferencesState.value.lang)
            } catch (e: Exception) {
                DebugLog.w("Dictionary", "lookup failed for '$clean': ${e.message}")
                null
            }
            _dictionaryLookup.value = DictionaryLookup(
                word = clean,
                definition = definition,
                loading = false,
                error = if (definition == null) "No definition found" else null
            )
        }
    }

    private fun addVocabularyWord(word: String) {
        val clean = word.trim().take(80)
        if (clean.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO + errorHandler) {
            val definition = _dictionaryLookup.value
                ?.takeIf { it.word.equals(clean, ignoreCase = true) }
                ?.definition
                ?: try {
                    wikipediaRepository.getWiktionaryDefinition(clean, preferencesState.value.lang)
                } catch (e: Exception) {
                    null
                }
            val title = (backStack.lastOrNull() as? HomeSubscreen.Article)?.title
            appDatabaseRepository.insertVocabularyWord(
                VocabularyWord(
                    word = clean,
                    lang = preferencesState.value.lang,
                    definition = definition,
                    articleTitle = title
                )
            )
            snackBarHostState.showSnackbar("Added \"$clean\" to vocabulary")
        }
    }

    fun dismissDictionary() {
        _dictionaryLookup.value = null
    }

    private fun statsDayKey(date: java.time.LocalDate = java.time.LocalDate.now()): String =
        date.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)

    /** Local reading totals for the statistics screen. */
    suspend fun getReadingStats(): ReadingStats = withContext(Dispatchers.IO) {
        val prefs = appPreferencesRepository.readAllPreferences()
        val today = java.time.LocalDate.now()
        val week = (0L..6L).map { today.minusDays(it) }
        fun secondsOn(date: java.time.LocalDate) = prefs.ints["stats-day-seconds-${statsDayKey(date)}"] ?: 0
        fun opensOn(date: java.time.LocalDate) = prefs.ints["stats-day-opens-${statsDayKey(date)}"] ?: 0
        val dayKeys = prefs.ints.keys
            .filter { it.startsWith("stats-day-seconds-") || it.startsWith("stats-day-opens-") }
            .map { it.substringAfterLast('-') }
            .toSet()
        // Streak: consecutive days with reading, counted back from today (or from yesterday while
        // today has no reading yet, so the streak is not shown as lost in the morning).
        var cursor = if (statsDayKey(today) in dayKeys) today else today.minusDays(1)
        var streak = 0
        while (statsDayKey(cursor) in dayKeys) {
            streak++
            cursor = cursor.minusDays(1)
        }
        val bestDay = prefs.ints.entries
            .filter { it.key.startsWith("stats-day-seconds-") }
            .maxOfOrNull { it.value } ?: 0
        ReadingStats(
            readingTodaySeconds = secondsOn(today).toLong(),
            readingWeekSeconds = week.sumOf { secondsOn(it).toLong() },
            articlesToday = opensOn(today),
            articlesWeek = week.sumOf { opensOn(it) },
            activeDays = dayKeys.size,
            currentStreakDays = streak,
            longestSessionSeconds = (prefs.ints["stats-longest-session"] ?: 0).toLong(),
            bestDaySeconds = bestDay.toLong(),
            articlesOpened = prefs.strings["article-open-count"]?.toIntOrNull() ?: 0,
            articlesInHistory = appDatabaseRepository.countViewHistory(),
            savedArticles = appDatabaseRepository.countSavedArticles(),
            savedHighlights = appDatabaseRepository.countSavedHighlights(),
            vocabularyWords = appDatabaseRepository.countVocabularyWords(),
            readingSeconds = prefs.ints["stats-reading-seconds"]?.toLong() ?: 0L,
            readingSessions = prefs.ints["stats-reading-sessions"] ?: 0
        )
    }

    fun getVocabularyWords(): kotlinx.coroutines.flow.Flow<List<VocabularyWord>> =
        appDatabaseRepository.getVocabularyWords()

    suspend fun getCommonsImages(title: String): List<CommonsImage> =
        withContext(Dispatchers.IO) {
            val lang = (backStack.lastOrNull() as? HomeSubscreen.Article)?.currentLang
                ?: preferencesState.value.lang
            wikipediaRepository.getCommonsImages(title, lang)
        }

    private val commonsGalleryCache = HashMap<String, com.wikirandomizerp.app.data.CommonsCategoryGallery>()
    private val commonsGalleryMisses = HashMap<String, Long>()

    /**
     * The Wikimedia Commons category gallery for the article [title], shown at the bottom of the
     * article. Results are kept in memory so switching tabs or scrolling back does not refetch;
     * articles without a category are not asked again for ten minutes.
     */
    suspend fun getCommonsCategoryGallery(
        title: String,
        lang: String
    ): com.wikirandomizerp.app.data.CommonsCategoryGallery? {
        val key = "$lang:$title"
        synchronized(commonsGalleryCache) {
            commonsGalleryCache[key]?.let { return it }
            val missAt = commonsGalleryMisses[key]
            if (missAt != null && System.currentTimeMillis() - missAt < 10 * 60 * 1000L) return null
        }
        val gallery = withContext(Dispatchers.IO) {
            wikipediaRepository.getCommonsCategoryGallery(title, lang)
        }
        synchronized(commonsGalleryCache) {
            if (gallery != null) commonsGalleryCache[key] = gallery
            else commonsGalleryMisses[key] = System.currentTimeMillis()
        }
        return gallery
    }

    /** Backups kept inside the app (made automatically before updates and before restores). */
    suspend fun listAutoBackups(): List<com.wikirandomizerp.app.data.BackupInfo> =
        withContext(Dispatchers.IO) { BackupManager(appContext).listAutoBackups() }

    /** Saves a backup to the place the user picked with the system file picker. */
    fun exportBackup(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO + errorHandler) {
            try {
                BackupManager(appContext).exportTo(uri)
                snackBarHostState.showSnackbar(
                    appContext.getString(com.wikirandomizerp.app.R.string.backupCreated)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DebugLog.e("Backup", "could not export backup", e)
                snackBarHostState.showSnackbar(
                    appContext.getString(com.wikirandomizerp.app.R.string.backupFailed)
                )
            }
        }
    }

    /**
     * Replaces all app data with the backup at [uri] (or the automatic backup [file]) and then
     * restarts the app, so every screen starts from the restored data.
     */
    fun restoreBackup(uri: android.net.Uri? = null, file: java.io.File? = null) {
        viewModelScope.launch(Dispatchers.IO + errorHandler) {
            try {
                val stream = when {
                    file != null -> java.io.FileInputStream(file)
                    uri != null -> appContext.contentResolver.openInputStream(uri)
                    else -> null
                } ?: throw java.io.IOException("Cannot open the backup")
                when (val result = stream.use { BackupManager(appContext).restore(it) }) {
                    is com.wikirandomizerp.app.data.RestoreResult.Success -> {
                        DebugLog.i("Backup", "restore finished; restarting the app")
                        BackupManager.restartApp(appContext)
                    }
                    is com.wikirandomizerp.app.data.RestoreResult.Failed -> {
                        snackBarHostState.showSnackbar(result.reason)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DebugLog.e("Backup", "could not restore backup", e)
                snackBarHostState.showSnackbar(
                    appContext.getString(com.wikirandomizerp.app.R.string.restoreFailed)
                )
            }
        }
    }

    /** Creates a local zip backup and opens the system share sheet so the user can save it. */
    fun createBackup() {
        viewModelScope.launch(Dispatchers.IO + errorHandler) {
            try {
                val file = BackupManager(appContext).createBackup()
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    appContext,
                    "${appContext.packageName}.fileprovider",
                    file
                )
                val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                appContext.startActivity(
                    android.content.Intent.createChooser(
                        share,
                        appContext.getString(com.wikirandomizerp.app.R.string.backupShare)
                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DebugLog.e("Backup", "could not create backup", e)
                snackBarHostState.showSnackbar(
                    appContext.getString(com.wikirandomizerp.app.R.string.backupFailed)
                )
            }
        }
    }

    fun deleteVocabularyWord(id: Long) {
        viewModelScope.launch(Dispatchers.IO + errorHandler) {
            appDatabaseRepository.deleteVocabularyWord(id)
        }
    }

    fun dismissTopicExhausted() {
        _topicExhausted.value = false
    }

    /** Yes on the "you have read everything" dialog: reset the history and pick a fresh article. */
    fun resetTopicHistoryAndReload() {
        _topicExhausted.value = false
        viewModelScope.launch(Dispatchers.IO + errorHandler) {
            resetSeenArticles()
            loadPage(title = null, random = true)
        }
    }

    fun setTutorialStep(step: Int) {
        _tutorialStep.value = step
    }

    fun finishTutorial() {
        _showTutorial.value = false
        viewModelScope.launch(Dispatchers.IO + errorHandler) {
            appPreferencesRepository.saveBooleanPreference("tutorial-done", true)
        }
    }

    fun replayTutorial() {
        _tutorialStep.value = 0
        _showTutorial.value = true
    }

    // 笏笏 Start-up: reopen the last article, prepare random ones 笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏

    private suspend fun readLastArticle(): Pair<String, String>? {
        val title = appPreferencesRepository.readStringPreference("last-article-title") ?: return null
        val lang = appPreferencesRepository.readStringPreference("last-article-lang") ?: return null
        return if (title.isBlank() || lang.isBlank()) null else Pair(title, lang)
    }

    private suspend fun startUp() {
        migrateTopicSelection()
        try {
            // Bound the permanent seen list: oldest entries may be offered again eventually.
            topicIndexProvider(preferencesState.value.lang).pruneSeen()
        } catch (e: Exception) {
            DebugLog.w("Startup", "could not prune the seen list: ${e.message}")
        }
        try {
            if (appPreferencesRepository.readBooleanPreference("tutorial-done") != true) {
                _showTutorial.value = true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DebugLog.w("Startup", "could not read the tutorial flag: ${e.message}")
        }

        val last = try {
            readLastArticle()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        // Restore the tab list before reopening the article so the restored article can activate
        // the matching tab instead of creating a duplicate.
        restoreTabs()

        val currentLang = preferencesState.value.lang
        if (last != null && last.second == currentLang) {
            // Always reopen what the user was reading, even if the text cache was evicted while
            // the app slept. The old code fell back to a feed/random article in that case, which
            // looked like the app had forgotten the reader's place.
            val restoredTab = homeScreenState.value.tabs.firstOrNull {
                it.title.equals(last.first, ignoreCase = true) && it.lang == last.second
            }
            DebugLog.i("Startup", "reopening the last article '${last.first}' (${last.second})")
            loadPage(
                title = last.first,
                lang = null,
                fromRestore = true,
                initialScroll = restoredTab?.let { it.scrollIndex to it.scrollOffset }
            )
            loadFeed(silent = true)
        } else {
            // No restorable article, or the Wikipedia language changed since it was read: show a
            // feed in the current language instead of reopening an article in the old one.
            loadFeed()
        }

        restorePrefetchQueue()
        schedulePrefetch()

        // An interrupted build (app closed, process killed, device slept) resumes by itself. The
        // membership rows are already on disk, so this only fills in the missing details.
        viewModelScope.launch(Dispatchers.IO + errorHandler) {
            delay(3_000)
            resumePendingLibraryBuild()
        }
    }

    /** Restarts incomplete saved libraries without any prompt. */
    private suspend fun resumePendingLibraryBuild() {
        if (indexJob?.isActive == true) return
        val index = topicIndexProvider(preferencesState.value.lang)
        val pending = selectedTopicList().filter { index.needsBuild(it) }.toSet()
        if (pending.isEmpty()) return
        DebugLog.i("IndexBuild", "resuming interrupted library build for $pending")
        startIndexBuild(pending)
    }

    /**
     * Called when the app goes to the background: remembers the article on screen so the next
     * launch can reopen it (or clears it if the user was on the feed).
     */
    fun saveSessionState() {
        captureCurrentScroll()
        finishReading()
        val top = backStack.lastOrNull()
        val shown = if (top is HomeSubscreen.Image) backStack.getOrNull(backStack.lastIndex - 1) else top
        val article = shown as? HomeSubscreen.Article
        val articleLang = article?.currentLang
        val onFeed = shown is HomeSubscreen.Feed || shown is HomeSubscreen.Logo
        viewModelScope.launch(Dispatchers.IO + errorHandler) {
            if (article != null && articleLang != null && article.title != "Error" && article.pageId != null) {
                appPreferencesRepository.saveStringPreference("last-article-title", article.title)
                appPreferencesRepository.saveStringPreference("last-article-lang", articleLang)
            } else if (onFeed) {
                appPreferencesRepository.saveStringPreference("last-article-title", "")
            }
            // Flush the reading position now instead of waiting for the debounce: the process may
            // be killed while the app is in the background.
            val current = homeScreenState.value
            writeTabs(current.tabs, current.activeTabId)
        }
    }

    fun setFilesDir(path: String) {
        filesDir = path
    }

    fun setCompositionLocals(cs: ColorScheme, tg: Typography) {
        colorScheme = cs
        typography = tg
    }

    fun onAction(action: HomeAction) {
        DebugLog.d("Action", "onAction ${action::class.simpleName}")
        DebugLog.crumb("action ${action::class.simpleName}")
        when (action) {
            is HomeAction.DeleteUserLanguage -> deleteUserLanguage(action.lang)
            is HomeAction.InsertUserLanguage -> viewModelScope.launch(errorHandler) {
                insertUserLanguage(action.userLanguage)
            }

            is HomeAction.LoadFeed -> loadFeed()
            is HomeAction.OpenInNewTab -> openInNewTab(action.title, action.lang)
            HomeAction.NewTab -> loadPage(title = null, random = true, forceNewTab = true)
            is HomeAction.SwitchTab -> switchToTab(action.id)
            is HomeAction.CloseTab -> closeTab(action.id)
            is HomeAction.ReopenClosedTab -> reopenClosedTab()
            HomeAction.NavigateBack -> navigateHistory(-1)
            HomeAction.NavigateForward -> navigateHistory(1)
            is HomeAction.OpenInBrowser -> {
                val project = if (preferencesState.value.wikivoyageEnabled) "wikivoyage" else "wikipedia"
                val url = "https://${action.lang}.$project.org/wiki/" +
                        Uri.encode(action.title.replace(' ', '_'))
                runCatching {
                    appContext.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }.onFailure {
                    DebugLog.w("Browser", "could not open '$url': ${it.message}")
                }
            }
            is HomeAction.LoadPage ->
                // `[[#Section]]` same-page anchors give an empty title: there is nothing to open, and
                // fetching "" used to push an "error" page onto the stack.
                if (action.random || action.title == null || action.title.isNotBlank())
                    loadPage(
                        title = action.title,
                        lang = action.lang,
                        random = action.random
                    )

            is HomeAction.LoadSavedArticle -> {
                // Same rule as loadPage: only one article load at a time (they share the global
                // reference state), and a newer request supersedes an older one.
                val previous = loaderJob
                previous?.cancel()
                loaderJob = viewModelScope.launch(errorHandler) {
                    previous?.join()
                    loadSavedArticle(action.pageId, action.lang)
                }
            }

            is HomeAction.LoadSearch -> loadSearch(action.query)
            is HomeAction.LoadSearchResultsDebounced -> loadSearchResultsDebounced(action.query)
            is HomeAction.MarkUserLanguageSelected -> markUserLanguageSelected(action.lang)
            is HomeAction.ReloadPage -> reloadPage(action.persistLang)
            is HomeAction.Rate -> viewModelScope.launch(Dispatchers.IO + errorHandler) {
                rateArticle(action)
            }

            is HomeAction.LoadArticleVote -> viewModelScope.launch(Dispatchers.IO + errorHandler) {
                val vote = runCatching {
                    topicIndexProvider(action.lang).getVote(action.title)
                }.getOrDefault(0)
                val key = articleVoteKey(action.lang, action.title)
                _homeScreenState.update { state ->
                    state.copy(
                        articleVotes = if (vote == 0) state.articleVotes - key
                        else state.articleVotes + (key to vote)
                    )
                }
            }

            is HomeAction.SaveHighlight -> viewModelScope.launch(errorHandler) {
                val top = backStack.lastOrNull() as? HomeSubscreen.Article ?: return@launch
                val text = action.text.trim()
                if (text.isEmpty()) return@launch
                val articleLang = top.currentLang ?: preferencesState.value.lang
                val topic = withContext(Dispatchers.IO) {
                    try {
                        topicIndexProvider(articleLang).topicsForTitle(top.title).firstOrNull()
                    } catch (e: Exception) {
                        null
                    }
                }
                try {
                    // Auto-save and the manual Highlight action can race; keep one row per
                    // (title, language, text) and let a repeat tap just say it is already saved.
                    val existing = appDatabaseRepository.findSavedHighlight(
                        top.title,
                        articleLang,
                        text
                    )
                    if (existing != null) {
                        snackBarHostState.showSnackbar(
                            appContext.getString(R.string.highlightAlreadySaved)
                        )
                        return@launch
                    }
                    appDatabaseRepository.insertSavedHighlight(
                        SavedHighlight(
                            pageId = top.pageId,
                            lang = articleLang,
                            title = top.title,
                            text = text,
                            thumbnail = top.thumbnail?.source ?: top.photo?.source,
                            description = top.photoDesc,
                            topic = topic
                        )
                    )
                    // Show the new highlight in the open article immediately, not only after a
                    // restart. The article state is also what the renderer tints.
                    mutateTopArticle(top.title) {
                        it.copy(highlights = (it.highlights + text).distinct())
                    }
                    // Confirmation only: no undo action, and dismiss after ~2.5 s instead of the
                    // platform's long default. A selection must never be saved without this tap.
                    withTimeoutOrNull(2_500) {
                        snackBarHostState.showSnackbar(
                            message = appContext.getString(R.string.highlightSaved)
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    DebugLog.e("Highlight", "could not save highlight", e)
                    snackBarHostState.showSnackbar(appContext.getString(R.string.highlightSaveFailed))
                }
            }

            is HomeAction.DefineWord -> defineWord(action.word)
            is HomeAction.AddVocabularyWord -> addVocabularyWord(action.word)
            HomeAction.DismissDictionary -> dismissDictionary()

            is HomeAction.SaveArticle -> viewModelScope.launch(errorHandler) {
                val last = backStack.lastOrNull() as? HomeSubscreen.Article
                if (last != null) {
                    if (last.savedStatus == SavedStatus.NOT_SAVED) {
                        val status = saveArticle(folder = action.folder, note = action.note)
                        if (status != WRStatus.SUCCESS)
                            snackBarHostState.showSnackbar(
                                String.format(action.unableToSaveError, status.name)
                            )
                        delay(150L)
                    } else if (last.savedStatus == SavedStatus.SAVED) {
                        val status = deleteArticle(
                            pageId = last.pageId ?: 0,
                            // The article's own language: saveArticle() stores it under currentLang,
                            // so deleting by the UI language silently deleted nothing for other languages.
                            lang = last.currentLang ?: preferencesState.value.lang
                        )
                        if (status != WRStatus.SUCCESS)
                            snackBarHostState.showSnackbar(
                                String.format(action.unableToDeleteError, status.name)
                            )
                    }
                }
            }

            is HomeAction.SetQuery -> textFieldState.setTextAndPlaceCursorAtEnd(action.text)
            is HomeAction.UpdateLanguageSearchStr -> updateLanguageSearchStr(action.str)
            is HomeAction.UpdateRef -> updateRef(action.ref)
            is HomeAction.FocusSearchBar -> {
                focusSearchBar()
                textFieldState.setTextAndPlaceCursorAtEnd(textFieldState.text.toString())
            }

            is HomeAction.HideRef -> hideRef()
            is HomeAction.LoadRandom -> loadPage(title = null, random = true)
            is HomeAction.ScrollToTop -> viewModelScope.launch(errorHandler) {
                // One read, then act: the loader may change the stack from another thread.
                when (val top = backStack.lastOrNull()) {
                    is HomeSubscreen.Article -> top.listState.scrollToItem(0)
                    is HomeSubscreen.Feed -> top.listState.scrollToItem(0)
                    else -> {}
                }
            }

            is HomeAction.ScrollToSection -> {
                // Do not touch LazyListState from viewModelScope: animateScrollToItem needs the
                // Compose MonotonicFrameClock and throws without it. Publish a one-shot request
                // instead; the rendered PageContent owns the list and consumes it on its own
                // composition frame clock.
                DebugLog.d(
                    "Search",
                    "requesting in-article scroll: extractIndex=${action.extractIndex}"
                )
                mutateTopArticle { it.copy(pendingScrollExtractIndex = action.extractIndex) }
            }

            is HomeAction.ConsumeScrollToSection -> mutateTopArticle {
                if (it.uid == action.uid) it.copy(pendingScrollExtractIndex = null) else it
            }

            is HomeAction.ReadingProgress -> onReadingProgress(action.progress)
            is HomeAction.UpdateScrollPosition -> onScrollPosition(action.index, action.offset)

            is HomeAction.ShowFeedErrorSnackBar -> viewModelScope.launch(errorHandler) {
                snackBarHostState.showSnackbar(
                    action.errorString
                )
            }

            is HomeAction.StopAll -> stopAll()
        }
    }

    private fun stopAll() {
        loaderJob?.cancel()
        fromLink = true
        _homeScreenState.update { it.copy(isLoading = false) }
    }

    private suspend fun loadSearchResults(query: String, lang: String? = null) {
        val q = query.trim()
        if (q.isEmpty()) {
            clearSearchResults()
            return
        }

        val t0 = System.nanoTime()
        // Snapshot the article on the main thread, then scan its parsed text off the main thread.
        // A long article has hundreds of paragraphs; matching them on the main thread made the
        // search-as-you-type field stutter.
        val article = backStack.lastOrNull { it is HomeSubscreen.Article } as? HomeSubscreen.Article
        val articleMatches = if (article == null) emptyList()
        else withContext(Dispatchers.Default) { buildArticleTextMatches(article, q) }
        _appSearchBarState.update { currentState ->
            currentState.copy(
                articleMatches = articleMatches,
                hasCurrentArticle = backStack.any { it is HomeSubscreen.Article }
            )
        }

        // Search the user's own library at the same time as Wikipedia. These queries are tiny
        // and indexed by SQLite, so they add no perceptible latency to the overlay. The cache
        // mainly avoids re-running both queries for every prefix of the same word.
        val localMatches: List<LocalArticleMatch> =
            readSearchCache(localSearchCache, q, LOCAL_SEARCH_CACHE_TTL_MS)
                ?: try {
                    val saved = appDatabaseRepository.searchSavedArticles(q)
                    val history = appDatabaseRepository.searchViewHistory(q)
                    val seen = HashSet<String>()
                    buildList {
                        saved.forEach { item ->
                            if (seen.add("s:${item.lang}:${item.title}")) add(
                                LocalArticleMatch(
                                    title = item.title,
                                    description = item.description,
                                    lang = item.lang,
                                    thumbnail = item.thumbnail,
                                    saved = true
                                )
                            )
                        }
                        history.forEach { item ->
                            if (seen.add("h:${item.lang}:${item.title}")) add(
                                LocalArticleMatch(
                                    title = item.title,
                                    description = item.description,
                                    lang = item.lang,
                                    thumbnail = item.thumbnail,
                                    saved = false
                                )
                            )
                        }
                    }.take(30).also { writeSearchCache(localSearchCache, q, it) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    DebugLog.w("Search", "local library search '$q' failed: ${e.message}")
                    emptyList()
                }
        _appSearchBarState.update { currentState ->
            currentState.copy(localMatches = localMatches)
        }

        try {
            // Set the host immediately before the request, not 250 ms earlier in the debounce:
            // an article load in another language can otherwise change the global host between
            // the two points (this made German searches return English results/descriptions).
            val searchLang = lang ?: preferencesState.value.lang
            val cacheKey = "$searchLang\u0001$q"
            val titleMatches = readSearchCache(
                prefixSearchCache,
                cacheKey,
                PREFIX_SEARCH_CACHE_TTL_MS
            ) ?: withHost(searchLang) {
                wikipediaRepository.getPrefixSearchResults(q)
            }.query.pages.sortedBy { it.index }.also {
                writeSearchCache(prefixSearchCache, cacheKey, it)
            }
            _appSearchBarState.update { currentState ->
                currentState.copy(prefixSearchResults = titleMatches)
            }
            DebugLog.d(
                "Search",
                "search '$q': ${articleMatches.size} in-article match(es), " +
                        "${localMatches.size} history/saved match(es), " +
                        "${titleMatches.size} title match(es) in " +
                        "${(System.nanoTime() - t0) / 1_000_000} ms"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DebugLog.w("Search", "title search '$q' failed: ${e.message}")
            _appSearchBarState.update { currentState ->
                currentState.copy(prefixSearchResults = null)
            }
        }
    }



    /**
     * Finds [query] in the article the user is reading. Search used to fire a second full-text
     * API request and label those global results "in article", which was both slower and wrong.
     */
    private fun buildArticleTextMatches(
        article: HomeSubscreen.Article,
        query: String
    ): List<ArticleTextMatch> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return emptyList()
        val out = ArrayList<ArticleTextMatch>()
        val whitespace = Regex("\\s+")
        article.extract.forEachIndexed { index, block ->
            // Odd slots are section headings, not body text.
            if (index % 2 == 1 || block.isEmpty()) return@forEachIndexed
            val text = block.joinToString(" ") { it.text }
                .replace(whitespace, " ")
                .trim()
            if (text.isEmpty()) return@forEachIndexed
            val matchAt = text.indexOf(normalizedQuery, ignoreCase = true)
            if (matchAt < 0) return@forEachIndexed

            val sectionTitle = if (index == 0) {
                "Introduction"
            } else {
                article.extract.getOrNull(index - 1)
                    ?.joinToString(" ") { it.text }
                    ?.replace(whitespace, " ")
                    ?.trim()
                    ?.ifBlank { "Section ${index / 2}" }
                    ?: "Section ${index / 2}"
            }
            val excerptStart = maxOf(0, matchAt - 70)
            val excerptEnd = minOf(text.length, matchAt + normalizedQuery.length + 110)
            val leadingEllipsis = if (excerptStart > 0) "\u2026 " else ""
            val trailingEllipsis = if (excerptEnd < text.length) " \u2026" else ""
            val excerpt = text.substring(excerptStart, excerptEnd)
            val highlightStart = leadingEllipsis.length + (matchAt - excerptStart)
            val highlightEnd = highlightStart + normalizedQuery.length
            val snippet = buildAnnotatedString {
                append(leadingEllipsis)
                append(excerpt)
                append(trailingEllipsis)
                if (highlightStart >= 0 && highlightEnd <= length) {
                    addStyle(
                        androidx.compose.ui.text.SpanStyle(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        ),
                        highlightStart,
                        highlightEnd
                    )
                }
            }
            out.add(ArticleTextMatch(index, sectionTitle, snippet))
            if (out.size >= 40) return out
        }
        return out
    }

    private fun clearSearchResults() {
        _appSearchBarState.update { currentState ->
            currentState.copy(
                prefixSearchResults = emptyList(),
                searchResults = emptyList(),
                articleMatches = emptyList(),
                localMatches = emptyList(),
                hasCurrentArticle = false
            )
        }
    }
    private fun loadSearchResultsDebounced(query: String) {
        searchDebounceJob?.cancel()
        searchDebounceJob = viewModelScope.launch(errorHandler) {
            delay(250)
            // Search-as-you-type has no explicit language: use the user's chosen Wikipedia.
            // The host is set inside loadSearchResults immediately before the request.
            loadSearchResults(query)
        }
    }

    private fun loadSearch(
        query: String?,
        lang: String? = null,
        random: Boolean = false,
        listStatePair: Pair<Int, Int>? = null
    ) {
        val q = query?.trim() ?: " "
        if (q != "") {
            viewModelScope.launch(Dispatchers.IO + errorHandler) {
                // A search must go to the user's chosen Wikipedia, not to the language of the
                // article that happened to be open. An explicit [lang] (language sheet/search
                // history) still wins.
                var setLang = lang ?: preferencesState.value.lang
                try {
                    _homeScreenState.update { currentState ->
                        currentState.copy(isLoading = true, loadingProgress = null)
                    }
                    if (!random && listStatePair == null && preferencesState.value.searchHistory) {
                        appDatabaseRepository.insertSearchHistory(
                            SearchHistoryItem(
                                query = q,
                                lang = setLang
                            )
                        )
                    }
                    if (!random) {
                        loadSearchResults(q, setLang)
                        // One read of the state: another search can replace it between a null check and `!!`.
                        val prefix = appSearchBarState.value.prefixSearchResults
                        if (prefix != null) {
                            val target = prefix.firstOrNull()?.title
                                ?: throw NoSuchElementException("no results for $q")
                            loadPage(title = target, lang = setLang)
                        } else throw NetworkException()
                    } else
                        loadPage(title = null, random = true)
                } catch (e: Exception) {
                    if (e is NetworkException) {
                        backStack.add(
                            HomeSubscreen.Article(
                                title = "Error",
                                extract = listOf(
                                    "An error occurred :(\n" +
                                            "Please check your internet connection"
                                ).map { parseWikitext(it) },
                                photo = null,
                                photoDesc = null,
                                langs = null,
                                currentLang = setLang,
                                pageId = null,
                                savedStatus = SavedStatus.NOT_SAVED
                            )
                        )
                        updateHistoryFlags()
                        _homeScreenState.update { currentState ->
                            currentState.copy(isLoading = false)
                        }
                    } else {
                        backStack.add(
                            HomeSubscreen.Article(
                                title = "Error",
                                extract = listOf("No search results found for $q").map {
                                    parseWikitext(it)
                                },
                                photo = null,
                                photoDesc = null,
                                langs = null,
                                currentLang = setLang,
                                pageId = null,
                                savedStatus = SavedStatus.NOT_SAVED
                            )
                        )
                        updateHistoryFlags()
                        _homeScreenState.update { currentState ->
                            currentState.copy(isLoading = false)
                        }
                    }
                }
            }
        }
    }

    /**
     * Loads page with the given title
     *
     * @param title Page title
     */
    private fun loadPage(
        title: String?,
        lang: String? = null,
        random: Boolean = false,
        replaceBackstackEntry: Boolean = false,
        bypassCache: Boolean = false,
        fromRestore: Boolean = false,
        fromHistory: Boolean = false,
        fromTabSwitch: Boolean = false,
        forceNewTab: Boolean = false,
        /** Restored reading place: (first visible item, pixel offset). */
        initialScroll: Pair<Int, Int>? = null
    ) {
        val previousLoad = loaderJob
        previousLoad?.cancel()
        loaderJob = viewModelScope.launch(Dispatchers.IO + errorHandler) {
            // Loads share global parser state (reference lists): never run two at once. Waiting for
            // the cancelled load to actually stop fixes crashes/garbled articles from rapid taps.
            previousLoad?.join()
            // The article that was on screen is done: count how it was read before switching.
            finishReading()
            val htmlMode = preferencesState.value.renderMode == "html"
            suspend fun fetchContent(pageTitle: String, articleLang: String): String =
                withHost(articleLang) {
                    if (htmlMode) wikipediaRepository.getPageHtml(pageTitle)
                    else wikipediaRepository.getPageContent(pageTitle)
                }
            suspend fun parseSection(text: String, lang: String): List<AnnotatedString> =
                if (htmlMode) {
                    if (text.isBlank()) emptyList()
                    else splitHtmlCladeBlocks(text).flatMap { block ->
                        if (block.trimStart().startsWith("{{clade", ignoreCase = true)) {
                            listOf(AnnotatedString(block))
                        } else {
                            // Pictures were lifted out of the HTML before sanitising; keep their
                            // `[[File:…]]` blocks out of fromHtml (which would print them as text).
                            splitHtmlMediaBlocks(block).map { piece ->
                                val head = piece.trimStart()
                                if (head.startsWith("[[File:", ignoreCase = true) ||
                                    head.startsWith(HTML_INFOBOX_MARKER, ignoreCase = true)
                                ) {
                                    AnnotatedString(piece)
                                } else {
                                    AnnotatedString.fromHtml(paragraphizeHtml(absolutizeHtmlLinks(piece, lang)))
                                }
                            }
                        }
                    }
                } else parseWikitext(text)
            val loadStart = System.nanoTime()
            DebugLog.i(
                "Load",
                "LOAD START title=$title random=$random lang=$lang replace=$replaceBackstackEntry " +
                        "history=$fromHistory tab=$fromTabSwitch newTab=$forceNewTab " +
                        "mode=${if (htmlMode) "html" else "wikitext"} backStack=${backStack.size} ${DebugLog.memory()}"
            )
            DebugLog.crumb("load start title=$title random=$random")
            // The request host is set inside withHost() immediately before the call. Never use the
            // interceptor's current host to label the article: it is global mutable state and may
            // still point at a language from an earlier request (the "always English" bug).
            var setLang = lang ?: preferencesState.value.lang
            if (title != null || random) {
                try {
                    if (title != null) {
                        lastQuery = Pair(title, setLang)
                    }
                    _homeScreenState.update { currentState ->
                        currentState.copy(isLoading = true, loadingProgress = null)
                    }

                    // 笏笏 Network: fetch page metadata and raw wikitext in parallel 笏笏笏笏笏笏笏笏笏笏笏笏
                    // An article opened before is read from the on-device cache: no network at all.
                    var servedFromCache = false
                    val cacheKind = if (htmlMode) "html" else "wikitext"
                    val cachedHit =
                        if (random || bypassCache || title == null) null
                        else articleCache.get(setLang, title)?.takeIf { it.contentKind == cacheKind }
                    if (cachedHit == null && !htmlMode && !random && !bypassCache && title != null) {
                        // An old/HTML entry was found but the user now renders wikitext: fetching
                        // fresh content is correct. Log it so a slow reopen is explainable.
                        articleCache.get(setLang, title)?.let {
                            DebugLog.d("Cache", "ignoring '${it.contentKind}' cache entry for '$title' (mode=$cacheKind)")
                        }
                    }
                    val apiResponse = when (random) {
                        false -> if (cachedHit != null) {
                            servedFromCache = true
                            DebugLog.i("Cache", "'$title' served from the on-device cache (mode=$cacheKind)")
                            Pair<WikiApiPage?, String>(cachedHit.page, cachedHit.wikitext)
                        } else coroutineScope {
                            val pageDataJob = async {
                                withHost(setLang) { wikipediaRepository.getPageData(title!!) }
                            }
                            // Fire content fetch using the supplied title immediately; Wikipedia's
                            // raw-content endpoint follows redirects, so this is safe in the vast
                            // majority of cases. If the pageData comes back with a different
                            // (redirect-resolved) title we re-fetch below.
                            val contentJob = async { fetchContent(title!!, setLang) }

                            val pageData = pageDataJob.await()
                            val resolvedTitle = pageData.query?.pages?.get(0)?.title
                            val rawContent = if (resolvedTitle != null && resolvedTitle != title) {
                                // Redirect: discard speculative fetch and get the real content.
                                contentJob.cancel()
                                fetchContent(resolvedTitle, setLang)
                            } else {
                                contentJob.await()
                            }
                            Pair(pageData.query?.pages?.get(0), rawContent)
                        }
                        else -> {
                          val pre = takePrefetched()
                          if (pre != null) {
                            DebugLog.i("Prefetch", "using the prepared article '${pre.page.title}'")
                            servedFromCache = true // already stored by the background preparation
                            Pair<WikiApiPage?, String>(pre.page, pre.content)
                          } else {
                            prefetchJob?.cancel()
                            val topicArticle = try {
                                getRandomArticleForTopic()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: LibraryExhaustedException) {
                                throw e
                            } catch (e: Exception) { null }
                            // getRandomArticleForTopic() only falls back to a general random
                            // article when no topic is selected, or when the selected topics have
                            // no complete library yet (first build / welcome tour). It never
                            // silently ignores a ready topic library.
                            val page = topicArticle ?: throw NetworkException()
                            // The article text can fail for one article (deleted since the
                            // library was built, odd redirect...). Try up to 3 articles rather
                            // than showing an error page for a single bad pick.
                            var candidate: WikiApiPage = page
                            var attempts = 0
                            var fetched: Pair<WikiApiPage?, String>? = null
                            while (fetched == null) {
                                // A prepared article can still be on disk even when the in-memory
                                // queue was invalidated (for example after a reading session).
                                // Reusing it avoids a second network round-trip and makes random
                                // HTML mode behave like wikitext mode.
                                val cached = articleCache.get(setLang, candidate.title)
                                    ?.takeIf { it.contentKind == cacheKind && it.page != null }
                                if (cached != null) {
                                    servedFromCache = true
                                    fetched = Pair(cached.page, cached.wikitext)
                                    DebugLog.d("Cache", "random '${candidate.title}' served from disk (mode=$cacheKind)")
                                    break
                                }
                                try {
                                    fetched = Pair(candidate, fetchContent(candidate.title, setLang))
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    attempts++
                                    DebugLog.w("Random", "content fetch for '${candidate.title}' failed (attempt $attempts): ${e.message}")
                                    if (attempts >= 3) {
                                        // Several topic picks failed (deleted pages, bad redirects).
                                        // One last chance with Wikipedia's own random generator
                                        // keeps the Random button from showing an error page.
                                        val fallback = try {
                                            randomArticleFromWikipedia()
                                        } catch (c: CancellationException) {
                                            throw c
                                        } catch (x: Exception) { null }
                                        if (fallback != null && fallback.title != candidate.title) {
                                            candidate = fallback
                                            attempts = 0
                                            continue
                                        }
                                        throw e
                                    }
                                    candidate = try {
                                        getRandomArticleForTopic()
                                    } catch (c: CancellationException) {
                                        throw c
                                    } catch (x: Exception) { null } ?: throw e
                                }
                            }
                            checkNotNull(fetched)
                          }
                        }
                    }

                    val pageInfo = apiResponse.first
                    val rawWikitext = apiResponse.second
                    DebugLog.i(
                        "Load",
                        "PERF network phase done in ${(System.nanoTime() - loadStart) / 1_000_000} ms: " +
                                "title='${pageInfo?.title}' pageId=${pageInfo?.pageId} content=${rawWikitext.length} chars " +
                                "mode=${if (htmlMode) "html" else "wikitext"} " +
                                "photo=${pageInfo?.photo != null} thumb=${pageInfo?.thumbnail != null}"
                    )

                    if (pageInfo != null && !servedFromCache)
                        cacheArticle(setLang, pageInfo, rawWikitext, title, cacheKind)

                    if (pageInfo?.title != null) {
                        lastQuery = Pair(pageInfo.title, setLang)
                        sessionSeen.add(pageInfo.title)
                        startReading(pageInfo.title, setLang)
                        recordReadForRetention(setLang, pageInfo.title)
                        if (fromRestore) initializeHistory(pageInfo.title, setLang)
                        else if (!fromHistory) recordHistory(pageInfo.title, setLang)
                        onArticleLoaded(
                            title = pageInfo.title,
                            lang = setLang,
                            fromRestore = fromRestore,
                            forceNewTab = forceNewTab,
                            thumbnail = pageInfo.thumbnail?.source ?: pageInfo.photo?.source
                        )
                    }

                    val tClean = System.nanoTime()
                    val extractText = if (htmlMode) rawWikitext else cleanUpWikitext(rawWikitext)
                    val tSplit = System.nanoTime()
                    val extract: List<String> =
                        if (htmlMode) splitHtmlSections(extractText) else parseSections(extractText)
                    DebugLog.d(
                        "Load",
                        "PERF section split (mode=$cacheKind): ${extract.size} sections, " +
                                "raw=${rawWikitext.length} chars, split=${(System.nanoTime() - tSplit) / 1_000_000} ms, " +
                                "cleanup=${(tSplit - tClean) / 1_000_000} ms"
                    )

                    val saved =
                        if (appDatabaseRepository.isArticleSaved(pageInfo?.pageId ?: 0, setLang))
                            SavedStatus.SAVED
                        else SavedStatus.NOT_SAVED

                    sections = extract.size

                    // 笏笏 Reset reference state BEFORE parsing (fixes race window) 笏笏笏笏笏笏笏笏笏笏笏笏笏
                    refCount = 1
                    refList.clear()
                    refListCount.clear()
                    refListIndex.clear()
                    if (!htmlMode) extractText.buildRefList()

                    if (pageInfo != null)
                        viewModelScope.launch(Dispatchers.IO + errorHandler) {
                            if (preferencesState.value.browsingHistory && !fromRestore && !fromHistory) {
                                val historyTopic = try {
                                    topicIndexProvider(setLang).topicsForTitle(pageInfo.title).firstOrNull()
                                } catch (e: Exception) { null }
                                appDatabaseRepository.insertViewHistory(
                                    ViewHistoryItem(
                                        thumbnail = pageInfo.thumbnail?.source,
                                        title = pageInfo.title,
                                        description = pageInfo.description,
                                        lang = setLang,
                                        topic = historyTopic
                                    )
                                )
                            }
                        }

                    var sectionIndex = 3
                    val articleSections = mutableListOf<Pair<Int, String>>()
                    val parsedExtract = mutableListOf<List<AnnotatedString>>()

                    // 笏笏 Progressive rendering: parse section 0 (intro) and push article 笏笏笏笏笏笏
                    // The user sees content immediately; remaining sections load behind the scenes.
                    val tSection0 = System.nanoTime()
                    val section0 = parseSection(extract[0], setLang)
                    DebugLog.d("Load", "PERF section 0 parsed in ${(System.nanoTime() - tSection0) / 1_000_000} ms (${extract[0].length} chars)")
                    parsedExtract.add(section0)
                    currentSection = 1
                    _homeScreenState.update { it.copy(loadingProgress = 1f / sections) }

                    // sessionSeen above already prevents a duplicate pick within this session;
                    // persist the seen flag off the main thread so the article can enter the
                    // visible back stack without waiting for a database write.
                    if (pageInfo?.title != null) {
                        val seenTitle = pageInfo.title
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                topicIndexProvider(setLang).markSeen(seenTitle)
                            } catch (e: Exception) {
                                DebugLog.w("Load", "could not record '$seenTitle' as seen: ${e.message}")
                            }
                        }
                    }

                    // A newer random tap may have cancelled this load while it was fetching or
                    // parsing. Never push an intermediate article after cancellation: rapid
                    // additions/removals are what made the navigation transition churn.
                    coroutineContext.ensureActive()
                    val restoredListState = initialScroll?.let { (index, offset) ->
                        LazyListState(
                            firstVisibleItemIndex = index.coerceAtLeast(0),
                            firstVisibleItemScrollOffset = offset.coerceAtLeast(0)
                        )
                    } ?: LazyListState()
                    val loadedTitle = pageInfo?.title ?: "Error"
                    val savedHighlights = savedHighlightsFor(loadedTitle, setLang)
                    if (!replaceBackstackEntry) backStack.add(
                        HomeSubscreen.Article(
                            title = loadedTitle,
                            photo = pageInfo?.photo,
                        thumbnail = pageInfo?.thumbnail,
                            photoDesc = pageInfo?.description,
                            langs = pageInfo?.langs,
                            currentLang = setLang,
                            pageId = pageInfo?.pageId,
                            savedStatus = saved,
                            extract = parsedExtract.toList(),
                            sections = articleSections.toList(),
                            listState = restoredListState,
                            highlights = savedHighlights
                        )
                    ) else backStack[backStack.lastIndex] = HomeSubscreen.Article(
                        title = loadedTitle,
                        photo = pageInfo?.photo,
                        thumbnail = pageInfo?.thumbnail,
                        photoDesc = pageInfo?.description,
                        langs = pageInfo?.langs,
                        currentLang = setLang,
                        pageId = pageInfo?.pageId,
                        savedStatus = saved,
                        extract = parsedExtract.toList(),
                        sections = articleSections.toList(),
                        listState = restoredListState,
                        highlights = savedHighlights
                    )

                    // History navigation keeps the render stack tiny: the lightweight list above
                    // already knows every article, so older parsed entries are dead weight.
                    if (fromHistory) pruneArticleBackstack()

                    // Show content immediately; stop the full-screen loading indicator.
                    _homeScreenState.update { it.copy(isLoading = false) }

                    // Start preparing the next random article now, not after every remaining section
                    // has been parsed. In HTML mode parsing is the slow part, so this overlap is what
                    // makes the *next* random tap as fast as a wikitext tap.
                    schedulePrefetch()

                    // Hopping through many random articles used to keep every parsed article
                    // alive in the back stack; drop the oldest ones (never the feed at 0).
                    while (backStack.size > MAX_BACKSTACK_ENTRIES) backStack.removeAt(1)

                    // 笏笏 Parse remaining sections, updating the backstack entry progressively 笏笏
                    val shownTitle = pageInfo?.title ?: "Error"
                    extract.drop(1).forEachIndexed { relIndex, sectionText ->
                        // A newer load cancelled this one: stop instead of overwriting the new
                        // article (and the shared reference state) with stale sections.
                        coroutineContext.ensureActive()
                        val index = relIndex + 1
                        currentSection = index + 1
                        val tSec = System.nanoTime()
                        val parsed = parseSection(sectionText, setLang)
                        DebugLog.d("Load", "PERF section $index/${extract.lastIndex} (${sectionText.length} chars) parsed in ${(System.nanoTime() - tSec) / 1_000_000} ms")
                        if (index % 2 == 1) {
                            articleSections.add(
                                Pair(
                                    sectionIndex,
                                    if (htmlMode) sectionText.trim()
                                    else parsed.joinToString(separator = "").parseAsHtml().toString()
                                )
                            )
                            sectionIndex += 2
                        }
                        parsedExtract.add(parsed)

                        // Throttle backstack writes: only update every 3 sections or at the end,
                        // to avoid thrashing Compose recomposition during background parsing.
                        if (index % 3 == 0 || index == extract.lastIndex) {
                            updateTopArticle(shownTitle, parsedExtract.toList(), articleSections.toList())
                        }
                    }

                    // Ensure final state is always committed.
                    updateTopArticle(shownTitle, parsedExtract.toList(), articleSections.toList())
                    refreshMissingLinks(shownTitle, setLang)
                    schedulePrefetch()
                    // Donation milestones count deliberate article opens, not history/tab replays.
                    if (!fromRestore && !fromHistory && !fromTabSwitch) noteArticleOpened()
                    DebugLog.i(
                        "Load",
                        "LOAD DONE '$shownTitle' in ${(System.nanoTime() - loadStart) / 1_000_000} ms, " +
                                "${extract.size} sections, backStack=${backStack.size} ${DebugLog.memory()}"
                    )

                } catch (e: CancellationException) {
                    // Superseded by a newer load (e.g. a second Random tap): not an error.
                    throw e
                } catch (e: Throwable) {
                    // Throwable (not Exception) so OutOfMemoryError on a huge article shows an
                    // error page instead of crashing the app.
                    Log.e("ViewModel", "Error in fetching results: ${e.message}")
                    DebugLog.e("Load", "LOAD FAILED title=$title random=$random after ${(System.nanoTime() - loadStart) / 1_000_000} ms ${DebugLog.memory()}", e)
                    _homeScreenState.update { it.copy(isLoading = false) }
                    // Every article in the selected topics has been read. Instead of showing an
                    // error page, ask the reader whether to reset the topic history and continue.
                    if (e is LibraryExhaustedException && e.unseenIgnoringFilters <= 0) {
                        DebugLog.i("Load", "topic library exhausted; offering to reset the view history")
                        _topicExhausted.value = true
                        return@launch
                    }
                    // Back/forward and tab switches are navigation, not new content: never replace
                    // the article the reader is looking at with an error page.
                    if (fromHistory) {
                        DebugLog.w("History", "history load failed; keeping the current article")
                        revertHistoryToCurrentArticle()
                        snackBarHostState.showSnackbar(appContext.getString(R.string.articleLoadFailed))
                        return@launch
                    }
                    if (fromTabSwitch) {
                        DebugLog.w("Tabs", "tab switch failed; keeping the current article")
                        revertActiveTabToCurrentArticle()
                        snackBarHostState.showSnackbar(appContext.getString(R.string.articleLoadFailed))
                        return@launch
                    }
                    // A random request is an optional, replaceable action: a rate limit or a
                    // network hiccup must never replace the article/feed the reader is already
                    // looking at with an error page. Keep the current screen and offer a retry.
                    val retryableRandom = random &&
                            (e is NetworkException || e is IOException || e is HttpException)
                    if (retryableRandom) {
                        DebugLog.w(
                            "Load",
                            "random load failed; keeping the current screen: ${e.javaClass.simpleName}: ${e.message}"
                        )
                        val result = snackBarHostState.showSnackbar(
                            message = appContext.getString(R.string.randomLoadFailed),
                            actionLabel = appContext.getString(R.string.retry),
                            withDismissAction = true,
                            duration = SnackbarDuration.Long
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            loadPage(title = null, random = true)
                        }
                        return@launch
                    }
                    // Repeated failures must not pile up a stack of error pages.
                    (backStack.lastOrNull() as? HomeSubscreen.Article)?.let {
                        if (it.title == "Error" && backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                    }
                    backStack.add(
                        HomeSubscreen.Article(
                            title = "Error",
                            extract = if (e is LibraryExhaustedException) {
                                listOf(
                                    if (e.unseenIgnoringFilters > 0)
                                        "No more articles match your filters.\n${e.unseenIgnoringFilters} unseen articles in your topics are hidden by your filters (images, stubs, topic strictness or popularity in Settings)."
                                    else
                                        "You've seen every article in your topics.\nAdd more topics, or open Random topics and press Start over to see them again."
                                ).map { parseWikitext(it) }
                            } else if (e.message?.contains("404") == true) {
                                listOf("No article with title $title")
                                    .map { parseWikitext(it) }
                            } else if (e is HttpException) {
                                listOf(
                                    "An error occurred :(\n" +
                                            "Please check your internet connection"
                                ).map { parseWikitext(it) }
                            } else {
                                listOf("An unknown error occured :(\n${e.message} caused by ${e.cause}")
                                    .map { parseWikitext(it) }
                            },
                            langs = null,
                            currentLang = null,
                            photo = null,
                            photoDesc = null,
                            pageId = null,
                            savedStatus = SavedStatus.NOT_SAVED
                        )
                    )
                    updateHistoryFlags()
                }

                if (lang != null)
                    preferencesStateMutableFlow.update { currentState ->
                        currentState.copy(lang = lang)
                    }
            } else {
                backStack.add(
                    HomeSubscreen.Article(
                        title = "Error",
                        extract = listOf("Null search query").map { parseWikitext(it) },
                        photo = null,
                        photoDesc = null,
                        langs = null,
                        currentLang = setLang,
                        pageId = null,
                        savedStatus = SavedStatus.NOT_SAVED
                    )
                )
                updateHistoryFlags()
                _homeScreenState.update { currentState ->
                    currentState.copy(isLoading = false)
                }
            }
        }
    }

    private val stackLock = Any()

    /**
     * Applies [transform] to the top-of-stack article (only if it is still [expectedTitle], when
     * given). All changes go through here: the lock stops the loader thread and the UI thread
     * overwriting each other. The article keeps its [HomeSubscreen.Article.uid], so navigation
     * content keys stay stable while progressive sections and save-state updates arrive.
     */
    /** Saved passage texts for [title], used to tint the article when it is opened. */
    private suspend fun savedHighlightsFor(title: String, lang: String): List<String> =
        try {
            appDatabaseRepository.getSavedHighlightsForArticle(title, lang).map { it.text }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DebugLog.w("Highlight", "could not read highlights for '$title': ${e.message}")
            emptyList()
        }

    private fun mutateTopArticle(
        expectedTitle: String? = null,
        transform: (HomeSubscreen.Article) -> HomeSubscreen.Article
    ): Boolean = synchronized(stackLock) {
        val idx = backStack.lastIndex
        val top = backStack.getOrNull(idx) as? HomeSubscreen.Article ?: return@synchronized false
        if (expectedTitle != null && top.title != expectedTitle) return@synchronized false
        backStack[idx] = transform(top)
        true
    }

    /** Updates the top-of-stack article only if it is still the article being loaded. */
    private fun updateTopArticle(
        expectedTitle: String,
        extract: List<List<AnnotatedString>>,
        sections: List<Pair<Int, String>>
    ) {
        val top = backStack.lastOrNull() as? HomeSubscreen.Article ?: return
        if (top.title != expectedTitle) return
        if (top.extract == extract && top.sections == sections) return // nothing new
        mutateTopArticle(expectedTitle) { it.copy(extract = extract, sections = sections) }
    }

    /**
     * Wikipedia paints links to pages that were never created red. The wikitext itself does not
     * say whether a target exists, so ask the API for the article's linked pages in the background
     * and update the already-visible article when the answer arrives. Failure is non-fatal: the
     * links simply stay in the normal link colour.
     */
    private fun refreshMissingLinks(title: String, lang: String) {
        if (title.isBlank() || title == "Error") return
        viewModelScope.launch(errorHandler) {
            val missing = try {
                withContext(Dispatchers.IO) {
                    withHost(lang) { wikipediaRepository.getMissingLinkedArticles(title) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DebugLog.w("Links", "could not check red links for '$title': ${e.message}")
                return@launch
            }
            if (missing.isEmpty()) return@launch
            val normalized = missing.mapTo(HashSet(), ::normalizeArticleLinkTitle)
            mutateTopArticle(title) { article ->
                // A newer navigation may have replaced the article while the request was in flight.
                if (article.currentLang == lang) article.copy(missingLinks = normalized)
                else article
            }
        }
    }

    private fun reloadPage(
        persistLang: Boolean = false
    ) {
        if (lastQuery != null) {
            if (persistLang)
                loadPage(
                    lastQuery?.first,
                    lang = lastQuery?.second,
                    random = false,
                    replaceBackstackEntry = true,
                    bypassCache = true
                )
            else
                loadPage(
                    lastQuery?.first,
                    random = false,
                    replaceBackstackEntry = true,
                    bypassCache = true
                )
        }
    }

    /**
     * Loads feed and updates the back stack
     *
     * If an error is encountered, back stack is set to display the logo instead
     */
    private fun loadFeed(silent: Boolean = false) {
        // A silent refresh (used while a restored article is on screen) only fills in the feed
        // underneath: it never cancels an article load, shows no progress bar and leaves the
        // rest of the stack alone.
        val previousLoad = if (silent) null else loaderJob
        previousLoad?.cancel()
        val feedLoad = viewModelScope.launch(Dispatchers.IO + errorHandler) {
            // Loads share global parser state (reference lists): never run two at once. Waiting for
            // the cancelled load to actually stop fixes crashes/garbled articles from rapid taps.
            previousLoad?.join()
            // Wikivoyage has no Wikipedia featured-feed endpoint; its home screen is the app
            // logo, with search / random travel guides one tap away.
            if (!preferencesState.value.dataSaver && preferencesState.value.feedEnabled &&
                !preferencesState.value.wikivoyageEnabled) {
                if (!silent) _homeScreenState.update { currentState ->
                    currentState.copy(isLoading = true, loadingProgress = null)
                }

                try {
                    // The feed must always come from the user's chosen Wikipedia language, even
                    // after reading an article in another language.
                    val feedLang = preferencesState.value.lang
                    // The feed for the device's local date may not be published yet (e.g. just
                    // after local midnight ahead of UTC): fall back to yesterday's instead of
                    // giving up and showing the logo.
                    val feedData = try {
                        withHost(feedLang) { wikipediaRepository.getFeed() }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        withHost(feedLang) {
                            wikipediaRepository.getFeed(
                                java.time.LocalDate.now().minusDays(1).format(
                                    java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd")
                                )
                            )
                        }
                    }
                    val sections = mutableListOf<Pair<Int, FeedSection>>()
                    var currentSection = 0

                    var feed = HomeSubscreen.Feed(
                        tfa = feedData.tfa,
                        mostReadArticles = feedData.mostRead?.articles?.sortedBy { it.rank },
                        image = feedData.image,
                        news = feedData.news,
                        onThisDay = feedData.onThisDay,
                        currentLang = feedLang
                    )

                    if (feed.tfa != null) {
                        sections.add(Pair(currentSection, FeedSection.TFA))
                        currentSection++
                    }
                    if (feed.mostReadArticles != null) {
                        sections.add(Pair(currentSection, FeedSection.MOST_READ))
                        currentSection++
                    }
                    if (feed.image != null) {
                        sections.add(Pair(currentSection, FeedSection.IMAGE))
                        currentSection++
                    }
                    if (feed.news != null) {
                        sections.add(Pair(currentSection, FeedSection.NEWS))
                        currentSection++
                    }
                    if (feed.onThisDay != null) {
                        sections.add(Pair(currentSection, FeedSection.ON_THIS_DAY))
                    }

                    feed = feed.copy(sections = sections)

                    backStack[0] = feed
                    if (!silent && backStack.size > 1) backStack.removeRange(1, backStack.size)

                    if (!silent) _homeScreenState.update { currentState ->
                        currentState.copy(isLoading = false)
                    }
                } catch (e: CancellationException) {
                    // Replaced by another load (link tap, random): keep the current screen.
                    throw e
                } catch (e: Exception) {
                    Log.e("ViewModel", "Error in loading feed: ${e.message}")
                    // A failed refresh (offline pull-to-refresh) must keep the feed we already have.
                    if (backStack[0] !is HomeSubscreen.Feed) backStack[0] = HomeSubscreen.Logo
                    if (!silent) _homeScreenState.update { currentState ->
                        currentState.copy(isLoading = false)
                    }
                }
            } else {
                backStack[0] = HomeSubscreen.Logo
            }
        }
        if (!silent) loaderJob = feedLoad
    }

    /**
     * Saves the article on screen.
     *
     * Default: a BOOKMARK only (title, language, page id, thumbnail, description). Nothing is
     * downloaded and the article is opened online later. With the "Save articles for offline
     * reading" setting the full text is stored too.
     */
    private suspend fun saveArticle(folder: String? = null, note: String? = null): WRStatus {
        val top = backStack.lastOrNull() as? HomeSubscreen.Article ?: return WRStatus.NO_SEARCH_RESULT
        val pageId = top.pageId ?: return WRStatus.NO_SEARCH_RESULT
        val articleLang = top.currentLang ?: preferencesState.value.lang
        val title = top.title
        val offline = preferencesState.value.saveOffline
        // Only keep the fields the user actually enabled. This also makes turning a setting off
        // effective immediately, even if an old value is still passed by a caller.
        val savedFolder = folder?.trim()?.takeIf { it.isNotEmpty() }
            ?.takeIf { preferencesState.value.saveFolders }
        val savedNote = note?.trim()?.takeIf { it.isNotEmpty() }
            ?.takeIf { preferencesState.value.saveNotes }
        DebugLog.crumb("saveArticle '$title' offline=$offline folder=$savedFolder note=${savedNote != null}")
        DebugLog.i("Save", "saving '$title' ($articleLang, pageId=$pageId) offline=$offline")

        mutateTopArticle(title) { it.copy(savedStatus = SavedStatus.SAVING) }
        try {
            var apiJson = ""
            var content = ""
            if (offline) {
                // The article on screen may be in another language; both requests must go to
                // that Wikipedia, not to the current preference.
                withHost(articleLang) {
                    val apiResponse = wikipediaRepository.getPageData(title)
                    val page = apiResponse.query?.pages?.getOrNull(0) ?: throw NetworkException()
                    content = wikipediaRepository.getPageContent(page.title)
                    apiJson = Json.encodeToString(apiResponse)
                }
            }
            val savedTopic = try {
                topicIndexProvider(articleLang).topicsForTitle(title).firstOrNull()
            } catch (e: Exception) { null }
            appDatabaseRepository.insertSavedArticle(
                SavedArticle(
                    pageId = pageId,
                    lang = articleLang,
                    langName = langCodeToName(articleLang),
                    title = title,
                    thumbnail = top.thumbnail?.source ?: top.photo?.source,
                    description = top.photoDesc,
                    apiResponse = apiJson,
                    pageContent = content,
                    savedAt = System.currentTimeMillis(),
                    topic = savedTopic,
                    folder = savedFolder,
                    note = savedNote
                )
            )
            mutateTopArticle(title) { it.copy(savedStatus = SavedStatus.SAVED) }
            // Keep the stored cover picture pinned in the image cache so the Saved library is
            // instant, even when the article's body images are later evicted.
            ImagePrefetcher.warmThumbnails(
                appContext,
                listOfNotNull(top.thumbnail?.source ?: top.photo?.source)
            )
            return WRStatus.SUCCESS
        } catch (e: CancellationException) {
            mutateTopArticle(title) { it.copy(savedStatus = SavedStatus.NOT_SAVED) }
            throw e
        } catch (e: Exception) {
            DebugLog.e("Save", "saving '$title' failed", e)
            mutateTopArticle(title) { it.copy(savedStatus = SavedStatus.NOT_SAVED) }
            return WRStatus.NETWORK_ERROR
        }
    }

    /**
     * Deletes the current article
     *
     * @return A [WRStatus] enum value indicating the status of the delete operation
     */
    private fun deleteArticle(pageId: Int, lang: String): WRStatus {
        viewModelScope.launch(Dispatchers.IO + errorHandler) {
            appDatabaseRepository.deleteSavedArticle(pageId, lang)
            mutateTopArticle { it.copy(savedStatus = SavedStatus.NOT_SAVED) }
        }

        return WRStatus.SUCCESS
    }

    /**
     * Opens a saved article. Anything can go wrong with a stored copy (corrupt JSON, a row too big
     * for Android's 2 MB CursorWindow, a page without sections...) and used to escape as an
     * exception with no feedback. Now the loading bar is cleared and the article is opened online
     * from its bookmark instead.
     */
    private suspend fun loadSavedArticle(pageId: Int, lang: String): WRStatus =
        try {
            loadSavedArticleInner(pageId, lang)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            DebugLog.e("Save", "opening saved article $pageId/$lang failed", e)
            _homeScreenState.update { it.copy(isLoading = false) }
            val info = try {
                appDatabaseRepository.getSavedArticleInfo(pageId, lang)
            } catch (_: Exception) {
                null
            }
            if (info != null) {
                loadPage(title = info.title, lang = info.lang)
                WRStatus.SUCCESS
            } else WRStatus.DATABASE_ERROR
        }

    private suspend fun loadSavedArticleInner(pageId: Int, lang: String): WRStatus =
        withContext(Dispatchers.IO) {
            _homeScreenState.update { currentState ->
                currentState.copy(isLoading = true, loadingProgress = null)
            }

            val savedArticle = appDatabaseRepository.getSavedArticle(pageId, lang)

            if (savedArticle != null && savedArticle.pageContent.isEmpty()) {
                // A bookmark without an offline copy: open it online.
                DebugLog.i("Save", "opening bookmark '${savedArticle.title}' online")
                loadPage(title = savedArticle.title, lang = savedArticle.lang)
                return@withContext WRStatus.SUCCESS
            }

            if (savedArticle != null) {
                val jsonInst = Json { ignoreUnknownKeys = true }
                val apiResponse =
                    jsonInst.decodeFromString<WikiApiPageData>(savedArticle.apiResponse)
                        .query?.pages?.getOrNull(0)

                val extractText = cleanUpWikitext(savedArticle.pageContent)
                val extract: List<String> = parseSections(extractText)

                sections = extract.size
                var sectionIndex = 3
                val articleSections = mutableListOf<Pair<Int, String>>()
                val parsedExtract = mutableListOf<List<AnnotatedString>>()

                // Opening a saved article must not silently change the user's default Wikipedia
                // language; the article carries its own language for links and the language sheet.
                // Reset reference state BEFORE parsing.
                refCount = 1
                refList.clear()
                refListCount.clear()
                refListIndex.clear()
                extractText.buildRefList()

                // Progressive rendering: parse intro first, push article, then fill in rest.
                val section0 = parseWikitext(extract[0])
                parsedExtract.add(section0)
                currentSection = 1

                backStack.add(
                    HomeSubscreen.Article(
                        title = apiResponse?.title ?: "Error",
                        photo = apiResponse?.photo,
                        thumbnail = apiResponse?.thumbnail,
                        photoDesc = apiResponse?.description,
                        langs = apiResponse?.langs,
                        currentLang = savedArticle.lang,
                        pageId = apiResponse?.pageId,
                        savedStatus = SavedStatus.SAVED,
                        extract = parsedExtract.toList(),
                        sections = articleSections.toList(),
                        highlights = savedHighlightsFor(
                            apiResponse?.title ?: "Error",
                            savedArticle.lang
                        )
                    )
                )
                _homeScreenState.update { it.copy(isLoading = false) }

                val savedTitle = apiResponse?.title ?: "Error"
                if (savedTitle != "Error") {
                    recordHistory(savedTitle, savedArticle.lang)
                    recordReadForRetention(savedArticle.lang, savedTitle)
                    ImagePrefetcher.warmThumbnails(
                        appContext,
                        listOf(apiResponse?.thumbnail?.source)
                    )
                    onArticleLoaded(
                        title = savedTitle,
                        lang = savedArticle.lang,
                        fromRestore = false,
                        forceNewTab = false,
                        thumbnail = apiResponse?.thumbnail?.source ?: apiResponse?.photo?.source
                    )
                }
                extract.drop(1).forEachIndexed { relIndex, sectionText ->
                    coroutineContext.ensureActive()
                    val index = relIndex + 1
                    currentSection = index + 1
                    val parsed = parseWikitext(sectionText)
                    if (index % 2 == 1) {
                        articleSections.add(
                            Pair(
                                sectionIndex,
                                parsed.joinToString(separator = "").parseAsHtml().toString()
                            )
                        )
                        sectionIndex += 2
                    }
                    parsedExtract.add(parsed)

                    if (index % 3 == 0 || index == extract.lastIndex) {
                        updateTopArticle(savedTitle, parsedExtract.toList(), articleSections.toList())
                    }
                }

                // Ensure final state committed.
                updateTopArticle(savedTitle, parsedExtract.toList(), articleSections.toList())
                refreshMissingLinks(savedTitle, savedArticle.lang)

                return@withContext WRStatus.SUCCESS
            } else return@withContext WRStatus.DATABASE_ERROR
        }

    /**
     * Function to split a Wikitext section source string into a list of [AnnotatedString]s where
     * each element represents a certain type of page content (text, table, image, gallery,
     * math expression)
     *
     * @param wikitext The Wikitext source string
     *
     * @return A list of [AnnotatedString]s representing the page content
     */
    private suspend fun parseWikitext(wikitext: String): List<AnnotatedString> =
        // CPU-bound (regex, string building): Default keeps it off the IO pool that also serves
        // network/database work and off the main thread.
        withContext(Dispatchers.Default) {
            splitWikitextBlocks(wikitext).map { block ->
                if (block.raw) AnnotatedString(block.text)
                else block.text.toWikitextAnnotatedStringSafe(
                    colorScheme = colorScheme,
                    typography = typography,
                    loadPage = ::loadPage,
                    fontSize = preferencesState.value.fontSize,
                    showRef = ::updateRef
                )
            }
        }

    private fun updateRef(ref: String) {
        _homeScreenState.update { currentState ->
            currentState.copy(
                showRef = true,
                ref = ref.toWikitextAnnotatedStringSafe(
                    colorScheme = colorScheme,
                    typography = typography,
                    loadPage = {
                        loadPage(it)
                        hideRef()
                    },
                    fontSize = preferencesState.value.fontSize,
                    showRef = ::updateRef
                )
            )
        }
    }

    private fun hideRef() {
        _homeScreenState.update { currentState ->
            currentState.copy(
                showRef = false
            )
        }
    }

    private fun focusSearchBar() {
        // The SearchBar is usually collapsed when the bottom toolbar button is tapped.
        // Bumping a counter lets the composable expand first and then focus the field;
        // requesting focus on a collapsed SearchBar was a no-op on some devices.
        _appSearchBarState.update { it.copy(focusRequest = it.focusRequest + 1) }
    }

    fun removeSearchHistoryItem(item: SearchHistoryItem?) {
        viewModelScope.launch(Dispatchers.IO + errorHandler) {
            if (item != null) appDatabaseRepository.deleteSearchHistory(item)
            else appDatabaseRepository.deleteAllSearchHistory()
        }
    }

    /**
     * Returns the set of currently saved random topic category strings.
     */
    suspend fun getRandomTopics(): Set<String> =
        withContext(Dispatchers.IO) {
            val saved = appPreferencesRepository.readStringPreference("random_topics") ?: ""
            if (saved.isBlank()) emptySet()
            else saved.split(",").filter { it.isNotBlank() }.toSet()
        }

    /**
     * Persists the given set of topic category strings to preferences.
     */
    suspend fun saveRandomTopics(topics: Set<String>) {
        invalidatePrefetch()
        appPreferencesRepository.saveStringPreference("random_topics", topics.joinToString(","))
    }

    // Aliases used by projects where these functions were renamed
    suspend fun getRandomSettings(): Set<String> = getRandomTopics()
    suspend fun saveRandomSettings(topics: Set<String>) = saveRandomTopics(topics)

    // 笏笏 Random article helpers 笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏笏

    /**
     * Picks a random article from the user's selected topics, skipping titles the user
     * has already seen in the current language, and (when enabled) skipping stubs and
     * articles without a lead image.
     *
     * Tries up to [MAX_TOPICS_PER_ATTEMPT] shuffled topics 窶・enough to route around one or
     * two sparse or misconfigured categories 窶・and the whole attempt is bounded by
     * [TOPIC_RANDOM_TIMEOUT_MS] so an unlucky run of topics can never turn into a long
     * hang; the caller falls back to a plain random article if this returns null.
     *
     * A repeat is only ever allowed once every topic tried has been fully exhausted (all
     * of its candidates already seen) 窶・until then, an exhausted topic is skipped in favour
     * of trying the next one, rather than immediately settling for a repeat from it.
     */
    private suspend fun getRandomArticleForTopic(): WikiApiPage? {
        if (selectedTopicList().isEmpty()) {
            DebugLog.d("Random", "no topics selected: using Wikipedia's general random generator")
            return randomArticleFromWikipedia()
        }
        val picked = try {
            pickFromIndex()
        } catch (e: CancellationException) {
            throw e
        } catch (e: LibraryExhaustedException) {
            DebugLog.w("Random", "topic library has no usable article (${e.unseenIgnoringFilters} unseen): falling back to a general random article")
            null
        }
        if (picked != null) return picked
        DebugLog.i("Random", "selected topics have no complete library yet: showing a general random article")
        notifyTopicLibraryBuilding()
        return randomArticleFromWikipedia()
    }

    private suspend fun randomArticleFromWikipedia(): WikiApiPage? =
        withHost(preferencesState.value.lang) {
            wikipediaRepository.getRandomResult().query?.pages?.randomOrNull()
        }

    private var lastAllSeenNoticeMs = 0L

    private fun notifyAllSeenRepeat() {
        val now = System.currentTimeMillis()
        if (now - lastAllSeenNoticeMs < 30_000L) return
        lastAllSeenNoticeMs = now
        viewModelScope.launch(errorHandler) {
            snackBarHostState.showSnackbar(appContext.getString(R.string.randomAllSeenRepeat))
        }
    }

    private var lastLibraryBuildingNoticeMs = 0L

    private fun notifyTopicLibraryBuilding() {
        val now = System.currentTimeMillis()
        if (now - lastLibraryBuildingNoticeMs < 15_000L) return
        lastLibraryBuildingNoticeMs = now
        viewModelScope.launch(errorHandler) {
            snackBarHostState.showSnackbar(appContext.getString(R.string.randomLibraryBuilding))
        }
    }

    private suspend fun insertUserLanguage(userLanguage: UserLanguage) {
        if (userLanguage.selected) {
            appDatabaseRepository.deselectAllUserLanguages()
            appDatabaseRepository.insertUserLanguage(userLanguage)
            markUserLanguageSelected(userLanguage.lang)
        } else appDatabaseRepository.insertUserLanguage(userLanguage)
    }

    private fun deleteUserLanguage(lang: String) {
        viewModelScope.launch(Dispatchers.IO + errorHandler) {
            appDatabaseRepository.deleteUserLanguage(lang)
        }
    }

    private fun markUserLanguageSelected(lang: String) {
        viewModelScope.launch(errorHandler) {
            appDatabaseRepository.deselectAllUserLanguages()
            appDatabaseRepository.markUserLanguageSelected(lang)
        }
        preferencesStateMutableFlow.update { currentState ->
            currentState.copy(lang = lang)
        }
        viewModelScope.launch(errorHandler) {
            appPreferencesRepository.saveStringPreference("lang", lang)
        }
    }

    private fun updateLanguageSearchStr(str: String) {
        _languageSearchStr.update {
            str
        }
    }

    override fun onCleared() {
        // The ViewModel scope is cancelled at this point, so persist the last session on a short
        // independent IO scope instead of losing it.
        val session = readingSession
        readingSession = null
        if (session != null) {
            val seconds = (System.currentTimeMillis() - session.startedMs) / 1000.0
            if (seconds >= 2.0) {
                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val topic = session.topic ?: return@launch
                        topicIndexProvider(session.lang).recordReading(
                            session.title, topic, seconds, session.maxProgress
                        )
                    } catch (e: Exception) {
                        // Process is shutting down; nothing useful to do.
                    }
                }
            }
        }
        super.onCleared()
    }

    companion object {
        private const val LOCAL_SEARCH_CACHE_TTL_MS = 5_000L
        private const val PREFIX_SEARCH_CACHE_TTL_MS = 60_000L

        /** Article opens at which the optional support prompt may appear. */
        private val DONATION_PROMPT_MILESTONES = listOf(100, 300, 600, 1000, 2000)

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as WikiRandomizerPApplication)
                val appStatusFlow = application.container.appStatus
                val preferencesStateMutableFlow = application.container.preferencesStateMutableFlow
                val interceptor = application.container.interceptor
                val wikipediaRepository = application.container.wikipediaRepository
                val appPreferencesRepository = application.container.appPreferencesRepository
                val appHistoryRepository = application.container.appDatabaseRepository
                HomeScreenViewModel(
                    appStatusFlow = appStatusFlow,
                    preferencesStateMutableFlow = preferencesStateMutableFlow,
                    interceptor = interceptor,
                    wikipediaRepository = wikipediaRepository,
                    appPreferencesRepository = appPreferencesRepository,
                    appDatabaseRepository = appHistoryRepository,
                    topicIndexProvider = { lang ->
                        // Wikivoyage libraries are kept in separate files so switching sources
                        // never mixes travel guides with encyclopedia topics or their seen lists.
                        TopicIndex.get(
                            application.applicationContext,
                            HostGate.storageLanguage(lang)
                        )
                    },
                    articleCache = ArticleCache(application.applicationContext),
                    appContext = application.applicationContext,
                    donationSupporterProvider = {
                        // Billing state, or the local supporter flag unlocked by a purchase or the
                        // developer menu.
                        application.container.donationBilling.state.value.supporter ||
                            preferencesStateMutableFlow.value.supporterMode
                    }
                )
            }
        }
    }
}
