package com.wikirandomizerp.app.data.sync

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.wikirandomizerp.app.data.AppDatabaseRepository
import com.wikirandomizerp.app.data.AppPreferencesRepository
import com.wikirandomizerp.app.data.DebugLog
import com.wikirandomizerp.app.data.PreferenceSnapshot
import com.wikirandomizerp.app.data.SavedHighlight
import com.wikirandomizerp.app.data.TopicIndex
import com.wikirandomizerp.app.data.VocabularyWord
import com.wikirandomizerp.app.data.ViewHistoryItem
import com.wikirandomizerp.app.data.sync.DriveSnapshot.Companion.MAX_HIGHLIGHTS
import com.wikirandomizerp.app.data.sync.DriveSnapshot.Companion.MAX_HISTORY
import com.wikirandomizerp.app.data.sync.DriveSnapshot.Companion.MAX_READING
import com.wikirandomizerp.app.data.sync.DriveSnapshot.Companion.MAX_VOCABULARY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

sealed interface DriveConnectResult {
    data object Connected : DriveConnectResult
    data class Resolution(val pendingIntent: PendingIntent) : DriveConnectResult
    data class Failed(val message: String) : DriveConnectResult
}

data class DriveSyncResult(
    val recordCount: Int,
    val lastSyncAt: Long,
    val settingsChanged: Boolean = false
)

/**
 * Google Drive transport for the compact state: settings, history, reading progress, highlights
 * and vocabulary. The file lives in the hidden drive.appdata folder, so the user cannot see or
 * share it and this app cannot see any other Drive file.
 */
class DriveSyncStore(
    context: Context,
    private val databaseRepository: AppDatabaseRepository,
    private val preferencesRepository: AppPreferencesRepository
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val directory = File(appContext.filesDir, "drive_sync")
    private val snapshotFile = File(directory, "snapshot.json")
    private val auth = DriveAuthManager(appContext)
    private val api by lazy { DriveApi() }
    private val mutex = Mutex()

    val configured: Boolean get() = auth.configured
    val connected: Boolean get() = auth.connected
    val autoSync: Boolean get() = prefs.getBoolean(KEY_AUTO_SYNC, false)
    val lastSyncAt: Long?
        get() = prefs.getLong(KEY_LAST_SYNC, 0L).takeIf { it > 0L }

    suspend fun connect(context: Context): DriveConnectResult {
        if (!auth.configured) {
            return DriveConnectResult.Failed(
                "Set GOOGLE_DRIVE_CLIENT_ID when building to enable Google Drive sync"
            )
        }
        return when (val result = auth.connect(context)) {
            is DriveAuthorization.Authorized -> {
                runCatching { DriveSyncScheduler.update(appContext, autoSync) }
                DriveConnectResult.Connected
            }
            is DriveAuthorization.Resolution -> DriveConnectResult.Resolution(result.pendingIntent)
            is DriveAuthorization.Failed -> DriveConnectResult.Failed(result.message)
        }
    }

    fun completeConnect(context: Context, data: Intent?): DriveConnectResult {
        return when (val result = auth.completeConnect(context, data)) {
            is DriveAuthorization.Authorized -> {
                runCatching { DriveSyncScheduler.update(appContext, autoSync) }
                DriveConnectResult.Connected
            }
            is DriveAuthorization.Resolution -> DriveConnectResult.Resolution(result.pendingIntent)
            is DriveAuthorization.Failed -> DriveConnectResult.Failed(result.message)
        }
    }

    fun disconnect() {
        auth.disconnect()
        // The cached merge state belongs to the account that was connected. Keeping it could
        // carry tombstones or settings timestamps into a different Google account.
        prefs.edit()
            .remove(KEY_AUTO_SYNC)
            .remove(KEY_LAST_SYNC)
            .remove(KEY_PREFS_HASH)
            .remove(KEY_PREFS_EDITED_AT)
            .apply()
        runCatching { snapshotFile.delete() }
        DriveSyncScheduler.update(appContext, false)
    }

    fun setAutoSync(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SYNC, enabled).apply()
        DriveSyncScheduler.update(appContext, enabled && configured && connected)
    }

    fun applyAutoSyncSchedule() {
        DriveSyncScheduler.update(appContext, autoSync && configured && connected)
    }

    suspend fun syncNow(): DriveSyncResult {
        if (!configured) throw SyncException("Google Drive sync is not configured in this build")
        if (!connected) throw SyncException("Connect Google Drive to sync your data")
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                var token = auth.accessToken(appContext)
                val local = captureSnapshot()
                val remote = try {
                    api.read(token)
                } catch (unauthorized: DriveUnauthorizedException) {
                    auth.invalidateToken()
                    token = auth.accessToken(appContext)
                    api.read(token)
                }
                val now = System.currentTimeMillis()
                val merged = mergeDriveSnapshots(local, remote, now)
                val settingsChanged = merged.preferences != local.preferences
                applySnapshot(local.history, merged)
                try {
                    api.write(token, merged)
                } catch (unauthorized: DriveUnauthorizedException) {
                    auth.invalidateToken()
                    token = auth.accessToken(appContext)
                    api.write(token, merged)
                }
                rememberUploadedPreferences(merged)
                writeLocalSnapshot(merged)
                prefs.edit().putLong(KEY_LAST_SYNC, now).apply()
                DebugLog.i(
                    "DriveSync",
                    "synced ${merged.history.size} history, ${merged.highlights.size} highlights, " +
                            "${merged.vocabulary.size} words, ${merged.reading.size} reading rows"
                )
                DriveSyncResult(merged.history.size, now, settingsChanged)
            }
        }
    }

    suspend fun syncIfAllowed(): DriveSyncResult {
        if (!autoSync) throw SyncException("Automatic sync is off")
        if (!configured) throw SyncException("Google Drive sync is not configured in this build")
        if (!connected) throw SyncException("Connect Google Drive to sync your data")
        return syncNow()
    }

    suspend fun deleteCloudData() = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!connected) throw SyncException("Connect Google Drive first")
            val token = auth.accessToken(appContext)
            api.deleteCloudCopy(token)
            prefs.edit()
                .remove(KEY_LAST_SYNC)
                .remove(KEY_AUTO_SYNC)
                .remove(KEY_PREFS_HASH)
                .remove(KEY_PREFS_EDITED_AT)
                .apply()
            runCatching { snapshotFile.delete() }
            DriveSyncScheduler.update(appContext, false)
            Unit
        }
    }

    private suspend fun captureSnapshot(): DriveSnapshot {
        val now = System.currentTimeMillis()
        val history = databaseRepository.getViewHistory().first()
        val preferences = preferencesRepository.readAllPreferences()
        val settingsUpdatedAt = settingsEditTime(preferences)
        val highlights = databaseRepository.getSavedHighlights().first()
        val vocabulary = databaseRepository.getVocabularyWords().first()
        return DriveSnapshot(
            updatedAt = now,
            settingsUpdatedAt = settingsUpdatedAt,
            history = historyWithDeletions(history, now),
            preferences = preferencesToList(preferences),
            highlights = highlights.map {
                DriveHighlight(
                    pageId = it.pageId,
                    lang = it.lang,
                    title = it.title,
                    text = it.text,
                    createdAt = it.createdAt,
                    thumbnail = it.thumbnail,
                    description = it.description,
                    topic = it.topic
                )
            }.sortedByDescending { it.createdAt }.take(MAX_HIGHLIGHTS),
            vocabulary = vocabulary.map {
                DriveVocabulary(
                    word = it.word,
                    lang = it.lang,
                    definition = it.definition,
                    articleTitle = it.articleTitle,
                    createdAt = it.createdAt
                )
            }.sortedByDescending { it.createdAt }.take(MAX_VOCABULARY),
            reading = readingEntries().take(MAX_READING)
        )
    }

    /**
     * A record that was in the last synced snapshot but is gone locally must travel as a tombstone,
     * otherwise the remote copy would resurrect deleted history on the next merge.
     */
    private fun historyWithDeletions(
        history: List<ViewHistoryItem>,
        now: Long
    ): List<SyncRecord> {
        val local = history.map {
            SyncRecord(
                id = "${it.lang}:${it.title}",
                title = it.title,
                lang = it.lang,
                updatedAt = it.time,
                description = it.description,
                thumbnail = it.thumbnail,
                topic = it.topic
            )
        }
        val previous = readLocalSnapshot() ?: return local
            .sortedByDescending { it.updatedAt }
            .take(MAX_HISTORY)
        val currentIds = local.mapTo(HashSet()) { it.id }
        val deletions = previous.history
            .filter { !it.deleted && it.id !in currentIds }
            .map { it.copy(deleted = true, updatedAt = now) }
        val combined = local + deletions
        val tombstones = combined.filter { it.deleted }
            .sortedByDescending { it.updatedAt }
            .take(SyncSnapshot.MAX_RECORDS)
        val live = combined.filter { !it.deleted }
            .sortedByDescending { it.updatedAt }
            .take(SyncSnapshot.MAX_RECORDS - tombstones.size)
        return live + tombstones
    }

    private fun readLocalSnapshot(): DriveSnapshot? {
        val text = runCatching { snapshotFile.readText() }.getOrNull() ?: return null
        return runCatching {
            json.decodeFromString(DriveSnapshot.serializer(), text)
        }.getOrNull()
    }

    private fun writeLocalSnapshot(snapshot: DriveSnapshot) {
        runCatching {
            directory.mkdirs()
            snapshotFile.writeText(json.encodeToString(DriveSnapshot.serializer(), snapshot))
        }
    }

    private fun settingsEditTime(preferences: PreferenceSnapshot): Long {
        val hash = preferencesHash(preferences)
        val storedHash = prefs.getString(KEY_PREFS_HASH, null)
        if (storedHash == null) return 0L
        if (hash != storedHash) {
            val now = System.currentTimeMillis()
            prefs.edit()
                .putString(KEY_PREFS_HASH, hash)
                .putLong(KEY_PREFS_EDITED_AT, now)
                .apply()
            return now
        }
        return prefs.getLong(KEY_PREFS_EDITED_AT, 0L)
    }

    private fun rememberUploadedPreferences(snapshot: DriveSnapshot) {
        val preferences = snapshot.preferences.toPreferenceSnapshot()
        prefs.edit()
            .putString(KEY_PREFS_HASH, preferencesHash(preferences))
            .putLong(KEY_PREFS_EDITED_AT, snapshot.settingsUpdatedAt)
            .apply()
    }

    private suspend fun applySnapshot(
        localHistory: List<SyncRecord>,
        snapshot: DriveSnapshot
    ) {
        val localById = localHistory.associateBy { it.id }
        snapshot.history.forEach { record ->
            if (record.deleted) {
                databaseRepository.deleteViewHistoryByTitle(record.title, record.lang)
            } else {
                val existing = localById[record.id]
                if (existing == null || record.updatedAt > existing.updatedAt) {
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

        val preferences = snapshot.preferences.toPreferenceSnapshot()
        preferences.strings.forEach { (key, value) ->
            preferencesRepository.saveStringPreference(key, value)
        }
        preferences.ints.forEach { (key, value) ->
            preferencesRepository.saveIntPreference(key, value)
        }
        preferences.booleans.forEach { (key, value) ->
            preferencesRepository.saveBooleanPreference(key, value)
        }

        snapshot.highlights.forEach { highlight ->
            if (databaseRepository.findSavedHighlight(
                    highlight.title,
                    highlight.lang,
                    highlight.text
                ) == null
            ) {
                databaseRepository.insertSavedHighlight(
                    SavedHighlight(
                        pageId = highlight.pageId,
                        lang = highlight.lang,
                        title = highlight.title,
                        text = highlight.text,
                        createdAt = highlight.createdAt,
                        thumbnail = highlight.thumbnail,
                        description = highlight.description,
                        topic = highlight.topic
                    )
                )
            }
        }

        snapshot.vocabulary.forEach { word ->
            if (databaseRepository.findVocabularyWord(word.word, word.lang) == null) {
                databaseRepository.insertVocabularyWord(
                    VocabularyWord(
                        word = word.word,
                        lang = word.lang,
                        definition = word.definition,
                        articleTitle = word.articleTitle,
                        createdAt = word.createdAt
                    )
                )
            }
        }

        snapshot.reading.groupBy { it.lang }.forEach { (lang, rows) ->
            if (rows.isEmpty()) return@forEach
            // The language becomes part of a database filename, so never trust arbitrary text
            // from a synced snapshot.
            if (lang.length !in 2..16 || !lang.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
                return@forEach
            }
            runCatching {
                TopicIndex.get(appContext, lang)
                    .mergeReadingEntries(rows.map { it.toReadingEntry() })
            }.onFailure {
                DebugLog.w("DriveSync", "could not merge reading rows for $lang: ${it.message}")
            }
        }
    }

    private fun readingEntries(): List<DriveReading> {
        val out = ArrayList<DriveReading>()
        appContext.databaseList()
            .filter { it.startsWith("topic_index_") && it.endsWith(".db") }
            .forEach { name ->
                val lang = name.removePrefix("topic_index_").removeSuffix(".db")
                runCatching {
                    TopicIndex.get(appContext, lang).readingEntries()
                }.getOrDefault(emptyList()).forEach { entry ->
                    out.add(
                        DriveReading(
                            lang = lang,
                            title = entry.title,
                            topic = entry.topic,
                            seconds = entry.seconds,
                            progress = entry.progress,
                            readAt = entry.readAt
                        )
                    )
                }
            }
        return out.sortedByDescending { it.readAt }
    }

    private fun preferencesToList(
        preferences: PreferenceSnapshot
    ): List<DrivePreference> {
        val out = ArrayList<DrivePreference>()
        preferences.strings.forEach { (key, value) ->
            if (shouldSyncPreference(key)) {
                out.add(DrivePreference(key = key, kind = "string", stringValue = value))
            }
        }
        preferences.ints.forEach { (key, value) ->
            if (shouldSyncPreference(key)) {
                out.add(DrivePreference(key = key, kind = "int", intValue = value))
            }
        }
        preferences.booleans.forEach { (key, value) ->
            if (shouldSyncPreference(key)) {
                out.add(DrivePreference(key = key, kind = "boolean", booleanValue = value))
            }
        }
        return out.sortedWith(compareBy({ it.kind }, { it.key }))
    }

    private fun preferencesHash(
        preferences: PreferenceSnapshot
    ): String {
        val text = preferencesToList(preferences).joinToString("\u0001") {
            "${it.kind}:${it.key}=${it.stringValue ?: it.intValue ?: it.booleanValue}"
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun List<DrivePreference>.toPreferenceSnapshot(): PreferenceSnapshot {
        val strings = LinkedHashMap<String, String>()
        val ints = LinkedHashMap<String, Int>()
        val booleans = LinkedHashMap<String, Boolean>()
        forEach { preference ->
            if (!shouldSyncPreference(preference.key)) return@forEach
            when (preference.kind) {
                "string" -> preference.stringValue?.let { strings[preference.key] = it }
                "int" -> preference.intValue?.let { ints[preference.key] = it }
                "boolean" -> preference.booleanValue?.let { booleans[preference.key] = it }
            }
        }
        return PreferenceSnapshot(strings, ints, booleans)
    }

    /**
     * Device-local, ephemeral or entitlement state must not travel between devices. In particular
     * supporter status is tied to the Play account, not to whichever Google account is connected.
     */
    private fun shouldSyncPreference(key: String): Boolean =
        key != "supporter-mode" &&
            key != "prefetch_queue" &&
            key != "article_tabs" &&
            key != "last-article-title" &&
            key != "last-article-lang" &&
            key != "tutorial-done" &&
            key != "defaults-1.0-beta" &&
            key != "topics-enabled-v7" &&
            key != "article-open-count" &&
            !key.startsWith("random_seen_") &&
            !key.startsWith("stats-") &&
            !key.startsWith("donation-prompt")

    private companion object {
        const val PREFS = "drive_sync"
        const val KEY_AUTO_SYNC = "auto_sync"
        const val KEY_LAST_SYNC = "last_sync"
        const val KEY_PREFS_HASH = "prefs_hash"
        const val KEY_PREFS_EDITED_AT = "prefs_edited_at"
    }
}
