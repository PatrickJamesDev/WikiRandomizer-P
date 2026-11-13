package com.wikirandomizerp.app.data.sync

import android.content.Context
import com.wikirandomizerp.app.data.AppDatabaseRepository
import com.wikirandomizerp.app.data.DebugLog
import com.wikirandomizerp.app.data.DeveloperMode
import com.wikirandomizerp.app.data.ViewHistoryItem
import com.wikirandomizerp.app.data.billing.DonationBilling
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class SyncException(message: String) : Exception(message)

/**
 * Reading-history sync. The local snapshot is always the cache and migration surface; Firebase is
 * only contacted when a signed-in user has an active supporter subscription/donation or developer
 * mode is on. Records are merged by id and timestamp, so a phone and a tablet can both add history
 * without either side overwriting the other.
 */
class LocalSyncStore(
    context: Context,
    private val databaseRepository: AppDatabaseRepository
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val directory = File(appContext.filesDir, "local_sync")
    private val snapshotFile = File(directory, "snapshot.json")
    private val previousSnapshotFile = File(directory, "snapshot.previous.json")
    private val firebase = FirebaseSyncClient(appContext)
    private val syncMutex = Mutex()

    val cloudConfigured: Boolean
        get() = firebase.configured

    val authState: Flow<SyncAuthState>
        get() = firebase.authState

    val currentAuth: SyncAuthState
        get() = firebase.authState.value

    val autoSync: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SYNC, false)

    val lastSyncAt: Long?
        get() = prefs.getLong(KEY_LAST_SYNC, 0L).takeIf { it > 0L }

    fun hasCloudAccess(): Boolean =
        DonationBilling.isSubscriptionActive(appContext) ||
            DonationBilling.isSupporterActive(appContext) ||
            DeveloperMode.isEnabled(appContext)

    fun setAutoSync(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SYNC, enabled).apply()
        CloudSyncScheduler.update(appContext, enabled && cloudConfigured)
    }

    fun applyAutoSyncSchedule() {
        CloudSyncScheduler.update(appContext, autoSync && cloudConfigured)
    }

    suspend fun signIn(email: String, password: String): Result<Unit> =
        firebase.signIn(email, password)

    suspend fun createAccount(email: String, password: String): Result<Unit> =
        firebase.createAccount(email, password)

    fun signOut() {
        firebase.signOut()
    }

    /** Removes the Firebase copy but keeps the history on this device. */
    suspend fun deleteCloudData(): Result<Unit> = runCatching {
        if (!cloudConfigured) throw SyncException("Cloud sync is not configured in this build")
        if (firebase.authState.value !is SyncAuthState.SignedIn) {
            throw SyncException("Sign in to delete the cloud copy")
        }
        syncMutex.withLock {
            firebase.deleteCloudData()
            prefs.edit()
                .remove(KEY_AUTO_SYNC)
                .remove(KEY_LAST_SYNC)
                .remove(KEY_LAST_VERSION)
                .apply()
            CloudSyncScheduler.update(appContext, false)
            runCatching { snapshotFile.delete() }
            runCatching { previousSnapshotFile.delete() }
            Unit
        }
    }

    /**
     * Used by the UI: reports a clear reason instead of silently flipping a local JSON file.
     */
    suspend fun syncNow(): LocalSyncResult {
        if (!cloudConfigured) throw SyncException("Cloud sync is not configured in this build")
        if (!hasCloudAccess()) throw SyncException("Cloud sync is available to supporters")
        val signedIn = firebase.authState.value as? SyncAuthState.SignedIn
            ?: throw SyncException("Sign in to sync your reading history")
        return syncMutex.withLock {
            withContext(Dispatchers.IO) {
                val history = databaseRepository.getViewHistory().first()
                val local = snapshotFrom(history)
                val previous = readSnapshot() ?: SyncSnapshot()
                // Anything in the last synced snapshot that is no longer in the database was
                // deleted locally. Send a tombstone so another device does not resurrect it.
                val currentIds = history.map { "${it.lang}:${it.title}" }.toHashSet()
                val now = System.currentTimeMillis()
                val deletions = previous.records
                    .filter { !it.deleted && it.id !in currentIds }
                    .map { it.copy(deleted = true, updatedAt = now) }
                val localWithDeletions = if (deletions.isEmpty()) local else {
                    val combined = local.records + deletions
                    val tombstones = combined.filter { it.deleted }
                        .sortedByDescending { it.updatedAt }
                        .take(SyncSnapshot.MAX_RECORDS)
                    val live = combined.filter { !it.deleted }
                        .sortedByDescending { it.updatedAt }
                        .take(SyncSnapshot.MAX_RECORDS - tombstones.size)
                    local.copy(records = live + tombstones)
                }
                val remoteMeta = try {
                    firebase.readMeta()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (t: Throwable) {
                    throw SyncException(t.message ?: "Could not reach cloud sync")
                }
                val remote = if (remoteMeta != null && remoteMeta.version > previous.version) {
                    firebase.readSnapshot()
                } else {
                    null
                }
                val merged = mergeSyncSnapshots(localWithDeletions, remote ?: previous, now)
                applyToLocal(history, merged)
                // The Firestore transaction can merge a remote write that happened after our read.
                // Apply that final snapshot too, otherwise the next local sync would mistake those
                // records for local deletions and tombstone them.
                val stored = firebase.writeSnapshot(merged)
                applyToLocal(history, stored)
                writeLocal(stored)
                prefs.edit()
                    .putLong(KEY_LAST_SYNC, now)
                    .putLong(KEY_LAST_VERSION, stored.version)
                    .apply()
                DebugLog.i(
                    "Sync",
                    "synced ${stored.records.size} records for ${signedIn.uid}"
                )
                LocalSyncResult(recordCount = stored.records.size, lastSyncAt = now)
            }
        }
    }

    suspend fun syncNowIfAllowed(): LocalSyncResult {
        if (!autoSync) throw SyncException("Automatic sync is off")
        if (!cloudConfigured) throw SyncException("Cloud sync is not configured in this build")
        if (!hasCloudAccess()) throw SyncException("Cloud sync is available to supporters")
        if (firebase.authState.value !is SyncAuthState.SignedIn) {
            throw SyncException("Sign in to sync your reading history")
        }
        return syncNow()
    }

    private fun snapshotFrom(history: List<ViewHistoryItem>): SyncSnapshot = SyncSnapshot(
        records = history.map {
            SyncRecord(
                id = "${it.lang}:${it.title}",
                title = it.title,
                lang = it.lang,
                updatedAt = it.time,
                description = it.description,
                thumbnail = it.thumbnail,
                topic = it.topic
            )
        }.sortedByDescending { it.updatedAt }.take(SyncSnapshot.MAX_RECORDS)
    )

    private suspend fun applyToLocal(
        localHistory: List<ViewHistoryItem>,
        merged: SyncSnapshot
    ) {
        val localById = localHistory.associateBy { "${it.lang}:${it.title}" }
        merged.records.forEach { record ->
            if (record.deleted) {
                databaseRepository.deleteViewHistoryByTitle(record.title, record.lang)
            } else {
                val existing = localById[record.id]
                if (existing == null || record.updatedAt > existing.time) {
                    databaseRepository.insertViewHistory(
                        ViewHistoryItem(
                            time = record.updatedAt,
                            thumbnail = record.thumbnail,
                            title = record.title,
                            description = record.description,
                            lang = record.lang,
                            topic = record.topic
                        )
                    )
                }
            }
        }
    }

    private fun writeLocal(snapshot: SyncSnapshot) {
        directory.mkdirs()
        if (snapshotFile.exists()) {
            runCatching {
                snapshotFile.copyTo(previousSnapshotFile, overwrite = true)
            }
        }
        snapshotFile.writeText(json.encodeToString(SyncSnapshot.serializer(), snapshot))
    }

    private fun readSnapshot(): SyncSnapshot? = runCatching {
        if (!snapshotFile.exists()) return null
        json.decodeFromString(SyncSnapshot.serializer(), snapshotFile.readText())
    }.getOrNull()

    private companion object {
        const val PREFS = "local_sync"
        const val KEY_AUTO_SYNC = "auto_sync"
        const val KEY_LAST_SYNC = "last_sync"
        const val KEY_LAST_VERSION = "last_version"
    }
}
