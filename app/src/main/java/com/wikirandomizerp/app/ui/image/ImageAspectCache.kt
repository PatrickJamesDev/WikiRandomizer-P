package com.wikirandomizerp.app.ui.image

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.wikirandomizerp.app.WikiRandomizerPApplication
import com.wikirandomizerp.app.data.DebugLog

/**
 * Width/height ratio of article pictures, so an article can reserve a picture's exact height
 * BEFORE the picture has loaded. Without it every picture is a 160 dp grey block that jumps to its
 * real height when the download finishes, pushing the text you are reading down the screen.
 *
 * The sizes come from one small API request per article (50 pictures per call, no image data),
 * and from pictures that were already shown once. The map is Compose state, so a picture that is
 * already on screen resizes as soon as its ratio arrives.
 */
object ImageAspectCache {
    private val ratios = mutableStateMapOf<String, Float>()
    private val asked = java.util.Collections.synchronizedSet(HashSet<String>())
    private const val MAX_ENTRIES = 4000

    /** Width / height ratio known for a picture address made by [ImagePrefetcher.fileUrl], or null. */
    fun ratioFor(source: String?): Float? {
        val key = keyOf(source) ?: return null
        return ratios[key]
    }

    /** Remembers the ratio of a picture that has loaded (covers pictures the API lookup missed). */
    fun record(source: String?, width: Float, height: Float) {
        val key = keyOf(source) ?: return
        if (key in ratios) return
        if (!width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f) return
        ratios[key] = width / height
    }

    /** Asks Wikipedia for the sizes of all inline pictures in [extract] that are not known yet. */
    suspend fun fetchFor(context: Context, extract: List<List<AnnotatedString>>) {
        if (ratios.size > MAX_ENTRIES) { // a long session: start over rather than grow forever
            ratios.clear()
            asked.clear()
        }
        val todo = withContext(Dispatchers.Default) {
            val names = LinkedHashSet<String>()
            for (section in extract) {
                for (block in section) {
                    if (!block.startsWith("[[File:")) continue
                    val raw = block.toString().substringAfter(':').substringBefore('|').substringBefore("]]")
                    val key = normalize(raw)
                    if (key.isNotEmpty()) names.add(key)
                }
            }
            names.filter { it !in ratios && asked.add(it) }
        }
        if (todo.isEmpty()) return
        val repository = (context.applicationContext as WikiRandomizerPApplication).container.wikipediaRepository
        for (chunk in todo.chunked(50)) {
            try {
                val query = repository.getImageSizes(chunk.map { "File:$it" }).query ?: continue
                // The API may spell a title differently than we did (underscores, capital letter):
                // remember which of our spellings it answered for.
                val alias = HashMap<String, String>()
                for (n in query.normalized) alias[normalize(n.to.substringAfter(':'))] = normalize(n.from.substringAfter(':'))
                for (page in query.pages) {
                    val info = page.imageinfo?.firstOrNull() ?: continue
                    if (info.width <= 0 || info.height <= 0) continue
                    val key = normalize(page.title.substringAfter(':'))
                    val ratio = info.width.toFloat() / info.height.toFloat()
                    ratios[key] = ratio
                    alias[key]?.let { ratios[it] = ratio }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                chunk.forEach { asked.remove(it) } // leaving the article: ask again next time
                throw e
            } catch (e: Exception) {
                chunk.forEach { asked.remove(it) }
                DebugLog.w("ImageSizes", "picture size lookup failed: ${e.message}")
            }
        }
    }

    private fun keyOf(source: String?): String? {
        if (source == null) return null
        val raw = source.substringAfter("Special:FilePath/", "").substringBefore('?')
        if (raw.isEmpty()) return null
        return normalize(raw.replace("%3F", "?").replace("%23", "#"))
    }

    /** MediaWiki file name form: spaces instead of underscores, first letter upper case. */
    private fun normalize(name: String): String {
        val n = name.trim().replace('_', ' ')
        return if (n.isEmpty()) n else n.substring(0, 1).uppercase() + n.substring(1)
    }
}
