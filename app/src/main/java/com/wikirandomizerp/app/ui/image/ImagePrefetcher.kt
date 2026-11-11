package com.wikirandomizerp.app.ui.image

import android.content.Context
import androidx.compose.ui.text.AnnotatedString
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
    private const val MAX_IMAGES = 80

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
        for (u in small) enqueueWarm(loader, context, u, ImageCacheKeys.thumbnail(u))
    }


    /** Fire-and-forget: loads the full-screen renditions of [urls] into the disk cache. */
    fun warmFullScreen(context: Context, urls: Collection<String>) {
        if (urls.isEmpty()) return
        val loader = SingletonImageLoader.get(context)
        for (u in urls) {
            val standard = standardThumbnailUrl(u) ?: u
            enqueueWarm(loader, context, standard, ImageCacheKeys.body(standard))
        }
    }

    /**
     * The first [FULL_RES_ALWAYS] pictures of an article (the lead picture first) are loaded at
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
        val fresh = candidates.distinct().take(FULL_RES_ALWAYS).filter { done.add(it) }
        if (fresh.isEmpty()) return@withContext
        val loader = SingletonImageLoader.get(context)
        for (u in fresh) enqueueWarm(loader, context, u, ImageCacheKeys.body(u))
    }

    /**
     * Wikimedia Commons strip of an article: EVERY thumbnail goes into the disk cache (so the strip
     * never shows grey boxes), and the first [FULL_RES_ALWAYS] pictures are also loaded at the size
     * the full-screen viewer shows.
     */
    fun preloadCommons(context: Context, gallery: com.wikirandomizerp.app.data.CommonsCategoryGallery) {
        val loader = SingletonImageLoader.get(context)
        gallery.images.forEachIndexed { index, image ->
            enqueueWarm(loader, context, image.thumbnailUrl, ImageCacheKeys.body(image.thumbnailUrl))
            if (index < FULL_RES_ALWAYS) {
                enqueueWarm(loader, context, image.viewUrl, ImageCacheKeys.body(image.viewUrl))
            }
        }
    }

    const val FULL_RES_ALWAYS = 3

    /** Fire-and-forget: loads [urls] into the disk cache so they appear instantly later. */
    fun warm(context: Context, urls: Collection<String>) {
        if (urls.isEmpty()) return
        val loader = SingletonImageLoader.get(context)
        for ((index, u) in urls.withIndex()) {
            enqueueWarm(loader, context, u, ImageCacheKeys.body(u))
            if (index < 2) largeVariant(u)?.let {
                enqueueWarm(loader, context, it, ImageCacheKeys.body(it))
            }
        }
    }

    private fun enqueueWarm(
        loader: coil3.ImageLoader,
        context: Context,
        url: String,
        diskCacheKey: String?
    ) {
        loader.enqueue(
            ImageRequest.Builder(context)
                .data(url)
                .diskCacheKey(diskCacheKey)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .size(Size(96, 96))
                .build()
        )
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
        val fresh = imageUrls(lang, extract).filter { it !in done }.take(MAX_IMAGES - done.size)
        if (fresh.isEmpty()) return@withContext
        DebugLog.i("Prefetch", "queueing ${fresh.size} article images in the background (${done.size} already queued)")
        val loader = SingletonImageLoader.get(context)
        for ((index, u) in fresh.withIndex()) {
            done.add(u)
            enqueueWarm(loader, context, u, ImageCacheKeys.body(u))
            if (index < 3) largeVariant(u)?.let {
                done.add(it)
                enqueueWarm(loader, context, it, ImageCacheKeys.body(it))
            }
            delay(60) // gentle: don't hit the server with 80 requests at once
        }
    }
}
