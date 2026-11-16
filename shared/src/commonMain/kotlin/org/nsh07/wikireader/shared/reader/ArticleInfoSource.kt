package org.nsh07.wikireader.shared.reader

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ArticleRevision(
    val user: String,
    val timestamp: String,
    val comment: String,
    val size: Int
)

/** Loads the talk-page wikitext and recent revisions for the shared article-info sheet. */
interface ArticleInfoSource {
    suspend fun talkWikitext(language: String, title: String): String?
    suspend fun revisions(language: String, title: String): List<ArticleRevision>
}

fun parseTalkWikitext(body: String): String? = runCatching {
    Json.parseToJsonElement(body)
        .jsonObject["parse"]
        ?.jsonObject
        ?.get("wikitext")
        ?.jsonPrimitive
        ?.contentOrNull
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}.getOrNull()

fun parseArticleRevisions(body: String): List<ArticleRevision> = runCatching {
    val pages = Json.parseToJsonElement(body)
        .jsonObject["query"]
        ?.jsonObject
        ?.get("pages")
        ?.jsonArray
        ?: return emptyList()
    pages.flatMap { page ->
        page.jsonObject["revisions"]?.jsonArray.orEmpty().mapNotNull { element ->
            val revision = element.jsonObject
            val timestamp = revision["timestamp"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            ArticleRevision(
                user = revision["user"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                timestamp = timestamp,
                comment = revision["comment"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                size = revision["size"]?.jsonPrimitive?.intOrNull ?: 0
            )
        }
    }
}.getOrDefault(emptyList())
