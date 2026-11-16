package com.wikirandomizerp.app.ui.image

import android.content.Context
import androidx.compose.ui.text.AnnotatedString
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Size
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.wikirandomizerp.app.data.CacheConfig
import com.wikirandomizerp.app.data.DebugLog
import org.nsh07.wikireader.shared.WikiUrls

/**
 * Explicit Coil disk-cache keys. Pinned thumbnails (History / Saved / tabs) use their own prefix
 * so the retention policy can evict article body pictures without ever removing the small covers
 * that make those screens instant.
 */
object ImageCacheKeys {
    fun thumbnail(url: String?): String? = url?.let { "thumb:$it" }
    fun body(url: String?): String? = url?.let { "body:$it" }
}

/**
 * Quietly loads every image of an article in the background, so scrolling and the gallery show
 * pictures instantly instead of when they come into view.
 */
object ImagePrefetcher {
    private const val MAX_IMAGES = CacheConfig.MAX_ARTICLE_IMAGES

    /** Image URLs (same form the article renderer uses) found in the parsed [extract]. */
    fun imageUrls(lang: String, extract: List<List<AnnotatedString>>): List<String> {
        val out = LinkedHashSet<String>()
        for (section in extract) {
            for (block in section) {
                val s = block.toString()
                when {
                    s.startsWith("[[File:") -> {
                        val name = s.substringAfter(':').substringBefore('|').substringBefore("]]")
                        if (name.isNotBlank()) out.add(url(lang, name))
                    }
                    s.startsWith("<gallery") -> {
                        s.substringAfter('>').trim(' ', '\n').lines().forEach { line ->
                            val raw = line.substringBefore('|').trim()
                            val name = if (raw.startsWith("File:", true) || raw.startsWith("Image:", true))
                                raw.substringAfter(':').trim() else raw
                            if (name.isNotBlank() && !name.startsWith("<")) out.add(url(lang, name))
                        }
                    }
                }
            }
        }
        return out.toList()
    }

    private fun url(lang: String, name: String) = fileUrl(lang, name)

    /**
     * Address of a Wikipedia/Commons picture scaled to [width] px. '?' and '#' inside a file name
     * used to cut the address short (the picture then silently failed to load).
     *
     * [width] must be one of Wikimedia's standard thumbnail steps (20, 40, 60, 120, 250, 330, 500,
     * 960, 1280, 1920, 3840): since 2026 other sizes are rate-limited (HTTP 429 "Use thumbnail
     * steps") whenever the thumbnail is not already cached, which shows up as pictures that
     * load slowly or not at all.
     */
    fun fileUrl(lang: String, name: String, width: Int = 960): String =
        WikiUrls.filePathUrl(lang, name, width)

    private val thumbWidthRegex = Regex("/(\\d+)px-")

    /** The only thumbnail widths Wikimedia serves without rate-limiting uncached requests. */
    private val thumbnailSteps = intArrayOf(20, 40, 60, 120, 250, 330, 500, 960, 1280, 1920, 3840)

    /**
     * Wikimedia answers thumbnail requests for any other width (the app used to ask for 128, 320
     * and 800 px) with HTTP 429 "Use thumbnail steps" whenever that exact size is not cached yet.
     * That is why many search results, history rows and tab thumbnails showed no picture at all.
     *
     * Addresses already stored by older versions (history, saved articles, tabs) are repaired here
     * by rounding the width DOWN to the nearest allowed step. Rounding down is always safe: a
     * thumbnail of width W exists only when the original is at least W pixels wide.
     */
    fun standardThumbnailUrl(url: String?): String? {
        if (url == null || !url.contains("/thumb/")) return url
        val match = thumbWidthRegex.findAll(url).lastOrNull() ?: return url
        val width = match.groupValues[1].toIntOrNull() ?: return url
        if (width in thumbnailSteps) return url
        val step = thumbnailSteps.lastOrNull { it <= width } ?: thumbnailSteps.first()
        return url.replaceRange(match.range, "/${step}px-")
    }

    /** A small (default 250 px) version of a thumbnail address, for list rows and tab covers. */
    fun smallThumbnailUrl(url: String?, maxWidth: Int = 250): String? {
        val standard = standardThumbnailUrl(url) ?: return null
        if (!standard.contains("/thumb/")) return standard
        val match = thumbWidthRegex.findAll(standard).lastOrNull() ?: return standard
        val width = match.groupValues[1].toIntOrNull() ?: return standard
        return if (width > maxWidth) standard.replaceRange(match.range, "/${maxWidth}px-") else standard
    }

    /** Pins small cover pictures in the disk cache (used by History, Saved and the tab panel). */
    fun warmThumbnails(context: Context, urls: Collection<String?>) {
        val small = urls.mapNotNull { smallThumbnailUrl(it) }.distinct()
        if (small.isEmpty()) return
        val loader = SingletonImageLoader.get(context)
        for (u in small) enqueueWarm(loader, context, u, ImageCacheKeys.thumbnail(u), 0)
    }


    /** Fire-and-forget: loads the full-screen renditions of [urls] into the disk cache. */
    fun warmFullScreen(context: Context, urls: Collection<String>) {
        if (urls.isEmpty()) return
        val loader = SingletonImageLoader.get(context)
        for (u in urls) {
            val standard = standardThumbnailUrl(u) ?: u
            enqueueWarm(loader, context, standard, ImageCacheKeys.body(standard), 0)
        }
    }

    /**
     * The first [MAX_HIGH_RES_IMAGES] pictures of an article (the lead picture first) are loaded at
     * their full-screen size right away, without waiting for the user to scroll, so opening one is
     * instant. Returns nothing; addresses already in [done] are skipped.
     */
    suspend fun preloadFirstFullRes(
        context: Context,
        lang: String,
        lead: String?,
        extract: List<List<AnnotatedString>>,
        done: MutableSet<String>
    ) = withContext(Dispatchers.Default) {
        val candidates = ArrayList<String>()
        lead?.let { src ->
            com.wikirandomizerp.app.ui.homeScreen.imageNameFromUrl(src)?.let { candidates.add(fileUrl(lang, it, 1920)) }
        }
        candidates.addAll(imageUrls(lang, extract).mapNotNull { largeVariant(it) })
        val fresh = candidates.distinct().take(CacheConfig.MAX_HIGH_RES_IMAGES).filter { done.add(it) }
        if (fresh.isEmpty()) return@withContext
        val loader = SingletonImageLoader.get(context)
        for (u in fresh) enqueueWarm(loader, context, u, ImageCacheKeys.body(u))
    }

    /**
     * Wikimedia Commons strip of an article: EVERY thumbnail goes into the disk cache (so the strip
     * never shows grey boxes), and the high-resolution viewer renditions are preloaded too.
     */
    fun preloadCommons(context: Context, gallery: com.wikirandomizerp.app.data.CommonsCategoryGallery) {
        val loader = SingletonImageLoader.get(context)
        gallery.images.forEachIndexed { index, image ->
            enqueueWarm(loader, context, image.thumbnailUrl, ImageCacheKeys.body(image.thumbnailUrl))
            if (index < CacheConfig.MAX_HIGH_RES_IMAGES) {
                enqueueWarm(loader, context, image.viewUrl, ImageCacheKeys.body(image.viewUrl))
            }
        }
    }

    /**
     * Fire-and-forget: loads [urls] into the disk cache so they appear instantly later. Body
     * pictures are stored at the 960 px step, and the full-screen 1920 px renditions are stored
     * too (up to [CacheConfig.MAX_HIGH_RES_IMAGES]) so opening the viewer never waits.
     */
    suspend fun warmAndWait(context: Context, urls: Collection<String>) =
        withContext(Dispatchers.IO) {
            val distinct = urls.mapNotNull { standardThumbnailUrl(it) }.distinct()
            if (distinct.isEmpty()) return@withContext
            val loader = SingletonImageLoader.get(context)
            coroutineScope {
                distinct.map { url ->
                    async {
                        try {
                            loader.execute(
                                ImageRequest.Builder(context)
                                    .data(url)
                                    .diskCacheKey(ImageCacheKeys.body(url))
                                    .memoryCachePolicy(CachePolicy.DISABLED)
                                    .size(Size(96, 96))
                                    .build()
                            )
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (_: Throwable) {
                        }
                    }
                }.awaitAll()
            }
        }

    suspend fun warm(
        context: Context,
        urls: Collection<String>,
        maxFullRes: Int = CacheConfig.MAX_HIGH_RES_IMAGES,
        gapMs: Long = 0L
    ) {
        val distinct = urls.distinct()
        if (distinct.isEmpty()) return
        warmPictures(
            context = context,
            thumbnails = distinct,
            fullRes = distinct.take(maxFullRes).mapNotNull(::largeVariant),
            gapMs = gapMs,
        )
    }

    suspend fun warmPictures(
        context: Context,
        thumbnails: Collection<String>,
        fullRes: Collection<String> = emptyList(),
        gapMs: Long = 0L
    ) = withContext(Dispatchers.Default) {
        if (thumbnails.isEmpty() && fullRes.isEmpty()) return@withContext
        val loader = SingletonImageLoader.get(context)
        val batch = WarmQueue.newBatch()
        // The first pictures of the article (the lead picture first) go before everything else.
        thumbnails.forEachIndexed { index, u ->
            enqueueWarm(loader, context, u, ImageCacheKeys.body(u), if (index < 3) 0 else 1, batch)
            if (gapMs > 0 && index >= 3) delay(gapMs)
        }
        for (u in fullRes) {
            enqueueWarm(loader, context, u, ImageCacheKeys.body(u), 3, batch)
            if (gapMs > 0) delay(gapMs)
        }
    }

    /** Lower number = loaded sooner. Covers/thumbnails first, 1920 px renditions last. */
    private fun defaultPriority(url: String): Int =
        if (url.contains("1920px") || url.contains("width=1920") || url.contains("1280px")) 3 else 1

    private fun enqueueWarm(
        loader: coil3.ImageLoader,
        context: Context,
        url: String,
        diskCacheKey: String?,
        priority: Int = defaultPriority(url),
        batch: Long = WarmQueue.newBatch()
    ) {
        WarmQueue.submit(loader, context.applicationContext, url, diskCacheKey, priority, batch)
    }

    /**
     * Background warming used to call loader.enqueue() for every picture of every prepared article
     * at once (hundreds of requests, many of them 1920 px, some of them duplicates). All of them
     * shared the same HTTP/2 connection to upload.wikimedia.org, so the picture the reader was
     * actually looking at got a tiny slice of the bandwidth and took 2-3 s to appear.
     *
     * This queue keeps background loading to a few requests at a time, skips pictures that are
     * already on disk or already queued, serves the newest request first (what the reader just
     * opened) and always lets small pictures go before huge ones.
     */
    private object WarmQueue {
        private const val MAX_CONCURRENT = 3
        private const val MAX_PENDING = 300

        private class Job(
            val priority: Int,
            val seq: Long,
            val batch: Long,
            val loader: coil3.ImageLoader,
            val context: Context,
            val url: String,
            val key: String?
        )

        private val scope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + Dispatchers.IO
        )
        private val lock = Any()
        private var seq = 0L
        private var running = 0
        private val known = HashSet<String>()
        private val pending = java.util.TreeSet<Job>(
            // Priority first; then the newest request group (the article the reader just opened);
            // inside one group the original order, so the first picture of an article loads first.
            compareBy<Job>({ it.priority }, { -it.batch }, { it.seq })
        )
        private val batches = java.util.concurrent.atomic.AtomicLong()

        fun newBatch(): Long = batches.incrementAndGet()

        fun submit(
            loader: coil3.ImageLoader,
            context: Context,
            url: String,
            key: String?,
            priority: Int,
            batch: Long
        ) {
            synchronized(lock) {
                val id = key ?: url
                if (!known.add(id)) return
                pending.add(Job(priority, seq++, batch, loader, context, url, key))
                while (pending.size > MAX_PENDING) {
                    val dropped = pending.pollLast() ?: break
                    known.remove(dropped.key ?: dropped.url)
                }
            }
            pump()
        }

        private fun pump() {
            while (true) {
                val job = synchronized(lock) {
                    if (running >= MAX_CONCURRENT) return
                    val next = pending.pollFirst() ?: return
                    running++
                    next
                }
                scope.launch {
                    try {
                        run(job)
                    } finally {
                        synchronized(lock) {
                            running--
                            known.remove(job.key ?: job.url)
                        }
                        pump()
                    }
                }
            }
        }

        private suspend fun run(job: Job) {
            try {
                val cacheKey = job.key
                if (cacheKey != null) {
                    val snapshot = job.loader.diskCache?.openSnapshot(cacheKey)
                    if (snapshot != null) {
                        snapshot.close()
                        return // already on disk: no network, no decoding
                    }
                }
                job.loader.execute(
                    ImageRequest.Builder(job.context)
                        .data(job.url)
                        .diskCacheKey(cacheKey)
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .size(Size(96, 96))
                        .build()
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
            }
        }
    }

    /** Removes body-picture cache entries (never pinned thumbnails) for a retired article. */
    fun evict(context: Context, urls: Collection<String>) {
        if (urls.isEmpty()) return
        val diskCache = try {
            SingletonImageLoader.get(context).diskCache
        } catch (_: Throwable) {
            null
        } ?: return
        for (u in urls) {
            runCatching { diskCache.remove(ImageCacheKeys.body(u) ?: return@runCatching) }
            largeVariant(u)?.let { large ->
                runCatching { diskCache.remove(ImageCacheKeys.body(large) ?: return@runCatching) }
            }
        }
    }

    /**
     * Address to use in the full-screen viewer: the 1920 px rendition when the URL supports it,
     * otherwise the original. Always rounded to an allowed thumbnail step so the viewer never
     * hits the "Use thumbnail steps" rate limit.
     */
    fun fullScreenUrl(url: String?): String? {
        if (url == null) return null
        return standardThumbnailUrl(largeVariant(url) ?: url)
    }

    /** Same picture at Wikimedia's 1920 px step, or null when the URL is not a Standard:FilePath URL. */
    internal fun largeVariant(url: String): String? {
        if (url.contains("?width=")) return url.substringBefore("?width=") + "?width=1920"
        // Thumbnail URLs from the API look like ".../thumb/a/ab/Name.jpg/960px-Name.jpg".
        val px = Regex("/\\d+px-")
        if (px.containsMatchIn(url)) return url.replace(px, "/1920px-")
        return null
    }

    /** The first few picture addresses of raw [wikitext] (used to prepare the next article). */
    fun firstImageUrls(lang: String, wikitext: String, max: Int = 4): List<String> {
        val out = LinkedHashSet<String>()
        var from = 0
        while (out.size < max) {
            val at = wikitext.indexOf("[[", from)
            if (at < 0) break
            from = at + 2
            val head = wikitext.substring(at + 2, minOf(wikitext.length, at + 200)).substringBefore('|')
            if (!head.contains(':')) continue
            val name = head.substringAfter(':').substringBefore("]]").trim()
            if (name.matches(fileNameRegex)) out.add(fileUrl(lang, name))
        }
        var galleryFrom = 0
        while (out.size < max) {
            val open = wikitext.indexOf("<gallery", galleryFrom, ignoreCase = true)
            if (open < 0) break
            val bodyStart = wikitext.indexOf('>', open)
            if (bodyStart < 0) break
            val close = wikitext.indexOf("</gallery", bodyStart, ignoreCase = true)
            if (close < 0) break
            galleryFrom = close + 1
            for (line in wikitext.substring(bodyStart + 1, close).lineSequence()) {
                if (out.size >= max) break
                val raw = line.substringBefore('|').trim()
                val name = if (raw.startsWith("File:", true) || raw.startsWith("Image:", true)) {
                    raw.substringAfter(':').trim()
                } else raw
                if (name.matches(fileNameRegex)) out.add(fileUrl(lang, name))
            }
        }
        return out.toList()
    }

    private val fileNameRegex =
        Regex(".+\\.(jpe?g|png|gif|svg|webp|tiff?|bmp)", RegexOption.IGNORE_CASE)

    /** Enqueues images not in [done] yet (and records them there). Network + disk cache only. */
    suspend fun prefetch(
        context: Context,
        lang: String,
        extract: List<List<AnnotatedString>>,
        done: MutableSet<String>
    ) = withContext(Dispatchers.Default) {
        // All of this used to run on the UI thread: scanning every text block of the article
        // (AnnotatedString.toString() copies each one) froze scrolling for 100-150 ms, and the first
        // SingletonImageLoader.get() builds an OkHttp client (SSL setup).
        val fresh = imageUrls(lang, extract).filter { it !in done }.take(MAX_IMAGES)
        if (fresh.isEmpty()) return@withContext
        DebugLog.i("Prefetch", "queueing ${fresh.size} article images in the background (${done.size} already queued)")
        val loader = SingletonImageLoader.get(context)
        for ((index, u) in fresh.withIndex()) {
            done.add(u)
            enqueueWarm(loader, context, u, ImageCacheKeys.body(u))
            if (index < CacheConfig.MAX_HIGH_RES_IMAGES) largeVariant(u)?.let {
                done.add(it)
                enqueueWarm(loader, context, it, ImageCacheKeys.body(it))
            }
            delay(50) // gentle: don't hit the server with a hundred requests at once
        }
    }
}
