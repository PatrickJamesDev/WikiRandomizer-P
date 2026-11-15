package org.nsh07.wikireader.shared.reader

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Platform-neutral Wiktionary lookup used by the shared vocabulary screen. */
interface DictionarySource {
    suspend fun define(word: String, language: String): String?
}

/** Turns Wiktionary's definition JSON into short plain-text lines. */
fun parseWiktionaryDefinition(body: String): String? = runCatching {
    val root = Json.parseToJsonElement(body).jsonObject
    val lines = mutableListOf<String>()
    for ((_, languageBlock) in root) {
        for (part in languageBlock.jsonArray) {
            val obj = part.jsonObject
            val speech = obj["partOfSpeech"]?.jsonPrimitive?.contentOrNull
            val definitions = obj["definitions"]?.jsonArray ?: continue
            for (definition in definitions) {
                val text = definition.jsonObject["definition"]
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.replace(Regex("<[^>]*>"), "")
                    ?.replace("&amp;", "&")
                    ?.replace("&quot;", "\"")
                    ?.replace("&#39;", "'")
                    ?.replace("&nbsp;", " ")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: continue
                lines += if (speech.isNullOrBlank()) text else "$speech: $text"
                if (lines.size >= 6) break
            }
            if (lines.size >= 6) break
        }
        if (lines.size >= 6) break
    }
    lines.takeIf { it.isNotEmpty() }?.joinToString("\n")
}.getOrNull()
