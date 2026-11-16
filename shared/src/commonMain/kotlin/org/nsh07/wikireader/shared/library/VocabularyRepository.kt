package org.nsh07.wikireader.shared.library

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.nsh07.wikireader.shared.settings.SettingsKeys
import org.nsh07.wikireader.shared.settings.SettingsStore

@Serializable
data class VocabularyEntry(
    val id: Long,
    val word: String,
    val language: String,
    val definition: String? = null,
    val articleTitle: String? = null
) {
    val key: String
        get() = "${language.lowercase()}|${word.trim().lowercase()}"
}

class VocabularyRepository(private val settingsStore: SettingsStore) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun words(): List<VocabularyEntry> =
        runCatching {
            json.decodeFromString<List<VocabularyEntry>>(
                settingsStore.getString(SettingsKeys.VOCABULARY, "[]")
            )
        }.getOrDefault(emptyList())

    fun add(
        word: String,
        language: String,
        definition: String? = null,
        articleTitle: String? = null
    ): List<VocabularyEntry> {
        val trimmed = word.trim().take(80)
        if (trimmed.isEmpty()) return words()
        val current = words()
        val entry = VocabularyEntry(
            id = (current.maxOfOrNull { it.id } ?: 0L) + 1L,
            word = trimmed,
            language = language.trim().lowercase(),
            definition = definition?.trim()?.takeIf { it.isNotEmpty() },
            articleTitle = articleTitle?.trim()?.takeIf { it.isNotEmpty() }
        )
        if (current.any { it.key == entry.key }) return current
        return write(listOf(entry) + current)
    }

    fun remove(entry: VocabularyEntry): List<VocabularyEntry> =
        write(words().filterNot { it.id == entry.id })

    fun clear(): List<VocabularyEntry> = write(emptyList())

    private fun write(entries: List<VocabularyEntry>): List<VocabularyEntry> {
        settingsStore.putString(SettingsKeys.VOCABULARY, json.encodeToString(entries))
        return entries
    }
}
