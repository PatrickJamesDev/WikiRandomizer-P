package org.nsh07.wikireader.shared.library

import org.nsh07.wikireader.shared.ArticleSummary
import org.nsh07.wikireader.shared.settings.SettingsKeys
import org.nsh07.wikireader.shared.settings.SettingsStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun savesAndUpdatesPersonalNotesWithoutChangingOrder() {
        val store = MemorySettingsStore()
        val repository = LibraryRepository(store)
        val kotlin = LibraryEntry("en", ArticleSummary(title = "Kotlin", extract = ""))
        val compose = LibraryEntry("en", ArticleSummary(title = "Compose", extract = ""))
        repository.addSaved(kotlin)
        repository.addSaved(compose)

        repository.setSavedNote(kotlin, "  Read again  ")
        repository.setSavedNote(compose, "   ")

        val reopened = LibraryRepository(store).saved()
        assertEquals(listOf("Compose", "Kotlin"), reopened.map { it.article.title })
        assertEquals("Read again", reopened.last().note)
        assertEquals(null, reopened.first().note)
    }

    @Test
    fun savesFoldersAndNotesTogether() {
        val store = MemorySettingsStore()
        val repository = LibraryRepository(store)
        val kotlin = LibraryEntry("en", ArticleSummary(title = "Kotlin", extract = ""))
        val compose = LibraryEntry("en", ArticleSummary(title = "Compose", extract = ""))
        repository.addSaved(kotlin)
        repository.addSaved(compose)

        repository.setSavedDetails(kotlin, "  Research  ", "  Check this  ")
        repository.setSavedFolder(compose, "Reading")

        val reopened = LibraryRepository(store).saved()
        assertEquals(listOf("Compose", "Kotlin"), reopened.map { it.article.title })
        assertEquals("Research", reopened.last().folder)
        assertEquals("Check this", reopened.last().note)
        assertEquals("Reading", reopened.first().folder)
        assertEquals(listOf("Reading", "Research"), LibraryRepository(store).savedFolders())
    }

    @Test
    fun savedFoldersDeduplicateCaseInsensitively() {
        val store = MemorySettingsStore()
        val repository = LibraryRepository(store)
        repository.addSaved(
            LibraryEntry("en", ArticleSummary(title = "A", extract = ""), folder = "Research")
        )
        repository.addSaved(
            LibraryEntry("en", ArticleSummary(title = "B", extract = ""), folder = "research")
        )
        repository.addSaved(
            LibraryEntry("en", ArticleSummary(title = "C", extract = ""), folder = " ")
        )

        assertEquals(listOf("Research"), repository.savedFolders())
    }

    @Test
    fun oldSavedEntriesDecodeWithoutFolderOrNote() {
        val store = MemorySettingsStore()
        store.putString(
            SettingsKeys.SAVED_ARTICLES,
            """[{"language":"en","article":{"title":"Kotlin","extract":""}}]"""
        )

        val entry = LibraryRepository(store).saved().single()
        assertEquals("Kotlin", entry.article.title)
        assertEquals(null, entry.folder)
        assertEquals(null, entry.note)
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

    @Test
    fun seenArticlesPersistAndDeduplicateCaseInsensitively() {
        val store = MemorySettingsStore()
        val repository = LibraryRepository(store)

        repository.markSeen("en", "Kotlin")
        repository.markSeen("en", "kotlin")
        repository.markSeen("de", "Kotlin")

        val seen = LibraryRepository(store).seenArticles()
        assertEquals(2, seen.size)
        assertTrue(repository.isSeen("en", "KOTLIN"))
        assertTrue(repository.isSeen("de", "kotlin"))
        assertFalse(repository.isSeen("fr", "Kotlin"))
    }

    @Test
    fun seenCacheReflectsMutations() {
        val store = MemorySettingsStore()
        val repository = LibraryRepository(store)

        assertTrue(repository.seenArticles().isEmpty())
        repository.markSeen("en", "Kotlin")
        assertEquals(1, repository.seenArticles().size)
        assertTrue(repository.isSeen("en", "Kotlin"))
        repository.clearSeen()
        assertTrue(repository.seenArticles().isEmpty())
        assertFalse(repository.isSeen("en", "Kotlin"))
    }

    @Test
    fun clearsSeenArticles() {
        val store = MemorySettingsStore()
        val repository = LibraryRepository(store)
        repository.markSeen("en", "Kotlin")

        assertEquals(0, repository.clearSeen().size)
        assertFalse(LibraryRepository(store).isSeen("en", "Kotlin"))
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
