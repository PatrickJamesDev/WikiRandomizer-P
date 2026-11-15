package org.nsh07.wikireader.shared.library

import org.nsh07.wikireader.shared.settings.SettingsStore
import kotlin.test.Test
import kotlin.test.assertEquals

class VocabularyRepositoryTest {
    @Test
    fun addsDeduplicatesAndRemovesWords() {
        val store = MemorySettingsStore()
        val repository = VocabularyRepository(store)
        assertEquals(1, repository.add("plant", "en", "a living organism").size)
        assertEquals(1, repository.add("PLANT", "EN", "other").size)
        assertEquals(2, repository.add("Baum", "de", "ein Gewächs").size)
        assertEquals(1, repository.remove(repository.words().first()).size)
        assertEquals(emptyList(), repository.clear())
    }

    private class MemorySettingsStore : SettingsStore {
        private val values = mutableMapOf<String, String>()
        override fun getString(key: String, defaultValue: String) = values[key] ?: defaultValue
        override fun putString(key: String, value: String) { values[key] = value }
    }
}
