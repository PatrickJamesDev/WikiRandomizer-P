package org.nsh07.wikireader.shared.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Exports and restores the shared reader's settings and JSON-backed library. */
class BackupRepository(private val settingsStore: SettingsStore) {
    private val json = Json { prettyPrint = true }

    fun export(): String = buildJsonObject {
        put("format", FORMAT)
        put("version", VERSION)
        val data = buildJsonObject {
            BACKUP_KEYS.forEach { key ->
                put(key, settingsStore.getString(key, ""))
            }
        }
        put("data", data)
    }.toString()

    /** Returns the number of restored keys, or null when [text] is not a shared backup. */
    fun import(text: String): Int? {
        val root = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return null
        if (root["format"]?.jsonPrimitive?.content != FORMAT) return null
        val data = root["data"]?.jsonObject ?: return null
        var restored = 0
        BACKUP_KEYS.forEach { key ->
            data[key]?.jsonPrimitive?.content?.let { value ->
                settingsStore.putString(key, value)
                restored++
            }
        }
        return restored
    }

    private companion object {
        const val FORMAT = "wikireader-shared-backup"
        const val VERSION = 1
        val BACKUP_KEYS = listOf(
            SettingsKeys.LANGUAGE,
            SettingsKeys.THEME,
            SettingsKeys.TOPIC,
            SettingsKeys.SAVED_ARTICLES,
            SettingsKeys.ARTICLE_HISTORY,
            SettingsKeys.SEARCH_HISTORY,
            SettingsKeys.SEEN_ARTICLES,
            SettingsKeys.BROWSING_HISTORY,
            SettingsKeys.FOLLOWED_CURATORS,
            SettingsKeys.ARTICLE_FONT_SCALE,
            SettingsKeys.SAVED_IMAGES,
            SettingsKeys.SAVED_HIGHLIGHTS,
            SettingsKeys.VOCABULARY,
            SettingsKeys.ARTICLE_LINE_SPACING,
            SettingsKeys.ARTICLE_MARGIN,
            SettingsKeys.ARTICLE_JUSTIFY,
            SettingsKeys.ARTICLE_SEPIA
        )
    }
}
