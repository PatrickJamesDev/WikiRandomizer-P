package org.nsh07.wikireader.data.kiwix

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.kiwix.libkiwix.JNIKiwix
import org.kiwix.libzim.Archive
import org.kiwix.libzim.Item
import org.nsh07.wikireader.data.DebugLog
import java.io.FileNotFoundException

/**
 * One opened ZIM archive.
 *
 * libzim opens its own descriptor from `/dev/fd/N` while the archive is being constructed; the
 * descriptor handed to [Archive] is only used for that construction. The [ParcelFileDescriptor]
 * is therefore closed right after opening (libzim explicitly does not take ownership).
 */
private class OpenArchive(
    val key: String,
    val archive: Archive
)

/** A single readable article from a Kiwix/ZIM archive. */
data class KiwixArticle(
    val path: String,
    val title: String,
    val html: String
)

/** A binary resource (image, stylesheet, font, …) belonging to a ZIM article. */
data class KiwixResource(
    val mimeType: String,
    val bytes: ByteArray
)

/** One title-index match shown in the Kiwix search sheet. */
data class KiwixSuggestion(
    val title: String,
    val path: String
)

/**
 * Current state of the optional Kiwix database. [ready] means an archive is open and readable;
 * [useForRandom] is the user's choice to route the Random article button to it.
 */
data class KiwixStatus(
    val selected: Boolean = false,
    val ready: Boolean = false,
    val useForRandom: Boolean = false,
    val displayName: String? = null,
    val title: String? = null,
    val description: String? = null,
    val language: String? = null,
    val articleCount: Int = 0,
    val sizeBytes: Long = 0L,
    val error: String? = null
)

/**
 * Reads a Kiwix `.zim` archive chosen with the system file picker.
 *
 * ZIM is a self-contained offline Wikipedia format: articles, pictures, stylesheets and fonts
 * are all packed into one file. The heavy lifting (decompression, indexes) is done by libzim via
 * the official `org.kiwix:libkiwix` bindings, so this class only maps libzim entries to the small
 * models the reader UI needs.
 *
 * All libzim calls are blocking and serialised on [lock]; callers must invoke them off the main
 * thread. The WebView resource interceptor already runs on a background thread.
 */
class KiwixRepository(private val context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val resolver = context.applicationContext.contentResolver
    private val lock = Any()

    private var opened: OpenArchive? = null
    private var nativeLoaded = false

    private val _status = MutableStateFlow(
        KiwixStatus(
            selected = preferences.getString(KEY_URI, null) != null,
            useForRandom = preferences.getBoolean(KEY_USE_FOR_RANDOM, true),
            displayName = preferences.getString(KEY_DISPLAY_NAME, null)
        )
    )
    val status: StateFlow<KiwixStatus> = _status.asStateFlow()

    /** Re-opens the stored archive (if any) and refreshes [status]. Safe to call repeatedly. */
    fun refresh() {
        synchronized(lock) {
            val uri = preferences.getString(KEY_URI, null)
            if (uri == null) {
                closeLocked()
                _status.value = KiwixStatus(
                    useForRandom = preferences.getBoolean(KEY_USE_FOR_RANDOM, true)
                )
                return
            }
            val displayName = preferences.getString(KEY_DISPLAY_NAME, null)
            try {
                val open = openLocked(Uri.parse(uri))
                val archive = open.archive
                _status.value = KiwixStatus(
                    selected = true,
                    ready = true,
                    useForRandom = preferences.getBoolean(KEY_USE_FOR_RANDOM, true),
                    displayName = displayName,
                    title = metadata(archive, "Title") ?: displayName,
                    description = metadata(archive, "Description"),
                    language = runCatching { archive.mainLanguage }.getOrNull(),
                    articleCount = runCatching { archive.articleCount }.getOrDefault(0),
                    sizeBytes = runCatching { archive.filesize }.getOrDefault(0L)
                )
                DebugLog.i(
                    "Kiwix",
                    "opened '$displayName' (${_status.value.articleCount} articles)"
                )
            } catch (e: Throwable) {
                closeLocked()
                _status.value = KiwixStatus(
                    selected = true,
                    useForRandom = preferences.getBoolean(KEY_USE_FOR_RANDOM, true),
                    displayName = displayName,
                    error = e.message ?: e.javaClass.simpleName
                )
                DebugLog.w("Kiwix", "could not open '$displayName': ${e.message}")
            }
        }
    }

    /**
     * Persists the SAF URI and opens it. The read permission is persisted so the archive still
     * works after a reboot even though the file itself lives outside the app's sandbox.
     */
    fun select(uri: Uri, displayName: String?) {
        try {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            // Some document providers do not offer persistable grants. Opening still works for
            // this process, and refresh() will show a clear error after a restart.
            DebugLog.w("Kiwix", "could not persist read permission for $uri: ${e.message}")
        }
        preferences.edit()
            .putString(KEY_URI, uri.toString())
            .putString(KEY_DISPLAY_NAME, displayName)
            .putBoolean(KEY_USE_FOR_RANDOM, true)
            .apply()
        refresh()
    }

    fun remove() {
        preferences.edit()
            .remove(KEY_URI)
            .remove(KEY_DISPLAY_NAME)
            .apply()
        synchronized(lock) {
            closeLocked()
            _status.value = KiwixStatus(
                useForRandom = preferences.getBoolean(KEY_USE_FOR_RANDOM, true)
            )
        }
    }

    fun setUseForRandom(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_USE_FOR_RANDOM, enabled).apply()
        _status.value = _status.value.copy(useForRandom = enabled)
    }

    /** The archive's main page, or the first usable article if it has no main entry. */
    fun mainArticle(): KiwixArticle? = synchronized(lock) {
        val open = ensureOpenLocked() ?: return null
        val entry = runCatching { open.archive.mainEntry }.getOrNull()
        val item = entry?.let { runCatching { it.getItem(true) }.getOrNull() }
        item?.toArticle() ?: randomLocked(open)
    }

    /** A random HTML article. Redirects and non-article entries are retried. */
    fun randomArticle(): KiwixArticle? = synchronized(lock) {
        val open = ensureOpenLocked() ?: return null
        randomLocked(open)
    }

    /** Looks up an article by ZIM path, falling back to a title lookup. */
    fun article(pathOrTitle: String): KiwixArticle? = synchronized(lock) {
        val open = ensureOpenLocked() ?: return null
        val archive = open.archive
        val byPath = runCatching { archive.getEntryByPath(pathOrTitle) }.getOrNull()
            ?.let { runCatching { it.getItem(true) }.getOrNull() }
        val item = byPath ?: runCatching { archive.getEntryByTitle(pathOrTitle) }.getOrNull()
            ?.let { runCatching { it.getItem(true) }.getOrNull() }
        item?.toArticle()
    }

    /**
     * Reads any entry (article, image, CSS, font) for the WebView. Returning null makes the
     * interceptor answer 404, which also blocks accidental network requests: a ZIM is offline.
     */
    fun resource(path: String): KiwixResource? = synchronized(lock) {
        val open = ensureOpenLocked() ?: return null
        val normalized = path.trimStart('/')
        if (normalized.isEmpty()) return null
        val item = runCatching { open.archive.getEntryByPath(normalized) }.getOrNull()
            ?.let { runCatching { it.getItem(true) }.getOrNull() }
            ?: return null
        // A video or audio entry can be hundreds of megabytes; reading it with getData() would
        // allocate all of it. Answering 404 is better than an out-of-memory crash.
        val size = runCatching { item.size }.getOrDefault(0L)
        if (size > MAX_RESOURCE_BYTES) {
            DebugLog.w("Kiwix", "skipping large resource $normalized ($size bytes)")
            return null
        }
        val bytes = runCatching { item.data.data }.getOrNull() ?: return null
        KiwixResource(
            mimeType = item.mimetype ?: "application/octet-stream",
            bytes = bytes
        )
    }

    /**
     * Prefix suggestions from the archive's title index.
     *
     * This deliberately uses [Archive.findByTitle] instead of libzim's `SuggestionSearcher`.
     * The Android build of libzim links ICU in "stubdata" mode (the AAR ships no `icudt73l.dat`),
     * and `SuggestionSearcher` builds an ICU transliterator on the first query. Without the data
     * file that either fails or crashes natively. Plain title-index prefix matching needs no ICU
     * data and finds what a reader expects while typing.
     */
    fun suggestions(query: String, limit: Int): List<KiwixSuggestion> = synchronized(lock) {
        val needle = query.trim()
        if (needle.isEmpty() || limit <= 0) return emptyList()
        val open = ensureOpenLocked() ?: return emptyList()
        val iterator = runCatching { open.archive.findByTitle(needle) }.getOrNull()
            ?: return emptyList()
        val out = ArrayList<KiwixSuggestion>(limit)
        // The title index also contains redirects and non-article entries; look at a few extra
        // candidates so the visible list can still reach [limit].
        var inspected = 0
        while (out.size < limit && inspected < limit * 10 &&
            runCatching { iterator.hasNext() }.getOrDefault(false)
        ) {
            inspected++
            val entry = runCatching { iterator.next() }.getOrNull() ?: break
            val item = runCatching { entry.getItem(true) }.getOrNull() ?: continue
            if (!item.mimetype.orEmpty().startsWith("text/html", ignoreCase = true)) continue
            val title = item.title?.takeIf { it.isNotBlank() }
                ?: entry.title?.takeIf { it.isNotBlank() }
                ?: continue
            val path = item.path.orEmpty()
            // Several title-index entries can be redirects to the same target; show it once.
            if (path.isNotBlank() && out.none { it.path == path }) {
                out.add(KiwixSuggestion(title, path))
            }
        }
        out
    }

    private fun randomLocked(open: OpenArchive): KiwixArticle? {
        repeat(RANDOM_ATTEMPTS) {
            val entry = runCatching { open.archive.randomEntry }.getOrNull() ?: return@repeat
            val item = runCatching { entry.getItem(true) }.getOrNull() ?: return@repeat
            item.toArticle()?.let { return it }
        }
        return null
    }

    private fun ensureOpenLocked(): OpenArchive? {
        opened?.let { return it }
        val uri = preferences.getString(KEY_URI, null) ?: return null
        return try {
            openLocked(Uri.parse(uri))
        } catch (e: Throwable) {
            DebugLog.w("Kiwix", "archive is not available: ${e.message}")
            null
        }
    }

    private fun openLocked(uri: Uri): OpenArchive {
        val key = uri.toString()
        // refresh() can be called repeatedly (app start, returning from the reader). Reusing the
        // open archive keeps the expensive ZIM indexes in memory instead of closing and reopening.
        opened?.let { if (it.key == key) return it }
        closeLocked()
        ensureNativeLocked()
        val descriptor = resolver.openFileDescriptor(uri, "r")
            ?: throw FileNotFoundException("Cannot open $uri")
        val archive = try {
            // libzim reads through /dev/fd/N while constructing the archive and takes its own
            // reference to the file. The Java descriptor is not owned by libzim, so it must be
            // closed here (leaving it open would leak one fd per archive selection).
            Archive(descriptor.fileDescriptor)
        } finally {
            runCatching { descriptor.close() }
        }
        return OpenArchive(key, archive).also { opened = it }
    }

    private fun closeLocked() {
        opened?.let { open ->
            // dispose() is idempotent in the wrapper (the finalizer calls it again), and it also
            // closes the native fd owned by libzim.
            runCatching { open.archive.dispose() }
        }
        opened = null
    }

    private fun ensureNativeLocked() {
        if (nativeLoaded) return
        // JNIKiwix loads libc++_shared, libkiwix, libzim and the two JNI wrappers via ReLinker.
        JNIKiwix(context.applicationContext)
        nativeLoaded = true
    }

    private fun metadata(archive: Archive, key: String): String? =
        runCatching { archive.getMetadata(key) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }

    private fun Item.toArticle(): KiwixArticle? {
        val mime = mimetype.orEmpty()
        if (!mime.startsWith("text/html", ignoreCase = true)) return null
        val bytes = runCatching { data.data }.getOrNull() ?: return null
        if (bytes.isEmpty()) return null
        val html = try {
            String(bytes, Charsets.UTF_8)
        } catch (e: Exception) {
            return null
        }
        if (html.isBlank()) return null
        val articlePath = path
        if (articlePath.isNullOrBlank()) return null
        return KiwixArticle(
            path = articlePath,
            title = title?.takeIf { it.isNotBlank() } ?: articlePath,
            html = html
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "kiwix"
        const val KEY_URI = "archive-uri"
        const val KEY_DISPLAY_NAME = "archive-name"
        const val KEY_USE_FOR_RANDOM = "use-for-random"

        /** Random entries can be redirects, images or metadata pages; retry a few times. */
        const val RANDOM_ATTEMPTS = 40

        /** Largest single archive entry served to the WebView (images, fonts, styles). */
        const val MAX_RESOURCE_BYTES = 24L * 1024 * 1024
    }
}
