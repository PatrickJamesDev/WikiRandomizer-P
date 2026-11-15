package org.nsh07.wikireader.shared.library

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.nsh07.wikireader.shared.settings.SettingsKeys
import org.nsh07.wikireader.shared.settings.SettingsStore

/** A picture saved from the feed or a reader summary. Remote URLs keep it portable across hosts. */
@Serializable
data class SavedImageEntry(
    val id: Long,
    val imageUrl: String,
    val thumbnailUrl: String? = null,
    val description: String? = null,
    val articleTitle: String = "",
    val articleLanguage: String = "",
    val pageUrl: String? = null
) {
    val key: String
        get() = "${imageUrl.trim()}|${articleLanguage.lowercase()}|${articleTitle.trim().lowercase()}"
}

class SavedImageRepository(private val settingsStore: SettingsStore) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun images(): List<SavedImageEntry> =
        runCatching {
            json.decodeFromString<List<SavedImageEntry>>(
                settingsStore.getString(SettingsKeys.SAVED_IMAGES, "[]")
            )
        }.getOrDefault(emptyList())

    fun add(
        imageUrl: String,
        thumbnailUrl: String? = null,
        description: String? = null,
        articleTitle: String = "",
        articleLanguage: String = "",
        pageUrl: String? = null
    ): List<SavedImageEntry> {
        val url = imageUrl.trim()
        if (url.isEmpty()) return images()
        val current = images()
        val candidate = SavedImageEntry(
            id = (current.maxOfOrNull { it.id } ?: 0L) + 1L,
            imageUrl = url,
            thumbnailUrl = thumbnailUrl?.trim()?.takeIf { it.isNotEmpty() },
            description = description?.trim()?.takeIf { it.isNotEmpty() },
            articleTitle = articleTitle.trim(),
            articleLanguage = articleLanguage.trim().lowercase(),
            pageUrl = pageUrl?.trim()?.takeIf { it.isNotEmpty() }
        )
        if (current.any { it.key == candidate.key }) return current
        return write(listOf(candidate) + current)
    }

    fun remove(entry: SavedImageEntry): List<SavedImageEntry> =
        write(images().filterNot { it.id == entry.id })

    fun clear(): List<SavedImageEntry> = write(emptyList())

    private fun write(entries: List<SavedImageEntry>): List<SavedImageEntry> {
        settingsStore.putString(SettingsKeys.SAVED_IMAGES, json.encodeToString(entries))
        return entries
    }
}
