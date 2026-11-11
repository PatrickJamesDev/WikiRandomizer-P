package com.wikirandomizerp.app.data.sync

import kotlinx.serialization.Serializable

@Serializable
data class SyncRecord(
    val id: String,
    val title: String,
    val lang: String,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val description: String? = null,
    val thumbnail: String? = null,
    val topic: String? = null
)

@Serializable
data class SyncSnapshot(
    val schema: Int = CURRENT_SCHEMA,
    val version: Long = 0,
    val records: List<SyncRecord> = emptyList()
) {
    companion object {
        const val CURRENT_SCHEMA = 2
        const val MAX_RECORDS = 200
        const val TOMBSTONE_DAYS = 90L
    }
}

fun mergeSyncSnapshots(
    local: SyncSnapshot,
    remote: SyncSnapshot?,
    now: Long = System.currentTimeMillis()
): SyncSnapshot {
    if (remote == null) return local.copy(version = local.version + 1)
    val merged = LinkedHashMap<String, SyncRecord>()
    remote.records.forEach { merged[it.id] = it }
    local.records.forEach { incoming ->
        val existing = merged[incoming.id]
        val deletionWinsTie = existing != null &&
            incoming.updatedAt == existing.updatedAt && incoming.deleted && !existing.deleted
        if (existing == null || incoming.updatedAt > existing.updatedAt || deletionWinsTie) {
            merged[incoming.id] = incoming
        }
    }
    val cutoff = now - SyncSnapshot.TOMBSTONE_DAYS * 24L * 60L * 60L * 1000L
    val live = merged.values
        .filter { !it.deleted }
        .sortedByDescending { it.updatedAt }
    val tombstones = merged.values
        .filter { it.deleted && it.updatedAt >= cutoff }
        .sortedByDescending { it.updatedAt }
    // Keep recent tombstones first so deletions still propagate when history is at the cap.
    val keptTombstones = tombstones.take(SyncSnapshot.MAX_RECORDS)
    val keptLive = live.take(SyncSnapshot.MAX_RECORDS - keptTombstones.size)
    val records = keptLive + keptTombstones
    return SyncSnapshot(
        schema = maxOf(local.schema, remote.schema),
        version = maxOf(local.version, remote.version) + 1,
        records = records
    )
}
