package com.wikirandomizerp.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/**
 * Keeps downloaded articles (page info + raw content) on disk so that
 *  - an article opened before opens instantly, with no network at all;
 *  - the article that was open when the app was closed comes back on the next launch;
 *  - articles prepared in the background (next random articles) survive a restart.
 *
 * [Entry.wikitext] keeps its historical name (it is also read by older app versions) but stores
 * whichever source the user renders: raw wikitext or REST HTML. [Entry.contentKind] prevents an
 * HTML response from ever being parsed as wikitext, and vice versa.
 *
 * One small JSON file per article. Every method is safe to call from any thread and never
 * throws: a broken or missing file simply counts as "not cached".
 */
class ArticleCache(context: Context) {

    @Serializable
    data class Entry(
        val lang: String,
        val title: String,
        val wikitext: String,
        val page: WikiApiPage? = null,
        val savedAt: Long = 0L,
        /** "wikitext" or "html". Missing in old cache files, so it defaults to wikitext. */
        val contentKind: String = "wikitext",
        /** True for articles the user explicitly downloaded from a topic library. */
        val offline: Boolean = false,
        /**
         * Picture addresses that belong to this article. Stored so the retention policy can
         * evict exactly this article's body images from Coil's disk cache, without touching the
         * pinned thumbnails used by History and Saved.
         */
        val imageUrls: List<String> = emptyList()
    )

    /** Number and total size of explicitly downloaded articles. */
    data class OfflineStats(val articles: Int, val bytes: Long)

    /** What a requested offline download would contain, before any network work starts. */
    data class OfflineSelection(val articles: Int, val estimatedBytes: Long)

    private val dir = File(context.filesDir, "article_cache")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    init {
        try {
            dir.mkdirs()
        } catch (_: Exception) {
        }
    }

    private fun normalize(title: String): String {
        val t = title.replace('_', ' ').trim()
        return t.replaceFirstChar { it.uppercaseChar() }
    }

    private fun digestName(lang: String, title: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest("$lang\u0000${normalize(title)}".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun fileFor(lang: String, title: String): File =
        File(dir, "${digestName(lang, title)}.json")

    /**
     * Offline downloads live in their own files. Keeping them separate means a normal cache
     * write can never downgrade a downloaded article (and vice versa), and cache trimming can
     * skip them without reading every JSON file.
     */
    private fun offlineFileFor(lang: String, title: String): File =
        File(dir, "${digestName(lang, title)}.offline.json")

    /** The cached article, or null if there is none (or it is older than [maxAgeMs]). */
    fun get(lang: String, title: String, maxAgeMs: Long = DEFAULT_MAX_AGE_MS): Entry? {
        // A downloaded copy always wins: it is the version the user asked to keep and it must
        // not be shadowed by a newer normal cache write of the same article. A broken download
        // falls back to the normal cache instead of making the article unavailable.
        return readEntry(offlineFileFor(lang, title), maxAgeMs = 0L)
            ?: readEntry(fileFor(lang, title), maxAgeMs)
    }

    private fun readEntry(file: File, maxAgeMs: Long): Entry? = try {
        if (!file.exists()) null
        else {
            val entry = json.decodeFromString(Entry.serializer(), file.readText(Charsets.UTF_8))
            when {
                entry.page == null || entry.wikitext.isBlank() -> null
                // Explicit offline downloads never expire: that is the point of downloading.
                !entry.offline && maxAgeMs > 0 &&
                    System.currentTimeMillis() - entry.savedAt > maxAgeMs -> null
                else -> entry
            }
        }
    } catch (e: Throwable) {
        DebugLog.w("Cache", "could not read ${file.name}: ${e.message}")
        null
    }

    fun has(lang: String, title: String): Boolean =
        try {
            fileFor(lang, title).exists() || offlineFileFor(lang, title).exists()
        } catch (_: Throwable) {
            false
        }

    /** Stores the article under its own title, plus under [alsoAs] (e.g. the redirect that was opened). */
    fun put(
        lang: String,
        title: String,
        page: WikiApiPage?,
        content: String,
        alsoAs: String? = null,
        contentKind: String = "wikitext",
        offline: Boolean = false,
        imageUrls: List<String> = emptyList()
    ) {
        if (page == null || content.isBlank()) return
        try {
            dir.mkdirs()
            val text = json.encodeToString(
                Entry.serializer(),
                Entry(
                    lang = lang,
                    title = title,
                    wikitext = content,
                    page = page,
                    savedAt = System.currentTimeMillis(),
                    contentKind = contentKind,
                    offline = offline,
                    imageUrls = imageUrls
                )
            )
            // Offline downloads use a separate file so a later normal write can never replace
            // them, and so trimming can skip them without decoding every file.
            val target = if (offline) offlineFileFor(lang, title) else fileFor(lang, title)
            write(target, text)
            if (alsoAs != null && normalize(alsoAs) != normalize(title)) {
                val alias = if (offline) offlineFileFor(lang, alsoAs) else fileFor(lang, alsoAs)
                write(alias, text)
            }
            // Only the normal cache is size-bounded; downloads are managed explicitly.
            if (!offline) trim()
        } catch (e: Throwable) {
            DebugLog.w("Cache", "could not cache article '$title': ${e.message}")
        }
    }

    private fun write(target: File, text: String) {
        val tmp = File(dir, target.name + ".tmp")
        tmp.writeText(text, Charsets.UTF_8)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    /** Keeps the cache bounded: oldest non-offline files go first. */
    private fun trim() {
        // Only normal cache files take part in trimming. `foo.offline.json` ends with `.json`
        // too, so the suffix must be excluded explicitly.
        val files = dir.listFiles { f -> isCacheFile(f) && !isOfflineFileName(f.name) } ?: return
        var total = files.sumOf { it.length() }
        if (files.size <= MAX_FILES && total <= MAX_BYTES) return
        val oldestFirst = files.sortedBy { it.lastModified() }.toMutableList()
        var count = files.size
        while ((count > MAX_FILES || total > MAX_BYTES) && oldestFirst.isNotEmpty()) {
            val f = oldestFirst.removeAt(0)
            total -= f.length()
            count--
            f.delete()
        }
    }

    private fun isCacheFile(file: File): Boolean = file.isFile && file.name.endsWith(".json")

    private fun isOfflineFileName(name: String): Boolean = name.endsWith(".offline.json")

    /** Number and size of explicit offline downloads (they are exempt from cache trimming). */
    fun offlineStats(): OfflineStats {
        val files = dir.listFiles { f -> isCacheFile(f) && isOfflineFileName(f.name) }
            ?: return OfflineStats(0, 0L)
        var bytes = 0L
        files.forEach { f -> bytes += f.length() }
        return OfflineStats(files.size, bytes)
    }

    fun isOffline(lang: String, title: String): Boolean = try {
        offlineFileFor(lang, title).exists()
    } catch (_: Throwable) {
        false
    }

    /** Deletes only the articles explicitly downloaded for offline reading. */
    fun deleteAllOffline() {
        val files = dir.listFiles { f -> isCacheFile(f) && isOfflineFileName(f.name) } ?: return
        files.forEach { f ->
            try {
                f.delete()
            } catch (_: Throwable) {
            }
        }
    }

    /** Removes the cache entry for one article (used when an offline copy is deleted). */
    fun remove(lang: String, title: String) {
        try {
            fileFor(lang, title).delete()
            offlineFileFor(lang, title).delete()
        } catch (_: Throwable) {
        }
    }

    /**
     * Removes only the normal (non-offline) copy and returns what was stored, so the caller can
     * also evict this article's body pictures. Offline downloads are never touched here.
     */
    fun removeNormal(lang: String, title: String): Entry? {
        return try {
            val file = fileFor(lang, title)
            val entry = readEntry(file, maxAgeMs = 0L)
            file.delete()
            entry
        } catch (_: Throwable) {
            null
        }
    }

    /** True when a normal (not explicitly downloaded) text cache exists for this article. */
    fun hasNormal(lang: String, title: String): Boolean = try {
        fileFor(lang, title).exists()
    } catch (_: Throwable) {
        false
    }

    // ── Recently read order (used by the 10-articles retention policy) ─────────────

    private val recentReadsFile = File(dir, "recent_reads.txt")

    /** Most recent reads last, as (lang, title) pairs. Safe to call from any thread. */
    fun recentReads(): MutableList<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        try {
            if (!recentReadsFile.exists()) return out
            recentReadsFile.forEachLine { line ->
                val at = line.indexOf('\u0001')
                if (at > 0 && at < line.length - 1) {
                    out.add(line.substring(0, at) to line.substring(at + 1))
                }
            }
        } catch (_: Throwable) {
        }
        return out
    }

    fun setRecentReads(reads: List<Pair<String, String>>) {
        try {
            dir.mkdirs()
            recentReadsFile.writeText(
                reads.joinToString("\n") { "${it.first}\u0001${it.second}" },
                Charsets.UTF_8
            )
        } catch (_: Throwable) {
        }
    }

    companion object {
        /** An article older than this is downloaded again when opened. */
        const val DEFAULT_MAX_AGE_MS = 3L * 24 * 60 * 60 * 1000
        private const val MAX_FILES = 120
        private const val MAX_BYTES = 60L * 1024 * 1024
    }
}
