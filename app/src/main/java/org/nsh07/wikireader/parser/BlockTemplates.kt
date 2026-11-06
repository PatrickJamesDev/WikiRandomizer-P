package org.nsh07.wikireader.parser

/*
 * Block-level templates that the article renderer draws specially instead of as running text:
 *
 *  - picture templates ({{Multiple image}}, {{Wide image}}, {{Gallery}}, ...): their pictures were
 *    silently dropped before, because the text converter only understands plain [[File:...]] links;
 *  - {{Clade}} trees: the phylogenetic trees ("cladograms") that the desktop site draws.
 *
 * Everything here is plain Kotlin string work (no Compose), so it is cheap and cannot crash the UI:
 * callers fall back to the old behaviour whenever a function returns null / an empty list.
 */

// ---------------------------------------------------------------------------
// Pre-compiled regexes
// ---------------------------------------------------------------------------
private val imageFileRegex = Regex(
    ".+\\.(jpe?g|png|gif|svg|webp|tiff?|bmp|jfif|apng)",
    RegexOption.IGNORE_CASE
)
private val cladeCommentRegex = Regex("<!--.*?-->", setOf(RegexOption.DOT_MATCHES_ALL))
private val imageKeyRegex = Regex("(image|file|photo|img)(\\d*[a-z]?)")
private val captionKeyRegex = Regex("caption(\\d*[a-z]?)")
private val skipArgRegex = Regex(
    "(left|right|center|centre|none|thumb|thumbnail|frameless|frame|border|\\d+(px)?|\\d*x\\d+px|upright.*)",
    RegexOption.IGNORE_CASE
)
private val refTagRegex = Regex(
    "<ref[^>]*/>|<ref[^>]*>.*?</ref>",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)
private val simpleTemplateRegex = Regex("\\{\\{[^\\{\\}]*\\}\\}")
private val cladeNumRegex = Regex("(grouplabel|sublabel|label|bar|state)?(\\d+)")

/**
 * Names that start a clade branch. Besides `{{clade}}` and `{{cladogram}}`, Wikipedia uses
 * wrappers such as `{{clade sequential}}` or `{{clade gallery}}`; those used to be treated as
 * plain leaf text, which hid large parts of real trees.
 */
private fun isCladeTemplateName(name: String): Boolean =
    name == "clade" || name == "cladogram" || name.startsWith("clade ")

/** Named clade parameters that are formatting, never child taxa. */
private val cladeIgnoredKeys = hashSetOf(
    "style", "caption", "align", "width", "state", "expanded", "navbar", "title",
    "font-size", "line-height", "background", "border", "class", "float"
)

/** Names (lower case) of the templates whose pictures we extract. */
private val imageTemplateNames = hashSetOf(
    "multiple image", "multiple images", "double image", "triple image", "quad image",
    "image array", "photomontage", "panorama", "wide image", "gallery", "image gallery",
    "image frame"
)

/** Pictures + caption(s) ready for the renderer; [length] is how much wikitext they replace. */
class BlockTemplate(val length: Int, val blocks: List<String>)

// ---------------------------------------------------------------------------
// Small wikitext helpers
// ---------------------------------------------------------------------------

/** Splits template arguments on every `|` that is not inside `[[..]]`, `{{..}}` or `[..]`. */
fun splitTemplateArgs(body: String): List<String> {
    val out = ArrayList<String>()
    val cur = StringBuilder()
    var depth = 0
    for (c in body) {
        if (c == '[' || c == '{') depth++
        else if ((c == ']' || c == '}') && depth > 0) depth--

        if (c == '|' && depth == 0) {
            out.add(cur.toString())
            cur.setLength(0)
        } else cur.append(c)
    }
    out.add(cur.toString())
    return out
}

/** `key=value` (the first `=` outside brackets), or null for a positional argument. */
fun splitKeyValue(arg: String): Pair<String, String>? {
    var depth = 0
    for (i in arg.indices) {
        val c = arg[i]
        if (c == '[' || c == '{') depth++
        else if ((c == ']' || c == '}') && depth > 0) depth--

        if (c == '=' && depth == 0) {
            val key = arg.substring(0, i).trim()
            if (key.isNotEmpty() && key.length <= 24 &&
                key.none { it == '[' || it == '{' || it == '<' }
            ) return Pair(key, arg.substring(i + 1).trim())
            return null
        }
    }
    return null
}

/** Lower-cased name of the template starting at [index] (`{{Multiple image|..` -> `multiple image`). */
fun templateNameAt(text: String, index: Int): String {
    var end = index + 2
    val limit = minOf(text.length, index + 60)
    while (end < limit && text[end] != '|' && text[end] != '}' && text[end] != '\n') end++
    if (index + 2 > end) return ""
    return text.substring(index + 2, end).trim().lowercase().replace('_', ' ')
}

/** Cheap pre-check used on every top-level `{{`: is this one of the templates handled here? */
fun isBlockTemplateStart(text: String, index: Int): Boolean {
    val name = templateNameAt(text, index)
    return isCladeTemplateName(name) || name in imageTemplateNames
}

private fun plainCaption(raw: String): String {
    var s = raw.replace(refTagRegex, "")
        .replace("<br>", " ").replace("<br/>", " ").replace("<br />", " ")
    repeat(3) {
        s = simpleTemplateRegex.replace(s) { m ->
            m.value.removePrefix("{{").removeSuffix("}}").substringAfterLast('|')
        }
    }
    // A bare pipe would split the generated `[[File:name|caption]]` block, but a pipe inside a
    // link (`[[Echeveria|the plant]]`) is meaningful and must be kept. `splitTemplateArgs` only
    // splits on top-level pipes, so joining its parts replaces just the dangerous ones.
    if (s.contains('|')) s = splitTemplateArgs(s).joinToString(" ")
    return s.replace('\n', ' ').trim()
}

/** `[[File:Foo.jpg|..]]`, `File:Foo.jpg`, `Foo.jpg` -> `Foo.jpg`; null if it is not a picture name. */
private fun cleanFileName(value: String): String? {
    var v = value.trim()
    if (v.startsWith("[[")) v = v.removePrefix("[[").substringBefore("]]")
    v = v.substringBefore('|').trim()
    if (v.contains(':')) v = v.substringAfter(':').trim()
    return if (v.isNotEmpty() && imageFileRegex.matches(v)) v else null
}

/** Finds `[[File:..|caption]]` links inside [arg] and adds (file, caption) pairs to [out]. */
private fun linkImages(arg: String, out: MutableList<Pair<String, String>>) {
    var from = 0
    while (true) {
        val start = arg.indexOf("[[", from)
        if (start < 0) return
        val link = arg.substringMatchingParen('[', ']', start)
        from = start + maxOf(link.length, 2)
        val parts = link.removePrefix("[[").removeSuffix("]]").split('|')
        val head = parts[0]
        if (!head.contains(':')) continue
        val name = cleanFileName(head) ?: continue
        val caption = parts.drop(1)
            .map { it.trim() }
            .filterNot {
                skipArgRegex.matches(it) || it.startsWith("alt=") ||
                        it.startsWith("link=") || it.startsWith("class=")
            }
            .lastOrNull() ?: ""
        out.add(Pair(name, plainCaption(caption)))
    }
}

// ---------------------------------------------------------------------------
// Picture templates
// ---------------------------------------------------------------------------

/** Every picture (file name, caption) a picture template contains, in order. */
fun extractTemplateImages(template: String): List<Pair<String, String>> {
    val inner = template.trim().removePrefix("{{").removeSuffix("}}")
    val args = splitTemplateArgs(inner).drop(1) // the first one is the template name

    val names = ArrayList<String>()
    val suffixes = ArrayList<String>()
    val captions = HashMap<String, String>()
    val linked = ArrayList<Pair<String, String>>()
    var lastPositional = -1

    for (raw in args) {
        val arg = raw.trim()
        if (arg.isEmpty()) continue
        val kv = splitKeyValue(arg)
        if (kv != null) {
            val key = kv.first.lowercase().replace(" ", "").replace("_", "")
            val value = kv.second
            val img = imageKeyRegex.matchEntire(key)
            val cap = captionKeyRegex.matchEntire(key)
            if (img != null) {
                val n = cleanFileName(value)
                if (n != null) {
                    names.add(n)
                    suffixes.add(img.groupValues[2])
                } else if (value.contains("[[")) linkImages(value, linked)
            } else if (cap != null) {
                captions[cap.groupValues[1]] = value
            } else if (key == "footer") {
                captions["footer"] = value
            } else if (value.contains("[[")) {
                linkImages(value, linked) // {{Image frame|content=[[File:..]]}} and similar
            }
        } else if (arg.contains("[[")) {
            linkImages(arg, linked)
        } else {
            val n = cleanFileName(arg)
            if (n != null) {
                names.add(n)
                suffixes.add("#" + names.size)
                lastPositional = names.size - 1
            } else if (lastPositional >= 0 && !skipArgRegex.matches(arg)) {
                val k = "#" + (lastPositional + 1)
                if (!captions.containsKey(k)) captions[k] = arg
            }
        }
    }

    val result = ArrayList<Pair<String, String>>()
    for (k in names.indices) {
        result.add(Pair(names[k], plainCaption(captions[suffixes[k]] ?: "")))
    }
    result.addAll(linked)

    // {{Image frame|content=[[File:X.png|300px]]|caption=...}}: the caption is a template argument,
    // not part of the link. Only unambiguous for a single picture.
    val general = captions[""]
    if (general != null && result.size == 1 && result[0].second.isEmpty()) {
        result[0] = Pair(result[0].first, plainCaption(general))
    }

    val footer = captions["footer"]
    if (footer != null && result.isNotEmpty() && result.last().second.isEmpty()) {
        val last = result.removeAt(result.lastIndex)
        result.add(Pair(last.first, plainCaption(footer)))
    }
    return result.distinctBy { it.first }
}

/** File-link prefixes that denote an embedded picture. */
private val fileLinkPrefixRegex = Regex("^\\[\\[(file|image)\\s*:", RegexOption.IGNORE_CASE)

/** Parameters of a `[[File:...]]` link that are formatting, not caption text. */
private val skipImageLinkArgRegex = Regex(
    "(thumb|thumbnail|frame|frameless|border|baseline|sub|super|center|centre|left|right|" +
            "upright.*|\\d+\\s*x?\\s*\\d*\\s*px|alt=.*|link=.*|page=.*|class=.*|lang=.*|" +
            "start=.*|end=.*|thumbtime=.*|upright)",
    RegexOption.IGNORE_CASE
)

/**
 * Converts one `[[File:Name.jpg|thumb|upright=1.2|alt=...|A caption]]` link into the compact
 * picture block the renderer understands: `[[File:Name.jpg|A caption]]`.
 *
 * Returns null when [link] is not a picture link. The whole caption (not just the first
 * parameter) is kept, so captions containing links or templates no longer become empty or
 * truncated. A `class=skin-invert-image` marker is preserved as a trailing `|invert` flag.
 */
fun wikitextFileLinkBlock(link: String): String? {
    if (!fileLinkPrefixRegex.containsMatchIn(link)) return null
    val inner = link.removePrefix("[[").let { if (it.endsWith("]]")) it.dropLast(2) else it }
    val parts = splitTemplateArgs(inner)
    val head = parts.firstOrNull()?.trim().orEmpty()
    val name = head.substringAfter(':').trim()
    if (name.isEmpty() || !imageFileRegex.matches(name)) return null

    val rawArgs = parts.drop(1).map { it.trim() }
    val invert = rawArgs.any {
        it.replace(" ", "").contains("class=skin-invert-image", ignoreCase = true)
    }
    val caption = rawArgs
        .filterNot { it.isEmpty() || skipImageLinkArgRegex.matches(it) }
        .joinToString(" ")
        .replace(Regex("\\s+"), " ")
        .replace('\n', ' ')
        .trim()

    val suffix = if (invert) "|invert]]" else "]]"
    return "[[File:" + name + "|" + caption + suffix
}

/** One picture block in the format the renderer expects ([ParsedBodyText] / [ImageWithCaption]). */
fun pictureBlock(name: String, caption: String): String =
    "[[File:" + name + "|" + caption + "]]"


/** A `<gallery>` block (no closing tag, like the ones the article parser produces). */
fun galleryBlock(images: List<Pair<String, String>>): String =
    "<gallery>\n" + images.joinToString("\n") { "File:" + it.first + "|" + it.second }

/**
 * The first picture of an `<imagemap>` block (the pictures are drawn without the clickable
 * areas), or null if it has none.
 */
fun imagemapBlock(body: String): String? {
    val line = body.lineSequence().map { it.trim() }.firstOrNull {
        it.startsWith("File:", true) || it.startsWith("Image:", true)
    } ?: return null
    val parts = line.split('|')
    val name = cleanFileName(parts[0]) ?: return null
    val caption = parts.drop(1).map { it.trim() }
        .filterNot { skipArgRegex.matches(it) || it.startsWith("alt=") || it.startsWith("link=") }
        .lastOrNull() ?: ""
    return pictureBlock(name, plainCaption(caption))
}

/**
 * If the template at [index] is one we draw specially, returns its renderer blocks and how many
 * characters it spans; null means "not handled, treat it as ordinary text like before".
 */
fun blockTemplateAt(text: String, index: Int): BlockTemplate? {
    val name = templateNameAt(text, index)
    val tpl = text.substringMatchingParen('{', '}', index)
    if (tpl.length < 5 || tpl.length > 40_000 || !tpl.endsWith("}}")) return null

    if (isCladeTemplateName(name)) {
        val start = if (name == "clade") 0 else tpl.indexOf("{{clade", ignoreCase = true)
        if (start < 0) return null
        val clade = if (start == 0) tpl else tpl.substringMatchingParen('{', '}', start)
        return if (parseClade(clade) != null) BlockTemplate(tpl.length, listOf(clade)) else null
    }

    val images = extractTemplateImages(tpl)
    if (images.isEmpty()) return null
    val block = if (images.size == 1) pictureBlock(images[0].first, images[0].second)
    else galleryBlock(images)
    return BlockTemplate(tpl.length, listOf(block))
}

/**
 * Clade templates found anywhere inside a table.
 *
 * Plant and animal articles usually put their cladogram in a `{| ... |}` layout table (sometimes
 * several cells with one clade each). The table renderer swallowed the whole block, so the clade
 * never reached [Cladogram] and the tree disappeared. This extracts the clade templates so they can
 * be rendered as trees instead of being lost with the table markup.
 */
fun extractCladeBlocks(text: String): List<String> =
    splitCladeTemplates(text).filter { it.first }.map { it.second }

/**
 * Splits [text] into alternating ordinary text and clade-template pieces.
 *
 * Each element is `true` for a clade template (the string is the `{{clade…}}` itself) and
 * `false` for the surrounding text. Unlike [extractCladeBlocks] this keeps the text between
 * trees, so a clade written inside a paragraph or another template can be lifted out without
 * losing the prose around it. `{{cladogram|…}}` wrappers yield the inner `{{clade…}}`.
 */
fun splitCladeTemplates(text: String): List<Pair<Boolean, String>> {
    if (text.indexOf("{{", 0) < 0) return listOf(false to text)
    val out = ArrayList<Pair<Boolean, String>>()
    var i = 0
    var last = 0
    while (i < text.length) {
        val start = text.indexOf("{{", i)
        if (start < 0) break
        val name = templateNameAt(text, start)
        if (isCladeTemplateName(name)) {
            val tpl = text.substringMatchingParen('{', '}', start)
            if (tpl.length >= 5 && tpl.endsWith("}}")) {
                val cladeStart =
                    if (name == "clade") 0 else tpl.indexOf("{{clade", ignoreCase = true)
                val clade = when {
                    cladeStart == 0 -> tpl
                    cladeStart > 0 -> tpl.substringMatchingParen('{', '}', cladeStart)
                    else -> ""
                }
                if (clade.length >= 5 && parseClade(clade) != null) {
                    if (start > last) out.add(false to text.substring(last, start))
                    out.add(true to clade)
                    last = start + tpl.length
                    i = last
                    continue
                }
            }
        }
        i = start + 2
    }
    if (last < text.length) out.add(false to text.substring(last))
    return out
}

/** One picture embedded in an infobox/taxobox template. */
data class InfoboxPicture(val name: String, val caption: String)

private val infoboxImageKeyRegex =
    Regex("(?:image|photo|img|logo|map|picture)(\\d*)", RegexOption.IGNORE_CASE)
private val infoboxCaptionKeyRegex =
    Regex("(?:image|photo|img|logo|map|picture)?(\\d*)caption(\\d*)", RegexOption.IGNORE_CASE)
private val infoboxMetaKeyRegex =
    Regex(
        "(?:image|photo|img|logo|map|picture)?(?:alt|size|upright|width)(\\d*)" +
                "|(?:image|photo|img|logo|map|picture)(\\d*)",
        RegexOption.IGNORE_CASE
    )

/**
 * Pictures and captions from an infobox template such as `{{Speciesbox}}`, `{{Taxobox}}` or
 * `{{Infobox …}}`.
 *
 * Infobox pictures used to be thrown away with the image parameters (or printed as raw
 * `File:…` text). They are the only pictures on many species articles, so they are lifted out
 * and rendered like any other article picture, with their captions.
 */
fun infoboxPictures(template: String): List<InfoboxPicture> {
    if (template.length < 5 || !template.startsWith("{{")) return emptyList()
    val inner = template.trim().removePrefix("{{").removeSuffix("}}")
    val args = splitTemplateArgs(inner).drop(1)
    val names = LinkedHashMap<String, String>()
    val captions = HashMap<String, String>()
    val positional = ArrayList<Pair<String, String>>()

    fun normalise(key: String) =
        key.lowercase().replace(" ", "").replace("_", "").replace("-", "")

    for (raw in args) {
        val arg = raw.trim()
        if (arg.isEmpty()) continue
        val kv = splitKeyValue(arg)
        if (kv == null) {
            // A picture may be positional in older taxoboxes.
            if (arg.startsWith("[[", ignoreCase = false)) {
                val block = wikitextFileLinkBlock(arg) ?: continue
                val parts = block.removePrefix("[[File:").removeSuffix("]]").split('|')
                val name = cleanFileName(parts.first()) ?: continue
                positional.add(name to plainCaption(parts.drop(1).joinToString("|").trim()))
            }
            continue
        }
        val key = normalise(kv.first)
        val value = kv.second.trim()
        if (value.isEmpty()) continue

        val captionMatch = infoboxCaptionKeyRegex.matchEntire(key)
        if (captionMatch != null) {
            // `image2_caption` puts the number before "caption"; `image caption 2` after it.
            val index = captionMatch.groupValues[1]
                .ifBlank { captionMatch.groupValues[2] }
                .ifBlank { "1" }
            val caption = plainCaption(value)
            // `image caption` is more specific than a bare `caption`; keep it even when the
            // generic one appears later in the template.
            val specific = key.startsWith("image") || key.startsWith("photo") ||
                    key.startsWith("img") || key.startsWith("logo") ||
                    key.startsWith("map") || key.startsWith("picture")
            if (specific) captions[index] = caption
            else captions.putIfAbsent(index, caption)
            continue
        }
        val imageMatch = infoboxImageKeyRegex.matchEntire(key)
        if (imageMatch != null) {
            val index = imageMatch.groupValues[1].ifBlank { "1" }
            val link = if (value.startsWith("[[", ignoreCase = false)) {
                wikitextFileLinkBlock(value)
            } else null
            if (link != null) {
                val parts = link.removePrefix("[[File:").removeSuffix("]]").split('|')
                val name = cleanFileName(parts.first()) ?: continue
                names.putIfAbsent(index, name)
                val caption = plainCaption(parts.drop(1).joinToString("|").trim())
                if (caption.isNotBlank()) captions.putIfAbsent(index, caption)
            } else {
                // `image = X.jpg`, `image = File:X.jpg` or `image = [[File:X.jpg]]`.
                val name = cleanFileName(value) ?: continue
                names[index] = name
            }
            continue
        }
        // alt/size/upright/width and similar formatting parameters are not pictures.
        if (infoboxMetaKeyRegex.matches(key)) continue
    }

    val out = ArrayList<InfoboxPicture>(names.size + positional.size)
    names.forEach { (index, name) ->
        out.add(InfoboxPicture(name, plainCaption(captions[index].orEmpty())))
    }
    positional.forEach { (name, caption) -> out.add(InfoboxPicture(name, caption)) }
    return out.distinctBy { it.name }
}

/**
 * The first picture of an infobox, used to caption the lead image that the API returns
 * separately from the article text.
 */
fun leadInfoboxPicture(template: String): InfoboxPicture? =
    infoboxPictures(template).firstOrNull { it.name.isNotBlank() }

// ---------------------------------------------------------------------------
// Phylogenetic trees ({{Clade}})
// ---------------------------------------------------------------------------

sealed class CladeNode {
    /** A taxon: [text] is wikitext (italics, links, {{extinct}} ...). */
    class Leaf(val text: String) : CladeNode()

    class Branch(val children: List<CladeChild>) : CladeNode()
}

/** One child of a branch with the labels written on the line leading to it. */
class CladeChild(
    val label: String,
    val sublabel: String,
    val node: CladeNode,
    /** Optional `grouplabelN` shown beside a whole group of descendants. */
    val groupLabel: String = "",
    /** Optional `barN` colour (a CSS colour name or hex value). */
    val barColor: String = ""
)

/** Parses `{{clade |label1=.. |1=.. |2={{clade ..}} }}`; null if it has no usable children. */
fun parseClade(template: String, depth: Int = 0): CladeNode.Branch? {
    if (depth > 30) return null
    // Editors often break a template over a multiline HTML comment:
    //     |1=<!--
    //     -->{{clade ...}}
    // Removing comments first makes the nested tree a normal branch instead of a leaf whose
    // "text" is the whole raw template (which effectively hid most of the tree).
    val source = template.replace(cladeCommentRegex, "")
    val inner = source.trim().removePrefix("{{").removeSuffix("}}")
    val args = splitTemplateArgs(inner)
    // Normalise like templateNameAt(): `{{Clade_gallery}}` must count as `clade gallery`.
    val templateName = args.firstOrNull()?.trim()?.lowercase()?.replace('_', ' ').orEmpty()
    if (!isCladeTemplateName(templateName)) return null

    val values = java.util.TreeMap<Int, String>()
    val labels = HashMap<Int, String>()
    val subs = HashMap<Int, String>()
    val groupLabels = HashMap<Int, String>()
    val bars = HashMap<Int, String>()
    var implicit = 0
    for (i in 1 until args.size) {
        val arg = args[i].trim()
        if (arg.isEmpty()) continue
        val kv = splitKeyValue(arg)
        // A taxon name may legitimately contain "=", e.g. "Tomato (= sect. Lycopersicon)".
        // Only the handful of keys that actually mean something in a clade are interpreted as
        // key/value pairs; everything else stays a positional child.
        val key = kv?.first?.lowercase()?.replace(" ", "")
        val m = key?.let { cladeNumRegex.matchEntire(it) }
        if (kv == null || m == null) {
            // Named formatting parameters (style, state, ...) are not children. A real taxon
            // may also contain "=" (e.g. "Tomato (= sect. Lycopersicon)"), so only a plain
            // parameter-looking key is ignored.
            if (key != null && key in cladeIgnoredKeys) continue
            implicit++
            values[implicit] = arg
            continue
        }
        val n = m.groupValues[2].toIntOrNull() ?: continue
        when (m.groupValues[1]) {
            "label" -> labels[n] = kv.second
            "sublabel" -> subs[n] = kv.second
            "grouplabel" -> groupLabels[n] = kv.second
            "bar" -> bars[n] = kv.second
            // `stateN` controls collapsing and is not a child node.
            "" -> {
                values[n] = kv.second
                if (n > implicit) implicit = n
            }
        }
    }

    val children = ArrayList<CladeChild>()
    for ((n, v) in values) {
        val value = v.replace(refTagRegex, "").trim()
        if (value.isEmpty()) continue
        val node: CladeNode =
            if (value.startsWith("{{") && isCladeTemplateName(templateNameAt(value, 0)))
                parseClade(value, depth + 1) ?: continue
            else CladeNode.Leaf(value)
        children.add(
            CladeChild(
                plainCaption(labels[n] ?: ""),
                plainCaption(subs[n] ?: ""),
                node,
                plainCaption(groupLabels[n] ?: ""),
                bars[n]?.trim().orEmpty()
            )
        )
    }
    return if (children.isEmpty()) null else CladeNode.Branch(children)
}
