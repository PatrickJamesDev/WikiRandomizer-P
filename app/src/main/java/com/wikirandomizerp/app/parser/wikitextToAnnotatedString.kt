package com.wikirandomizerp.app.parser

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.fastMap
import com.github.tomtung.latex2unicode.LaTeX2Unicode
import com.wikirandomizerp.app.data.DebugLog
import com.wikirandomizerp.app.data.LanguageData
import com.wikirandomizerp.app.data.alpha3CodeToAlpha2CodeMap
import com.wikirandomizerp.app.data.countryNameToAlpha2CodeMap
import com.wikirandomizerp.app.data.countryFlag
import com.wikirandomizerp.app.data.formatToHumanReadable
import com.wikirandomizerp.app.data.langCodeToName
import com.wikirandomizerp.app.parser.ReferenceData.refCount
import com.wikirandomizerp.app.parser.ReferenceData.refList
import com.wikirandomizerp.app.parser.ReferenceData.refListCount
import com.wikirandomizerp.app.parser.ReferenceData.refListIndex
import com.wikirandomizerp.app.parser.ReferenceData.refTemplate
import com.wikirandomizerp.app.parser.ReferenceData.refTemplates
import kotlin.math.min
import kotlin.text.Typography.bullet
import kotlin.text.Typography.mdash
import kotlin.text.Typography.nbsp
import kotlin.text.Typography.ndash

private const val MAGIC_SEP = "{{!}}"

/** Template names already logged by the "unhandled template" diagnostic (bounded). */
private val unknownTemplates: MutableSet<String> =
    java.util.concurrent.ConcurrentHashMap.newKeySet()

// ---------------------------------------------------------------------------
// Pre-compiled regexes — compiled once at class-load time, NEVER inside loops.
// ---------------------------------------------------------------------------
private val imageExtRegex =
    ".*\\.jpg.*|.*\\.jpeg.*|.*\\.png.*|.*\\.svg.*|.*\\.gif.*"
        .toRegex(RegexOption.IGNORE_CASE)
private val thumbFilterRegex =
    "thumb|thumbnail|frame|frameless|border|baseline|class=.*|center|left|right|upright.*|.+px|alt=.*"
        .toRegex()
private val dfMfRegex = "df|mf".toRegex()
private val digitEqRegex = "\\d+=.+".toRegex(RegexOption.DOT_MATCHES_ALL)
private val urlRegex = ".+://.+".toRegex()
private val aboutPat1  = "\\{\\{[Aa]bout\\|\\|[^|]+\\|[^|]+".toRegex()
private val aboutPat1b = "\\{\\{[Aa]bout\\|\\|\\|[^|]+".toRegex()
private val aboutPat2  = "\\{\\{[Aa]bout\\|[^|]+\\|\\|[^|]+".toRegex()
private val aboutPat3  = "\\{\\{[Aa]bout\\|[^|]+\\|\\|[^|]+\\|and\\|[^|]+".toRegex()
private val aboutPat4  = "\\{\\{[Aa]bout\\|[^|]+\\|[^|]+\\|[^|]+".toRegex()
private val aboutPat5  = "\\{\\{[Aa]bout\\|[^|]+\\|[^|]+\\|[^|]+\\|and\\|[^|]+".toRegex()
private val aboutPat6  = "\\{\\{[Aa]bout\\|[^|]+\\|[^|]+\\|[^|]+\\|[^|]+\\|[^|]+".toRegex()
private val refNameAttrRegex = Regex(
    "\\bname\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s/>]+))",
    RegexOption.IGNORE_CASE
)

/**
 * Named HTML entities that occur in article text often enough to be worth decoding without
 * calling Android's HTML parser. Keeping the exact spelling matters (`&Prime;` is not `&prime;`).
 */
private val htmlEntities: Map<String, String> = buildMap {
    fun putAll(vararg pairs: Pair<String, String>) = pairs.forEach { put(it.first, it.second) }
    putAll(
        "nbsp" to "\u00A0", "ensp" to "\u2002", "emsp" to "\u2003", "thinsp" to "\u2009",
        "zwnj" to "\u200C", "zwj" to "\u200D", "lrm" to "\u200E", "rlm" to "\u200F",
        "shy" to "\u00AD", "ndash" to "\u2013", "mdash" to "\u2014", "horbar" to "\u2015",
        "minus" to "\u2212", "plusmn" to "\u00B1", "times" to "\u00D7", "divide" to "\u00F7",
        "deg" to "\u00B0", "micro" to "\u00B5", "para" to "\u00B6", "sect" to "\u00A7",
        "middot" to "\u00B7", "bull" to "\u2022", "hellip" to "\u2026",
        "prime" to "\u2032", "Prime" to "\u2033", "frasl" to "\u2044",
        "frac12" to "\u00BD", "frac14" to "\u00BC", "frac34" to "\u00BE",
        "sup1" to "\u00B9", "sup2" to "\u00B2", "sup3" to "\u00B3",
        "copy" to "\u00A9", "reg" to "\u00AE", "trade" to "\u2122",
        "euro" to "\u20AC", "pound" to "\u00A3", "yen" to "\u00A5", "cent" to "\u00A2",
        "curren" to "\u00A4", "laquo" to "\u00AB", "raquo" to "\u00BB",
        "lsquo" to "\u2018", "rsquo" to "\u2019", "sbquo" to "\u201A",
        "ldquo" to "\u201C", "rdquo" to "\u201D", "bdquo" to "\u201E",
        "lsaquo" to "\u2039", "rsaquo" to "\u203A",
        "dagger" to "\u2020", "Dagger" to "\u2021", "permil" to "\u2030",
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "larr" to "\u2190", "uarr" to "\u2191", "rarr" to "\u2192", "darr" to "\u2193",
        "harr" to "\u2194", "lArr" to "\u21D0", "uArr" to "\u21D1", "rArr" to "\u21D2",
        "dArr" to "\u21D3", "hArr" to "\u21D4",
        "forall" to "\u2200", "part" to "\u2202", "exist" to "\u2203", "empty" to "\u2205",
        "nabla" to "\u2207", "isin" to "\u2208", "notin" to "\u2209", "ni" to "\u220B",
        "prod" to "\u220F", "sum" to "\u2211", "lowast" to "\u2217", "radic" to "\u221A",
        "prop" to "\u221D", "infin" to "\u221E", "ang" to "\u2220",
        "and" to "\u2227", "or" to "\u2228", "cap" to "\u2229", "cup" to "\u222A",
        "int" to "\u222B", "there4" to "\u2234", "sim" to "\u223C", "cong" to "\u2245",
        "asymp" to "\u2248", "ne" to "\u2260", "equiv" to "\u2261", "le" to "\u2264",
        "ge" to "\u2265", "sub" to "\u2282", "sup" to "\u2283", "nsub" to "\u2284",
        "sube" to "\u2286", "supe" to "\u2287", "oplus" to "\u2295", "otimes" to "\u2297",
        "perp" to "\u22A5", "sdot" to "\u22C5", "loz" to "\u25CA",
        "spades" to "\u2660", "clubs" to "\u2663", "hearts" to "\u2665", "diams" to "\u2666"
    )
}

/** Decodes `&amp;`, `&#160;` and `&#xA0;`; null when [entity] is not a valid HTML entity. */
internal fun decodeHtmlEntity(entity: String): String? {
    if (entity.length < 3 || entity.first() != '&' || entity.last() != ';') return null
    val body = entity.substring(1, entity.length - 1)
    if (body.isEmpty()) return null
    if (body[0] == '#') {
        val code = if (body.length > 1 && (body[1] == 'x' || body[1] == 'X')) {
            body.substring(2).toIntOrNull(16)
        } else {
            body.substring(1).toIntOrNull()
        } ?: return null
        if (code <= 0 || code > 0x10FFFF || code in 0xD800..0xDFFF) return null
        return String(Character.toChars(code))
    }
    // Unknown entity names still fall back to fromHtml, but a stray `&word;` in source text
    // should not be turned into a parser call unless it looks like an entity.
    if (!body.all { it.isLetterOrDigit() }) return null
    return htmlEntities[body] ?: htmlEntities[body.lowercase()]
}

/**
 * Converts Wikitext source code into an [AnnotatedString] that can be rendered by [androidx.compose.material3.Text]
 */
fun String.toWikitextAnnotatedString(
    colorScheme: ColorScheme,
    typography: Typography,
    loadPage: (String) -> Unit,
    fontSize: Int,
    newLine: Boolean = true,
    inIndentCode: Boolean = false,
    showRef: (String) -> Unit,
): AnnotatedString {
    val hrChar = '─'
    val input = this
    var i = 0
    var number = 1 // Count for numbered lists

    var italic = false
    var bold = false

    val twas: String.() -> AnnotatedString = {
        this.toWikitextAnnotatedString(
            colorScheme,
            typography,
            loadPage,
            fontSize,
            showRef = showRef
        )
    }

    val twasNoNewline: String.() -> AnnotatedString = {
        this.toWikitextAnnotatedString(
            colorScheme,
            typography,
            loadPage,
            fontSize,
            newLine = false,
            showRef = showRef
        )
    }

    return buildAnnotatedString {
        while (i < input.length) {
            if (input[i] != '#') number = 1
            when (input[i]) {
                ' ' ->
                    if ((getOrNull(i - 1) == '\n' || i == 0) && !inIndentCode) {
                        val curr = substringBeforeFrom(i + 1, '\n')
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                color = colorScheme.onSurfaceVariant
                            )
                        ) {
                            append(
                                curr.toWikitextAnnotatedString(
                                    colorScheme,
                                    typography,
                                    loadPage,
                                    fontSize,
                                    inIndentCode = true,
                                    showRef = showRef
                                )
                            )
                        }
                        i += curr.length
                    } else append(input[i])

                '<' -> {
                    // Bounded check for tag-type detection — avoids allocating the
                    // entire remaining string (potentially hundreds of KB) on every '<'.
                    val tagDetect = input.substring(i, minOf(i + 22, input.length))
                    when {
                        tagDetect.startsWith("<nowiki>") -> {
                            val curr =
                                input.substringBeforeFrom(i, "</nowiki>").substringAfter('>')
                            append(curr)
                            i += 8 + curr.length + 8
                        }

                        tagDetect.startsWith("<ref", ignoreCase = true) &&
                                input.getOrNull(i + 4).let {
                                    it == '>' || it == '/' || it == null || it.isWhitespace()
                                } -> {
                            // Handle references
                            // When a reference footnote is clicked, the showRef lambda is called.
                            // `<references>` and tag-like text must not enter this branch.
                            val open = input.substringMatchingParenFrom(i, '<', '>')
                            val refName = refNameAttrRegex.find(open)?.let { m ->
                                m.groupValues[1].ifEmpty { m.groupValues[2] }
                                    .ifEmpty { m.groupValues[3] }
                                    .trim()
                            }.orEmpty()
                            if (open.getOrNull(4) == '>') {
                                // Style 1: Plain reference without named references
                                val curr =
                                    input.substringBeforeFrom(i, "</ref>").substringAfter('>')
                                withLink(
                                    LinkAnnotation.Url(
                                        "",
                                        TextLinkStyles(
                                            SpanStyle(color = colorScheme.primary)
                                        )
                                    ) {
                                        showRef(curr)
                                    }
                                ) {
                                    // Do not re-run the whole parser just to style a footnote
                                    // number: references are the most frequent inline markup.
                                    withStyle(
                                        SpanStyle(
                                            baselineShift = BaselineShift.Superscript,
                                            fontSize = (fontSize - 4).sp
                                        )
                                    ) { append("${ReferenceData.nextInlineRef(curr)} ") }
                                }
                                i += 5 + curr.length + 5
                            } else if (open.endsWith("/>")) {
                                // Style 2: reference that refers to a named reference
                                val refWt = refList[refName] ?: ""
                                val refNumber = ReferenceData.numberForNamedRef(refWt)
                                withLink(
                                    LinkAnnotation.Url(
                                        "",
                                        TextLinkStyles(
                                            SpanStyle(color = colorScheme.primary)
                                        )
                                    ) {
                                        showRef(refWt)
                                    }
                                ) {
                                    withStyle(
                                        SpanStyle(
                                            baselineShift = BaselineShift.Superscript,
                                            fontSize = (fontSize - 4).sp
                                        )
                                    ) { append("$refNumber ") }
                                }
                                i += open.length - 1
                            } else {
                                // Style 3: Names reference definition
                                val refWt = refList[refName] ?: ""
                                // (the old code had a no-op `refList[refName]` here and never added
                                // the entry to the reference list, so numbers went missing in {{reflist}})
                                val refNumber = ReferenceData.numberForNamedRef(refWt)
                                withLink(
                                    LinkAnnotation.Url(
                                        "",
                                        TextLinkStyles(
                                            SpanStyle(color = colorScheme.primary)
                                        )
                                    ) {
                                        showRef(refWt)
                                    }
                                ) {
                                    withStyle(
                                        SpanStyle(
                                            baselineShift = BaselineShift.Superscript,
                                            fontSize = (fontSize - 4).sp
                                        )
                                    ) { append("$refNumber ") }
                                }
                                i += input.substringBeforeFrom(i, "</ref>").length + 5
                            }
                        }

                        tagDetect.startsWith("<br", ignoreCase = true) -> {
                            append('\n')
                            i += input.substringBeforeFrom(i, '>').length
                        }

                        tagDetect.startsWith("<hr", ignoreCase = true) -> {
                            append('\n')
                            repeat(5) { append(hrChar) }
                            append('\n')
                            i += input.substringBeforeFrom(i, '>').length
                        }

                        tagDetect.startsWith("<u>") -> {
                            val curr = input.substringBeforeFrom(i, "</u>").substringAfter('>')
                            withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                                append(curr.twasNoNewline())
                            }
                            i += 3 + curr.length + 3
                        }

                        tagDetect.startsWith("<div") -> {
                            val divTag = input.substringBeforeFrom(i, '>')
                            val curr = input.substringBeforeFrom(i, "</div>").substringAfter('>')
                            append(
                                curr.twasNoNewline()
                            )
                            i += divTag.length + 1 + curr.length + 5
                        }

                        tagDetect.startsWith("<section") -> {
                            val curr = input.substringBeforeFrom(i, '>')
                            i += curr.length
                        }

                        tagDetect.startsWith("<noinclude>") -> {
                            val curr =
                                input.substringBeforeFrom(i, "</noinclude>").substringAfter('>')
                            append(curr.twasNoNewline())
                            i += 11 + curr.length + 11
                        }

                        tagDetect.startsWith("<code>") -> {
                            val curr = input.substringBeforeFrom(i, "</code>").substringAfter('>')
                            withStyle(
                                SpanStyle(
                                    fontFamily = FontFamily.Monospace,
                                    color = colorScheme.onSurfaceVariant
                                )
                            ) { append(curr.twasNoNewline()) }
                            i += 6 + curr.length + 6
                        }

                        tagDetect.startsWith("<syntaxhighlight") -> {
                            val curr =
                                input.substringBeforeFrom(i, "</syntaxhighlight>")
                                    .substringAfter('>')
                            withStyle(
                                SpanStyle(
                                    fontFamily = FontFamily.Monospace,
                                    color = colorScheme.onSurfaceVariant
                                )
                            ) { append(curr) }
                            i += input.substringBeforeFrom(i, '>').length + curr.length + 18
                        }

                        tagDetect.startsWith("<sub>") -> {
                            val curr = input.substringBeforeFrom(i, "</sub>").substringAfter('>')
                            withStyle(
                                SpanStyle(
                                    baselineShift = BaselineShift.Subscript,
                                    fontSize = (fontSize - 4).sp
                                )
                            ) { append(curr.twasNoNewline()) }
                            i += 5 + curr.length + 5
                        }

                        tagDetect.startsWith("<var>") -> {
                            val curr = input.substringBeforeFrom(i, "</var>").substringAfter('>')
                            append("''$curr''".twasNoNewline())
                            i += 5 + curr.length + 5
                        }

                        tagDetect.startsWith("<sup>") -> {
                            val curr = input.substringBeforeFrom(i, "</sup>").substringAfter('>')
                            withStyle(
                                SpanStyle(
                                    baselineShift = BaselineShift.Superscript,
                                    fontSize = (fontSize - 4).sp
                                )
                            ) { append(curr.twasNoNewline()) }
                            i += 5 + curr.length + 5
                        }

                        tagDetect.startsWith("<small>") -> {
                            val curr = input.substringBeforeFrom(i, "</small>").substringAfter('>')
                            withStyle(SpanStyle(fontSize = (fontSize - 4).sp)) {
                                append(curr.twasNoNewline())
                            }
                            i += 7 + curr.length + 7
                        }

                        tagDetect.startsWith("<math>") -> {
                            val curr = input.substringBeforeFrom(i, "</math>").substringAfter('>')
                            withStyle(SpanStyle(fontFamily = FontFamily.Serif)) {
                                append(LaTeX2Unicode.convert(curr).replace(' ', nbsp))
                            }
                            i += 6 + curr.length + 6
                        }

                        tagDetect.startsWith("<math display") -> {
                            val curr = input.substringBeforeFrom(i, "</math>").substringAfter('>')
                            append("\t\t")
                            withStyle(SpanStyle(fontFamily = FontFamily.Serif)) {
                                append(LaTeX2Unicode.convert(curr).replace(' ', nbsp))
                            }
                            i += input.substringBeforeFrom(i, '>').length + curr.length + "</math>".length
                        }

                        tagDetect.startsWith("<blockquote") -> {
                            val curr =
                                input.substringBeforeFrom(i, "</blockquote>").substringAfter('>')
                            withStyle(
                                ParagraphStyle(
                                    textIndent = TextIndent(
                                        firstLine = 16.sp,
                                        restLine = 16.sp
                                    )
                                )
                            ) {
                                withStyle(SpanStyle(color = colorScheme.onSurfaceVariant)) {
                                    append('\n')
                                    append(curr.twas())
                                    append('\n')
                                }
                            }
                            i += 12 + curr.length + 13
                        }

                        tagDetect.startsWith("<!--") -> {
                            val curr = input.substringMatchingParenFrom(i, '<', '>')
                            i += curr.length - 1
                        }

                        else -> {
                            append(input[i])
                        }
                    }
                }

                '{' ->
                    if (input.getOrNull(i + 1) == '{') {
                        val currSubstring =
                            substringMatchingParen('{', '}', i).substringBeforeLast("}}")
                        when {
                            refTemplates.fastAny { item ->
                                currSubstring
                                    .startsWith(item, ignoreCase = true)
                                    .also { if (it) refTemplate = item }
                            } -> {
                                val text =
                                    if (currSubstring.startsWith("$refTemplate book", true)) {
                                        val params = mutableMapOf<String, String>()

                                        // Extract inside of {{Cite book ...}}
                                        val content =
                                            currSubstring.substringAfter('|').trim()

                                        // Split by pipes, then split each param by '='
                                        val parts = content.split("|")
                                        for (part in parts) {
                                            val (key, value) = part.split("=", limit = 2)
                                                .map { it.trim() }.let {
                                                    if (it.size == 2) it[0] to it[1] else return@let null
                                                } ?: continue
                                            params[key.lowercase()] = value
                                        }

                                        // Build citation text
                                        val first = params["first"]
                                        val last = params["last"]
                                        val author =
                                            listOfNotNull(last, first).joinToString().trim()
                                        if (params["script-title"] != null) params["title"] =
                                            params["script-title"] ?: ""
                                        val title =
                                            if (params["title"] != null)
                                                "''${params["title"]}''" + if (params["edition"] != null) {
                                                    " (" + params["edition"]!! + " ed.)"
                                                } else ""
                                            else null

                                        listOfNotNull(
                                            "$author (${
                                                listOfNotNull(
                                                    params["date"],
                                                    params["year"]
                                                ).joinToString()
                                            })",
                                            title,
                                            params["publisher"],
                                            params["isbn"]?.let { "[[ISBN]] $it" }
                                        )
                                            .fastFilter { it.isNotBlank() }
                                            .joinToString(". ")
                                            .plus(".")
                                            .trim()
                                            .twas()
                                    } else if (
                                        currSubstring.startsWith("$refTemplate web", true) ||
                                        currSubstring.startsWith("$refTemplate news", true) ||
                                        currSubstring.startsWith("$refTemplate AV media", true) ||
                                        currSubstring.startsWith("$refTemplate press release", true)
                                    ) {
                                        val params = mutableMapOf<String, String>()

                                        // Extract inside of {{Cite ...}}
                                        val content =
                                            currSubstring.substringAfter('|').trim()

                                        // Split by pipes, then split each param by '='
                                        val parts = content.split("|")
                                        for (part in parts) {
                                            val (key, value) = part.split("=", limit = 2)
                                                .map { it.trim() }.let {
                                                    if (it.size == 2) it[0] to it[1] else return@let null
                                                } ?: continue
                                            params[key.lowercase()] = value
                                        }

                                        // Build citation text
                                        val first = params["first"]
                                        val last = params["last"]
                                        val author =
                                            listOfNotNull(last, first).joinToString().trim()
                                        if (params["script-title"] != null) params["title"] =
                                            params["script-title"] ?: ""
                                        var title =
                                            if (params["title"] != null)
                                                "''${params["title"]}''" + if (params["edition"] != null) {
                                                    " (" + params["edition"]!! + " ed.)"
                                                } else ""
                                            else null
                                        if (params["trans-title"] != null) title += " [${params["trans-title"]}]"

                                        listOfNotNull(
                                            "$author (${
                                                listOfNotNull(
                                                    params["date"],
                                                    params["year"]
                                                ).joinToString()
                                            })".takeIf { it.trim('(', ')').isNotBlank() },
                                            if (params["url"] == null) title.takeIf {
                                                it?.trim('"')?.isNotBlank() == true
                                            }
                                            else "[${params["url"]} $title]".takeIf {
                                                it.trim('"').isNotBlank()
                                            },
                                            params["website"],
                                            "''${params["work"] ?: ""}''".takeIf {
                                                it.trim('\'').isNotBlank()
                                            },
                                            params["publisher"],
                                            "[${params["archive-url"]} Archived] ${params["archive-date"]}".takeIf { params["archive-url"] != null },
                                            "via ${params["via"]}".takeIf { params["via"] != null }
                                        )
                                            .fastFilter { it.isNotBlank() }
                                            .joinToString(". ")
                                            .plus(".")
                                            .trim()
                                            .twas()
                                    } else if (currSubstring.startsWith(
                                            "$refTemplate journal",
                                            true
                                        )
                                    ) {
                                        val params = mutableMapOf<String, String>()

                                        // Extract inside of {{Cite journal ...}}
                                        val content =
                                            currSubstring.substringAfter('|').trim()

                                        // Split by pipes, then split each param by '='
                                        val parts = content.split("|")
                                        for (part in parts) {
                                            val (key, value) = part.split("=", limit = 2)
                                                .map { it.trim() }.let {
                                                    if (it.size == 2) it[0] to it[1] else return@let null
                                                } ?: continue
                                            params[key.lowercase()] = value
                                        }

                                        // Build citation text
                                        val first = params["first"]
                                        val last = params["last"]
                                        val author =
                                            listOfNotNull(last, first).joinToString().trim()
                                        if (params["script-title"] != null) params["title"] =
                                            params["script-title"] ?: ""
                                        val title =
                                            if (params["title"] != null)
                                                "''${params["title"]}''" + if (params["edition"] != null) {
                                                    " (" + params["edition"]!! + " ed.)"
                                                } else ""
                                            else null
                                        val volume =
                                            "'''${params["volume"]}'''${" (${params["issue"]})".takeIf { params["issue"] != null } ?: ""}${": ${params["pages"]}".takeIf { params["pages"] != null } ?: ""}".takeIf { params["volume"] != null }

                                        listOfNotNull(
                                            "$author (${
                                                listOfNotNull(
                                                    params["date"],
                                                    params["year"]
                                                ).joinToString()
                                            })",
                                            if (params["url"] == null) title.takeIf {
                                                it?.trim('"')?.isNotBlank() == true
                                            }
                                            else "[${params["url"]} $title]".takeIf {
                                                it.trim('"').isNotBlank()
                                            },
                                            "''${params["journal"]}''".takeIf { params["journal"] != null },
                                            volume,
                                            params["publisher"],
                                            "[[ISSN]] ${params["issn"]}".takeIf { params["issn"] != null },
                                            "[[Bibcode]]:${params["bibcode"]}".takeIf { params["bibcode"] != null },
                                            "[[Doi_(identifier)|doi]]:${params["doi"]}".takeIf { params["doi"] != null },
                                            "[[PMC]]:${params["PMC"]}".takeIf { params["PMC"] != null },
                                            "[[PMID]]:${params["PMID"]}".takeIf { params["PMID"] != null },
                                        )
                                            .fastFilter { it.isNotBlank() }
                                            .joinToString(". ")
                                            .plus(".")
                                            .trim()
                                            .twas()
                                    } else AnnotatedString(currSubstring)
                                append(text)
                            }

                            currSubstring.startsWith("{{abbr", ignoreCase = true) -> {
                                val curr = currSubstring.substringAfter('|').substringBefore('|')
                                append(curr.twas())
                            }

                            currSubstring.startsWith("{{TableTBA", ignoreCase = true) -> {
                                withStyle(SpanStyle(fontSize = (fontSize - 4).sp)) {
                                    append("TBA")
                                }
                            }

                            currSubstring.startsWith("{{efn", ignoreCase = true) -> {
                                val curr = currSubstring.substringAfter('|', "")
                                curr.twas()
                            }

                            currSubstring.startsWith("{{convert", ignoreCase = true) ||
                                    currSubstring.startsWith("{{cvt", ignoreCase = true)
                                -> {
                                // Full {{convert}} support (ranges, fractions, unit conversion and
                                // the named options). The old code stopped after the input value,
                                // which is why articles showed "100 and(-)" and no units at all.
                                val body = currSubstring.substringAfter('|', "")
                                append(
                                    if (body.isBlank()) ""
                                    else ConvertTemplate.render(body)
                                )
                            }

                            currSubstring.startsWith("{{mono|", ignoreCase = true) -> {
                                val curr = currSubstring.substringAfter('|')
                                withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                                    append(curr.twas())
                                }
                            }

                            currSubstring.startsWith("{{math", ignoreCase = true) ||
                                    currSubstring.startsWith("{{mvar", ignoreCase = true)
                                -> {
                                val curr = currSubstring.substringAfter('|', "").removePrefix("1=")
                                withStyle(SpanStyle(fontFamily = FontFamily.Serif)) {
                                    append(curr.replace(' ', nbsp).twas())
                                }
                            }

                            currSubstring.startsWith("{{val", ignoreCase = true) -> {
                                // {{val|1.23|e=4|u=m}} -> "1.23×10⁴ m". The exponent used to be
                                // dropped, which made scientific values in chemistry/physics
                                // articles read 10 000 times too small.
                                val parts = ConvertTemplate.splitTopLevel(
                                    currSubstring.substringAfter('|', ""), '|'
                                )
                                val value = parts.firstOrNull()?.trim().orEmpty()
                                val exponent = parts.firstOrNull {
                                    it.trim().startsWith("e=", ignoreCase = true)
                                }?.substringAfter('=')?.trim()?.toIntOrNull()
                                val unit = parts.firstOrNull {
                                    it.trim().startsWith("u=", ignoreCase = true)
                                }?.substringAfter('=')?.trim().orEmpty()
                                if (value.isNotBlank()) {
                                    append(value.removePrefix("1="))
                                    if (exponent != null) {
                                        append('×')
                                        append(ConvertTemplate.powerOfTen(exponent))
                                    }
                                    if (unit.isNotBlank()) {
                                        append(' ')
                                        append(unit)
                                    }
                                }
                            }

                            currSubstring.startsWith("{{var", ignoreCase = true) -> {
                                val curr = currSubstring.substringAfter('|')
                                append("''$curr''".twas())
                            }

                            arrayOf("{{small", "{{smaller", "{{petit", "{{hw-small", "{{sma").any {
                                currSubstring.startsWith(it, ignoreCase = true)
                            } -> {
                                val curr = currSubstring.substringAfter('|')
                                withStyle(SpanStyle(fontSize = (fontSize - 2).sp)) {
                                    append(curr.twas())
                                }
                            }

                            currSubstring.startsWith("{{main", ignoreCase = true) -> {
                                val curr = currSubstring.substringAfter('|')
                                val splitList = curr.split('|')
                                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                    append("Main article")
                                    if (splitList.size > 1) append("s: ")
                                    else append(": ")
                                    splitList.fastForEachIndexed { index, it ->
                                        append(
                                            "[[${it.substringBefore(MAGIC_SEP)}|${
                                                it.substringAfter(MAGIC_SEP).replace(
                                                    "#",
                                                    " § "
                                                )
                                            }]]".twas()
                                        )
                                        if (index == splitList.size - 2 && splitList.size > 1) append(
                                            ", and "
                                        )
                                        else if (index < splitList.size - 1) append(", ")
                                    }
                                }
                                append('\n')
                            }

                            currSubstring.startsWith("{{see also", ignoreCase = true) -> {
                                val curr = currSubstring.substringAfter('|')
                                val splitList = curr.split('|').filterNot { it.startsWith('#') }
                                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                    append("See also: ")
                                    splitList.fastForEachIndexed { index, it ->
                                        append(
                                            "[[${it.substringBefore(MAGIC_SEP)}|${
                                                it.substringAfter((MAGIC_SEP)).replace("#", " § ")
                                            }]]".twas()
                                        )
                                        if (index == splitList.size - 2 && splitList.size > 1) append(
                                            ", and "
                                        )
                                        else if (index < splitList.size - 1) append(", ")
                                    }
                                }
                            }

                            currSubstring.startsWith("{{date", ignoreCase = true) -> {
                                val curr = currSubstring.substringAfter('|')
                                val splitList = curr.split('|')
                                if (splitList.size < 3) {
                                    append(splitList[0])
                                } else {
                                    append(splitList[0])
                                    append(' ')
                                    append(splitList[1])
                                    append(' ')
                                    append(splitList[2])
                                }
                            }

                            currSubstring.startsWith("{{dfn", true) -> {
                                val curr = currSubstring.substringAfter('|')
                                append("'''$curr'''".twas())
                            }

                            currSubstring.startsWith("{{distinguish", ignoreCase = true) -> {
                                val textSpecified =
                                    currSubstring.contains("text=") || currSubstring.contains("text =")
                                val curr =
                                    if (textSpecified) currSubstring.substringAfter('=').trim()
                                    else currSubstring.substringAfter('|')
                                val splitList =
                                    if (textSpecified) listOf(curr)
                                    else curr.split('|')
                                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                    append("Not to be confused with ")
                                    if (!textSpecified) splitList.fastForEachIndexed { index, it ->
                                        append(
                                            "[[${it.substringBefore(MAGIC_SEP)}|${
                                                it.substringAfter(
                                                    MAGIC_SEP
                                                ).replace("#", " § ")
                                            }]]".twas()
                                        )
                                        if (index == splitList.size - 2 && splitList.size > 1) append(
                                            ", or "
                                        )
                                        else if (index < splitList.size - 1) append(", ")
                                    } else append(splitList[0].twas())
                                    append('.')
                                }
                            }

                            currSubstring.startsWith("{{redirect-distinguish", true) -> {
                                val splitList = currSubstring.substringAfter('|').split('|')
                                append("\"${splitList.getOrNull(0)}\" redirects here; not to be confused with ")
                                splitList.subList(1, splitList.size)
                                    .fastForEachIndexed { index: Int, it: String ->
                                        append(
                                            "[[${it.substringBefore(MAGIC_SEP)}|${
                                                it.substringAfter(
                                                    MAGIC_SEP
                                                )
                                            }]]".twas()
                                        )

                                        if (index == splitList.size - 2 && splitList.size > 2) append(
                                            ", or "
                                        )
                                        else if (index < splitList.size - 2) append(", ")
                                    }
                                append('.')
                            }

                            currSubstring.startsWith("{{format price", true) -> {
                                val curr = currSubstring.substringAfter('|').substringBefore('|')
                                append(
                                    if (curr.contains('.')) curr.toDoubleOrNull()
                                        ?.formatToHumanReadable() ?: curr.twas()
                                    else curr.toLongOrNull()?.formatToHumanReadable() ?: curr.twas()
                                )
                            }

                            currSubstring.startsWith("{{for", true) -> {
                                val splitList = currSubstring.substringAfter('|').split('|')
                                    .fastFilter { !it.contains('=') }
                                if (splitList.size > 1) {
                                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                        append("For ${splitList[0]}, see ")
                                        splitList.subList(1, splitList.size)
                                            .fastForEachIndexed { index: Int, it: String ->
                                                append(
                                                    "[[${it.substringBefore(MAGIC_SEP)}|${
                                                        it.substringAfter(
                                                            MAGIC_SEP
                                                        )
                                                    }]]".twas()
                                                )

                                                if (index == splitList.size - 2 && splitList.size > 2) append(
                                                    ", and "
                                                )
                                                else if (index < splitList.size - 2) append(", ")
                                            }
                                        append('.')
                                    }
                                }
                            }

                            currSubstring.startsWith("{{about", true) -> {
                                val splitList = currSubstring.substringAfter('|').split('|')
                                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                    when {
                                        currSubstring.matches(aboutPat1) -> {
                                            append(
                                                "''For ${splitList[1]}, see [[${
                                                    splitList[2].replace(
                                                        MAGIC_SEP,
                                                        "|"
                                                    )
                                                }]]''".twas()
                                            )
                                        }

                                        currSubstring.matches(aboutPat1b) -> {
                                            append(
                                                "''For other uses, see [[${
                                                    splitList.last().replace(MAGIC_SEP, "|")
                                                }]]''".twas()
                                            )
                                        }

                                        currSubstring.matches(aboutPat2) -> {
                                            append(
                                                "''This article is about ${splitList[0]}. For other uses, see [[${
                                                    splitList[2].replace(
                                                        MAGIC_SEP,
                                                        "|"
                                                    )
                                                }]]''".twas()
                                            )
                                        }

                                        currSubstring.matches(aboutPat3) -> {
                                            append(
                                                "''This article is about ${splitList[0]}. For other uses, see [[${
                                                    splitList[2].replace(
                                                        MAGIC_SEP,
                                                        "|"
                                                    )
                                                }]] and [[${
                                                    splitList[4].replace(
                                                        MAGIC_SEP,
                                                        "|"
                                                    )
                                                }]].''".twas()
                                            )
                                        }

                                        currSubstring.matches(aboutPat4) -> {
                                            append(
                                                "''This article is about ${splitList[0]}. For ${splitList[1]}, see [[${
                                                    splitList[2].replace(
                                                        MAGIC_SEP,
                                                        "|"
                                                    )
                                                }]].''".twas()
                                            )
                                        }

                                        currSubstring.matches(aboutPat5) -> {
                                            append(
                                                "''This article is about ${splitList[0]}. For ${splitList[1]}, see [[${
                                                    splitList[2].replace(
                                                        MAGIC_SEP,
                                                        "|"
                                                    )
                                                }]] and [[${
                                                    splitList[4].replace(
                                                        MAGIC_SEP,
                                                        "|"
                                                    )
                                                }]].''".twas()
                                            )
                                        }

                                        currSubstring.matches(aboutPat6) -> {
                                            append(
                                                "''This article is about ${splitList[0]}. For ${splitList[1]}, see [[${
                                                    splitList[2].replace(
                                                        MAGIC_SEP,
                                                        "|"
                                                    )
                                                }]]. For ${splitList[3]}, see [[${
                                                    splitList[4].replace(
                                                        MAGIC_SEP,
                                                        "|"
                                                    )
                                                }]].''".twas()
                                            )
                                        }
                                    }
                                }
                            }

                            currSubstring.startsWith("{{US$", true) -> {
                                if (currSubstring.contains('|')) {
                                    val curr = currSubstring.substringAfter('|')
                                    append("US$${curr.twas()}")
                                } else append("US$")
                            }

                            currSubstring.startsWith("{{hatnote", ignoreCase = true) -> {
                                val curr = currSubstring.substringAfter('|').replace('\n', ' ')
                                append("''$curr''".twas())
                            }

                            currSubstring.startsWith("{{IPAc-en", ignoreCase = true) -> {
                                val curr = currSubstring.substringAfter('|').split('|')
                                    .filterNot { it.contains('=') }.joinToString("")
                                append("/${curr.replace(' ', nbsp)}/")
                            }

                            currSubstring.startsWith("{{langx", ignoreCase = true) -> {
                                val lang = langCodeToName(
                                    currSubstring.substringAfter('|').substringBefore('|').trim()
                                )
                                val text =
                                    currSubstring.substringAfter('|').substringAfter('|').split('|')
                                        .filterNot { it.contains('=') }.joinToString()
                                append("[[$lang|$lang]]: ".twas())
                                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                    append(
                                        text.twas()
                                    )
                                }
                            }

                            currSubstring.startsWith("{{lang|", ignoreCase = true) -> {
                                val curr = currSubstring.substringAfter('|')
                                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                    append(curr.substringAfter('|').substringBefore('|').twas())
                                }
                            }

                            currSubstring.startsWith("{{transliteration", true) -> {
                                val curr = currSubstring.substringAfterLast('|', "")
                                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                    append(curr.twas())
                                }
                            }

                            currSubstring.startsWith("{{IPA", ignoreCase = true) -> {
                                val curr = currSubstring.substringAfter('|')
                                withStyle(SpanStyle(fontSize = (fontSize - 2).sp)) {
                                    append("${langCodeToName(curr.substringBefore('|'))}: ")
                                }
                                append(
                                    "[${
                                        curr.substringAfter('|').substringBefore("|")
                                            .replace("|", "").replace(' ', nbsp)
                                    }]"
                                )
                            }

                            currSubstring.startsWith("{{respell", ignoreCase = true) -> {
                                val curr = currSubstring.substringAfter('|')
                                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                    append(curr.replace('|', '-'))
                                }
                            }

                            currSubstring.startsWith("{{BCE", ignoreCase = true) -> {
                                val curr = currSubstring.substringAfter('|').substringBefore('|')
                                append(curr)
                                append(nbsp)
                                append("BCE")
                            }

                            currSubstring.startsWith("{{blockquote", ignoreCase = true) -> {
                                val curr = currSubstring.substringAfter('|')
                                withStyle(
                                    ParagraphStyle(
                                        textIndent = TextIndent(
                                            firstLine = 16.sp,
                                            restLine = 16.sp
                                        )
                                    )
                                ) {
                                    withStyle(SpanStyle(color = colorScheme.onSurfaceVariant)) {
                                        append('\n')
                                        append(
                                            curr.substringAfter("text=")
                                                .substringBefore('=')
                                                .substringBeforeLast('|')
                                                .twas()
                                        )
                                        append('\n')
                                    }
                                }
                            }

                            currSubstring.startsWith("{{further", ignoreCase = true) -> {
                                val curr = currSubstring.substringAfter('|')
                                val splitList = curr.split('|').fastFilter { !it.contains('=') }
                                val topic = curr.substringAfter("topic=", "").substringBefore('|')
                                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                    append("Further")
                                    if (topic.isNotEmpty()) {
                                        append(" information on $topic")
                                        append(
                                            ": [[${
                                                curr.substringAfter('|').substringBefore('|')
                                                    .substringBefore('#')
                                            }]]\n".twas()
                                        )
                                    } else {
                                        append(" reading")
                                        append(
                                            ": [[${splitList.getOrNull(0)}]]\n".twas()
                                        )
                                    }
                                }
                            }

                            currSubstring.startsWith("{{rp", true) -> {
                                val curr = currSubstring.substringAfter('|')
                                withStyle(
                                    SpanStyle(
                                        baselineShift = BaselineShift.Superscript,
                                        fontSize = (fontSize - 4).sp
                                    )
                                ) { append(":$curr ") }
                            }

                            currSubstring.startsWith("{{isbn", true) -> {
                                val curr = currSubstring.substringAfter('|').split('|')
                                    .filterNot { it.contains('=') }.joinToString()
                                append("[[ISBN]] $curr".twas())
                            }

                            currSubstring.startsWith("{{sfrac", true) ||
                                    currSubstring.startsWith("{{frac", true) -> {
                                val splitList = ConvertTemplate.splitTopLevel(
                                    currSubstring.substringAfter('|', ""), '|'
                                ).map { it.trim() }
                                fun sup(s: String) = withStyle(
                                    SpanStyle(
                                        baselineShift = BaselineShift.Superscript,
                                        fontSize = (fontSize - 4).sp
                                    )
                                ) { append(s) }
                                fun sub(s: String) = withStyle(
                                    SpanStyle(
                                        baselineShift = BaselineShift.Subscript,
                                        fontSize = (fontSize - 4).sp
                                    )
                                ) { append(s) }
                                when (splitList.size) {
                                    3 -> {
                                        append(splitList[0]); sup(splitList[1]); append("/")
                                        sub(splitList[2])
                                    }
                                    2 -> {
                                        sup(splitList[0]); append("/"); sub(splitList[1])
                                    }
                                    1 -> {
                                        sup("1"); append("/"); sub(splitList[0])
                                    }
                                }
                            }

                            currSubstring.startsWith("{{as of", ignoreCase = true) -> {
                                val curr = currSubstring.substringAfter("{{")
                                append(curr.substringBefore('|'))
                                append(' ')
                                var date = ""
                                curr.substringAfter('|').split('|').fastForEach {
                                    if (it.toIntOrNull() != null) {
                                        date += it
                                        date += '/'
                                    }
                                }
                                append(date.trim('/'))
                            }

                            currSubstring.startsWith("{{unichar", ignoreCase = true) -> {
                                val curr = currSubstring.substringAfter('|').substringBefore('|')
                                append("<code>U+$curr</code> ".twas())
                                try {
                                    append(Character.toString(curr.toInt(16)))
                                } catch (_: Exception) {
                                }
                            }

                            currSubstring.startsWith("{{char", ignoreCase = true) -> {
                                append(currSubstring.substringAfter('|').twas())
                            }

                            currSubstring.startsWith("{{Nihongo", ignoreCase = true) -> {
                                val curr = currSubstring.substringAfter('|').split('|')
                                    .filterNot { it.contains('=') }.joinToString("|")
                                append(curr.twas())
                            }

                            currSubstring.startsWith("{{flagg", ignoreCase = true) -> {
                                val curr = currSubstring.substringAfter('|').substringAfter('|')
                                    .substringBefore('|')

                                when (curr.length) {
                                    3 -> append(
                                        countryFlag(LanguageData.alpha3CodeToAlpha2CodeMap[curr] ?: "")
                                                + ' '
                                    )

                                    else -> append(
                                        countryFlag(LanguageData.countryNameToAlpha2CodeMap[curr] ?: "")
                                                + ' '
                                    )
                                }
                                append(curr.twas())
                            }

                            currSubstring.startsWith("{{noflag", ignoreCase = true) -> {
                                val curr = currSubstring.substringAfter('|')
                                append(curr.twas())
                            }

                            listOf("{{nowrap", "{{nobr", "{{nobreak", "{{nwr", "{{nbr").fastAny {
                                currSubstring.startsWith(it, ignoreCase = true)
                            } -> {
                                val curr = currSubstring.substringAfter('|', "")
                                append(curr.replace(' ', nbsp).twas())
                            }

                            currSubstring.startsWith(
                                "{{spaced en dash",
                                ignoreCase = true
                            ) || currSubstring.startsWith("{{snd", ignoreCase = true)
                                -> {
                                append(nbsp)
                                append(ndash)
                                append(' ')
                            }

                            currSubstring.startsWith("{{empty section", ignoreCase = true) -> {
                                withStyle(
                                    SpanStyle(
                                        fontStyle = FontStyle.Italic,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    append("This section is empty. You can help by adding to it on Wikipedia.")
                                }
                            }

                            currSubstring.startsWith("{{\"|") -> {
                                val curr = currSubstring.substringAfter('|')
                                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                    append(curr.twas())
                                }
                            }

                            listOf("{{birth date", "{{death date").fastAny {
                                currSubstring.startsWith(it)
                            } -> {
                                val splitList = currSubstring
                                    .substringAfter('|')
                                    .splitNotInBraces('|')
                                    .fastFilter { !it.contains(dfMfRegex) }
                                    .fastMap { it.substringAfter('=').trim() }

                                append(
                                    splitList
                                        .take(min(splitList.size, 3))
                                        .joinToString("/")
                                        .twas()
                                )
                            }

                            currSubstring.startsWith("{{unbulleted list", true) -> {
                                val splitList = currSubstring
                                    .substringAfter('|', "")
                                    .splitNotInBraces('|')
                                    .fastFilter { !it.contains('=') }
                                    .fastMap { it.trim() }
                                    .joinToString("\n")

                                append(splitList.twas())
                            }

                            listOf(
                                "{{bulleted list",
                                "{{ulist",
                                "{{blist",
                                "{{bulleted",
                                "{{unordered list"
                            ).fastAny {
                                currSubstring.startsWith(it, true)
                            } -> {
                                val splitList = currSubstring
                                    .substringAfter('|', "")
                                    .splitNotInBraces('|')
                                    .fastMap {
                                        if (it.trim()
                                                .matches(digitEqRegex)
                                        )
                                            it.substringAfter('=').trim()
                                        else it.trim()
                                    }
                                    .joinToString("\n* ")

                                append("* $splitList".twas())
                            }

                            currSubstring.startsWith("{{Transcluded section", true) -> {
                                val title =
                                    currSubstring.split('|').filter { it.contains("source=") }
                                        .joinToString().substringAfter('=')
                                append("''This section is transcluded from [[$title]]''".twas())
                            }

                            currSubstring.startsWith("{{fb", true) -> {
                                val country = currSubstring.substringAfter('|').substringBefore('|')

                                when (country.length) {
                                    3 -> append(
                                        countryFlag(
                                            LanguageData.alpha3CodeToAlpha2CodeMap[LanguageData.fifaCodeToAlpha3CodeMap[country.uppercase()]
                                                ?: country] ?: ""
                                        ) + " $country"
                                    )

                                    else -> append(
                                        countryFlag(
                                            LanguageData.countryNameToAlpha2CodeMap[country] ?: ""
                                        ) + " $country"
                                    )
                                }
                            }

                            currSubstring.startsWith("{{citation needed", true) -> {
                                append("<sup>[citation needed]</sup>".twas())
                            }

                            currSubstring.startsWith("{{url", true) -> {
                                val curr = currSubstring.substringAfter('|', "")
                                if (curr.matches(urlRegex))
                                    append("[$curr ${curr.substringAfter("//")}]".twas())
                                else append("[https://$curr $curr]".twas())
                            }

                            currSubstring.startsWith("{{Starbox begin", ignoreCase = true) -> {
                                val templateLength = currSubstring.length
                                i = input.indexOf(
                                    "{{starbox end}}",
                                    i,
                                    ignoreCase = true
                                ) - templateLength + 15
                            }

                            currSubstring.startsWith(
                                "{{lahirmati",
                                true
                            ) -> { // Indonesian template for date of birth and death
                                val curr =
                                    currSubstring.substringAfter('|').substringAfter('|').split('|')
                                if (curr.size <= 3) {
                                    append("lahir " + curr.joinToString("/").twas())
                                } else append(
                                    "${
                                        curr.subList(0, min(curr.size, 3)).joinToString("/").twas()
                                    } $ndash ${curr.subList(3, curr.size).joinToString("/")}"
                                )
                            }

                            listOf("{{siglo", "{{segle").fastAny {
                                currSubstring.startsWith(it, true)
                            } -> {
                                // Spanish/Catalan template for century
                                val first = currSubstring.substringAfter("{{").substringBefore('|')
                                val second = currSubstring.substringAfter('|').substringBefore('|')
                                append("$first <small>$second</small>".twas())
                            }

                            currSubstring.startsWith("{{romanes", true) -> {
                                // Spanish/Catalan template for roman numerals
                                val second = currSubstring.substringAfter('|').substringBefore('|')
                                withStyle(SpanStyle(fontSize = (fontSize - 4).sp)) {
                                    append(second)
                                }
                            }

                            currSubstring.startsWith("{{tracce", true) -> {
                                // Italian template for music track lists
                                val splitList = currSubstring.substringAfter('|')
                                    .split('|')
                                    .fastMap { it.substringAfter('=').trim() }
                                    .chunked(2)

                                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                    splitList.fastForEach {
                                        append("${it.getOrNull(0)} $mdash ${it.getOrNull(1)}\n")
                                    }
                                }
                            }

                            currSubstring.startsWith("{{reflist", true) -> {
                                val reflist = refListIndex
                                    .toSortedMap()
                                    .map { "${it.key}.\t\t${it.value}" }

                                reflist.fastForEach {
                                    withStyle(
                                        ParagraphStyle(
                                            textIndent = TextIndent(restLine = 27.sp),
                                            lineHeight = (24 * (fontSize / 16.0)).toInt().sp,
                                            lineHeightStyle = LineHeightStyle(
                                                alignment = LineHeightStyle.Alignment.Center,
                                                trim = LineHeightStyle.Trim.None
                                            )
                                        )
                                    ) { append(it.twas()) }
                                }
                            }

                            else -> {
                                // Keep a small, bounded registry of templates the renderer does
                                // not understand. One line per template name makes future parser
                                // gaps visible without turning a long article into log spam.
                                val name = templateNameAt(currSubstring, 0)
                                if (name.isNotEmpty() && unknownTemplates.size < 200 &&
                                    unknownTemplates.add(name)
                                ) {
                                    DebugLog.d("Parser", "unhandled template {{$name}}")
                                }
                                val curr = input.getOrNull(i + 1 + currSubstring.length + 1)
                                if (curr == '\n') i++
                            }
                        }
                        i += currSubstring.length + 1
                    } else append(input[i])

                '&' -> {
                    val semi = input.indexOf(';', i)
                    if (semi in (i + 2)..min(i + 32, input.length - 1)) {
                        val curr = input.substring(i, semi + 1)
                        val decoded = decodeHtmlEntity(curr)
                        if (decoded != null) {
                            append(decoded)
                        } else {
                            // Rare named entity: let the platform decode just this one.
                            try {
                                append(AnnotatedString.fromHtml(curr))
                            } catch (e: Exception) {
                                append(curr)
                            }
                        }
                        i = semi
                    } else append(input[i])
                }

                '*' ->
                    if ((i == 0 || input.getOrNull(i - 1) == '\n') && newLine) {
                        val bulletCount =
                            input.substringBeforeFrom(i, ' ').count { it == '*' }
                        val curr = input.substringBeforeFrom(i, '\n')
                        withStyle(
                            ParagraphStyle(
                                textIndent = TextIndent(restLine = (12 * bulletCount).sp),
                                lineHeight = (24 * (fontSize / 16.0)).toInt().sp,
                                lineHeightStyle = LineHeightStyle(
                                    alignment = LineHeightStyle.Alignment.Center,
                                    trim = LineHeightStyle.Trim.None
                                )
                            )
                        ) {
                            append("\t\t".repeat(bulletCount - 1))
                            append(bullet)
                            append("\t\t")
                            append(curr.substringAfterLast('*').trim().twas())
                        }
                        i += curr.length
                    } else append(input[i])

                '#' ->
                    if ((i == 0 || input.getOrNull(i - 1) == '\n') && newLine) {
                        val bulletCount =
                            input.substringBeforeFrom(i, ' ').count { it == '#' }
                        val curr = input.substringBeforeFrom(i, '\n')
                        withStyle(
                            ParagraphStyle(
                                textIndent = TextIndent(restLine = (27 * bulletCount).sp),
                                lineHeight = (24 * (fontSize / 16.0)).toInt().sp,
                                lineHeightStyle = LineHeightStyle(
                                    alignment = LineHeightStyle.Alignment.Center,
                                    trim = LineHeightStyle.Trim.None
                                )
                            )
                        ) {
                            append("\t\t".repeat(bulletCount - 1))
                            append("$number.")
                            append("\t\t")
                            append(curr.substringAfterLast('#').trim().twas())
                        }
                        i += curr.length
                        number++
                    } else append(input[i])

                '=' ->
                    if (input.getOrNull(i + 1) == '=' && input.getOrNull(i + 2) == '=') {
                        if (input.getOrNull(i + 3) == '=') {
                            if (input.getOrNull(i + 4) == '=') { // h5
                                val curr = input.substringBeforeFrom(i + 5, "=====")
                                withStyle(typography.titleMedium.toSpanStyle()) {
                                    append(curr.trim().twas())
                                }
                                i += 4 + curr.length + 5
                            } else { // h4
                                val curr = input.substringBeforeFrom(i + 4, "====")
                                withStyle(typography.titleLarge.toSpanStyle()) {
                                    append("${curr.trim()}\n".twas())
                                }
                                i += 3 + curr.length + 4
                            }
                        } else { // h3
                            val curr = input.substringBeforeFrom(i + 3, "===")
                            withStyle(typography.headlineSmall.toSpanStyle()) {
                                append("${curr.trim()}\n".twas())
                            }
                            i += 2 + curr.length + 3
                        }
                    } else append(input[i])

                '\'' ->
                    if (input.getOrNull(i + 1) == '\'' && input.getOrNull(i + 2) == '\'') {
                        if (!bold) {
                            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                            bold = true
                        } else {
                            pop()
                            bold = false
                        }
                        i += 2
                    } else if (input.getOrNull(i + 1) == '\'') {
                        if (!italic) {
                            pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                            italic = true
                        } else {
                            pop()
                            italic = false
                        }
                        i += 1
                    } else append(input[i])

                '[' ->
                    if (input.getOrNull(i + 1) == '[') {
                        val curr = input.substringBeforeFrom(i + 2, "]]")
                        if (!curr.startsWith("File:", ignoreCase = true)) {
                            val target = curr.substringBefore('|').substringBefore('#')
                            withLink(
                                LinkAnnotation.Clickable(
                                    tag = target,
                                    styles = TextLinkStyles(
                                        SpanStyle(color = colorScheme.primary)
                                    )
                                ) {
                                    loadPage(target)
                                }
                            ) {
                                append(curr.substringAfter('|').trim().twas())
                            }
                            i += 1 + curr.length + 2
                        } else i += substringMatchingParen('[', ']', i).length - 1
                    } else if (input.getOrNull(i + 1) == 'h') {
                        val linkText = input.substringMatchingParen('[', ']', i)
                        withLink(
                            LinkAnnotation.Url(
                                "",
                                TextLinkStyles(
                                    SpanStyle(color = colorScheme.primary)
                                )
                            ) {
                                showRef(linkText.substringBefore(' ').removePrefix("["))
                            }
                        ) {
                            append(
                                (linkText.substringAfter(' ').removeSuffix("]")
                                    .trim() + " \uD83D\uDD17")
                            )
                        }
                        i += linkText.length - 1
                    } else append(input[i])

                else -> append(input[i])
            }
            i++
        }
    }
}

fun String.substringMatchingParen(
    openingParen: Char,
    closingParen: Char,
    startIndex: Int = 0
): String {
    var i = startIndex
    var stack = 0
    while (i < length) {
        if (this[i] == openingParen) stack++
        else if (this[i] == closingParen) stack--

        if (stack == 0) break

        i++
    }

    return if (i < length) this.substring(startIndex, i + 1)
    else this
}

/**
 * Same result as `substring(start).substringBefore(delimiter)`, but only the returned part is
 * copied. The old form copied the whole rest of the text for every link, tag and list item,
 * which is quadratic on long sections (big lists took seconds to parse).
 */
fun String.substringBeforeFrom(start: Int, delimiter: String): String {
    val end = indexOf(delimiter, start)
    return if (end < 0) substring(start) else substring(start, end)
}

fun String.substringBeforeFrom(start: Int, delimiter: Char): String {
    val end = indexOf(delimiter, start)
    return if (end < 0) substring(start) else substring(start, end)
}

/** Same result as `substring(start).substringMatchingParen(open, close)` without copying the rest of the text. */
fun String.substringMatchingParenFrom(start: Int, openingParen: Char, closingParen: Char): String {
    var i = start
    var stack = 0
    while (i < length) {
        if (this[i] == openingParen) stack++
        else if (this[i] == closingParen) stack--

        if (stack == 0) break

        i++
    }

    return if (i < length) substring(start, i + 1)
    else substring(start)
}

fun String.buildRefList() {
    // Uses indexOf instead of copying the whole remaining text for every named ref, which was
    // O(length x refs) on long articles. The old scan only found the exact lowercase spelling
    // `<ref name` with no space around `=`, so `<ref name = "x">` and `<REF NAME="x">` were
    // silently missing from the reference list.
    var i = this.indexOf("<ref", ignoreCase = true)
    while (i >= 0) {
        val tagEnd = this.indexOf('>', i)
        if (tagEnd < 0) break
        val open = this.substring(i, tagEnd + 1)
        val name = refNameAttrRegex.find(open)?.let { m ->
            (m.groupValues[1].ifEmpty { m.groupValues[2] })
                .ifEmpty { m.groupValues[3] }
                .trim()
        }
        if (!open.trimEnd().endsWith("/>") && !name.isNullOrEmpty()) {
            val close = this.indexOf("</ref>", tagEnd, ignoreCase = true)
            val refWt = this.substring(tagEnd + 1, if (close < 0) this.length else close)
            refList[name] = refWt
        }
        i = this.indexOf("<ref", tagEnd + 1, ignoreCase = true)
    }
}

fun String.splitNotInBraces(delimiter: Char, open: Char = '[', close: Char = ']'): List<String> {
    var stack = 0
    val out = mutableListOf<String>()
    val curr = StringBuilder()

    for (c in this) {
        if (c == open) stack++
        else if (c == close) stack--

        if (c == delimiter && stack == 0) {
            out.add(curr.toString())
            curr.clear()
        } else {
            curr.append(c)
        }
    }

    if (curr.isNotEmpty()) {
        out.add(curr.toString())
    }

    return out
}

/**
 * Same as [toWikitextAnnotatedString] but never throws: wikitext in the wild is wildly irregular
 * and the hand-written converter can hit an unexpected shape (bad index, deep recursion...).
 * One odd paragraph, table cell or caption must not abort the whole article (or crash the app
 * when it happens during composition), so on failure the raw text is shown instead.
 */
fun String.toWikitextAnnotatedStringSafe(
    colorScheme: ColorScheme,
    typography: Typography,
    loadPage: (String) -> Unit,
    fontSize: Int,
    newLine: Boolean = true,
    inIndentCode: Boolean = false,
    showRef: (String) -> Unit,
): AnnotatedString = try {
    toWikitextAnnotatedString(
        colorScheme = colorScheme,
        typography = typography,
        loadPage = loadPage,
        fontSize = fontSize,
        newLine = newLine,
        inIndentCode = inIndentCode,
        showRef = showRef
    )
} catch (e: Exception) {
    DebugLog.w("Parser", "wikitext conversion failed, showing plain text (${length} chars)", e)
    AnnotatedString(this)
} catch (e: StackOverflowError) {
    DebugLog.w("Parser", "wikitext conversion overflowed the stack, showing plain text (${length} chars)", e)
    AnnotatedString(this)
}

/**
 * Footnote bookkeeping shared by every wikitext conversion in the process.
 *
 * It is touched from several threads at once: the article loader (Dispatchers.IO) while the
 * infobox / table composables parse their own text on another IO thread, and the next article
 * clearing it. Plain LinkedHashMaps threw ConcurrentModificationException (e.g. in
 * `refListIndex.toSortedMap()`), which showed up as random crashes while an article was loading.
 * Concurrent maps fix the crash; the two helpers make "take the next number" atomic.
 */
object ReferenceData {
    @Volatile
    var refCount = 1
    val refList: MutableMap<String, String> = java.util.concurrent.ConcurrentHashMap()
    val refListIndex: MutableMap<Int, String> = java.util.concurrent.ConcurrentHashMap()
    val refListCount: MutableMap<String, Int> = java.util.concurrent.ConcurrentHashMap()

    /** A footnote with its own text: always gets a fresh number. */
    @Synchronized
    fun nextInlineRef(text: String): Int {
        val n = refCount
        refListIndex[n] = text
        refCount = n + 1
        return n
    }

    /** A named footnote: same text -> same number, numbered on first use. */
    @Synchronized
    fun numberForNamedRef(text: String): Int {
        refListCount[text]?.let { return it }
        val n = refCount
        refListCount[text] = n
        refListIndex[n] = text
        refCount = n + 1
        return n
    }

    var refTemplate = "{{cite"
    val refTemplates = listOf("{{cite", "{{lien", "{{cita|")
    val infoboxTemplates = listOf(
        "{{infobox", "{{taxobox", "{{automatic taxobox", "{{speciesbox", "{{subspeciesbox",
        "{{infraspeciesbox", "{{hybridbox", "{{ichnobox", "{{oobox", "{{paraphyletic group",
        "{{картка"
    )
}