package org.nsh07.wikireader.shared.library

import org.nsh07.wikireader.shared.ArticleSummary
import org.nsh07.wikireader.shared.settings.SettingsStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LibraryRepositoryTest {
    @Test
    fun persistsSavedAndHistoryEntries() {
        val store = MemorySettingsStore()
        val repository = LibraryRepository(store)
        val article = ArticleSummary(title = "Kotlin", extract = "Programming language")

        repository.addSaved(LibraryEntry("en", article))
        repository.addHistory(LibraryEntry("en", article))

        val reopened = LibraryRepository(store)
        assertEquals(1, reopened.saved().size)
        assertEquals(1, reopened.history().size)
        assertEquals("Kotlin", reopened.saved().single().article.title)
    }

    @Test
    fun removesAndClearsEntries() {
        val store = MemorySettingsStore()
        val repository = LibraryRepository(store)
        val article = ArticleSummary(title = "Kotlin", extract = "Programming language")
        val entry = LibraryEntry("en", article)

        repository.addSaved(entry)
        assertEquals(0, repository.removeSaved(entry).size)
        repository.addSaved(entry)
        assertEquals(0, repository.clearSaved().size)
    }

    @Test
    fun searchHistoryDeduplicatesAndKeepsNewestFirst() {
        val store = MemorySettingsStore()
        val repository = LibraryRepository(store)

        repository.addSearchQuery("Kotlin", "en")
        repository.addSearchQuery("kotlin", "en")
        repository.addSearchQuery("Kotlin", "de")

        val searches = repository.recentSearches()
        assertEquals(2, searches.size)
        assertEquals("Kotlin", searches.first().query)
        assertEquals("de", searches.first().language)
    }

    @Test
    fun searchHistoryIgnoresShortQueriesAndCapsSize() {
        val store = MemorySettingsStore()
        val repository = LibraryRepository(store)

        assertEquals(0, repository.addSearchQuery("a", "en").size)
        repeat(20) { index -> repository.addSearchQuery("query $index", "en") }

        val searches = repository.recentSearches()
        assertEquals(12, searches.size)
        assertEquals("query 19", searches.first().query)
        assertTrue(searches.none { it.query == "query 8" })
    }

    @Test
    fun clearsSearchHistory() {
        val store = MemorySettingsStore()
        val repository = LibraryRepository(store)
        repository.addSearchQuery("Kotlin", "en")

        assertEquals(0, repository.clearSearches().size)
        assertEquals(0, LibraryRepository(store).recentSearches().size)
    }

    private class MemorySettingsStore : SettingsStore {
        private val values = mutableMapOf<String, String>()

        override fun getString(key: String, defaultValue: String): String =
            values[key] ?: defaultValue

        override fun putString(key: String, value: String) {
            values[key] = value
        }
    }
}
