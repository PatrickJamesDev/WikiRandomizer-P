package org.nsh07.wikireader.shared.library

import org.nsh07.wikireader.shared.settings.SettingsStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HighlightRepositoryTest {
    @Test
    fun addsDeduplicatesSearchesAndRemovesHighlights() {
        val store = MemorySettingsStore()
        val repository = HighlightRepository(store)

        val added = repository.add("en", "Article", "A useful passage")
        assertEquals(1, added.size)
        assertEquals(1, repository.add("EN", " article ", "a useful passage").size)

        repository.add("de", "Anderer", "Ein Satz")
        assertTrue(repository.search("useful").isNotEmpty())
        assertEquals(1, repository.search("Ein").size)

        val removed = repository.remove(added.first())
        assertEquals(1, removed.size)
        assertEquals(emptyList(), repository.clear())
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
