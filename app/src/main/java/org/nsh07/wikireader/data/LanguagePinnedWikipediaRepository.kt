package org.nsh07.wikireader.data

import org.nsh07.wikireader.network.HostGate
import org.nsh07.wikireader.network.HostSelectionInterceptor

/**
 * A [WikipediaRepository] view pinned to one Wikipedia language.
 *
 * The underlying Retrofit service selects the language through a shared mutable host, so a
 * long-running background build must not follow the user's language setting. Every call made
 * through this wrapper sets the host inside [HostGate] immediately before the request.
 */
class LanguagePinnedWikipediaRepository(
    private val delegate: WikipediaRepository,
    private val interceptor: HostSelectionInterceptor,
    private val lang: String
) : WikipediaRepository {

    private suspend fun <T> onWiki(block: suspend () -> T): T =
        HostGate.withHost(interceptor, lang, block)

    override suspend fun getPrefixSearchResults(query: String): WikiApiPrefixSearchResults =
        onWiki { delegate.getPrefixSearchResults(query) }

    override suspend fun getSearchResults(query: String): WikiApiSearchResults =
        onWiki { delegate.getSearchResults(query) }

    override suspend fun getPageData(query: String): WikiApiPageData =
        onWiki { delegate.getPageData(query) }

    override suspend fun getPageContent(title: String): String =
        onWiki { delegate.getPageContent(title) }

    override suspend fun getPageHtml(title: String): String =
        onWiki { delegate.getPageHtml(title) }

    override suspend fun getWiktionaryDefinition(word: String, lang: String): String? =
        delegate.getWiktionaryDefinition(word, lang)

    override suspend fun getCommonsImages(title: String, lang: String): List<CommonsImage> =
        delegate.getCommonsImages(title, lang)

    override suspend fun getRandomResult(): WikiApiPageData =
        onWiki { delegate.getRandomResult() }

    override suspend fun getCategoryMembers(category: String): WikiApiCategoryResponse =
        onWiki { delegate.getCategoryMembers(category) }

    override suspend fun getCategoryChildren(
        category: String,
        cont: WikiContinue?
    ): WikiApiCategoryResponse = onWiki { delegate.getCategoryChildren(category, cont) }

    override suspend fun getImageSizes(titles: List<String>): WikiApiImageInfoResponse =
        onWiki { delegate.getImageSizes(titles) }

    override suspend fun getIntros(titles: List<String>): WikiApiIntroResponse =
        onWiki { delegate.getIntros(titles) }

    override suspend fun searchArticlesPaged(
        query: String,
        offset: Int
    ): WikiApiCategorySearchResponse = onWiki { delegate.searchArticlesPaged(query, offset) }

    override suspend fun getPageViews(titles: List<String>): WikiApiPageViewsResponse =
        onWiki { delegate.getPageViews(titles) }

    override suspend fun getCategoryInfo(categories: List<String>): WikiApiCategoryInfoResponse =
        onWiki { delegate.getCategoryInfo(categories) }

    override suspend fun searchArticleTitles(query: String): WikiApiCategorySearchResponse =
        onWiki { delegate.searchArticleTitles(query) }

    override suspend fun getPageCategories(titles: List<String>): WikiApiPageInfoResponse =
        onWiki { delegate.getPageCategories(titles) }

    override suspend fun searchCategoryNames(query: String): WikiApiCategorySearchResponse =
        onWiki { delegate.searchCategoryNames(query) }

    override suspend fun getCategorySubcats(category: String): WikiApiCategoryResponse =
        onWiki { delegate.getCategorySubcats(category) }

    override suspend fun searchInCategory(
        category: String,
        offset: Int,
        limit: Int,
        sort: String
    ): WikiApiCategorySearchResponse =
        onWiki { delegate.searchInCategory(category, offset, limit, sort) }

    override suspend fun getPageInfo(titles: List<String>): WikiApiPageInfoResponse =
        onWiki { delegate.getPageInfo(titles) }

    override suspend fun getMissingLinkedArticles(title: String): Set<String> =
        onWiki { delegate.getMissingLinkedArticles(title) }

    override suspend fun getFeed(date: String): FeedApiResponse =
        onWiki { delegate.getFeed(date) }
}
