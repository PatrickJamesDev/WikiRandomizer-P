package org.nsh07.wikireader.shared.library

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.nsh07.wikireader.shared.settings.SettingsKeys
import org.nsh07.wikireader.shared.settings.SettingsStore

/** A passage kept from an article. The shared summary view can save its introduction. */
@Serializable
data class HighlightEntry(
    val id: Long,
    val language: String,
    val title: String,
    val text: String,
    val thumbnail: String? = null,
    val description: String? = null
) {
    val key: String
        get() = "${language.lowercase()}|${title.trim().lowercase()}|${text.trim().lowercase()}"
}

class HighlightRepository(private val settingsStore: SettingsStore) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun highlights(): List<HighlightEntry> =
        runCatching {
            json.decodeFromString<List<HighlightEntry>>(
                settingsStore.getString(SettingsKeys.SAVED_HIGHLIGHTS, "[]")
            )
        }.getOrDefault(emptyList())

    fun add(
        language: String,
        title: String,
        text: String,
        thumbnail: String? = null,
        description: String? = null
    ): List<HighlightEntry> {
        val trimmedText = text.trim()
        val trimmedTitle = title.trim()
        if (trimmedText.isEmpty() || trimmedTitle.isEmpty()) return highlights()
        val current = highlights()
        val entry = HighlightEntry(
            id = (current.maxOfOrNull { it.id } ?: 0L) + 1L,
            language = language.trim().lowercase(),
            title = trimmedTitle,
            text = trimmedText,
            thumbnail = thumbnail,
            description = description
        )
        if (current.any { it.key == entry.key }) return current
        return write(listOf(entry) + current)
    }

    fun remove(entry: HighlightEntry): List<HighlightEntry> =
        write(highlights().filterNot { it.id == entry.id })

    fun clear(): List<HighlightEntry> = write(emptyList())

    fun search(query: String): List<HighlightEntry> {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) return highlights()
        return highlights().filter {
            it.title.lowercase().contains(normalized) ||
                it.text.lowercase().contains(normalized) ||
                it.description?.lowercase()?.contains(normalized) == true
        }
    }

    private fun write(entries: List<HighlightEntry>): List<HighlightEntry> {
        settingsStore.putString(SettingsKeys.SAVED_HIGHLIGHTS, json.encodeToString(entries))
        return entries
    }
}
