package org.nsh07.wikireader.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The subset of Wikipedia's REST summary response that the mobile readers display.
 *
 * Android still uses its richer MediaWiki API model, but the iOS shell and future Compose
 * Multiplatform screens share this schema so titles, descriptions and images cannot drift
 * between platforms. Unknown fields are ignored because the REST API adds data over time.
 */
@Serializable
data class ArticleSummary(
    val pageid: Int? = null,
    val title: String,
    @SerialName("description") val summaryDescription: String? = null,
    val extract: String,
    val thumbnail: ArticleImage? = null,
    @SerialName("content_urls") val contentUrls: ArticleContentUrls? = null
)

@Serializable
data class ArticleImage(
    val source: String,
    val width: Int? = null,
    val height: Int? = null
)

@Serializable
data class ArticleContentUrls(
    val desktop: ArticleContentPage? = null,
    val mobile: ArticleContentPage? = null
)

@Serializable
data class ArticleContentPage(
    val page: String? = null
)

/** Platform-neutral JSON entry point used by the iOS shell and by tests. */
object ArticleSummaryJson {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun parse(raw: String): ArticleSummary? {
        if (raw.isBlank()) return null
        return runCatching { json.decodeFromString<ArticleSummary>(raw) }.getOrNull()
    }
}
