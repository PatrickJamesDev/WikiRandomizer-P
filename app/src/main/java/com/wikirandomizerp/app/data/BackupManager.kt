package com.wikirandomizerp.app.data

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject
import com.wikirandomizerp.app.network.HostGate

/** One backup kept inside the app (made automatically before an update or before a restore). */
data class BackupInfo(
    val file: File,
    val createdAt: Long,
    val sizeBytes: Long,
    /** Why it was made: "before-update", "before-restore" or "manual". */
    val reason: String
)

sealed class RestoreResult {
    data object Success : RestoreResult()
    data class Failed(val reason: String) : RestoreResult()
}

/**
 * Local-only backup and restore. Everything the user created lives in the Room database (saved
 * articles, highlights, history, vocabulary, statistics, open tabs and settings), so a backup is a
 * zip holding a consistent copy of that file, plus the articles downloaded for offline reading.
 * No cloud backup API and no network permission are involved.
 *
 * Backups are written to a place the user picks (export), shared, or kept inside the app
 * (automatic backups before every update and before every restore; the newest few are kept).
 */
class BackupManager(private val context: Context) {
    private val databaseFile: File
        get() = context.getDatabasePath(DB_NAME)

    private val offlineDir: File
        get() = File(context.filesDir, "article_cache")

    private val autoDir: File
        get() = autoBackupDir(context)

    private fun stamp(): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    private fun appVersionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()

    /**
     * Writes the backup zip to [out]. With [checkpointFirst] the write-ahead log is folded into the
     * main database file first, so the copy is complete; that opens the database, so it is skipped
     * at app start (before the first launch of a new version may migrate the data).
     */
    private fun writeZip(out: OutputStream, reason: String, checkpointFirst: Boolean) {
        if (checkpointFirst) runCatching { AppDatabase.checkpoint(context) }
        val dbFiles = listOf(
            databaseFile,
            File(databaseFile.path + "-wal"),
            File(databaseFile.path + "-shm")
        ).filter { it.exists() && it.length() > 0 }
        val offline = offlineDir.listFiles { f -> f.isFile && f.name.endsWith(".offline.json") }
            ?.toList().orEmpty()
        // The permanent "seen" list lives in the separate topic_index_<lang>.db files, not in
        // the Room database. Export it too, so restoring an older backup does not make already
        // read articles appear as new again.
        val seen = readSeenLists()

        ZipOutputStream(out).use { zip ->
            dbFiles.forEach { file ->
                zip.putNextEntry(ZipEntry(file.name))
                FileInputStream(file).use { it.copyTo(zip) }
                zip.closeEntry()
            }
            offline.forEach { file ->
                zip.putNextEntry(ZipEntry("offline/${file.name}"))
                FileInputStream(file).use { it.copyTo(zip) }
                zip.closeEntry()
            }
            if (seen.isNotEmpty()) {
                val root = JSONObject()
                seen.forEach { (lang, titles) -> root.put(lang, JSONArray(titles)) }
                zip.putNextEntry(ZipEntry("seen.json"))
                zip.write(root.toString().toByteArray())
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("README.txt"))
            zip.write(
                ("WikiReader local backup\n" +
                        "Created: ${stamp()}\n" +
                        "Reason: $reason\n" +
                        "App version: ${appVersionName()}\n" +
                        "Contains: saved articles, highlights, history, vocabulary, statistics, " +
                        "open tabs, settings, seen-article lists and ${offline.size} offline " +
                        "article(s)\n")
                    .toByteArray()
            )
            zip.closeEntry()
        }
    }

    /** A backup file in the cache folder, ready to be shared with the system share sheet. */
    suspend fun createBackup(prefix: String = "wikireader-backup"): File =
        withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, "backups").apply { mkdirs() }
            val output = File(directory, "$prefix-${stamp()}.zip")
            FileOutputStream(output).use { writeZip(it, "manual", checkpointFirst = true) }
            DebugLog.i("Backup", "created ${output.absolutePath} (${output.length()} bytes)")
            output
        }

    /** Writes a backup to a location the user chose with the system file picker. */
    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openOutputStream(uri)
            ?: throw java.io.IOException("Cannot write to the chosen location")
        stream.use { writeZip(it, "manual", checkpointFirst = true) }
        DebugLog.i("Backup", "exported to $uri")
    }

    /**
     * Synchronous automatic backup into the app's own storage (`filesDir`, so it survives cache
     * clearing). [reason] is "before-update" or "before-restore". Returns null if there was
     * nothing to back up or it failed; it never throws.
     */
    fun createAutoBackupNow(reason: String, checkpointFirst: Boolean = true): File? = try {
        if (!databaseFile.exists()) null
        else {
            autoDir.mkdirs()
            val output = File(autoDir, "$reason-${stamp()}.zip")
            FileOutputStream(output).use { writeZip(it, reason, checkpointFirst) }
            trimAutoBackups()
            DebugLog.i("Backup", "automatic backup '$reason': ${output.name} (${output.length()} bytes)")
            output
        }
    } catch (e: Throwable) {
        DebugLog.w("Backup", "automatic backup '$reason' failed: ${e.message}")
        null
    }

    private fun trimAutoBackups() {
        val all = autoDir.listFiles { f -> f.isFile && f.name.endsWith(".zip") }
            ?.sortedByDescending { it.lastModified() }.orEmpty()
        all.drop(MAX_AUTO_BACKUPS).forEach { runCatching { it.delete() } }
    }

    /** Backups kept inside the app, newest first. */
    fun listAutoBackups(): List<BackupInfo> =
        autoDir.listFiles { f -> f.isFile && f.name.endsWith(".zip") }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                val reason = when {
                    file.name.startsWith("before-update") -> "before-update"
                    file.name.startsWith("before-restore") -> "before-restore"
                    else -> "manual"
                }
                BackupInfo(file, file.lastModified(), file.length(), reason)
            }.orEmpty()

    /** Reads every existing topic index's permanent seen list, keyed by its database language. */
    private fun readSeenLists(): Map<String, List<String>> {
        val out = LinkedHashMap<String, List<String>>()
        val files = context.databaseList()
            .asSequence()
            .filter { it.startsWith("topic_index_") && it.endsWith(".db") }
            .sorted()
        for (name in files) {
            val lang = name.removePrefix("topic_index_").removeSuffix(".db")
            val titles = runCatching { TopicIndex.get(context, lang).seenTitles() }
                .getOrDefault(emptyList())
            if (titles.isNotEmpty()) out[lang] = titles
        }
        return out
    }

    /** Older backups have no seen.json: rebuild what we can from the restored view history. */
    private fun readHistorySeen(candidate: File): Map<String, List<String>> {
        val out = LinkedHashMap<String, MutableList<String>>()
        return runCatching {
            val db = SQLiteDatabase.openDatabase(
                candidate.path, null, SQLiteDatabase.OPEN_READONLY
            )
            try {
                db.rawQuery("SELECT lang, title FROM view_history", null).use { cursor ->
                    while (cursor.moveToNext()) {
                        val lang = cursor.getString(0) ?: continue
                        val title = cursor.getString(1) ?: continue
                        if (title.isNotBlank()) {
                            out.getOrPut(HostGate.storageLanguage(lang)) { ArrayList() }
                                .add(title)
                        }
                    }
                }
            } finally {
                db.close()
            }
            out
        }.getOrDefault(emptyMap())
    }

    private fun applySeen(lists: Map<String, List<String>>) {
        lists.forEach { (lang, titles) ->
            runCatching {
                TopicIndex.get(context, lang).markSeenAll(titles)
            }.onFailure {
                DebugLog.w("Backup", "could not restore seen list for $lang: ${it.message}")
            }
        }
    }

    /**
     * Replaces the app's data with the backup in [input]. The current data is backed up first, the
     * backup is checked (SQLite file, not newer than this app understands) before anything is
     * touched, and the caller must restart the app afterwards ([restartApp]).
     */
    suspend fun restore(input: InputStream): RestoreResult = withContext(Dispatchers.IO) {
        val work = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        try {
            var foundDb = false
            val offlineFiles = ArrayList<File>()
            var foundSeen = false
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    // Only the file name is used: nothing in the zip can write outside [work].
                    val name = entry.name.substringAfterLast('/')
                    val target = when {
                        entry.isDirectory -> null
                        name == DB_NAME || name == "$DB_NAME-wal" -> File(work, name)
                        entry.name.startsWith("offline/") && name.endsWith(".offline.json") ->
                            File(File(work, "offline").apply { mkdirs() }, name)
                        name == "seen.json" -> File(work, name)
                        else -> null
                    }
                    if (target != null) {
                        FileOutputStream(target).use { zip.copyTo(it) }
                        if (name == DB_NAME) foundDb = true
                        if (name == "seen.json") foundSeen = true
                        if (target.parentFile?.name == "offline") offlineFiles.add(target)
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            if (!foundDb) return@withContext RestoreResult.Failed("This file is not a WikiReader backup.")

            // Opening the copy replays its write-ahead log (older backups include one); checking
            // it also proves the file is a healthy SQLite database of a version we can read.
            val candidate = File(work, DB_NAME)
            val appVersion = AppDatabase.currentVersion(context)
            var backupVersion = 0
            try {
                val db = SQLiteDatabase.openDatabase(
                    candidate.path, null, SQLiteDatabase.OPEN_READWRITE
                )
                try {
                    db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
                    val integrity = db.rawQuery("PRAGMA quick_check", null).use { c ->
                        if (c.moveToFirst()) c.getString(0) else "no result"
                    }
                    if (integrity != "ok") {
                        return@withContext RestoreResult.Failed("The backup file is damaged.")
                    }
                    backupVersion = db.version
                } finally {
                    db.close()
                }
            } catch (e: Exception) {
                return@withContext RestoreResult.Failed("The backup file could not be read.")
            }
            if (backupVersion < 1) return@withContext RestoreResult.Failed("This file is not a WikiReader backup.")
            if (backupVersion > appVersion) {
                return@withContext RestoreResult.Failed(
                    "This backup was made by a newer version of the app. Update the app first."
                )
            }

            val seenJson = File(work, "seen.json")
                .takeIf { foundSeen }
                ?.let { runCatching { it.readText() }.getOrNull() }
            val historySeen = if (seenJson == null) readHistorySeen(candidate) else emptyMap()

            // Everything checks out: keep the current data as a safety net, then swap the files.
            createAutoBackupNow("before-restore")
            AppDatabase.closeAndReset()
            File(databaseFile.path + "-wal").delete()
            File(databaseFile.path + "-shm").delete()
            File(databaseFile.path + "-journal").delete()
            candidate.copyTo(databaseFile, overwrite = true)

            if (offlineFiles.isNotEmpty()) {
                offlineDir.mkdirs()
                offlineFiles.forEach { runCatching { it.copyTo(File(offlineDir, it.name), overwrite = true) } }
            }

            // Restore the permanent seen list (or, for old backups, rebuild it from history).
            if (seenJson != null) {
                runCatching {
                    val root = JSONObject(seenJson)
                    val lists = LinkedHashMap<String, List<String>>()
                    root.keys().forEach { lang ->
                        val array = root.optJSONArray(lang) ?: return@forEach
                        val titles = ArrayList<String>(array.length())
                        for (i in 0 until array.length()) {
                            array.optString(i).takeIf { it.isNotBlank() }?.let(titles::add)
                        }
                        if (titles.isNotEmpty()) lists[lang] = titles
                    }
                    applySeen(lists)
                }.onFailure {
                    DebugLog.w("Backup", "could not read seen list from backup: ${it.message}")
                    applySeen(historySeen)
                }
            } else {
                applySeen(historySeen)
            }
            DebugLog.i("Backup", "restored backup (schema v$backupVersion, ${offlineFiles.size} offline articles)")
            RestoreResult.Success
        } catch (e: Throwable) {
            DebugLog.e("Backup", "restore failed", e)
            RestoreResult.Failed(e.message ?: "Restore failed")
        } finally {
            work.deleteRecursively()
        }
    }

    companion object {
        private const val DB_NAME = "app_database"
        private const val MAX_AUTO_BACKUPS = 8

        fun autoBackupDir(context: Context): File = File(context.filesDir, "backups/auto")

        /**
         * Call once at app start, before the database is opened. When this launch is the first one
         * after the app was installed or updated, the previous data is copied into a backup first,
         * so a faulty update or schema migration can never cost the user their data. Android
         * offers no hook before the new version replaces the old one, so the first launch of the
         * new version (which has not touched the database yet) is the earliest possible moment.
         */
        fun backupIfAppWasUpdated(context: Context) {
            try {
                val updatedAt = context.packageManager
                    .getPackageInfo(context.packageName, 0).lastUpdateTime
                val prefs = context.getSharedPreferences("backup_meta", Context.MODE_PRIVATE)
                val last = prefs.getLong("last_update_time", -1L)
                if (last == updatedAt) return
                prefs.edit().putLong("last_update_time", updatedAt).apply()
                // No database yet means a fresh install: nothing to protect.
                BackupManager(context).createAutoBackupNow("before-update", checkpointFirst = false)
            } catch (e: Throwable) {
                DebugLog.w("Backup", "update backup check failed: ${e.message}")
            }
        }

        /** Starts the app again from scratch (after a restore) and ends this process. */
        fun restartApp(context: Context) {
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            if (launch != null) context.startActivity(launch)
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }
}
