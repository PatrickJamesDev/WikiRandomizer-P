package org.nsh07.wikireader.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.nsh07.wikireader.data.WikiApiCategorySearchResponse
import org.nsh07.wikireader.network.WikipediaApiService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter

interface WikipediaRepository {
    suspend fun getPrefixSearchResults(query: String): WikiApiPrefixSearchResults
    suspend fun getSearchResults(query: String): WikiApiSearchResults
    suspend fun getPageData(query: String): WikiApiPageData
    suspend fun getPageContent(title: String): String
    /** Fully rendered article HTML (REST API), used when the user selects the HTML renderer. */
    suspend fun getPageHtml(title: String): String
    /** Plain-text Wiktionary definitions for [word], or null when none were found/offline. */
    suspend fun getWiktionaryDefinition(word: String, lang: String): String?
    /** Images used on [title], for the Wikimedia Commons gallery. */
    suspend fun getCommonsImages(title: String, lang: String): List<CommonsImage>

    /**
     * The Wikimedia Commons category that belongs to the article [title] (found through Wikidata,
     * falling back to a category with the same name) and some of its pictures, or null when the
     * article has no Commons category with images.
     */
    suspend fun getCommonsCategoryGallery(title: String, lang: String): CommonsCategoryGallery? = null
    suspend fun getRandomResult(): WikiApiPageData
    suspend fun getCategoryMembers(category: String): WikiApiCategoryResponse
    /** Articles + subcategories of [category] in one call; pass the previous [cont] to page on. */
    suspend fun getCategoryChildren(category: String, cont: WikiContinue? = null): WikiApiCategoryResponse
    /** Pixel sizes of the picture files [titles] ("File:Name.jpg", 50 per call). */
    suspend fun getImageSizes(titles: List<String>): WikiApiImageInfoResponse
    /** Plain-text introductions of [titles] (20 per call). */
    suspend fun getIntros(titles: List<String>): WikiApiIntroResponse
    /** One page (50) of full-text search results starting at [offset]. */
    suspend fun searchArticlesPaged(query: String, offset: Int): WikiApiCategorySearchResponse
    /** Views of the last 30 days for [titles] (50 per call). */
    suspend fun getPageViews(titles: List<String>): WikiApiPageViewsResponse
    /** Sizes of categories; [categories] are bare names without the "Category:" prefix. */
    suspend fun getCategoryInfo(categories: List<String>): WikiApiCategoryInfoResponse
    /** Full-text article search (top 25 titles). */
    suspend fun searchArticleTitles(query: String): WikiApiCategorySearchResponse
    /** Visible categories of [titles] (keep to ~10 titles per call). */
    suspend fun getPageCategories(titles: List<String>): WikiApiPageInfoResponse
    /** Searches category pages by name; titles come back as "Category:Name". */
    suspend fun searchCategoryNames(query: String): WikiApiCategorySearchResponse
    /** Returns only subcategories of [category] — used for the random-walk traversal strategy. */
    suspend fun getCategorySubcats(category: String): WikiApiCategoryResponse
    /**
     * @param sort `"relevance"` (default) or `"incoming_links_desc"` for popularity-ranked results.
     */
    suspend fun searchInCategory(
        category: String,
        offset: Int = 0,
        limit: Int = 200,
        sort: String = "relevance"
    ): WikiApiCategorySearchResponse
    /**
     * Batch page-info lookup for stub detection.
     * [titles] is a list of article titles (batched internally in groups of 50).
     */
    suspend fun getPageInfo(titles: List<String>): WikiApiPageInfoResponse
    suspend fun getFeed(
        date: String = LocalDate.now()
            .format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
    ): FeedApiResponse
}

class NetworkWikipediaRepository(
    private val wikipediaApiService: WikipediaApiService,
    private val wikipediaPageApiService: WikipediaApiService,
    private val ioDispatcher: CoroutineDispatcher
) : WikipediaRepository {
    override suspend fun getPrefixSearchResults(query: String): WikiApiPrefixSearchResults =
        withContext(ioDispatcher) {
            wikipediaApiService.getPrefixSearchResults(query)
        }

    override suspend fun getSearchResults(query: String): WikiApiSearchResults =
        withContext(ioDispatcher) {
            wikipediaApiService.getSearchResults(query)
        }

    override suspend fun getPageData(query: String): WikiApiPageData =
        withContext(ioDispatcher) {
            wikipediaApiService.getPageData(query)
        }

    override suspend fun getPageContent(title: String): String =
        withContext(ioDispatcher) {
            wikipediaPageApiService.getPageContent(title)
        }

    override suspend fun getPageHtml(title: String): String =
        withContext(ioDispatcher) {
            wikipediaPageApiService.getPageHtml(title)
        }

    override suspend fun getWiktionaryDefinition(word: String, lang: String): String? =
        withContext(ioDispatcher) {
            val clean = word.trim()
            if (clean.isEmpty()) return@withContext null
            val encoded = URLEncoder.encode(clean, "UTF-8").replace("+", "%20")
            suspend fun lookup(code: String): String? = runCatching {
                wikipediaApiService.getUrl("https://$code.wiktionary.org/api/rest_v1/page/definition/$encoded")
            }.getOrNull()
            val body = lookup(lang)?.takeIf { it.contains("definitions") }
                ?: lookup("en")
                ?: return@withContext null
            runCatching {
                val root = Json.parseToJsonElement(body).jsonObject
                val out = ArrayList<String>()
                for ((_, languageBlock) in root) {
                    for (part in languageBlock.jsonArray) {
                        val obj = part.jsonObject
                        val speech = obj["partOfSpeech"]?.jsonPrimitive?.contentOrNull
                        val definitions = obj["definitions"]?.jsonArray ?: continue
                        for (definition in definitions) {
                            val raw = definition.jsonObject["definition"]
                                ?.jsonPrimitive?.contentOrNull ?: continue
                            val text = raw
                                .replace(Regex("<[^>]+>"), "")
                                .replace("&quot;", "\"")
                                .replace("&amp;", "&")
                                .replace(Regex("\\s+"), " ")
                                .trim()
                            if (text.isNotEmpty()) {
                                out.add(if (!speech.isNullOrBlank()) "($speech) $text" else text)
                            }
                            if (out.size >= 5) break
                        }
                        if (out.size >= 5) break
                    }
                    if (out.size >= 5) break
                }
                out.joinToString("\n\n").ifBlank { null }
            }.getOrNull()
        }

    override suspend fun getCommonsImages(title: String, lang: String): List<CommonsImage> =
        withContext(ioDispatcher) {
            val encoded = URLEncoder.encode(title, "UTF-8").replace("+", "%20")
            val url = "https://$lang.wikipedia.org/w/api.php?action=query&format=json" +
                    "&generator=images&gimlimit=50&redirects=1" +
                    "&titles=$encoded&prop=imageinfo&iiprop=url&iiurlwidth=600"
            runCatching {
                val root = Json.parseToJsonElement(wikipediaApiService.getUrl(url)).jsonObject
                val pages = root["query"]?.jsonObject?.get("pages")?.jsonObject ?: return@runCatching emptyList()
                pages.values.mapNotNull { page ->
                    val info = page.jsonObject["imageinfo"]?.jsonArray?.firstOrNull()?.jsonObject
                        ?: return@mapNotNull null
                    val name = page.jsonObject["title"]?.jsonPrimitive?.contentOrNull
                        ?.removePrefix("File:") ?: return@mapNotNull null
                    val thumb = info["thumburl"]?.jsonPrimitive?.contentOrNull
                    val full = info["url"]?.jsonPrimitive?.contentOrNull
                    val extension = (full ?: thumb).orEmpty().substringAfterLast('.', "").lowercase()
                    if (extension !in setOf("jpg", "jpeg", "png", "webp", "gif")) return@mapNotNull null
                    CommonsImage(
                        title = name,
                        thumbnailUrl = thumb,
                        fullUrl = full,
                        descriptionUrl = info["descriptionurl"]?.jsonPrimitive?.contentOrNull
                    )
                }
            }.getOrDefault(emptyList())
        }

    override suspend fun getCommonsCategoryGallery(title: String, lang: String): CommonsCategoryGallery? =
        withContext(ioDispatcher) {
            runCatching {
                fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

                // 1. Ask Wikidata which Commons category the article is linked to. The article
                //    "Navel orange" points at "Category:Navel oranges", which a name match would miss.
                val site = lang.replace('-', '_') + "wiki"
                val wikidataUrl = "https://www.wikidata.org/w/api.php?action=wbgetentities&format=json" +
                        "&sites=$site&titles=${enc(title)}&normalize=1&props=sitelinks|claims" +
                        "&sitefilter=commonswiki"
                val candidates = LinkedHashSet<String>()
                runCatching {
                    val root = Json.parseToJsonElement(wikipediaApiService.getUrl(wikidataUrl)).jsonObject
                    val entities = root["entities"]?.jsonObject ?: return@runCatching
                    for (entity in entities.values) {
                        val obj = entity.jsonObject
                        // P373 = "Commons category" (a plain category name)
                        obj["claims"]?.jsonObject?.get("P373")?.jsonArray?.forEach { claim ->
                            claim.jsonObject["mainsnak"]?.jsonObject?.get("datavalue")
                                ?.jsonObject?.get("value")?.jsonPrimitive?.contentOrNull
                                ?.let { candidates.add(it.trim().removePrefix("Category:")) }
                        }
                        // Sitelink to Commons: either "Category:X" or a gallery page "X"
                        obj["sitelinks"]?.jsonObject?.get("commonswiki")?.jsonObject?.get("title")
                            ?.jsonPrimitive?.contentOrNull?.let { link ->
                                candidates.add(link.trim().removePrefix("Category:"))
                            }
                    }
                }
                // 2. Fall back to a category named exactly like the article.
                candidates.add(title.trim())

                // 3. Take the first candidate category that really holds pictures.
                for (name in candidates.filter { it.isNotBlank() }) {
                    val gallery = loadCommonsCategory(name)
                    if (gallery != null) return@runCatching gallery
                }
                null
            }.getOrNull()
        }

    /** Pictures of the Commons category [name], or null when it is missing or holds no usable image. */
    private suspend fun loadCommonsCategory(name: String): CommonsCategoryGallery? {
        val encoded = URLEncoder.encode("Category:$name", "UTF-8").replace("+", "%20")
        val url = "https://commons.wikimedia.org/w/api.php?action=query&format=json&formatversion=2" +
                "&generator=categorymembers&gcmtitle=$encoded&gcmtype=file&gcmlimit=40" +
                "&prop=imageinfo&iiprop=url|size|mime&iiurlwidth=330"
        val root = Json.parseToJsonElement(wikipediaApiService.getUrl(url)).jsonObject
        val pages = root["query"]?.jsonObject?.get("pages")?.jsonArray ?: return null
        val images = pages
            .sortedBy { it.jsonObject["index"]?.jsonPrimitive?.intOrNull ?: Int.MAX_VALUE }
            .mapNotNull { page ->
                val obj = page.jsonObject
                val info = obj["imageinfo"]?.jsonArray?.firstOrNull()?.jsonObject ?: return@mapNotNull null
                val mime = info["mime"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (mime !in setOf("image/jpeg", "image/png", "image/webp", "image/gif")) return@mapNotNull null
                val thumb = info["thumburl"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val full = info["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val width = info["width"]?.jsonPrimitive?.intOrNull ?: 0
                val height = info["height"]?.jsonPrimitive?.intOrNull ?: 0
                if (width < 200 || height < 150) return@mapNotNull null
                // A 1280 px version for the viewer when the original is larger; Wikimedia only
                // serves a fixed set of thumbnail widths, 1280 is one of them.
                val view = if (width >= 1280 && thumb.contains("/330px-")) {
                    thumb.replace("/330px-", "/1280px-")
                } else full
                CommonsGalleryImage(
                    title = (obj["title"]?.jsonPrimitive?.contentOrNull ?: "")
                        .removePrefix("File:").substringBeforeLast('.').replace('_', ' '),
                    thumbnailUrl = thumb,
                    viewUrl = view,
                    width = width,
                    height = height,
                    descriptionUrl = info["descriptionurl"]?.jsonPrimitive?.contentOrNull
                )
            }
        if (images.isEmpty()) return null
        return CommonsCategoryGallery(
            category = name,
            categoryUrl = "https://commons.wikimedia.org/wiki/Category:" +
                    URLEncoder.encode(name.replace(' ', '_'), "UTF-8").replace("+", "%20"),
            images = images
        )
    }

    override suspend fun getRandomResult(): WikiApiPageData =
        withContext(ioDispatcher) {
            wikipediaApiService.getRandomResult()
        }

    override suspend fun getCategoryMembers(category: String): WikiApiCategoryResponse =
        withContext(ioDispatcher) {
            wikipediaApiService.getCategoryMembers("Category:$category")
        }

    override suspend fun getCategoryChildren(
        category: String,
        cont: WikiContinue?
    ): WikiApiCategoryResponse =
        withContext(ioDispatcher) {
            wikipediaApiService.getCategoryChildren("Category:$category", cont?.cont, cont?.cmcontinue)
        }

    override suspend fun getImageSizes(titles: List<String>): WikiApiImageInfoResponse =
        withContext(ioDispatcher) { wikipediaApiService.getImageSizes(titles.joinToString("|")) }

    override suspend fun getIntros(titles: List<String>): WikiApiIntroResponse =
        withContext(ioDispatcher) { wikipediaApiService.getIntros(titles.joinToString("|")) }

    override suspend fun searchArticlesPaged(query: String, offset: Int): WikiApiCategorySearchResponse =
        withContext(ioDispatcher) { wikipediaApiService.searchArticlesPaged(query, offset) }

    override suspend fun getPageViews(titles: List<String>): WikiApiPageViewsResponse =
        withContext(ioDispatcher) { wikipediaApiService.getPageViews(titles.joinToString("|")) }

    override suspend fun getCategoryInfo(categories: List<String>): WikiApiCategoryInfoResponse =
        withContext(ioDispatcher) {
            wikipediaApiService.getCategoryInfo(categories.joinToString("|") { "Category:$it" })
        }

    override suspend fun searchArticleTitles(query: String): WikiApiCategorySearchResponse =
        withContext(ioDispatcher) { wikipediaApiService.searchArticleTitles(query) }

    override suspend fun getPageCategories(titles: List<String>): WikiApiPageInfoResponse =
        withContext(ioDispatcher) { wikipediaApiService.getPageCategories(titles.joinToString("|")) }

    override suspend fun searchCategoryNames(query: String): WikiApiCategorySearchResponse =
        withContext(ioDispatcher) {
            wikipediaApiService.searchCategoryNames(query)
        }

    override suspend fun getCategorySubcats(category: String): WikiApiCategoryResponse =
        withContext(ioDispatcher) {
            wikipediaApiService.getCategorySubcats("Category:$category")
        }

    override suspend fun searchInCategory(
        category: String,
        offset: Int,
        limit: Int,
        sort: String
    ): WikiApiCategorySearchResponse =
        withContext(ioDispatcher) {
            wikipediaApiService.searchInCategory(
                query = "incategory:\"${category.replace('_', ' ')}\"",
                offset = offset,
                limit = limit,
                sort = sort
            )
        }

    override suspend fun getPageInfo(titles: List<String>): WikiApiPageInfoResponse =
        withContext(ioDispatcher) {
            // MediaWiki accepts up to 50 titles per prop=info call; caller batches if needed
            wikipediaApiService.getPageInfo(titles.joinToString("|"))
        }

    override suspend fun getFeed(date: String): FeedApiResponse =
        withContext(ioDispatcher) {
            wikipediaApiService.getFeed(date)
        }
}
