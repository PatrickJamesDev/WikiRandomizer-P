package com.wikirandomizerp.app.data.sync

import kotlinx.serialization.Serializable

@Serializable
data class SyncRecord(
    val id: String,
    val title: String,
    val lang: String,
    val updatedAt: Long,
    val deleted: Boolean = false
)

@Serializable
data class SyncSnapshot(
    val schema: Int = CURRENT_SCHEMA,
    val version: Long = 0,
    val records: List<SyncRecord> = emptyList()
) {
    companion object {
        const val CURRENT_SCHEMA = 1
        const val MAX_RECORDS = 500
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
        if (existing == null || incoming.updatedAt >= existing.updatedAt) {
            merged[incoming.id] = incoming
        }
    }
    val cutoff = now - SyncSnapshot.TOMBSTONE_DAYS * 24L * 60L * 60L * 1000L
    val records = merged.values
        .filter { !it.deleted || it.updatedAt >= cutoff }
        .sortedByDescending { it.updatedAt }
        .take(SyncSnapshot.MAX_RECORDS)
    return SyncSnapshot(
        schema = maxOf(local.schema, remote.schema),
        version = maxOf(local.version, remote.version) + 1,
        records = records
    )
}
