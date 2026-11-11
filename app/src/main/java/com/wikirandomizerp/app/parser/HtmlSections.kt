package com.wikirandomizerp.app.parser

/**
 * Splits the HTML returned by Wikipedia's REST `page/html` endpoint into the same
 * `[intro, heading, body, heading, body, ...]` shape that [com.wikirandomizerp.app.data.parseSections]
 * produces for wikitext.
 *
 * Only top-level `h2` headings become sections. Everything readable stays in the body (paragraphs,
 * lists, links, tables, captions and reference lists), while Wikipedia chrome that has no place in
 * a reader (scripts, edit links, infoboxes, navigation boxes, maintenance banners and raw metadata)
 * is removed first.
 *
 * The sanitizer intentionally uses a small quote-aware scanner instead of regular expressions for
 * elements. REST HTML puts raw `>` characters inside `data-mw` attributes; regex-based tag matching
 * stops at those characters and can leak template/citation source into the article or truncate the
 * following prose. The scanner also removes nested chrome as one balanced element.
 *
 * This is plain Kotlin so it can be unit tested on the JVM and runs off the main thread.
 */
private val headingRegex = Regex("(?is)<h2\\b[^>]*>(.*?)</h2\\s*>")
private val tagRegex = Regex("(?s)<[^>]+>")
private val entityRegex = Regex("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);")
private val hrefRegex = Regex("(?i)href\\s*=\\s*\"([^\"]*)\"")
private val manyNewlinesRegex = Regex("\\n[ \\t]*\\n(?:[ \\t]*\\n)+")
private val classAttrRegex =
    Regex("(?is)\\bclass\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))")
private val hrefAttrRegex =
    Regex("(?is)\\bhref\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))")
private val resourceAttrRegex =
    Regex("(?is)\\bresource\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))")
private val srcAttrRegex =
    Regex("(?is)\\bsrc\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))")
private val widthAttrRegex =
    Regex("(?is)\\bwidth\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))")
private val typeofAttrRegex =
    Regex("(?is)\\btypeof\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))")
private val styleAttrRegex =
    Regex("(?is)\\bstyle\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))")
private val imgTagRegex = Regex("(?is)<img\\b[^>]*>")
private val figcaptionRegex = Regex("(?is)<figcaption\\b[^>]*>(.*?)</figcaption\\s*>")
private val imageExtensionRegex =
    Regex(".+\\.(jpe?g|png|gif|svg|webp|tiff?|bmp|jfif|apng)$", RegexOption.IGNORE_CASE)

/** Elements whose entire content is not article text. */
private val droppedTags = setOf(
    "style", "script", "noscript", "link", "meta", "img", "input", "wbr", "iframe",
    "audio", "video", "source", "track", "object", "embed", "annotation", "svg"
)

/** HTML void elements: an opening tag has no matching closing tag. */
private val voidTags = setOf(
    "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta",
    "param", "source", "track", "wbr"
)

/** Class tokens that mark chrome rather than readable article content. */
private val removedClasses = setOf(
    "navbox", "vertical-navbox", "navbox-styles", "infobox", "sidebar", "metadata",
    "ambox", "tmbox", "hatnote", "shortdescription", "mw-empty-elt", "noprint",
    "mw-editsection", "mw-hidden-catlinks", "mw-indicators", "printfooter", "catlinks"
)

private val inlineTags = setOf(
    "b", "strong", "i", "em", "u", "s", "strike", "del", "ins", "sub", "sup",
    "code", "tt", "kbd", "samp", "var", "small", "big", "cite", "q"
)

private val blockTags = setOf(
    "blockquote", "p", "ul", "ol", "li", "dl", "dt", "dd", "pre", "table",
    "thead", "tbody", "tfoot", "tr", "td", "th", "caption", "h2", "h3", "h4", "h5", "h6"
)

/** Layout-only wrappers: drop the tag but keep the text and block boundaries. */
private val wrapperTags = setOf(
    "div", "section", "figure", "figcaption", "header", "footer", "article", "aside",
    "main", "nav"
)

private data class HtmlTag(
    val name: String,
    val closing: Boolean,
    val selfClosing: Boolean,
    val attributes: String
)

/**
 * @return alternating intro/heading/body strings. The first element is always the intro (possibly
 * empty). Every heading is plain text; every body is sanitised HTML.
 */
fun splitHtmlSections(html: String): List<String> {
    val content = extractContent(html)
    // Lift pictures out before sanitise() deletes every <img>. They are re-emitted as ordinary
    // `[[File:…|caption]]` lines, which ImageWithCaption draws exactly like wikitext pictures.
    val withMedia = extractHtmlMediaBlocks(content)
    val cleaned = sanitise(withMedia)

    val allHeadings = headingRegex.findAll(cleaned).toList()
    // Ignore empty h2 elements. They used to be treated as a section boundary and then skipped,
    // which silently threw away all text up to the next heading.
    val headings = allHeadings.filter { plainText(it.groupValues[1]).isNotBlank() }
    if (headings.isEmpty()) return listOf(tidy(cleaned))

    val out = ArrayList<String>(headings.size * 2 + 1)
    // Keep everything before the first *readable* heading, including text between ignored empty
    // headings and real prose.
    out.add(tidy(cleaned.substring(0, headings.first().range.first)))

    headings.forEachIndexed { index, match ->
        val heading = plainText(match.groupValues[1])
        val bodyEnd = headings.getOrNull(index + 1)?.range?.first ?: cleaned.length
        val body = tidy(cleaned.substring(match.range.last + 1, bodyEnd))
        out.add(heading)
        out.add(body)
    }

    // A page with no readable h2 still needs a valid intro slot.
    if (out.isEmpty()) out.add("")
    return out
}

private fun extractContent(html: String): String {
    val bodyStart = html.indexOf("<body", ignoreCase = true)
    val content = if (bodyStart >= 0) {
        val openEnd = html.indexOf('>', bodyStart).let { if (it < 0) html.length else it + 1 }
        val bodyEnd = html.lastIndexOf("</body", ignoreCase = true).let { if (it < 0) html.length else it }
        html.substring(openEnd.coerceAtMost(bodyEnd), bodyEnd)
    } else html

    // REST HTML usually puts `mw-parser-output` on <body>, which is already stripped above. Some
    // responses instead wrap the article in a <div class="mw-parser-output">. Find that class in an
    // actual opening tag: `mw-parser-output` also appears inside inline <style> CSS blocks (for
    // example the citation styles at the end of an article). Searching the raw string for the class
    // name used to enter one of those CSS blocks and throw away every section before References.
    val wrapper = findParserOutputRange(content) ?: return content
    return content.substring(wrapper.first, wrapper.last + 1)
}

// ---------------------------------------------------------------------------
// Pictures in REST HTML
// ---------------------------------------------------------------------------

/**
 * Replaces picture markup with the `[[File:…|caption]]` blocks the article renderer already
 * draws. Called before [sanitise], because the sanitiser removes `<img>` (Compose's HTML parser
 * cannot display it) and removes the whole infobox. Without this pass HTML mode showed no
 * in-article pictures at all — only the API page image, which has no caption.
 *
 * Handles the three shapes REST HTML uses:
 *  - `<figure typeof="mw:File/Thumb"> … <figcaption>…</figcaption></figure>` (body pictures),
 *  - `<table class="infobox …">` with `<img resource="./File:X.jpg">` plus the small caption
 *    cell below each picture,
 *  - `<ul class="gallery">` with one `<li class="gallerybox">` per picture.
 */
private fun extractHtmlMediaBlocks(html: String): String {
    if (!html.contains("<figure", ignoreCase = true) &&
        !html.contains("class=\"gallery", ignoreCase = true) &&
        !html.contains("<img", ignoreCase = true) &&
        !html.contains("infobox", ignoreCase = true)
    ) return html

    val out = StringBuilder(html.length)
    var i = 0
    while (i < html.length) {
        val start = html.indexOf('<', i)
        if (start < 0) {
            out.append(html, i, html.length)
            break
        }
        if (start > i) out.append(html, i, start)
        if (html.startsWith("<!--", start)) {
            val commentEnd = html.indexOf("-->", start + 4)
            val end = if (commentEnd < 0) html.length else commentEnd + 3
            out.append(html, start, end)
            i = end
            continue
        }
        val end = findTagEnd(html, start)
        if (end < 0) {
            out.append(html, start, html.length)
            break
        }
        val tag = parseTag(html.substring(start, end + 1))
        if (tag == null || tag.closing || tag.selfClosing || tag.name in voidTags) {
            out.append(html, start, end + 1)
            i = end + 1
            continue
        }
        val classes = classTokens(tag.attributes)
        val isInfobox = tag.name == "table" && "infobox" in classes
        val isFigure = tag.name == "figure"
        val isGallery = "gallery" in classes &&
                (tag.name == "ul" || tag.name == "div" || tag.name == "table")
        // `div.thumb` (one picture + thumbcaption) and `div.tmulti` ({{Multiple image}}) hold
        // their caption beside/under the pictures. Treat the whole container as one unit so a
        // caption cannot be paired with a picture from the next block.
        val isThumbDiv = tag.name == "div" && ("thumb" in classes || "tmulti" in classes)
        val isFileSpan = tag.name == "span" &&
                "noviewer" !in classes &&
                attributeValue(tag.attributes, typeofAttrRegex)
                    .orEmpty().startsWith("mw:File", ignoreCase = true)

        if (isInfobox || isFigure || isGallery || isThumbDiv) {
            val close = findMatchingClose(html, end + 1, tag.name)
            if (close >= 0) {
                val fragment = html.substring(start, close + 1)
                // The infobox is read once: its rows become a card block and its picture rows
                // give a caption to pictures whose caption the generic search above missed.
                val scan: HtmlInfoboxScan? = if (isInfobox) {
                    try {
                        scanHtmlInfobox(fragment)
                    } catch (e: Exception) {
                        null
                    }
                } else null
                val media = htmlMediaLines(fragment, gallery = isGallery)
                    .map { line -> withInfoboxCaption(line, scan?.captions) }
                if (media.isNotEmpty()) {
                    out.append('\n').append(media.joinToString("\n")).append('\n')
                }
                val box = scan?.box
                if (box != null) {
                    // One line, no raw angle brackets: it passes through sanitise() untouched and
                    // is split off again by splitHtmlMediaBlocks().
                    out.append('\n').append(encodeHtmlInfobox(box)).append('\n')
                }
                i = close + 1
                continue
            }
        } else if (isFileSpan) {
            val close = findMatchingClose(html, end + 1, tag.name)
            if (close >= 0) {
                val media = htmlMediaLines(
                    html.substring(start, close + 1),
                    gallery = false
                )
                if (media.isNotEmpty()) {
                    out.append('\n').append(media.joinToString("\n")).append('\n')
                }
                i = close + 1
                continue
            }
        }
        out.append(html, start, end + 1)
        i = end + 1
    }
    return out.toString()
}

/** Splits a sanitised HTML chunk into ordinary HTML and `[[File:…]]` picture blocks. */
fun splitHtmlMediaBlocks(html: String): List<String> {
    if (!html.contains("[[File:", ignoreCase = true) &&
        !html.contains(HTML_INFOBOX_MARKER, ignoreCase = true)
    ) return listOf(html)
    val out = ArrayList<String>()
    val text = StringBuilder()
    for (line in html.lineSequence()) {
        val head = line.trimStart()
        // Picture lines and infobox cards are single-line blocks of their own.
        if (head.startsWith("[[File:", ignoreCase = true) ||
            head.startsWith(HTML_INFOBOX_MARKER, ignoreCase = true)
        ) {
            if (text.isNotBlank()) out.add(text.toString())
            text.setLength(0)
            out.add(line.trim())
        } else {
            text.append(line).append('\n')
        }
    }
    if (text.isNotBlank()) out.add(text.toString())
    return out
}

private fun htmlMediaLines(fragment: String, gallery: Boolean): List<String> {
    val lines = ArrayList<String>()
    if (gallery) {
        for (item in directElements(fragment, "li")) {
            val img = imgTagRegex.find(item.inner) ?: continue
            val name = htmlImageName(img.value) ?: continue
            lines.add(pictureLine(name, htmlCaptions(item.inner).firstOrNull().orEmpty()))
        }
        if (lines.isNotEmpty()) return lines.distinct()
    }
    val captions = htmlCaptions(fragment)
    var index = 0
    for (img in imgTagRegex.findAll(fragment)) {
        val name = htmlImageName(img.value) ?: continue
        val caption = captions.getOrNull(index).orEmpty()
        index++
        lines.add(pictureLine(name, caption))
    }
    return lines.distinct()
}

/** File name from an `<img>` tag, preferring `resource` (a stable `File:` title). */
private fun htmlImageName(token: String): String? {
    var value = attributeValue(token, resourceAttrRegex)
        ?: attributeValue(token, srcAttrRegex)
        ?: return null
    var name = value.substringBefore('?').substringAfterLast('/').trim()
    name = runCatching { java.net.URLDecoder.decode(name, "UTF-8") }.getOrDefault(name)
    name = name.replace(Regex("^\\d+px-"), "").trim()
    if (name.startsWith("File:", true) || name.startsWith("Image:", true)) {
        name = name.substringAfter(':').trim()
    }
    if (!imageExtensionRegex.matches(name)) return null
    // Flags, logos, icons and maths sprites are not article pictures.
    val width = attributeValue(token, widthAttrRegex)?.toIntOrNull()
    if (width != null && width < 48) return null
    if (attributeValue(token, classAttrRegex)?.contains("noviewer", true) == true) return null
    return name
}

/**
 * Caption texts, in document order. `<figcaption>` wins; otherwise infobox caption cells and
 * gallery captions are collected.
 */
private fun htmlCaptions(fragment: String): List<String> {
    val out = ArrayList<String>()
    figcaptionRegex.findAll(fragment).forEach { match ->
        val text = plainText(match.groupValues[1])
        if (text.isNotBlank()) out.add(text)
    }
    if (out.isNotEmpty()) return out

    var i = 0
    while (i < fragment.length) {
        val start = fragment.indexOf('<', i)
        if (start < 0) break
        val end = findTagEnd(fragment, start)
        if (end < 0) break
        val tag = parseTag(fragment.substring(start, end + 1))
        if (tag == null || tag.closing || tag.selfClosing || tag.name in voidTags) {
            i = end + 1
            continue
        }
        val classes = classTokens(tag.attributes)
        val style = attributeValue(tag.attributes, styleAttrRegex).orEmpty()
        val looksCaption = classes.any { it.contains("caption") || it == "gallerytext" } ||
                style.replace(" ", "").contains("font-size:88%", ignoreCase = true)
        if (!looksCaption) {
            i = end + 1
            continue
        }
        val close = findMatchingClose(fragment, end + 1, tag.name)
        if (close < 0) {
            i = end + 1
            continue
        }
        // findMatchingClose returns the '>' of the closing tag; exclude the tag itself.
        val closeTagStart = fragment.lastIndexOf('<', close)
        val text = plainText(fragment.substring(end + 1, closeTagStart))
            .replace(Regex("\\s+([,.;:!?])"), "$1")
        if (text.isNotBlank()) out.add(text)
        i = close + 1
    }
    return out
}

private fun pictureLine(name: String, caption: String): String {
    val clean = caption
        .replace("]]", "")
        .replace('|', '/')
        .replace('\n', ' ')
        .replace(Regex("\\s+([,.;:!?])"), "$1")
        .trim()
    return "[[File:$name|$clean]]"
}

/** Inner range of the first real element carrying the `mw-parser-output` class. */
private fun findParserOutputRange(html: String): IntRange? {
    var i = 0
    while (i < html.length) {
        val tagStart = html.indexOf('<', i)
        if (tagStart < 0) return null
        if (html.startsWith("<!--", tagStart)) {
            val commentEnd = html.indexOf("-->", tagStart + 4)
            i = if (commentEnd < 0) html.length else commentEnd + 3
            continue
        }
        val tagEnd = findTagEnd(html, tagStart)
        if (tagEnd < 0) return null
        val tag = parseTag(html.substring(tagStart, tagEnd + 1))
        if (tag != null && !tag.closing && !tag.selfClosing &&
            "mw-parser-output" in classTokens(tag.attributes)
        ) {
            val close = findMatchingClose(html, tagEnd + 1, tag.name)
            val end = if (close < 0) html.length - 1 else close - 1
            return (tagEnd + 1)..end
        }
        i = tagEnd + 1
    }
    return null
}

/**
 * Removes non-reader markup while preserving ordinary prose and nested structure.
 *
 * The scanner finds the end of a tag by respecting quoted attribute values, so `>` inside
 * `data-mw` is not mistaken for the end of the tag. Chrome elements are removed with balanced
 * closing-tag matching, which keeps nested infoboxes/sidebars from swallowing prose that follows
 * them.
 */
private fun sanitise(html: String): String {
    val out = StringBuilder(html.length)
    var i = 0
    while (i < html.length) {
        val tagStart = html.indexOf('<', i)
        if (tagStart < 0) {
            out.append(html, i, html.length)
            break
        }
        if (tagStart > i) out.append(html, i, tagStart)

        if (html.startsWith("<!--", tagStart)) {
            val commentEnd = html.indexOf("-->", tagStart + 4)
            i = if (commentEnd < 0) html.length else commentEnd + 3
            continue
        }

        val tagEnd = findTagEnd(html, tagStart)
        if (tagEnd < 0) {
            out.append(html, tagStart, html.length)
            break
        }

        val tag = parseTag(html.substring(tagStart, tagEnd + 1))
        if (tag == null) {
            // Doctype / processing instruction / malformed token.
            i = tagEnd + 1
            continue
        }

        if (shouldRemove(tag)) {
            if (tag.closing || tag.selfClosing || tag.name in voidTags) {
                i = tagEnd + 1
                continue
            }
            val close = findMatchingClose(html, tagEnd + 1, tag.name)
            if (close >= 0) {
                out.append('\n')
                i = close + 1
            } else {
                // Malformed or unclosed chrome: drop only its opening tag. Keeping the rest of the
                // page is more important than showing an unclosed navigation box.
                i = tagEnd + 1
            }
            continue
        }

        appendNormalized(out, tag)
        i = tagEnd + 1
    }
    return out.toString()
}

/** Index of the `>` that ends a tag, ignoring `>` inside quoted attribute values. */
private fun findTagEnd(html: String, start: Int): Int {
    var quote = '\u0000'
    var i = start + 1
    while (i < html.length) {
        val c = html[i]
        if (quote != '\u0000') {
            if (c == quote) quote = '\u0000'
        } else {
            when (c) {
                '"', '\'' -> quote = c
                '>' -> return i
            }
        }
        i++
    }
    return -1
}

private fun parseTag(token: String): HtmlTag? {
    if (token.length < 3 || token[0] != '<' || token[1] == '!' || token[1] == '?') return null

    var i = 1
    var closing = false
    if (token[i] == '/') {
        closing = true
        i++
    }
    while (i < token.length - 1 && token[i].isWhitespace()) i++

    val nameStart = i
    while (i < token.length - 1) {
        val c = token[i]
        if (c.isLetterOrDigit() || c == ':' || c == '-' || c == '_') i++ else break
    }
    if (i == nameStart) return null

    val name = token.substring(nameStart, i).lowercase()
    val selfClosing = !closing && token.length >= 2 && token[token.length - 2] == '/'
    val attributes = if (closing) "" else token.substring(i, token.length - 1)
    return HtmlTag(name, closing, selfClosing, attributes)
}

private fun attributeValue(attributes: String, regex: Regex): String? {
    val match = regex.find(attributes) ?: return null
    return match.groupValues[1]
        .ifEmpty { match.groupValues[2] }
        .ifEmpty { match.groupValues[3] }
}

private fun classTokens(attributes: String): Set<String> {
    val value = attributeValue(attributes, classAttrRegex) ?: return emptySet()
    return value.split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .mapTo(HashSet()) { it.lowercase() }
}

private fun shouldRemove(tag: HtmlTag): Boolean {
    if (tag.closing) return false
    if (tag.name in droppedTags) return true
    val classes = classTokens(tag.attributes)
    if (classes.any { it in removedClasses }) return true
    return tag.name == "sup" && "reference" in classes
}

/** Finds the closing tag matching an already-consumed opening tag, honouring nested tags. */
private fun findMatchingClose(html: String, from: Int, name: String): Int {
    var depth = 1
    var i = from
    while (i < html.length) {
        val tagStart = html.indexOf('<', i)
        if (tagStart < 0) return -1

        if (html.startsWith("<!--", tagStart)) {
            val commentEnd = html.indexOf("-->", tagStart + 4)
            if (commentEnd < 0) return -1
            i = commentEnd + 3
            continue
        }

        val tagEnd = findTagEnd(html, tagStart)
        if (tagEnd < 0) return -1
        val tag = parseTag(html.substring(tagStart, tagEnd + 1))
        if (tag != null && tag.name == name && !tag.selfClosing) {
            if (tag.closing) {
                depth--
                if (depth == 0) return tagEnd
            } else {
                depth++
            }
        }
        i = tagEnd + 1
    }
    return -1
}

private fun appendNormalized(out: StringBuilder, tag: HtmlTag) {
    val name = tag.name
    when {
        name == "a" -> {
            if (tag.closing) {
                out.append("</a>")
            } else {
                val href = attributeValue(tag.attributes, hrefAttrRegex)
                if (href == null) out.append("<a>")
                else out.append("<a href=\"").append(href).append("\">")
            }
        }
        name == "br" -> if (!tag.closing) out.append("<br>")
        name == "hr" -> if (!tag.closing) out.append('\n')
        name in inlineTags || name in blockTags -> {
            if (tag.closing) out.append("</").append(name).append('>')
            else {
                out.append('<').append(name)
                // Keep the class on tables and cells: Wikipedia's phylogenetic trees are ordinary
                // <table class="clade"> blocks, and the HTML reader turns them back into a
                // Cladogram. Other attributes (including inline CSS) are still dropped.
                if (name == "table" || name == "td" || name == "th") {
                    val rawClass = attributeValue(tag.attributes, classAttrRegex)
                    if (!rawClass.isNullOrBlank()) {
                        out.append(" class=\"").append(rawClass).append('"')
                    }
                }
                out.append('>')
            }
        }
        name in wrapperTags -> out.append('\n')
        // Unknown tags are ignored, but their text content is kept.
    }
}

private fun tidy(html: String): String =
    html.replace(manyNewlinesRegex, "\n\n").trim()

/**
 * REST HTML uses relative links (`/wiki/Foo`). Compose's HTML parser keeps them as-is, and an
 * Android intent cannot open a relative URI. Make them absolute for [lang] before parsing.
 */
fun absolutizeHtmlLinks(html: String, lang: String): String = hrefRegex.replace(html) { match ->
    val href = match.groupValues[1]
    val absolute = when {
        href.startsWith("//") -> "https:$href"
        href.startsWith("/wiki/") -> "https://$lang.wikipedia.org$href"
        href.startsWith("./") -> "https://$lang.wikipedia.org/wiki/" + href.removePrefix("./")
        // A bare fragment (`#cite_note-1`) has no scheme and Android's intent handler
        // cannot open it. Point it at the wiki's main page instead of letting Text crash.
        href.startsWith("#") -> "https://$lang.wikipedia.org/wiki/"
        else -> href
    }
    "href=\"$absolute\""
}

/**
 * Compose's HTML parser drops the visual separation between block elements. Without these
 * explicit newlines, consecutive `<p>` paragraphs, list items and headings ran together into a
 * single wall of text in HTML mode. The markup itself is left intact so inline formatting and
 * links still work; only whitespace is added around block boundaries.
 */
fun paragraphizeHtml(html: String): String {
    if (html.isBlank()) return html
    return html
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)</p\\s*>"), "</p>\n\n")
        .replace(Regex("(?i)</li\\s*>"), "</li>\n")
        .replace(Regex("(?i)</(h[2-6]|blockquote|pre|table|dl|dt|dd)\\s*>"), "</$1>\n\n")
        .replace(Regex("(?i)<(p|ul|ol|dl|dt|dd|blockquote|pre|table)\\b"), "\n<$1")
        .replace(manyNewlinesRegex, "\n\n")
        .trim()
}

/**
 * Splits a sanitised HTML section into ordinary HTML chunks and phylogenetic trees.
 *
 * Wikipedia renders `{{Clade}}` as nested `<table class="clade">` elements. Compose's
 * `AnnotatedString.fromHtml` silently drops tables, so a plant article in HTML mode lost its
 * tree entirely. Each clade table is converted back to the small `{{clade|...}}` syntax that
 * [com.wikirandomizerp.app.ui.homeScreen.Cladogram] already draws, and the rest stays HTML.
 */
fun splitHtmlCladeBlocks(html: String): List<String> {
    val out = ArrayList<String>()
    var i = 0
    while (i < html.length) {
        val tableStart = findNextCladeTable(html, i)
        if (tableStart < 0) {
            if (i < html.length) out.add(html.substring(i))
            break
        }
        if (tableStart > i) out.add(html.substring(i, tableStart))
        val tagEnd = findTagEnd(html, tableStart)
        val close = if (tagEnd < 0) -1 else findMatchingClose(html, tagEnd + 1, "table")
        if (tagEnd < 0 || close < 0) {
            out.add(html.substring(tableStart))
            break
        }
        val tableHtml = html.substring(tableStart, close + 1)
        out.add(htmlCladeToWikitext(tableHtml) ?: tableHtml)
        i = close + 1
    }
    return out.filter { it.isNotBlank() }
}

private data class HtmlElement(val attributes: String, val inner: String)

/** First `<table class="clade">` at or after [from], or -1. */
private fun findNextCladeTable(html: String, from: Int): Int {
    var i = from
    while (i < html.length) {
        val start = html.indexOf("<table", i, ignoreCase = true)
        if (start < 0) return -1
        val end = findTagEnd(html, start)
        if (end < 0) return -1
        val tag = parseTag(html.substring(start, end + 1))
        if (tag != null && !tag.closing && "clade" in classTokens(tag.attributes)) return start
        i = end + 1
    }
    return -1
}

/**
 * Direct [name] children of [html], descending through wrappers such as `<tbody>` but never into a
 * nested `<table>` (the leaf cells contain the child clades).
 */
private fun directElements(html: String, name: String): List<HtmlElement> {
    val out = ArrayList<HtmlElement>()
    var i = 0
    while (i < html.length) {
        val start = html.indexOf('<', i)
        if (start < 0) break
        if (html.startsWith("<!--", start)) {
            val commentEnd = html.indexOf("-->", start + 4)
            i = if (commentEnd < 0) html.length else commentEnd + 3
            continue
        }
        val end = findTagEnd(html, start)
        if (end < 0) break
        val tag = parseTag(html.substring(start, end + 1))
        if (tag == null || tag.closing || tag.selfClosing || tag.name in voidTags) {
            i = end + 1
            continue
        }
        if (tag.name == name) {
            val close = findMatchingClose(html, end + 1, name)
            if (close < 0) {
                out.add(HtmlElement(tag.attributes, html.substring(end + 1)))
                break
            }
            // findMatchingClose returns the index of the closing tag's '>'. Exclude the whole
            // `</td>`/`</tr>`, otherwise it becomes visible text in the generated clade template.
            val closeTagStart = html.lastIndexOf('<', close)
            out.add(HtmlElement(tag.attributes, html.substring(end + 1, closeTagStart)))
            i = close + 1
        } else if (tag.name == "table") {
            // Never collect rows/cells from a nested clade table.
            val close = findMatchingClose(html, end + 1, tag.name)
            i = if (close < 0) end + 1 else close + 1
        } else {
            // Descend into tbody/thead/tr/div/... to reach the requested element.
            i = end + 1
        }
    }
    return out
}

private fun htmlCladeToWikitext(tableHtml: String): String? {
    val root = parseHtmlCladeTable(tableHtml) ?: return null
    val sb = StringBuilder("{{clade")
    appendCladeChildren(sb, root)
    sb.append("}}")
    return sb.toString()
}

private fun parseHtmlCladeTable(tableHtml: String): CladeNode.Branch? {
    val openEnd = tableHtml.indexOf('>')
    val closeStart = tableHtml.lastIndexOf("</table", ignoreCase = true)
    if (openEnd < 0 || closeStart < 0 || closeStart <= openEnd) return null
    val inner = tableHtml.substring(openEnd + 1, closeStart)
    val rows = directElements(inner, "tr")
    val children = ArrayList<CladeChild>()

    // Wikipedia writes the edge label on the same row as the leaf, but the sublabel is a
    // *separate* row below it (`<tr><td class="clade-slabel">65 Mya</td></tr>`), with the leaf
    // cell using rowspan. The old loop only read a sublabel from the leaf row, so every edge
    // label written that way disappeared. Remember the pending label/sublabel and attach it to
    // the child the previous row created.
    var pendingLabel = ""
    var pendingSublabel = ""

    fun attachPending(label: String, sublabel: String) {
        if (children.isEmpty()) return
        val last = children[children.lastIndex]
        val newLabel = last.label.ifBlank { label }
        val newSublabel = last.sublabel.ifBlank { sublabel }
        if (newLabel != last.label || newSublabel != last.sublabel) {
            children[children.lastIndex] = CladeChild(
                label = newLabel,
                sublabel = newSublabel,
                node = last.node,
                groupLabel = last.groupLabel,
                barColor = last.barColor
            )
        }
    }

    for (row in rows) {
        val cells = directElements(row.inner, "td") + directElements(row.inner, "th")
        var label = ""
        var sublabel = ""
        var leafHtml: String? = null
        for (cell in cells) {
            val classes = classTokens(cell.attributes)
            when {
                "clade-label" in classes -> label = plainText(cell.inner)
                "clade-slabel" in classes -> sublabel = plainText(cell.inner)
                "clade-leaf" in classes -> leafHtml = cell.inner
            }
        }
        val leaf = leafHtml
        if (leaf == null) {
            // Label/sublabel-only row. It belongs to the child created by the row above; if it
            // arrives before any leaf (some templates put the label first), keep it for the next.
            if (children.isNotEmpty() && (label.isNotBlank() || sublabel.isNotBlank())) {
                attachPending(label, sublabel)
            } else {
                if (label.isNotBlank()) pendingLabel = label
                if (sublabel.isNotBlank()) pendingSublabel = sublabel
            }
            continue
        }
        val effectiveLabel = label.ifBlank { pendingLabel }
        val effectiveSublabel = sublabel.ifBlank { pendingSublabel }
        pendingLabel = ""
        pendingSublabel = ""

        val nestedStart = findNextCladeTable(leaf, 0)
        val branch = if (nestedStart >= 0) {
            val nestedTagEnd = findTagEnd(leaf, nestedStart)
            val nestedClose =
                if (nestedTagEnd < 0) -1 else findMatchingClose(leaf, nestedTagEnd + 1, "table")
            if (nestedClose < 0) null
            else parseHtmlCladeTable(leaf.substring(nestedStart, nestedClose + 1))
        } else null
        val node: CladeNode = branch ?: CladeNode.Leaf(plainText(leaf))
        if (branch == null && (node as CladeNode.Leaf).text.isBlank()) continue
        children.add(CladeChild(effectiveLabel, effectiveSublabel, node))
    }
    return if (children.isEmpty()) null else CladeNode.Branch(children)
}

private fun appendCladeChildren(sb: StringBuilder, branch: CladeNode.Branch) {
    branch.children.forEachIndexed { index, child ->
        val n = index + 1
        if (child.label.isNotBlank()) {
            sb.append("|label").append(n).append('=').append(cladeText(child.label))
        }
        if (child.sublabel.isNotBlank()) {
            sb.append("|sublabel").append(n).append('=').append(cladeText(child.sublabel))
        }
        sb.append('|').append(n).append('=')
        when (val node = child.node) {
            is CladeNode.Leaf -> sb.append(cladeText(node.text))
            is CladeNode.Branch -> {
                sb.append("{{clade")
                appendCladeChildren(sb, node)
                sb.append("}}")
            }
        }
    }
}

/** Keeps the generated mini-template parseable: label text must not contain argument syntax. */
private fun cladeText(text: String): String = text
    .replace('|', '/')
    .replace('=', '-')
    .replace("{", "")
    .replace("}", "")
    .replace('\n', ' ')
    .trim()

/** Tag-free heading text (also decodes the small set of entities Wikipedia uses in headings). */
internal fun plainText(html: String): String {
    val withoutTags = html.replace(tagRegex, " ")
    return decodeEntities(withoutTags)
        .replace('\u00a0', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun decodeEntities(text: String): String = entityRegex.replace(text) { match ->
    val entity = match.groupValues[1]
    when {
        entity.equals("amp", true) -> "&"
        entity.equals("lt", true) -> "<"
        entity.equals("gt", true) -> ">"
        entity.equals("quot", true) -> "\""
        entity.equals("apos", true) -> "'"
        entity.equals("nbsp", true) -> " "
        entity.equals("ndash", true) -> "\u2013"
        entity.equals("mdash", true) -> "\u2014"
        entity.startsWith("#x", true) ->
            entity.drop(2).toIntOrNull(16)
                ?.takeIf { Character.isValidCodePoint(it) }
                ?.let { String(Character.toChars(it)) } ?: match.value
        entity.startsWith("#") ->
            entity.drop(1).toIntOrNull()
                ?.takeIf { Character.isValidCodePoint(it) }
                ?.let { String(Character.toChars(it)) } ?: match.value
        else -> match.value
    }
}

// ---------------------------------------------------------------------------
// Infoboxes and taxoboxes in REST HTML
// ---------------------------------------------------------------------------

/** Result of reading one `<table class="infobox …">`. */
internal class HtmlInfoboxScan(
    /** The rows for the infobox card, or null when the table holds nothing but pictures. */
    val box: HtmlInfobox?,
    /** Picture file name → caption, for pictures found in picture rows. */
    val captions: Map<String, String>
)

private class HtmlCell(val name: String, val attributes: String, val inner: String)

private val infoboxSpaceRegex = Regex("[ \\t\\r\\n]+")
private val infoboxDoubleSpaceRegex = Regex(" {2,}")
private val infoboxBreakRunRegex = Regex("(?:\\s*<br>\\s*)+")
private val infoboxNestedListRegex = Regex("(?i)<(?:ul|ol)\\b")
private val infoboxCaptionStartRegex =
    Regex("(?is)^\\s*<(?:div|span|small|p)\\b[^>]*(?:font-size:\\s*88%|caption)")
private val infoboxBracketLabelRegex = Regex("^\\[[^\\]\\s]{1,12}\\]$")
private val cssBackgroundRegex = Regex("background(?:-color)?:([^;]+)")
private val cssRgbRegex = Regex("rgba?\\((\\d{1,3}),(\\d{1,3}),(\\d{1,3})")
private val cssHexRegex = Regex("#([0-9a-f]{6}|[0-9a-f]{3})(?![0-9a-f])")

/** True for an element that is never reading content (icons, edit links, navbars, hidden). */
private fun isInfoboxDropped(tag: HtmlTag): Boolean {
    if (tag.name in infoboxDroppedTags) return true
    if (classTokens(tag.attributes).any { it in infoboxDroppedClasses }) return true
    if (infoboxStyle(tag.attributes).contains("display:none")) return true
    val typeOf = attributeValue(tag.attributes, typeofAttrRegex) ?: ""
    return typeOf.startsWith("mw:File", ignoreCase = true)
}

/** Elements inside an infobox cell that are never reading content. */
private val infoboxDroppedTags = setOf(
    "style", "script", "noscript", "link", "meta", "img", "input", "iframe", "audio", "video",
    "source", "track", "object", "embed", "svg", "math", "annotation", "figure", "template"
)

/** Edit pencils ("Edit this classification"), navbars (view/talk/edit) and icon fonts. */
private val infoboxDroppedClasses = setOf(
    "noprint", "mw-editsection", "navbar", "mw-ui-icon", "mw-empty-elt", "nomobile"
)

/** Block-level elements: they start a new line inside a cell. */
private val infoboxBreakTags = setOf(
    "div", "p", "li", "ul", "ol", "dl", "dt", "dd", "tr", "table", "tbody", "thead", "tfoot",
    "caption", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "center", "section"
)

/** The handful of colour names Wikipedia's taxobox and infobox headers use. */
private val cssNamedColors: Map<String, Int> = mapOf(
    "lightgreen" to 0x90EE90, "palegreen" to 0x98FB98, "pink" to 0xFFC0CB,
    "lightblue" to 0xADD8E6, "lightgray" to 0xD3D3D3, "lightgrey" to 0xD3D3D3,
    "lavender" to 0xE6E6FA, "khaki" to 0xF0E68C, "gold" to 0xFFD700, "orange" to 0xFFA500,
    "lightyellow" to 0xFFFFE0, "lightcyan" to 0xE0FFFF, "lightsalmon" to 0xFFA07A,
    "wheat" to 0xF5DEB3, "tan" to 0xD2B48C, "silver" to 0xC0C0C0, "gainsboro" to 0xDCDCDC,
    "white" to 0xFFFFFF, "beige" to 0xF5F5DC, "thistle" to 0xD8BFD8, "plum" to 0xDDA0DD,
    "skyblue" to 0x87CEEB, "powderblue" to 0xB0E0E6, "aquamarine" to 0x7FFFD4,
    "yellowgreen" to 0x9ACD32, "darkgray" to 0xA9A9A9, "darkgrey" to 0xA9A9A9
)

/**
 * Reads an `<table class="infobox …">` fragment.
 *
 * Wikipedia lays a taxobox out as one table:
 * ```
 * <tr><th colspan=2 style="background-color: rgb(180,250,180)"><i>Echeveria agavoides</i></th></tr>
 * <tr><td colspan=2><img …><div style="font-size: 88%">Lower photo: …</div></td></tr>
 * <tr><th colspan=2 style="…">Scientific classification <edit link></th></tr>
 * <tr><td>Kingdom:</td><td><a href="./Plant">Plantae</a></td></tr>
 * …
 * <tr><th colspan=2>Binomial name</th></tr>
 * <tr><td colspan=2><b><i>Echeveria agavoides</i></b><br>Lem.</td></tr>
 * <tr><th colspan=2>Synonyms<sup class="reference">[1]</sup></th></tr>
 * <tr><td colspan=2><ul><li>…</li></ul></td></tr>
 * ```
 * Picture rows are not part of the card: [extractHtmlMediaBlocks] emits them as `[[File:…]]`
 * blocks, so they keep their lead-image de-duplication and captions.
 */
internal fun scanHtmlInfobox(tableHtml: String): HtmlInfoboxScan {
    val empty = HtmlInfoboxScan(null, emptyMap())
    val openEnd = findTagEnd(tableHtml, 0)
    val closeStart = tableHtml.lastIndexOf("</table", ignoreCase = true)
    if (openEnd < 0 || closeStart <= openEnd) return empty
    val openTag = parseTag(tableHtml.substring(0, openEnd + 1)) ?: return empty
    val inner = tableHtml.substring(openEnd + 1, closeStart)
    val taxobox = "biota" in classTokens(openTag.attributes)

    val captions = LinkedHashMap<String, String>()
    val rows = ArrayList<HtmlInfoboxRow>()
    var title = ""
    var titleColor: Int? = null
    var sawContent = false

    val captionElement = directElements(inner, "caption").firstOrNull()
    if (captionElement != null) {
        val captionHtml = infoboxInline(captionElement.inner)
        if (hasVisibleText(captionHtml)) {
            title = captionHtml
            titleColor = cssBackgroundColor(infoboxStyle(captionElement.attributes))
            sawContent = true
        }
    }

    val trs = directElements(inner, "tr")
    var r = 0
    while (r < trs.size && rows.size < INFOBOX_MAX_ROWS) {
        val row = trs[r]
        r++
        val rowStyle = infoboxStyle(row.attributes)
        if (rowStyle.contains("display:none") || "mw-empty-elt" in classTokens(row.attributes)) {
            continue
        }
        val cells = directCells(row.inner)
        if (cells.isEmpty()) continue

        if (cells.size == 1) {
            val cell = cells[0]

            // Picture row: the picture itself is drawn by ImageWithCaption, outside the card.
            val pictures = infoboxPictureNames(cell.inner)
            if (pictures.isNotEmpty()) {
                sawContent = true
                // Everything that is not a picture is the caption (picture markup is dropped).
                var captionText = plainText(infoboxInline(cell.inner))
                if (captionText.isBlank() && r < trs.size) {
                    val nextRow = trs[r]
                    val nextCells = directCells(nextRow.inner)
                    if (nextCells.size == 1 &&
                        infoboxPictureNames(nextCells[0].inner).isEmpty() &&
                        looksLikeCaptionCell(nextCells[0], nextRow.attributes)
                    ) {
                        captionText = plainText(infoboxInline(nextCells[0].inner))
                        r++ // the caption row belongs to the picture above it
                    }
                }
                if (captionText.isNotBlank()) {
                    // A collage shares one caption: the first picture keeps it.
                    val first = pictures[0]
                    if (!captions.containsKey(first)) captions[first] = captionText
                }
                continue
            }

            val classes = classTokens(cell.attributes)
            val isHeader = cell.name == "th" || "infobox-header" in classes ||
                    "infobox-subheader" in classes || "infobox-above" in classes
            if (isHeader) {
                val html = infoboxInline(cell.inner)
                if (!hasVisibleText(html)) continue
                val color = cssBackgroundColor(infoboxStyle(cell.attributes))
                    ?: cssBackgroundColor(rowStyle)
                if (title.isEmpty() && !sawContent && !looksLikeClassificationHeader(html)) {
                    // The name band above everything else.
                    title = html
                    titleColor = color
                } else {
                    rows.add(HtmlInfoboxRow.Header(color, html))
                }
                sawContent = true
                continue
            }

            if (addInfoboxWide(rows, cell.inner)) sawContent = true
            continue
        }

        // Label cell + value cell(s).
        val labelHtml = infoboxInline(cells[0].inner)
        val valueHtml = infoboxInline(cells.drop(1).joinToString(" ") { it.inner })
        if (!hasVisibleText(valueHtml)) continue
        if (hasVisibleText(labelHtml)) {
            rows.add(HtmlInfoboxRow.Field(labelHtml, valueHtml))
        } else {
            rows.add(HtmlInfoboxRow.Wide(valueHtml))
        }
        sawContent = true
    }

    // A table with nothing but a title, pictures and bands has nothing worth a card.
    val hasBody = rows.any { it !is HtmlInfoboxRow.Header }
    val box = if (hasBody) HtmlInfobox(taxobox, titleColor, title, rows) else null
    return HtmlInfoboxScan(box, captions)
}

private const val INFOBOX_MAX_ROWS = 250

/** Gives an empty-caption `[[File:name|]]` line the caption found in the infobox rows. */
private fun withInfoboxCaption(line: String, captions: Map<String, String>?): String {
    if (captions == null || captions.isEmpty()) return line
    if (!line.startsWith("[[File:") || !line.endsWith("]]")) return line
    val body = line.substring("[[File:".length, line.length - 2)
    val bar = body.indexOf('|')
    if (bar < 0) return line
    val name = body.substring(0, bar)
    val caption = body.substring(bar + 1)
    if (caption.isNotBlank()) return line
    val found = captions[name] ?: return line
    return pictureLine(name, found)
}

private fun infoboxStyle(attributes: String): String =
    (attributeValue(attributes, styleAttrRegex) ?: "").replace(" ", "").lowercase()

private fun looksLikeClassificationHeader(html: String): Boolean =
    plainText(html).contains("classification", ignoreCase = true)

private fun looksLikeCaptionCell(cell: HtmlCell, rowAttributes: String): Boolean {
    if (classTokens(cell.attributes).any { it.contains("caption") }) return true
    if (infoboxStyle(cell.attributes).contains("font-size:88%")) return true
    if (infoboxStyle(rowAttributes).contains("font-size:88%")) return true
    return infoboxCaptionStartRegex.containsMatchIn(cell.inner)
}

/** File names of the real pictures (not flags or icons) in [inner]. */
private fun infoboxPictureNames(inner: String): List<String> {
    val names = ArrayList<String>()
    for (img in imgTagRegex.findAll(inner)) {
        val name = htmlImageName(img.value) ?: continue
        if (name !in names) names.add(name)
    }
    return names
}

private fun hasVisibleText(html: String): Boolean =
    tagRegex.replace(html, "").replace("&nbsp;", " ").replace('\u00a0', ' ').isNotBlank()

/** Direct `td`/`th` cells of one row, in document order, without entering nested tables. */
private fun directCells(rowHtml: String): List<HtmlCell> {
    val out = ArrayList<HtmlCell>()
    var i = 0
    while (i < rowHtml.length) {
        val start = rowHtml.indexOf('<', i)
        if (start < 0) break
        if (rowHtml.startsWith("<!--", start)) {
            val commentEnd = rowHtml.indexOf("-->", start + 4)
            i = if (commentEnd < 0) rowHtml.length else commentEnd + 3
            continue
        }
        val end = findTagEnd(rowHtml, start)
        if (end < 0) break
        val tag = parseTag(rowHtml.substring(start, end + 1))
        if (tag == null || tag.closing || tag.selfClosing || tag.name in voidTags) {
            i = end + 1
            continue
        }
        if (tag.name == "td" || tag.name == "th") {
            val close = findMatchingClose(rowHtml, end + 1, tag.name)
            if (close < 0) {
                out.add(HtmlCell(tag.name, tag.attributes, rowHtml.substring(end + 1)))
                break
            }
            val closeTagStart = rowHtml.lastIndexOf('<', close)
            out.add(HtmlCell(tag.name, tag.attributes, rowHtml.substring(end + 1, closeTagStart)))
            i = close + 1
        } else if (tag.name == "table") {
            val close = findMatchingClose(rowHtml, end + 1, "table")
            i = if (close < 0) end + 1 else close + 1
        } else {
            i = end + 1
        }
    }
    return out
}

/**
 * Adds the content of a full-width cell. `<li>` elements become separate [HtmlInfoboxRow.Item]
 * rows (the synonym list), the rest becomes one [HtmlInfoboxRow.Wide].
 * @return true when something was added.
 */
private fun addInfoboxWide(rows: MutableList<HtmlInfoboxRow>, cellHtml: String): Boolean {
    var added = false
    for (piece in splitInfoboxItems(cellHtml)) {
        val html = infoboxInline(piece.second)
        if (!hasVisibleText(html)) continue
        if (piece.first) rows.add(HtmlInfoboxRow.Item(html)) else rows.add(HtmlInfoboxRow.Wide(html))
        added = true
    }
    return added
}

/** (isListItem, rawHtml) pieces of a cell, in order. Nested lists are flattened. */
private fun splitInfoboxItems(html: String): List<Pair<Boolean, String>> {
    if (!html.contains("<li", ignoreCase = true)) return listOf(Pair(false, html))
    val out = ArrayList<Pair<Boolean, String>>()
    val buffer = StringBuilder()
    var i = 0
    while (i < html.length) {
        val lt = html.indexOf('<', i)
        if (lt < 0) {
            buffer.append(html, i, html.length)
            break
        }
        if (lt > i) buffer.append(html, i, lt)
        if (html.startsWith("<!--", lt)) {
            val commentEnd = html.indexOf("-->", lt + 4)
            i = if (commentEnd < 0) html.length else commentEnd + 3
            continue
        }
        val end = findTagEnd(html, lt)
        if (end < 0) {
            buffer.append(html, lt, html.length)
            break
        }
        val tag = parseTag(html.substring(lt, end + 1))
        if (tag != null && !tag.closing && !tag.selfClosing && tag.name !in voidTags &&
            isInfoboxDropped(tag)
        ) {
            // A navbar or hidden block must not leak its <li> entries as list items.
            val dropClose = findMatchingClose(html, end + 1, tag.name)
            i = if (dropClose < 0) end + 1 else dropClose + 1
            continue
        }
        if (tag != null && !tag.closing && !tag.selfClosing && tag.name == "li") {
            if (buffer.isNotBlank()) out.add(Pair(false, buffer.toString()))
            buffer.setLength(0)
            val close = findMatchingClose(html, end + 1, "li")
            val itemHtml = if (close < 0) html.substring(end + 1)
            else html.substring(end + 1, html.lastIndexOf('<', close))
            val nested = infoboxNestedListRegex.find(itemHtml)
            if (nested == null) {
                out.add(Pair(true, itemHtml))
            } else {
                val head = itemHtml.substring(0, nested.range.first)
                if (head.isNotBlank()) out.add(Pair(true, head))
                for (sub in splitInfoboxItems(itemHtml.substring(nested.range.first))) {
                    out.add(Pair(true, sub.second))
                }
            }
            i = if (close < 0) html.length else close + 1
            continue
        }
        // Any other tag (ul, ol, div, …) stays in the running text; infoboxInline() treats it as
        // a line break.
        if (tag != null) buffer.append(html, lt, end + 1)
        i = end + 1
    }
    if (buffer.isNotBlank()) out.add(Pair(false, buffer.toString()))
    return out
}

/**
 * Cell HTML reduced to what the card can show: links, bold, italic, sub/superscript, small text
 * and line breaks. Pictures, edit links ("Edit this classification"), navbars, icons and hidden
 * elements are removed; a citation marker becomes a plain `[1]`.
 */
private fun infoboxInline(html: String): String {
    val out = StringBuilder()
    infoboxInlineInto(html, out)
    return finishInline(out.toString())
}

private fun finishInline(raw: String): String {
    var s = infoboxDoubleSpaceRegex.replace(raw, " ")
    s = infoboxBreakRunRegex.replace(s, "<br>")
    while (true) {
        val t = s.trim().removePrefix("<br>").removeSuffix("<br>").trim()
        if (t == s) break
        s = t
    }
    return s
}

private fun appendInlineText(out: StringBuilder, raw: String) {
    if (raw.isEmpty()) return
    out.append(infoboxSpaceRegex.replace(raw, " "))
}

private fun appendInlineBreak(out: StringBuilder) {
    if (out.isEmpty()) return
    if (out.endsWith("<br>")) return
    out.append("<br>")
}

private fun infoboxInlineInto(html: String, out: StringBuilder) {
    var i = 0
    while (i < html.length) {
        val lt = html.indexOf('<', i)
        if (lt < 0) {
            appendInlineText(out, html.substring(i))
            return
        }
        if (lt > i) appendInlineText(out, html.substring(i, lt))
        if (html.startsWith("<!--", lt)) {
            val commentEnd = html.indexOf("-->", lt + 4)
            i = if (commentEnd < 0) html.length else commentEnd + 3
            continue
        }
        val end = findTagEnd(html, lt)
        if (end < 0) {
            appendInlineText(out, html.substring(lt))
            return
        }
        val tag = parseTag(html.substring(lt, end + 1))
        if (tag == null) {
            i = end + 1
            continue
        }
        if (tag.closing) {
            if (tag.name in infoboxBreakTags) appendInlineBreak(out)
            else if (tag.name == "td" || tag.name == "th") appendInlineText(out, " ")
            i = end + 1
            continue
        }

        val name = tag.name
        val classes = classTokens(tag.attributes)
        val isVoid = tag.selfClosing || name in voidTags

        if (name == "br") {
            appendInlineBreak(out)
            i = end + 1
            continue
        }

        val typeOf = attributeValue(tag.attributes, typeofAttrRegex) ?: ""
        if (isInfoboxDropped(tag)) {
            if (isVoid) {
                i = end + 1
            } else {
                val close = findMatchingClose(html, end + 1, name)
                i = if (close < 0) end + 1 else close + 1
            }
            continue
        }

        // Citation marker: keep the visible "[1]" but not the link to the reference list.
        if (name == "sup" && ("reference" in classes || typeOf.contains("mw:Extension/ref"))) {
            val close = if (isVoid) -1 else findMatchingClose(html, end + 1, name)
            if (close >= 0) {
                val markerHtml = html.substring(end + 1, html.lastIndexOf('<', close))
                val label = decodeEntities(tagRegex.replace(markerHtml, ""))
                    .replace('\u00a0', ' ').trim()
                if (infoboxBracketLabelRegex.matches(label)) {
                    out.append("<sup>").append(label).append("</sup>")
                }
                i = close + 1
            } else {
                i = end + 1
            }
            continue
        }

        if (name == "a") {
            val close = if (isVoid) -1 else findMatchingClose(html, end + 1, name)
            if (close < 0) {
                i = end + 1
                continue
            }
            val linkInner = html.substring(end + 1, html.lastIndexOf('<', close))
            i = close + 1
            val rawHref = (attributeValue(tag.attributes, hrefAttrRegex) ?: "").trim()
            val isRedLink = rawHref.contains("redlink=1")
            // "Edit this classification", "view · talk · edit": navigation, not content.
            if (rawHref.contains("action=edit") && !isRedLink) continue
            val linkText = StringBuilder()
            infoboxInlineInto(linkInner, linkText)
            val text = linkText.toString()
            if (!hasVisibleText(text)) {
                out.append(text)
                continue
            }
            val linkable = rawHref.isNotEmpty() && !rawHref.startsWith("#") && !isRedLink
            if (linkable) {
                out.append("<a href=\"").append(rawHref.replace("\"", "%22")).append("\">")
                out.append(text).append("</a>")
            } else {
                out.append(text)
            }
            continue
        }

        val mapped = when (name) {
            "b", "strong" -> "b"
            "i", "em", "cite", "dfn" -> "i"
            "sup" -> "sup"
            "sub" -> "sub"
            "small" -> "small"
            else -> null
        }
        if (mapped != null) {
            val close = if (isVoid) -1 else findMatchingClose(html, end + 1, name)
            if (close < 0) {
                i = end + 1
                continue
            }
            val innerHtml = html.substring(end + 1, html.lastIndexOf('<', close))
            val innerOut = StringBuilder()
            infoboxInlineInto(innerHtml, innerOut)
            val text = innerOut.toString()
            if (hasVisibleText(text)) {
                out.append('<').append(mapped).append('>').append(text)
                out.append("</").append(mapped).append('>')
            } else {
                out.append(text)
            }
            i = close + 1
            continue
        }

        // Any other tag is only a wrapper: its text is kept, block elements start a new line and
        // table cells (a nested table) are separated by a space.
        if (name in infoboxBreakTags) appendInlineBreak(out)
        else if (name == "td" || name == "th") appendInlineText(out, " ")
        i = end + 1
    }
}

/** `background-color` / `background` of an inline style as 0xRRGGBB (style is already compact). */
private fun cssBackgroundColor(style: String): Int? {
    val value = cssBackgroundRegex.find(style)?.groupValues?.get(1) ?: return null
    val rgb = cssRgbRegex.find(value)
    if (rgb != null) {
        val r = rgb.groupValues[1].toInt().coerceIn(0, 255)
        val g = rgb.groupValues[2].toInt().coerceIn(0, 255)
        val b = rgb.groupValues[3].toInt().coerceIn(0, 255)
        return (r shl 16) or (g shl 8) or b
    }
    val hex = cssHexRegex.find(value)
    if (hex != null) {
        val digits = hex.groupValues[1]
        val full = if (digits.length == 3) {
            "" + digits[0] + digits[0] + digits[1] + digits[1] + digits[2] + digits[2]
        } else digits
        return full.toInt(16)
    }
    return cssNamedColors[value.trim().removeSuffix("!important")]
}
