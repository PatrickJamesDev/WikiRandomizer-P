package org.nsh07.wikireader.shared.reader

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ArticleLanguageLink(
    val language: String,
    val title: String,
    val autonym: String?,
    val languageName: String?
) {
    val label: String
        get() = autonym?.takeIf { it.isNotBlank() }
            ?: languageName?.takeIf { it.isNotBlank() }
            ?: language
}

/** Returns the other-language versions of an article through MediaWiki's langlinks API. */
interface ArticleLanguagesSource {
    suspend fun languages(language: String, title: String): List<ArticleLanguageLink>
}

fun parseArticleLanguages(body: String): List<ArticleLanguageLink> = runCatching {
    val pages = Json.parseToJsonElement(body)
        .jsonObject["query"]
        ?.jsonObject
        ?.get("pages")
        ?.jsonArray
        ?: return emptyList()
    pages.flatMap { page ->
        page.jsonObject["langlinks"]?.jsonArray.orEmpty().mapNotNull { element ->
            val link = element.jsonObject
            val language = link["lang"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val title = link["title"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            ArticleLanguageLink(
                language = language,
                title = title,
                autonym = link["autonym"]?.jsonPrimitive?.contentOrNull,
                languageName = link["langname"]?.jsonPrimitive?.contentOrNull
            )
        }
    }.distinctBy { it.language }.sortedBy { it.label.lowercase() }
}.getOrDefault(emptyList())
