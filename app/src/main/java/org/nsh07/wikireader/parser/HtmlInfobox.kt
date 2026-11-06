package org.nsh07.wikireader.parser

/**
 * An infobox read from Wikipedia's REST HTML, most importantly the taxobox of a plant, animal or
 * fungus article ("Scientific classification", "Binomial name", "Synonyms").
 *
 * HTML mode used to delete every `<table class="infobox">` and keep only its pictures, so the
 * whole classification table (Kingdom / Clade / Order / Family / Genus / Species) never appeared.
 * [scanHtmlInfobox] now converts the table into this small model, [encodeHtmlInfobox] turns it
 * into ONE line of text that travels through the existing section pipeline exactly like a
 * `[[File:…]]` picture line, and `HtmlInfoboxCard` draws it.
 *
 * Cells hold a tiny HTML subset (`a`, `b`, `i`, `sup`, `sub`, `small`, `br`) that
 * `AnnotatedString.fromHtml` understands, so links stay tappable and italic species names stay
 * italic.
 *
 * This file is plain Kotlin so it can be unit tested on the JVM.
 */
sealed class HtmlInfoboxRow {
    /** A coloured band: "Scientific classification", "Binomial name", "Synonyms", … */
    class Header(val color: Int?, val html: String) : HtmlInfoboxRow()

    /** `Kingdom: | Plantae`. */
    class Field(val labelHtml: String, val valueHtml: String) : HtmlInfoboxRow()

    /** A cell that spans the whole width, for example the binomial name. */
    class Wide(val html: String) : HtmlInfoboxRow()

    /** One entry of a list inside a wide cell, for example one synonym. */
    class Item(val html: String) : HtmlInfoboxRow()
}

/**
 * @property taxobox true for `class="infobox biota"` tables.
 * @property titleColor 0xRRGGBB of the title band, or null when the table gave none.
 * @property titleHtml the name shown at the top of the table (may be empty).
 */
class HtmlInfobox(
    val taxobox: Boolean,
    val titleColor: Int?,
    val titleHtml: String,
    val rows: List<HtmlInfoboxRow>
)

/** First characters of the one-line block. Lower case on purpose: block detection ignores case. */
const val HTML_INFOBOX_MARKER = "{{wrhtmlinfobox"

private const val RECORD_SEP = '\u001E'
private const val FIELD_SEP = '\u001F'

// Raw `<` and `>` are replaced while the block travels through the HTML sanitiser, which would
// otherwise treat the cell markup as real tags. Private-use characters never occur in articles
// in a way that matters.
private const val LT_ESCAPE = '\uE000'
private const val GT_ESCAPE = '\uE001'

private fun escapeCell(html: String): String {
    val sb = StringBuilder(html.length)
    for (c in html) {
        if (c == '<') {
            sb.append(LT_ESCAPE)
        } else if (c == '>') {
            sb.append(GT_ESCAPE)
        } else if (c == RECORD_SEP || c == FIELD_SEP || c == '\n' || c == '\r' || c == '\t') {
            sb.append(' ')
        } else {
            sb.append(c)
        }
    }
    return sb.toString()
}

private fun unescapeCell(text: String): String =
    text.replace(LT_ESCAPE, '<').replace(GT_ESCAPE, '>')

private fun colorField(color: Int?): String =
    if (color == null) "" else Integer.toHexString(color and 0xFFFFFF).padStart(6, '0')

private fun parseColorField(text: String?): Int? {
    if (text.isNullOrEmpty()) return null
    return text.toIntOrNull(16)
}

/** Serialises [box] into a single line that starts with [HTML_INFOBOX_MARKER]. */
fun encodeHtmlInfobox(box: HtmlInfobox): String {
    val sb = StringBuilder(HTML_INFOBOX_MARKER)
    sb.append(FIELD_SEP).append(if (box.taxobox) "taxobox" else "infobox")
    sb.append(FIELD_SEP).append(colorField(box.titleColor))
    sb.append(FIELD_SEP).append(escapeCell(box.titleHtml))
    for (row in box.rows) {
        sb.append(RECORD_SEP)
        when (row) {
            is HtmlInfoboxRow.Header -> {
                sb.append('H').append(FIELD_SEP).append(colorField(row.color))
                sb.append(FIELD_SEP).append(escapeCell(row.html))
            }

            is HtmlInfoboxRow.Field -> {
                sb.append('F').append(FIELD_SEP).append(escapeCell(row.labelHtml))
                sb.append(FIELD_SEP).append(escapeCell(row.valueHtml))
            }

            is HtmlInfoboxRow.Wide -> {
                sb.append('W').append(FIELD_SEP).append(escapeCell(row.html))
            }

            is HtmlInfoboxRow.Item -> {
                sb.append('I').append(FIELD_SEP).append(escapeCell(row.html))
            }
        }
    }
    sb.append("}}")
    return sb.toString()
}

/** Reads a block produced by [encodeHtmlInfobox]; null when [text] is not such a block. */
fun parseHtmlInfoboxBlock(text: String): HtmlInfobox? {
    val trimmed = text.trim()
    if (!trimmed.startsWith(HTML_INFOBOX_MARKER, ignoreCase = true)) return null
    var body = trimmed.substring(HTML_INFOBOX_MARKER.length)
    if (body.endsWith("}}")) body = body.substring(0, body.length - 2)

    val records = body.split(RECORD_SEP)
    val head = records[0].split(FIELD_SEP)
    // head = ["", kind, titleColor, titleHtml]
    if (head.size < 4) return null
    val taxobox = head[1] == "taxobox"
    val titleColor = parseColorField(head[2])
    val titleHtml = unescapeCell(head[3])

    val rows = ArrayList<HtmlInfoboxRow>()
    for (index in 1 until records.size) {
        val fields = records[index].split(FIELD_SEP)
        when (fields[0]) {
            "H" -> if (fields.size >= 3) {
                rows.add(HtmlInfoboxRow.Header(parseColorField(fields[1]), unescapeCell(fields[2])))
            }

            "F" -> if (fields.size >= 3) {
                rows.add(HtmlInfoboxRow.Field(unescapeCell(fields[1]), unescapeCell(fields[2])))
            }

            "W" -> if (fields.size >= 2) {
                rows.add(HtmlInfoboxRow.Wide(unescapeCell(fields[1])))
            }

            "I" -> if (fields.size >= 2) {
                rows.add(HtmlInfoboxRow.Item(unescapeCell(fields[1])))
            }
        }
    }
    if (rows.isEmpty()) return null
    return HtmlInfobox(taxobox, titleColor, titleHtml, rows)
}
