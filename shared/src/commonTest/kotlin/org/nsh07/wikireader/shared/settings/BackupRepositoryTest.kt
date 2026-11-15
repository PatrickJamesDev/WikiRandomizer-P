package org.nsh07.wikireader.shared.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupRepositoryTest {
    @Test
    fun exportsAndRestoresKnownKeys() {
        val source = MemorySettingsStore()
        source.putString(SettingsKeys.LANGUAGE, "de")
        source.putString(SettingsKeys.SAVED_ARTICLES, "[]")
        val backup = BackupRepository(source).export()

        val target = MemorySettingsStore()
        assertTrue(BackupRepository(target).import(backup)!! > 0)
        assertEquals("de", target.getString(SettingsKeys.LANGUAGE, "en"))
        assertEquals("[]", target.getString(SettingsKeys.SAVED_ARTICLES, ""))
        assertNull(BackupRepository(target).import("{\"other\":true}"))
        assertNull(BackupRepository(target).import("not json"))
        assertNull(
            BackupRepository(target).import(
                """{"format":{"unexpected":true},"data":{}}"""
            )
        )
    }

    private class MemorySettingsStore : SettingsStore {
        private val values = mutableMapOf<String, String>()
        override fun getString(key: String, defaultValue: String) = values[key] ?: defaultValue
        override fun putString(key: String, value: String) { values[key] = value }
    }
}
