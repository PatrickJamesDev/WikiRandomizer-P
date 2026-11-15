package org.nsh07.wikireader.shared.library

import org.nsh07.wikireader.shared.settings.SettingsStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SavedImageRepositoryTest {
    @Test
    fun addsDeduplicatesAndRemovesImages() {
        val store = MemorySettingsStore()
        val repository = SavedImageRepository(store)

        val first = repository.add(
            imageUrl = "https://example.org/a.jpg",
            articleTitle = "Article",
            articleLanguage = "en"
        )
        assertEquals(1, first.size)

        val duplicate = repository.add(
            imageUrl = "https://example.org/a.jpg",
            articleTitle = " article ",
            articleLanguage = "EN"
        )
        assertEquals(1, duplicate.size)

        val second = repository.add(
            imageUrl = "https://example.org/b.jpg",
            articleTitle = "Other",
            articleLanguage = "de"
        )
        assertEquals(2, second.size)
        assertTrue(SavedImageRepository(store).images().any { it.id == second.first().id })

        val remaining = repository.remove(second.first())
        assertEquals(1, remaining.size)
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
