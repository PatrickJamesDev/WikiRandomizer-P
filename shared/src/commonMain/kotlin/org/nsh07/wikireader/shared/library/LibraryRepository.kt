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
    val article: ArticleSummary,
    /** Optional personal note shown in the Saved list. Old entries decode with no note. */
    val note: String? = null
) {
    val key: String
        get() = "${language.lowercase()}|${article.title.trim().lowercase()}"
}

@Serializable
data class SearchQuery(
    val query: String,
    val language: String
) {
    val key: String
        get() = "${language.lowercase()}|${query.trim().lowercase()}"
}

/** A title the reader has already been shown, remembered across launches and app updates. */
@Serializable
data class SeenArticle(
    val language: String,
    val title: String
) {
    val key: String
        get() = "${language.lowercase()}|${title.trim().lowercase()}"
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

    // Seen checks run for every random candidate. Keep the decoded list in memory instead of
    // parsing the settings JSON again for each title.
    private var seenCache: List<SeenArticle>? = null

    fun saved(): List<LibraryEntry> = read(SettingsKeys.SAVED_ARTICLES)

    fun history(): List<LibraryEntry> = read(SettingsKeys.ARTICLE_HISTORY)

    fun addSaved(entry: LibraryEntry): List<LibraryEntry> =
        write(SettingsKeys.SAVED_ARTICLES, prepend(entry, saved()))

    fun removeSaved(entry: LibraryEntry): List<LibraryEntry> =
        write(SettingsKeys.SAVED_ARTICLES, saved().filterNot { it.key == entry.key })

    /** Replaces the saved entry that matches [entry]'s article, preserving its list position. */
    fun updateSaved(entry: LibraryEntry): List<LibraryEntry> {
        val current = saved()
        if (current.none { it.key == entry.key }) return current
        return write(
            SettingsKeys.SAVED_ARTICLES,
            current.map { if (it.key == entry.key) entry else it }
        )
    }

    fun setSavedNote(entry: LibraryEntry, note: String?): List<LibraryEntry> =
        updateSaved(entry.copy(note = note?.trim()?.takeIf { it.isNotEmpty() }))

    fun clearSaved(): List<LibraryEntry> =
        write(SettingsKeys.SAVED_ARTICLES, emptyList())

    fun addHistory(entry: LibraryEntry): List<LibraryEntry> =
        write(SettingsKeys.ARTICLE_HISTORY, prepend(entry, history()).take(MAX_HISTORY))

    fun removeHistory(entry: LibraryEntry): List<LibraryEntry> =
        write(SettingsKeys.ARTICLE_HISTORY, history().filterNot { it.key == entry.key })

    fun clearHistory(): List<LibraryEntry> =
        write(SettingsKeys.ARTICLE_HISTORY, emptyList())

    fun recentSearches(): List<SearchQuery> = readSearches()

    fun addSearchQuery(query: String, language: String): List<SearchQuery> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return recentSearches()
        val entry = SearchQuery(trimmed, language)
        return writeSearches(
            (listOf(entry) + recentSearches().filterNot { it.key == entry.key })
                .take(MAX_SEARCHES)
        )
    }

    fun clearSearches(): List<SearchQuery> = writeSearches(emptyList())

    fun seenArticles(): List<SeenArticle> =
        seenCache ?: readSeen().also { seenCache = it }

    fun isSeen(language: String, title: String): Boolean {
        val key = SeenArticle(language, title).key
        return seenArticles().any { it.key == key }
    }

    /**
     * Remembers [title] as shown. Newest entries are kept first, so a bounded list forgets the
     * oldest articles first instead of losing the current reading session.
     */
    fun markSeen(language: String, title: String): List<SeenArticle> {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return seenArticles()
        val entry = SeenArticle(language, trimmed)
        val updated = (listOf(entry) + seenArticles().filterNot { it.key == entry.key })
            .take(MAX_SEEN)
        settingsStore.putString(SettingsKeys.SEEN_ARTICLES, json.encodeToString(updated))
        seenCache = updated
        return updated
    }

    fun clearSeen(): List<SeenArticle> {
        settingsStore.putString(SettingsKeys.SEEN_ARTICLES, "[]")
        seenCache = emptyList()
        return emptyList()
    }

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

    private fun readSearches(): List<SearchQuery> =
        runCatching {
            json.decodeFromString<List<SearchQuery>>(
                settingsStore.getString(SettingsKeys.SEARCH_HISTORY, "[]")
            )
        }.getOrDefault(emptyList())

    private fun writeSearches(entries: List<SearchQuery>): List<SearchQuery> {
        settingsStore.putString(SettingsKeys.SEARCH_HISTORY, json.encodeToString(entries))
        return entries
    }

    private fun readSeen(): List<SeenArticle> =
        runCatching {
            json.decodeFromString<List<SeenArticle>>(
                settingsStore.getString(SettingsKeys.SEEN_ARTICLES, "[]")
            )
        }.getOrDefault(emptyList())

    private companion object {
        const val MAX_HISTORY = 200
        const val MAX_SEARCHES = 12
        const val MAX_SEEN = 5_000
    }
}
