package com.wikirandomizerp.app.data.sync

import android.content.Context
import com.wikirandomizerp.app.data.AppDatabaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

data class LocalSyncResult(
    val recordCount: Int,
    val lastSyncAt: Long
)

class LocalSyncStore(
    context: Context,
    private val databaseRepository: AppDatabaseRepository
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val directory = File(appContext.filesDir, "local_sync")
    private val snapshotFile = File(directory, "snapshot.json")

    val autoSync: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SYNC, false)

    val lastSyncAt: Long?
        get() = prefs.getLong(KEY_LAST_SYNC, 0L).takeIf { it > 0L }

    fun setAutoSync(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SYNC, enabled).apply()
    }

    suspend fun syncNow(): LocalSyncResult = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val history = databaseRepository.getViewHistory().first()
        val local = SyncSnapshot(
            records = history.map {
                SyncRecord(
                    id = "${it.lang}:${it.title}",
                    title = it.title,
                    lang = it.lang,
                    updatedAt = it.time
                )
            }
        )
        val previous = readSnapshot()
        val merged = mergeSyncSnapshots(local, previous)
        snapshotFile.writeText(json.encodeToString(SyncSnapshot.serializer(), merged))
        val now = System.currentTimeMillis()
        prefs.edit()
            .putLong(KEY_LAST_SYNC, now)
            .putLong(KEY_LAST_VERSION, merged.version)
            .apply()
        LocalSyncResult(recordCount = merged.records.size, lastSyncAt = now)
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
