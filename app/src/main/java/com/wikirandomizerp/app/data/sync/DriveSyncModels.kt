package com.wikirandomizerp.app.data.sync

import com.wikirandomizerp.app.data.ReadingEntry
import kotlinx.serialization.Serializable

/**
 * Compact, user-visible state stored in Google Drive's hidden app-data folder. Article bodies,
 * offline copies, images and caches are deliberately excluded: Drive is not an app-content backup
 * service, and those files would also make every sync needlessly large.
 */
@Serializable
data class DriveSnapshot(
    val schema: Int = CURRENT_SCHEMA,
    val updatedAt: Long = 0L,
    /** When settings last changed on the device that produced this snapshot. */
    val settingsUpdatedAt: Long = 0L,
    val history: List<SyncRecord> = emptyList(),
    val preferences: List<DrivePreference> = emptyList(),
    val highlights: List<DriveHighlight> = emptyList(),
    val vocabulary: List<DriveVocabulary> = emptyList(),
    val reading: List<DriveReading> = emptyList()
) {
    companion object {
        const val CURRENT_SCHEMA = 1
        const val MAX_HISTORY = SyncSnapshot.MAX_RECORDS
        const val MAX_HIGHLIGHTS = 2_000
        const val MAX_VOCABULARY = 5_000
        const val MAX_READING = 5_000
    }
}

@Serializable
data class DrivePreference(
    val key: String,
    val kind: String,
    val stringValue: String? = null,
    val intValue: Int? = null,
    val booleanValue: Boolean? = null
)

@Serializable
data class DriveHighlight(
    val pageId: Int? = null,
    val lang: String,
    val title: String,
    val text: String,
    val createdAt: Long,
    val thumbnail: String? = null,
    val description: String? = null,
    val topic: String? = null
) {
    val key: String get() = "$lang\u0001$title\u0001$text"
}

@Serializable
data class DriveVocabulary(
    val word: String,
    val lang: String,
    val definition: String? = null,
    val articleTitle: String? = null,
    val createdAt: Long
) {
    val key: String get() = "$lang\u0001$word"
}

@Serializable
data class DriveReading(
    val lang: String,
    val title: String,
    val topic: String,
    val seconds: Double,
    val progress: Double,
    val readAt: Long
) {
    val key: String get() = "$lang\u0001$title"

    fun toReadingEntry(): ReadingEntry =
        ReadingEntry(title, topic, seconds, progress, readAt)
}

/**
 * Three-way-free merge: history is record-based, settings are last-writer-wins by edit time, and
 * the self-contained collections are unioned by their natural keys. Deletions for highlights and
 * vocabulary are intentionally not propagated, so a sync can never erase a word or passage that
 * was just saved on another device.
 */
fun mergeDriveSnapshots(
    local: DriveSnapshot,
    remote: DriveSnapshot?,
    now: Long
): DriveSnapshot {
    if (remote == null) return local.copy(updatedAt = now)
    val history = mergeSyncSnapshots(
        SyncSnapshot(records = local.history),
        SyncSnapshot(records = remote.history),
        now
    ).records.take(DriveSnapshot.MAX_HISTORY)

    val preferences = if (remote.settingsUpdatedAt > local.settingsUpdatedAt) {
        remote.preferences
    } else {
        local.preferences
    }
    val settingsUpdatedAt = maxOf(local.settingsUpdatedAt, remote.settingsUpdatedAt)

    val highlights = LinkedHashMap<String, DriveHighlight>()
    remote.highlights.forEach { highlights[it.key] = it }
    local.highlights.forEach { incoming ->
        val existing = highlights[incoming.key]
        if (existing == null || incoming.createdAt < existing.createdAt) {
            highlights[incoming.key] = incoming
        }
    }

    val vocabulary = LinkedHashMap<String, DriveVocabulary>()
    remote.vocabulary.forEach { vocabulary[it.key] = it }
    local.vocabulary.forEach { incoming ->
        val existing = vocabulary[incoming.key]
        if (existing == null || incoming.createdAt > existing.createdAt) {
            vocabulary[incoming.key] = incoming
        }
    }

    val reading = LinkedHashMap<String, DriveReading>()
    remote.reading.forEach { reading[it.key] = it }
    local.reading.forEach { incoming ->
        val existing = reading[incoming.key]
        reading[incoming.key] = if (existing == null) {
            incoming
        } else {
            existing.copy(
                topic = if (incoming.readAt >= existing.readAt) incoming.topic else existing.topic,
                seconds = maxOf(existing.seconds, incoming.seconds),
                progress = maxOf(existing.progress, incoming.progress),
                readAt = maxOf(existing.readAt, incoming.readAt)
            )
        }
    }

    return DriveSnapshot(
        schema = maxOf(local.schema, remote.schema),
        updatedAt = now,
        settingsUpdatedAt = settingsUpdatedAt,
        history = history,
        preferences = preferences,
        highlights = highlights.values.sortedByDescending { it.createdAt }
            .take(DriveSnapshot.MAX_HIGHLIGHTS),
        vocabulary = vocabulary.values.sortedByDescending { it.createdAt }
            .take(DriveSnapshot.MAX_VOCABULARY),
        reading = reading.values.sortedByDescending { it.readAt }
            .take(DriveSnapshot.MAX_READING)
    )
}
