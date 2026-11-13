package org.nsh07.wikireader.shared.settings

import android.content.Context

/**
 * Android implementation of the shared settings boundary.
 *
 * The preview host stores its own keys in a dedicated preferences file so it can never overwrite
 * the production app's Room-backed preferences.
 */
class AndroidSettingsStore(context: Context) : SettingsStore {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)

    override fun getString(key: String, defaultValue: String): String =
        preferences.getString(key, defaultValue) ?: defaultValue

    override fun putString(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    private companion object {
        const val PREFERENCES_FILE = "wiki_reader_shared_preview"
    }
}
