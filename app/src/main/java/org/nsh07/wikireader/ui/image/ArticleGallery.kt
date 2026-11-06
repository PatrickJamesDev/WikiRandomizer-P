package org.nsh07.wikireader.ui.image

import androidx.compose.ui.text.AnnotatedString
import org.nsh07.wikireader.data.CommonsCategoryGallery
import org.nsh07.wikireader.ui.homeScreen.normalizeImageName

/**
 * One picture of an article's combined gallery. The full-screen viewer swipes through a list of
 * these: the lead picture, every picture in the article body (in reading order, including the
 * pictures of `<gallery>` blocks) and finally the Wikimedia Commons strip.
 */
data class ArticleGalleryItem(
    /** Address shown in the viewer (a standard 1920 px rendition, or the Commons view size). */
    val uri: String,
    /** Caption as wikitext (links/templates are flattened to plain text when shown). */
    val caption: String,
    /** The picture's page on Wikipedia/Commons, for "share" and "open in browser". */
    val pageUrl: String? = null
)

/** Builds the combined gallery of an article. Pure functions, cheap enough to run on a tap. */
object ArticleGalleryBuilder {

    /** Same picture, whatever the size/prefix of its address: used to match a tapped picture. */
    fun keyOf(uri: String?): String? =
        normalizeImageName(uri?.substringBefore('?')?.substringAfterLast('/'))?.lowercase()

    /**
     * @param leadSource address of the lead picture the article header shows (may be null)
     * @param leadCaption fallback caption for the lead picture (normally the article title)
     */
    fun build(
        lang: String,
        leadSource: String?,
        leadCaption: String,
        extract: List<List<AnnotatedString>>,
        commons: CommonsCategoryGallery?
    ): List<ArticleGalleryItem> {
        val out = ArrayList<ArticleGalleryItem>()
        val seen = HashSet<String>()

        fun add(uri: String, caption: String, pageUrl: String?) {
            val key = keyOf(uri) ?: return
            if (!seen.add(key)) return
            out.add(ArticleGalleryItem(uri, caption, pageUrl))
        }

        fun filePage(name: String) =
            "https://$lang.wikipedia.org/wiki/File:" + name.trim().replace(' ', '_')

        // Lead picture first. Its real caption (when the body shows the same file) is filled in below.
        var leadIndex = -1
        val leadName = leadSource?.let { org.nsh07.wikireader.ui.homeScreen.imageNameFromUrl(it) }
        if (leadSource != null) {
            val uri = if (leadName != null) ImagePrefetcher.fileUrl(lang, leadName, 1920) else leadSource
            add(uri, leadCaption, leadName?.let(::filePage))
            leadIndex = out.lastIndex
        }
        val leadKey = if (leadIndex >= 0) keyOf(out[leadIndex].uri) else null

        for (section in extract) {
            for (block in section) {
                // Cheap prefix test first: most blocks are paragraphs and must not be copied to a String.
                val isFile = block.startsWith("[[File:")
                val isGallery = !isFile && block.startsWith("<gallery")
                if (!isFile && !isGallery) continue
                val text = block.toString()
                if (isFile) {
                    val name = text.substringAfter(':').substringBefore('|').substringBefore("]]").trim()
                    if (name.isBlank()) continue
                    val caption = text.substringAfter('|', "")
                        .substringBeforeLast("]]")
                        .substringBeforeLast("|invert")
                        .trim()
                    val uri = ImagePrefetcher.fileUrl(lang, name, 1920)
                    if (leadKey != null && keyOf(uri) == leadKey) {
                        // The body shows the lead picture again; keep its caption on the lead entry.
                        if (caption.isNotBlank() && leadIndex >= 0) {
                            out[leadIndex] = out[leadIndex].copy(caption = caption)
                        }
                    } else add(uri, caption, filePage(name))
                } else {
                    text.substringAfter('>').trim(' ', '\n').lines().forEach { line ->
                        val raw = line.substringBefore('|').trim()
                        val name = if (raw.startsWith("File:", true) || raw.startsWith("Image:", true))
                            raw.substringAfter(':').trim() else raw
                        if (name.isBlank() || name.startsWith("<")) return@forEach
                        add(
                            ImagePrefetcher.fileUrl(lang, name, 1920),
                            line.substringAfter('|', "").trim(),
                            filePage(name)
                        )
                    }
                }
            }
        }

        commons?.images?.forEach { image -> add(image.viewUrl, image.title, image.descriptionUrl) }
        return out
    }

    /** Position of the picture behind [uri] in [items], or -1. */
    fun indexOf(items: List<ArticleGalleryItem>, uri: String): Int {
        val key = keyOf(uri) ?: return -1
        return items.indexOfFirst { keyOf(it.uri) == key }
    }
}
