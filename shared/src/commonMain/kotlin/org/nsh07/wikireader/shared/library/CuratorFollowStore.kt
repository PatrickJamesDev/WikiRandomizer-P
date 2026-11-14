package org.nsh07.wikireader.shared.library

import org.nsh07.wikireader.shared.settings.SettingsKeys
import org.nsh07.wikireader.shared.settings.SettingsStore

/** Small persisted set of curator ids the reader follows (comma-separated, like the Android app). */
class CuratorFollowStore(private val settingsStore: SettingsStore) {
    fun followed(): Set<String> =
        settingsStore.getString(SettingsKeys.FOLLOWED_CURATORS, "")
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    fun toggle(id: String): Set<String> {
        val current = followed()
        val next = if (id in current) current - id else current + id
        settingsStore.putString(SettingsKeys.FOLLOWED_CURATORS, next.sorted().joinToString(","))
        return next
    }
}
