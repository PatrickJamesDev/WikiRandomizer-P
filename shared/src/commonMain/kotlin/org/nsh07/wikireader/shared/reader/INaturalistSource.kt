package org.nsh07.wikireader.shared.reader

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Resolves a Wikipedia article title to an iNaturalist taxon id. */
interface INaturalistSource {
    suspend fun taxonId(title: String): Long?
}

fun parseINaturalistTaxonId(body: String): Long? = runCatching {
    Json.parseToJsonElement(body)
        .jsonObject["results"]
        ?.jsonArray
        ?.firstOrNull()
        ?.jsonObject
        ?.get("id")
        ?.jsonPrimitive
        ?.longOrNull
        ?.takeIf { it > 0 }
}.getOrNull()

object INaturalistUrls {
    const val USER_AGENT = "WikiRandomizer-P (iNaturalist taxon lookup)"

    fun autocompleteUrl(title: String): String =
        "https://api.inaturalist.org/v1/taxa/autocomplete?q=${encode(title.trim())}&per_page=1"

    fun taxonUrl(id: Long): String = "https://www.inaturalist.org/taxa/$id"

    fun searchUrl(title: String): String =
        "https://www.inaturalist.org/taxa/search?q=${encode(title.trim())}"

    private fun encode(value: String): String = buildString {
        for (byte in value.encodeToByteArray()) {
            val code = byte.toInt() and 0xFF
            val char = code.toChar()
            when {
                char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' -> append(char)
                char == '-' || char == '_' || char == '.' || char == '~' -> append(char)
                char == ' ' -> append("%20")
                else -> {
                    append('%')
                    append("0123456789ABCDEF"[code shr 4])
                    append("0123456789ABCDEF"[code and 0x0F])
                }
            }
        }
    }
}
