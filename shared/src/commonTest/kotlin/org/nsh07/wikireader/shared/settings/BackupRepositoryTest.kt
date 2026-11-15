package org.nsh07.wikireader.shared.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BackupRepositoryTest {
    @Test
    fun exportsAndRestoresKnownKeys() {
        val source = MemorySettingsStore()
        source.putString(SettingsKeys.LANGUAGE, "de")
        source.putString(SettingsKeys.SAVED_ARTICLES, "[]")
        val backup = BackupRepository(source).export()

        val target = MemorySettingsStore()
        assertEquals(2, BackupRepository(target).import(backup))
        assertEquals("de", target.getString(SettingsKeys.LANGUAGE, "en"))
        assertNull(BackupRepository(target).import("{\"other\":true}"))
    }

    private class MemorySettingsStore : SettingsStore {
        private val values = mutableMapOf<String, String>()
        override fun getString(key: String, defaultValue: String) = values[key] ?: defaultValue
        override fun putString(key: String, value: String) { values[key] = value }
    }
}
