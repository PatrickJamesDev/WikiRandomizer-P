package com.wikirandomizerp.app.data.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncModelsCommonTest {
    @Test
    fun newerRecordWinsAndVersionAdvances() {
        val local = SyncSnapshot(
            version = 4,
            records = listOf(SyncRecord("a", "Old", "en", updatedAt = 10))
        )
        val remote = SyncSnapshot(
            version = 7,
            records = listOf(SyncRecord("a", "New", "en", updatedAt = 20))
        )

        val merged = mergeSyncSnapshots(local, remote, now = 100)

        assertEquals(8, merged.version)
        assertEquals("New", merged.records.single().title)
    }

    @Test
    fun deletionWinsTieButNotOlderUpdate() {
        val local = SyncSnapshot(
            records = listOf(SyncRecord("a", "A", "en", updatedAt = 20, deleted = true))
        )
        val remote = SyncSnapshot(
            records = listOf(SyncRecord("a", "A", "en", updatedAt = 20))
        )
        assertTrue(mergeSyncSnapshots(local, remote, now = 100).records.single().deleted)

        val staleDelete = local.copy(records = listOf(SyncRecord("a", "A", "en", updatedAt = 10, deleted = true)))
        assertFalse(mergeSyncSnapshots(staleDelete, remote, now = 100).records.single().deleted)
    }

    @Test
    fun oldTombstonesAreDroppedAndLiveRecordsCapped() {
        val live = (0 until SyncSnapshot.MAX_RECORDS).map {
            SyncRecord("id$it", "T$it", "en", updatedAt = 1_000L + it)
        }
        val snapshot = SyncSnapshot(
            records = live + SyncRecord("old", "Old", "en", updatedAt = 0, deleted = true)
        )

        val merged = mergeSyncSnapshots(snapshot, SyncSnapshot(), now = 10_000_000_000L)

        assertEquals(SyncSnapshot.MAX_RECORDS, merged.records.size)
        assertTrue(merged.records.none { it.id == "old" })
    }
}