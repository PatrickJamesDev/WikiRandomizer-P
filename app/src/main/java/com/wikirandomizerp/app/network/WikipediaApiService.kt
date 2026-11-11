package com.wikirandomizerp.app.network

import com.wikirandomizerp.app.data.WikiApiCategoryInfoResponse
import com.wikirandomizerp.app.data.FeedApiResponse
import com.wikirandomizerp.app.data.WikiApiCategoryResponse
import com.wikirandomizerp.app.data.WikiApiCategorySearchResponse
import com.wikirandomizerp.app.data.WikiApiPageData
import com.wikirandomizerp.app.data.WikiApiImageInfoResponse
import com.wikirandomizerp.app.data.WikiApiIntroResponse
import com.wikirandomizerp.app.data.WikiApiPageInfoResponse
import com.wikirandomizerp.app.data.WikiApiPageViewsResponse
import com.wikirandomizerp.app.data.WikiApiPrefixSearchResults
import com.wikirandomizerp.app.data.WikiApiSearchResults
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

private const val CATEGORY_MEMBERS_QUERY =
    "w/api.php?format=json&action=query&list=categorymembers&cmtype=page&cmlimit=100&formatversion=2&maxage=3600&smaxage=3600"

private const val CATEGORY_SUBCATS_QUERY =
    "w/api.php?format=json&action=query&list=categorymembers&cmtype=subcat&cmlimit=max&formatversion=2&maxage=3600&smaxage=3600"

/** First sentences of articles as plain text (20 per call): the text the relevance model reads. */
private const val INTRO_QUERY =
    "w/api.php?format=json&action=query&prop=extracts&exintro=1&explaintext=1&exchars=420&exlimit=max&formatversion=2&maxlag=5"

/** Pixel size of picture files (50 per call): lets an article reserve a picture's height before it loads. */
private const val IMAGE_INFO_QUERY =
    "w/api.php?format=json&action=query&prop=imageinfo&iiprop=size&formatversion=2&maxlag=5"

/** Full-text search with paging (50 per page): finds on-topic articles outside the category tree. */
private const val ARTICLE_SEARCH_PAGED_QUERY =
    "w/api.php?format=json&action=query&list=search&srnamespace=0&srlimit=50&srprop=snippet&formatversion=2&maxlag=5"

/** Daily views of the last 30 days per article, used to prefer obscure or popular articles. */
private const val PAGE_VIEWS_QUERY =
    // PageViewInfo has no pvilimit parameter (it used to be sent and ignored). It returns at most
    // ten pages per request; TopicIndexBuilder therefore batches page views in tens.
    "w/api.php?format=json&action=query&prop=pageviews&pvipdays=30&formatversion=2&maxlag=5"

/** Member counts of categories (size = articles + subcategories + files). */
private const val CATEGORY_INFO_QUERY =
    "w/api.php?format=json&action=query&prop=categoryinfo&formatversion=2&maxlag=5"

/** Full-text article search used to find "seed" articles for a natural-language interest. */
private const val ARTICLE_SEARCH_QUERY =
    "w/api.php?format=json&action=query&list=search&srnamespace=0&srlimit=25&formatversion=2&maxlag=5"

/** Visible (non-maintenance) categories of several pages at once. */
private const val PAGE_CATEGORIES_QUERY =
    "w/api.php?format=json&action=query&prop=categories&clshow=!hidden&cllimit=max&formatversion=2&maxlag=5"

/** One call returns both articles and subcategories (up to 500) plus a continuation token. */
private const val CATEGORY_CHILDREN_QUERY =
    "w/api.php?format=json&action=query&list=categorymembers&cmtype=page|subcat&cmprop=ids|title|type&cmlimit=500&formatversion=2&maxage=3600&smaxage=3600&maxlag=5"

/** Finds category pages by name; used to repair a wrong/missing hand-written category name. */
private const val CATEGORY_NAME_SEARCH_QUERY =
    "w/api.php?format=json&action=query&list=search&srnamespace=14&srlimit=5&formatversion=2&maxlag=5"

private const val CATEGORY_SEARCH_QUERY =
    "w/api.php?format=json&action=query&list=search&srnamespace=0&srinfo=totalhits&formatversion=2"

/**
 * Used for stub detection and images-only filtering: returns wikitext byte-length plus a
 * small thumbnail (if any) for a pipe-separated list of titles. A tiny pithumbsize keeps this
 * bandwidth-cheap since only presence/absence of a thumbnail is needed, not the image itself.
 */
private const val PAGE_INFO_QUERY =
    "w/api.php?format=json&action=query&prop=info|pageimages|categories|description&piprop=thumbnail&pithumbsize=60&pilicense=any&clcategories=Category:Good_articles|Category:Featured_articles&cllimit=max&redirects=1&formatversion=2&maxage=3600&smaxage=3600"

/**
 * Main-namespace pages linked from an article, with existence information. Used to paint wiki
 * links to pages that do not exist red, matching desktop Wikipedia's "red link" convention.
 */
private const val LINKED_PAGES_QUERY =
    "w/api.php?format=json&action=query&generator=links&gplnamespace=0&gpllimit=max&prop=info&formatversion=2&maxlag=5&maxage=3600&smaxage=3600"

private const val API_QUERY =
    "w/api.php?format=json&action=query&prop=pageimages|description|langlinks&piprop=thumbnail|original&pithumbsize=960&pilicense=any&lllimit=max&redirects=1&formatversion=2&maxage=900&smaxage=900"

private const val CONTENT_QUERY =
    "wiki/{title}?action=raw&maxage=900&smaxage=900"

/** Fully rendered article HTML from the REST API (used by the optional HTML renderer). */
private const val HTML_QUERY =
    "api/rest_v1/page/html/{title}"

private const val FEED_QUERY =
    "api/rest_v1/feed/featured/{date}"

private const val PREFIX_SEARCH_QUERY =
    "w/api.php?action=query&generator=prefixsearch&prop=pageimages|pageterms&piprop=thumbnail&pithumbsize=250&pilicense=any&wbptterms=description&gpslimit=20&redirects=1&format=json&formatversion=2&maxage=900&smaxage=900"

private const val RANDOM_QUERY =
    "w/api.php?format=json&action=query&prop=pageimages|description|langlinks&piprop=thumbnail|original&pithumbsize=330&pilicense=any&lllimit=max&generator=random&grnlimit=6&grnfilterredir=nonredirects&formatversion=2&grnnamespace=0"

private const val SEARCH_QUERY =
    "w/api.php?action=query&generator=search&prop=pageimages&piprop=thumbnail&pithumbsize=250&pilicense=any&gsrnamespace=0&gsrlimit=20&gsrprop=snippet|titlesnippet|redirecttitle|extensiondata&redirects=1&format=json&formatversion=2&maxage=900&smaxage=900"

interface WikipediaApiService {
    @GET(IMAGE_INFO_QUERY)
    suspend fun getImageSizes(@Query("titles") titles: String): WikiApiImageInfoResponse

    @GET(INTRO_QUERY)
    suspend fun getIntros(@Query("titles") titles: String): WikiApiIntroResponse

    @GET(ARTICLE_SEARCH_PAGED_QUERY)
    suspend fun searchArticlesPaged(
        @Query("srsearch") query: String,
        @Query("sroffset") offset: Int
    ): WikiApiCategorySearchResponse

    @GET(PAGE_VIEWS_QUERY)
    suspend fun getPageViews(@Query("titles") titles: String): WikiApiPageViewsResponse

    @GET(CATEGORY_INFO_QUERY)
    suspend fun getCategoryInfo(@Query("titles") titles: String): WikiApiCategoryInfoResponse

    @GET(ARTICLE_SEARCH_QUERY)
    suspend fun searchArticleTitles(@Query("srsearch") query: String): WikiApiCategorySearchResponse

    @GET(PAGE_CATEGORIES_QUERY)
    suspend fun getPageCategories(@Query("titles") titles: String): WikiApiPageInfoResponse

    @GET(CATEGORY_CHILDREN_QUERY)
    suspend fun getCategoryChildren(
        @Query("cmtitle") category: String,
        @Query("continue") cont: String? = null,
        @Query("cmcontinue") cmContinue: String? = null
    ): WikiApiCategoryResponse

    @GET(CATEGORY_NAME_SEARCH_QUERY)
    suspend fun searchCategoryNames(@Query("srsearch") query: String): WikiApiCategorySearchResponse

    @GET(CATEGORY_MEMBERS_QUERY)
    suspend fun getCategoryMembers(@Query("cmtitle") category: String): WikiApiCategoryResponse

    /** Returns only subcategories of [category], used for tree-walk-based discovery. */
    @GET(CATEGORY_SUBCATS_QUERY)
    suspend fun getCategorySubcats(@Query("cmtitle") category: String): WikiApiCategoryResponse

    /**
     * @param sort "relevance" (default) or "incoming_links_desc" for popularity-ranked results.
     */
    @GET(CATEGORY_SEARCH_QUERY)
    suspend fun searchInCategory(
        @Query("srsearch") query: String,
        @Query("sroffset") offset: Int = 0,
        @Query("srlimit") limit: Int = 200,
        @Query("srsort") sort: String = "relevance"
    ): WikiApiCategorySearchResponse

    /**
     * Batch page-info lookup. [titles] should be pipe-separated article titles (max 50).
     * Each result includes WikiPageInfo.length (wikitext bytes) for stub detection.
     */
    @GET(PAGE_INFO_QUERY)
    suspend fun getPageInfo(@Query("titles") titles: String): WikiApiPageInfoResponse

    /** Pages linked from [title]; continue with [gplcontinue] until the response has no continuation. */
    @GET(LINKED_PAGES_QUERY)
    suspend fun getLinkedPagesInfo(
        @Query("titles") title: String,
        @Query("gplcontinue") gplcontinue: String? = null
    ): WikiApiPageInfoResponse

    @GET(PREFIX_SEARCH_QUERY)
    suspend fun getPrefixSearchResults(@Query("gpssearch") query: String): WikiApiPrefixSearchResults

    @GET(SEARCH_QUERY)
    suspend fun getSearchResults(@Query("gsrsearch") query: String): WikiApiSearchResults

    @GET(API_QUERY)
    suspend fun getPageData(@Query("titles") query: String): WikiApiPageData

    @GET(CONTENT_QUERY)
    suspend fun getPageContent(@Path("title", encoded = true) title: String): String

    /** Wikimedia REST HTML for an article, with links and basic formatting already rendered. */
    @GET(HTML_QUERY)
    suspend fun getPageHtml(@Path("title", encoded = true) title: String): String

    /** Absolute-URL request used for Wiktionary definitions and other Wikimedia projects. */
    @GET
    suspend fun getUrl(@Url url: String): String

    @GET(RANDOM_QUERY)
    suspend fun getRandomResult(): WikiApiPageData

    @GET(FEED_QUERY)
    suspend fun getFeed(
        @Path("date", encoded = true) date: String,
    ): FeedApiResponse
}
