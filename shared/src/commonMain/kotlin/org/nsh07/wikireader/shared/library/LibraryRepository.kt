package org.nsh07.wikireader.shared.library

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.nsh07.wikireader.shared.ArticleSummary
import org.nsh07.wikireader.shared.settings.SettingsKeys
import org.nsh07.wikireader.shared.settings.SettingsStore

@Serializable
data class LibraryEntry(
    val language: String,
    val article: ArticleSummary
) {
    val key: String
        get() = "${language.lowercase()}|${article.title.trim().lowercase()}"
}

/**
 * Small shared Saved/History store.
 *
 * The summaries are JSON encoded into the existing settings boundary so iOS can keep a real
 * library before the Room KMP migration. It is deliberately narrow: the Android host can later
 * provide a database-backed repository without changing the shared screens.
 */
class LibraryRepository(private val settingsStore: SettingsStore) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun saved(): List<LibraryEntry> = read(SettingsKeys.SAVED_ARTICLES)

    fun history(): List<LibraryEntry> = read(SettingsKeys.ARTICLE_HISTORY)

    fun addSaved(entry: LibraryEntry): List<LibraryEntry> =
        write(SettingsKeys.SAVED_ARTICLES, prepend(entry, saved()))

    fun removeSaved(entry: LibraryEntry): List<LibraryEntry> =
        write(SettingsKeys.SAVED_ARTICLES, saved().filterNot { it.key == entry.key })

    fun clearSaved(): List<LibraryEntry> =
        write(SettingsKeys.SAVED_ARTICLES, emptyList())

    fun addHistory(entry: LibraryEntry): List<LibraryEntry> =
        write(SettingsKeys.ARTICLE_HISTORY, prepend(entry, history()).take(MAX_HISTORY))

    fun removeHistory(entry: LibraryEntry): List<LibraryEntry> =
        write(SettingsKeys.ARTICLE_HISTORY, history().filterNot { it.key == entry.key })

    fun clearHistory(): List<LibraryEntry> =
        write(SettingsKeys.ARTICLE_HISTORY, emptyList())

    private fun prepend(entry: LibraryEntry, entries: List<LibraryEntry>): List<LibraryEntry> =
        listOf(entry) + entries.filterNot { it.key == entry.key }

    private fun read(key: String): List<LibraryEntry> =
        runCatching {
            json.decodeFromString<List<LibraryEntry>>(settingsStore.getString(key, "[]"))
        }.getOrDefault(emptyList())

    private fun write(key: String, entries: List<LibraryEntry>): List<LibraryEntry> {
        settingsStore.putString(key, json.encodeToString(entries))
        return entries
    }

    private companion object {
        const val MAX_HISTORY = 200
    }
}
