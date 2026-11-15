package org.nsh07.wikireader.shared.reader

import com.wikirandomizerp.app.data.CommonsImage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Platform-neutral Wikimedia Commons image lookup for an article. */
interface CommonsSource {
    suspend fun images(language: String, title: String): List<CommonsImage>
}

/** Extracts image thumbnails from the MediaWiki `generator=images` response. */
fun parseCommonsImages(body: String): List<CommonsImage> = runCatching {
    val root = Json.parseToJsonElement(body).jsonObject
    val pages = root["query"]?.jsonObject?.get("pages")?.jsonArray
        ?: root["query"]?.jsonObject?.get("pages")?.jsonObject?.values
        ?: return emptyList()
    pages.mapNotNull { page ->
        val obj = page.jsonObject
        val info = obj["imageinfo"]?.jsonArray?.firstOrNull()?.jsonObject ?: return@mapNotNull null
        val title = obj["title"]?.jsonPrimitive?.contentOrNull
            ?.removePrefix("File:")
            ?: return@mapNotNull null
        val thumb = info["thumburl"]?.jsonPrimitive?.contentOrNull
        val full = info["url"]?.jsonPrimitive?.contentOrNull
        val extension = (full ?: thumb).orEmpty().substringAfterLast('.', "").lowercase()
        if (extension !in setOf("jpg", "jpeg", "png", "webp", "gif")) return@mapNotNull null
        CommonsImage(
            title = title,
            thumbnailUrl = thumb,
            fullUrl = full,
            descriptionUrl = info["descriptionurl"]?.jsonPrimitive?.contentOrNull
        )
    }
}.getOrDefault(emptyList())
