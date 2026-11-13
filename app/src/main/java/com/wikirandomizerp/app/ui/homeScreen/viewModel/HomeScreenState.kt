package com.wikirandomizerp.app.ui.homeScreen.viewModel

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.AnnotatedString
import com.wikirandomizerp.app.data.FeedApiImage
import com.wikirandomizerp.app.data.IndexProgress
import com.wikirandomizerp.app.data.TopicBuildResult
import com.wikirandomizerp.app.data.FeedApiNews
import com.wikirandomizerp.app.data.FeedApiOTD
import com.wikirandomizerp.app.data.FeedApiTFA
import com.wikirandomizerp.app.data.MostReadArticle
import com.wikirandomizerp.app.data.SavedStatus
import com.wikirandomizerp.app.data.WikiLang
import com.wikirandomizerp.app.data.WikiPhoto
import com.wikirandomizerp.app.data.WikiPrefixSearchResult
import com.wikirandomizerp.app.data.WikiSearchResult

/** One match in the article that is currently open (not an API search result). */
@Immutable
data class ArticleTextMatch(
    /** Index into [HomeSubscreen.Article.extract], used to scroll to the matching section. */
    val sectionIndex: Int,
    val sectionTitle: String,
    val snippet: AnnotatedString
)

/** A saved article or history row matching the search text. */
@Immutable
data class LocalArticleMatch(
    val title: String,
    val description: String?,
    val lang: String,
    val thumbnail: String?,
    val saved: Boolean
)

@Immutable
data class AppSearchBarState(
    val prefixSearchResults: List<WikiPrefixSearchResult>? = emptyList(),
    /** Kept for older callers; the search overlay now uses [articleMatches] on the right. */
    val searchResults: List<WikiSearchResult>? = emptyList(),
    val articleMatches: List<ArticleTextMatch> = emptyList(),
    /** Saved articles and history rows whose title/description match the query. */
    val localMatches: List<LocalArticleMatch> = emptyList(),
    val hasCurrentArticle: Boolean = false,
    val focusRequester: FocusRequester = FocusRequester(),
    /** Incremented by the bottom search button so the search bar can expand and take focus. */
    val focusRequest: Int = 0
)

/** State of the dictionary sheet opened from a text selection. */
@Immutable
data class DictionaryLookup(
    val word: String,
    val definition: String? = null,
    val loading: Boolean = true,
    val error: String? = null
)

/** Stable key for the per-article like/dislike state kept in [HomeScreenState.articleVotes]. */
internal fun articleVoteKey(lang: String, title: String): String = "$lang\u0001$title"

/**
 * MediaWiki canonicalises the first letter of an article title while leaving the rest case
 * sensitive. Link targets written as `[[photosynthesis]]` therefore refer to
 * `Photosynthesis`; compare titles in that form so red-link matches do not depend on how the
 * writer capitalised the link.
 */
internal fun normalizeArticleLinkTitle(title: String): String {
    val clean = title
        .replace('_', ' ')
        .substringBefore('#')
        .trim()
        .removePrefix(":")
    if (clean.isEmpty()) return clean
    return clean.replaceFirstChar {
        if (it.isLowerCase()) it.titlecase() else it.toString()
    }
}

/** One open article tab. Title + language reopen it; the scroll fields restore the exact place. */
@Immutable
data class ArticleTab(
    val id: Long,
    val title: String,
    val lang: String,
    val lastUsedAt: Long = System.currentTimeMillis(),
    /** First visible article item (LazyColumn index) when the tab was last left. */
    val scrollIndex: Int = 0,
    /** Pixel offset inside that item. */
    val scrollOffset: Int = 0,
    /** Lead-image thumbnail shown in the tab panel, when the article has one. */
    val thumbnail: String? = null
)

@Immutable
data class HomeScreenState(
    val isLoading: Boolean = false,
    val loadingProgress: Float? = null,
    val showRef: Boolean = false,
    val ref: AnnotatedString = AnnotatedString(""),
    /** Open article tabs, most recently used first. */
    val tabs: List<ArticleTab> = emptyList(),
    /** Folders already in use, for the save dialog's quick picks. */
    val savedFolders: List<String> = emptyList(),
    val activeTabId: Long? = null,
    /** Tabs closed during this session, oldest first, so a closed tab can be brought back. */
    val recentlyClosed: List<ArticleTab> = emptyList(),
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    /** Current like (+1) / dislike (-1) vote per article, keyed by [articleVoteKey]. */
    val articleVotes: Map<String, Int> = emptyMap()
)

private val articleUidCounter = java.util.concurrent.atomic.AtomicLong()

internal fun nextArticleUid(): Long = articleUidCounter.incrementAndGet()

sealed class HomeSubscreen {
    data class Article(
        val title: String = "",
        val extract: List<List<AnnotatedString>> = emptyList(),
        val sections: List<Pair<Int, String>> = emptyList(),
        /**
         * One-shot request to scroll to an [extract] index. It is consumed by the rendered
         * [PageContent], because a LazyListState must be scrolled on the Compose frame clock;
         * doing it from viewModelScope throws "A MonotonicFrameClock is not available".
         */
        val pendingScrollExtractIndex: Int? = null,
        val listState: LazyListState = LazyListState(),
        val photo: WikiPhoto? = null,
        /** Medium-size preview used in the article header; [photo] is the full-size original. */
        val thumbnail: WikiPhoto? = null,
        val photoDesc: String? = null,
        val langs: List<WikiLang>? = null,
        val currentLang: String? = null,
        val pageId: Int? = null,
        val savedStatus: SavedStatus = SavedStatus.NOT_SAVED,
        /** Text of passages the user saved as highlights for this article. */
        val highlights: List<String> = emptyList(),
        /**
         * Normalised titles of linked main-namespace pages that do not exist. Rendered in the
         * error colour, matching Wikipedia's red links; empty until the background lookup finishes.
         */
        val missingLinks: Set<String> = emptySet(),
        /**
         * Stable per load. This is the navigation content key (`article-<uid>`), so progressive
         * text updates and save-state changes must keep it unchanged. Two different loads still
         * get different uids, so two back-stack entries can never share a key.
         */
        val uid: Long = nextArticleUid()
    ) : HomeSubscreen() {
        /**
         * [uid] is unique per load and is part of [equals]. Hashing only it keeps Compose and
         * NavDisplay bookkeeping from walking every parsed [AnnotatedString] whenever the article
         * updates. Equal objects still have equal hashes; collisions are resolved by [equals].
         */
        override fun hashCode(): Int = uid.hashCode()
    }

    data class Feed(
        val tfa: FeedApiTFA? = null,
        val mostReadArticles: List<MostReadArticle>? = null,
        val image: FeedApiImage? = null,
        val news: List<FeedApiNews>? = null,
        val onThisDay: List<FeedApiOTD>? = null,
        val sections: List<Pair<Int, FeedSection>> = emptyList(),
        /** Wikipedia language this feed was loaded from; article taps must stay in it. */
        val currentLang: String? = null,
        val listState: LazyListState = LazyListState()
    ) : HomeSubscreen()

    sealed class Image : HomeSubscreen() {
        data class FullScreenImage(
            val photo: WikiPhoto?,
            val photoDesc: String?,
            val title: String,
            val background: Boolean,
            val modifier: Modifier = Modifier,
            val link: String? = null,
            val articleTitle: String? = null,
            val articleLang: String? = null,
            val onBack: () -> Unit,
            /** Unique per push: two taps on the same picture must never produce two equal entries. */
            val uid: Long = nextArticleUid(),
        ) : Image()

        /** All pictures of an article as one swipeable gallery, opened at [startIndex]. */
        data class FullScreenGallery(
            val items: List<com.wikirandomizerp.app.ui.image.ArticleGalleryItem>,
            val startIndex: Int,
            val background: Boolean,
            val onBack: () -> Unit,
            val uid: Long = nextArticleUid(),
        ) : Image()

        data class FullScreenArticleImage(
            val uri: String,
            val description: String,
            val modifier: Modifier = Modifier,
            val link: String? = null,
            val background: Boolean,
            val articleTitle: String? = null,
            val articleLang: String? = null,
            val onBack: () -> Unit,
            val uid: Long = nextArticleUid(),
        ) : Image()
    }

    object FeedLoader : HomeSubscreen()
    object Logo : HomeSubscreen()
}

enum class FeedSection {
    TFA, MOST_READ, IMAGE, NEWS, ON_THIS_DAY
}
/** Progress and outcome of building the on-device topic library (see TopicIndexBuilder). */
@Immutable
data class IndexBuildState(
    val running: Boolean = false,
    val finished: Boolean = false,
    val cancelled: Boolean = false,
    val startedAtMs: Long = 0L,
    val finishedAtMs: Long = 0L,
    /** Last time any request completed: lets the UI show "last response 3 s ago". */
    val lastActivityMs: Long = 0L,
    val currentTopic: String = "",
    val topicIndex: Int = 0,
    val topicTotal: Int = 0,
    val progress: IndexProgress? = null,
    val results: List<TopicBuildResult> = emptyList()
)
