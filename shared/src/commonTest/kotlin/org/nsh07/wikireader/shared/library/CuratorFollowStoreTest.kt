package org.nsh07.wikireader.shared.library

import org.nsh07.wikireader.shared.settings.SettingsStore
import kotlin.test.Test
import kotlin.test.assertEquals

class CuratorFollowStoreTest {
    @Test
    fun followsAndUnfollowsCuratorsPersistently() {
        val store = MemorySettingsStore()
        val followStore = CuratorFollowStore(store)

        assertEquals(emptySet(), followStore.followed())
        assertEquals(setOf("alice"), followStore.toggle("alice"))
        assertEquals(setOf("alice", "bob"), followStore.toggle("bob"))
        assertEquals(setOf("alice"), CuratorFollowStore(store).toggle("bob"))
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
