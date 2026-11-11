package com.wikirandomizerp.app.parser

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.max
import kotlin.text.Typography.mdash

/**
 * Converts a Wikitable into a format which is possible to be displayed by the app
 *
 * @param table The Wikitable to be parsed
 * @param colorScheme [ColorScheme] composition local
 * @param typography [Typography] composition local
 * @param loadPage Function to load a page, to be provided by the [androidx.lifecycle.ViewModel]
 * @param showRef Function to show the reference bottom sheet/popup with the given reference string
 * @param fontSize Font size in app preferences
 *
 * @return A [Pair] containing the table caption at the first value, and the table matrix as the
 * second value (both are [AnnotatedString]s)
 */
suspend fun parseWikitable(
    table: String,
    colorScheme: ColorScheme,
    typography: Typography,
    loadPage: (String) -> Unit,
    showRef: (String) -> Unit,
    fontSize: Int
): Pair<AnnotatedString, List<List<AnnotatedString>>> = withContext(Dispatchers.IO) {
    val rows = mutableListOf<MutableList<AnnotatedString>>()
    var caption = AnnotatedString("")
    val lines = table.lines().fastMap { it.trim() }.fastFilter { it.isNotEmpty() }
    val styling =
        "class=|align=|scope=|style=|rowspan=|colspan=|width=|[^{]nowrap|data-sort".toRegex()

    var currentRow = mutableListOf<AnnotatedString>()
    val rowSpan = mutableMapOf<Int, Int>()
    var maxSize = 0
    var lastNestedTableEnd = 2
    var insideTable = false

    for (line in lines) {
        val currSize = currentRow.size
        val spanLeft = rowSpan[currSize]
        if (spanLeft != null) {
            if (spanLeft - 1 > 0) {
                currentRow.add(AnnotatedString(""))
                rowSpan[currSize] = spanLeft - 1
            } else
                rowSpan.remove(currSize)
        }
        when {
            line.startsWith("{|") -> {
                // Table begin
                if (!insideTable) insideTable = true
                else {
                    currentRow[currentRow.lastIndex] += AnnotatedString(
                        table.substringMatchingParen(
                            '{',
                            '}',
                            table.indexOf("{|", lastNestedTableEnd)
                        )
                    )
                    lastNestedTableEnd = table.indexOf("|}", lastNestedTableEnd)
                }
            }

            line.startsWith("|+") -> {
                // Table caption. MediaWiki allows cell attributes before the caption text:
                //     |+ style="text-align:centre;" | Panthera tigris tigris
                // Only the part after the attribute separator is the caption; otherwise the
                // whole `style=...` string used to be rendered as a bold header.
                var captionText = line.removePrefix("|+").trim()
                val attributeEnd = captionText.indexOf('|')
                if (attributeEnd >= 0 &&
                    captionText.substring(0, attributeEnd).contains('=')
                ) {
                    captionText = captionText.substring(attributeEnd + 1).trim()
                }
                caption = "'''$captionText'''".toWikitextAnnotatedStringSafe(
                    colorScheme,
                    typography,
                    loadPage,
                    fontSize,
                    showRef = showRef
                )
            }

            line.startsWith("|}") -> {
                // Table end
                if (currentRow.isNotEmpty()) {
                    rows.add(currentRow)
                    maxSize = max(currentRow.size, maxSize)
                    currentRow = mutableListOf()
                }
                insideTable = false
            }

            line.startsWith("|-") -> {
                // Row delimiter
                if (currentRow.isNotEmpty()) {
                    rows.add(currentRow)
                    maxSize = max(currentRow.size, maxSize) // Keep track of max row size
                    currentRow = mutableListOf()
                }
            }

            line.startsWith("!") -> {
                // Header cells, can be split by '!!' or on multiple lines
                val content = line.removePrefix("!")
                val cells = mutableListOf<AnnotatedString>()
                var sep = "!!"
                val pipeSep = "||" in line
                if ("!!" in line || pipeSep) {
                    if (pipeSep) sep = "||"
                    content.split(sep).fastForEach {
                        var colspan = 1
                        if (it.contains("colspan")) {
                            colspan = it.substringAfter("colspan=").substringBefore('|')
                                .substringBefore(' ').trim('"')
                                .toIntOrNull() ?: 1
                        }
                        val curr =
                            if (it.contains(styling)) {
                                it.substringAfter('|')
                            } else it

                        if (it.contains("rowspan")) { // Read rowspan count of cell
                            rowSpan[currentRow.size] =
                                it.substringAfter("rowspan=").substringBefore('|')
                                    .substringBefore(' ').trim('"')
                                    .toIntOrNull() ?: 1
                        }

                        cells.add(
                            ("'''${curr.trim()}'''")
                                .toWikitextAnnotatedStringSafe(
                                    colorScheme,
                                    typography,
                                    loadPage,
                                    fontSize,
                                    showRef = showRef
                                )
                        )
                        repeat(colspan - 1) {
                            cells.add(AnnotatedString(""))
                        }
                    }
                } else {
                    var colspan = 1
                    if (content.contains("colspan")) {
                        colspan = content.substringAfter("colspan=").substringBefore('|')
                            .substringBefore(' ').trim('"')
                            .toIntOrNull() ?: 1
                    }
                    val curr =
                        if (content.contains(styling)) {
                            content.substringAfter('|')
                        } else content

                    if (content.contains("rowspan")) { // Store rowspan count of cell
                        rowSpan[currentRow.size] =
                            content.substringAfter("rowspan=").substringBefore('|')
                                .substringBefore(' ').trim('"')
                                .toIntOrNull() ?: 1
                    }

                    cells.add(
                        ("'''${curr.trim()}'''")
                            .toWikitextAnnotatedStringSafe(
                                colorScheme,
                                typography,
                                loadPage,
                                fontSize,
                                showRef = showRef
                            )
                    )
                    repeat(colspan - 1) {
                        cells.add(AnnotatedString(""))
                    }
                }
                currentRow.addAll(cells)
            }

            line.startsWith("|") -> {
                // Data cells, can be split by '||' or on multiple lines
                val content = line.removePrefix("|")
                val cells = mutableListOf<AnnotatedString>()
                if ("||" in content) {
                    content.split("||").fastForEach {
                        var colspan = 1
                        if (it.contains("colspan")) {
                            colspan = it.substringAfter("colspan=").substringBefore('|')
                                .substringBefore(' ').trim('"')
                                .toIntOrNull() ?: 1
                        }
                        val curr =
                            if (it.contains(styling)) {
                                it.substringAfter('|')
                            } else it

                        if (it.contains("rowspan")) { // Read rowspan count of cell
                            rowSpan[currentRow.size] =
                                it.substringAfter("rowspan=").substringBefore('|')
                                    .substringBefore(' ').trim('"')
                                    .toIntOrNull() ?: 1
                        }

                        if (it.contains("{{n/a}}", ignoreCase = true)) {
                            cells.add(AnnotatedString("$mdash"))
                        } else {
                            cells.add(
                                curr.trim()
                                    .toWikitextAnnotatedStringSafe(
                                        colorScheme,
                                        typography,
                                        loadPage,
                                        fontSize,
                                        showRef = showRef
                                    )
                            )
                        }
                        repeat(colspan - 1) {
                            cells.add(AnnotatedString(""))
                        }
                    }
                } else {
                    var colspan = 1
                    if (content.contains("colspan")) {
                        colspan = content.substringAfter("colspan=").substringBefore('|')
                            .substringBefore(' ').trim('"')
                            .toIntOrNull() ?: 1
                    }
                    val curr =
                        if (content.contains(styling)) {
                            content.substringAfter('|')
                        } else content

                    if (content.contains("rowspan")) { // Read rowspan count of cell
                        rowSpan[currentRow.size] =
                            content.substringAfter("rowspan=").substringBefore('|')
                                .substringBefore(' ').trim('"')
                                .toIntOrNull() ?: 1
                    }

                    if (content.contains("{{n/a}}", ignoreCase = true)) {
                        cells.add(AnnotatedString("$mdash"))
                    } else {
                        cells.add(
                            curr.trim()
                                .toWikitextAnnotatedStringSafe(
                                    colorScheme,
                                    typography,
                                    loadPage,
                                    fontSize,
                                    showRef = showRef
                                )
                        )
                    }
                    repeat(colspan - 1) {
                        cells.add(AnnotatedString(""))
                    }
                }
                currentRow.addAll(cells)
            }

            else -> {
                // Possibly a continuation of a multi-line cell?
                if (insideTable) {
                    val lastIndex = currentRow.lastIndex
                    if (lastIndex != -1)
                        currentRow[lastIndex] += line.trim()
                            .toWikitextAnnotatedStringSafe(
                                colorScheme,
                                typography,
                                loadPage,
                                fontSize,
                                showRef = showRef
                            )
                }
            }
        }
    }

    if (currentRow.isNotEmpty()) {
        rows.add(currentRow)
        maxSize = max(currentRow.size, maxSize)
    }

    // Sanity check: every row must have the same number of cells. Use the widest row that was
    // actually seen rather than only the widest one before a `|-`, otherwise a table whose last
    // row is wider than all previous rows could index outside the matrix in the renderer.
    val columnCount = max(maxSize, rows.maxOfOrNull { it.size } ?: 0)
    for (row in rows) {
        repeat(columnCount - row.size) {
            row.add(AnnotatedString(""))
        }
    }

    Pair(caption, rows)
}

/**
 * Converts an Infobox into a format which is possible to be displayed by the app
 *
 * @param infoboxSource The Infobox to be parsed
 * @param colorScheme [ColorScheme] composition local
 * @param typography [Typography] composition local
 * @param loadPage Function to load a page, to be provided by the [androidx.lifecycle.ViewModel]
 * @param showRef Function to show the reference bottom sheet/popup with the given reference string
 * @param fontSize Font size in app preferences
 *
 * @return a [List] of [Pair]s of the Infobox entries, in key-value format.
 */
suspend fun parseInfobox(
    infoboxSource: String,
    colorScheme: ColorScheme,
    typography: Typography,
    loadPage: (String) -> Unit,
    showRef: (String) -> Unit,
    fontSize: Int
): List<Pair<AnnotatedString, AnnotatedString>> = withContext(Dispatchers.IO) {
    val rows = mutableListOf<Pair<AnnotatedString, AnnotatedString>>()
    val locale = Locale.getDefault()

    val lines = infoboxSource.lines().fastFilter { it.isNotEmpty() }.drop(1)
    var currentRowKey = ""
    var currentRowVal = ""

    lines.fastForEach { item ->
        val it = item.trim()
        if (it.startsWith('|') && it.contains('=')) {
            if (currentRowVal.matches(imageRowRegex)) { // Add image data in plaintext
                rows.add(Pair(AnnotatedString(currentRowKey), AnnotatedString(currentRowVal)))
            } else if (currentRowVal.isNotBlank()) {
                rows.add(
                    Pair(
                        currentRowKey.toWikitextAnnotatedStringSafe(
                            colorScheme,
                            typography,
                            loadPage,
                            fontSize,
                            showRef = showRef
                        ),
                        currentRowVal.toWikitextAnnotatedStringSafe(
                            colorScheme,
                            typography,
                            loadPage,
                            fontSize,
                            showRef = showRef
                        )
                    )
                )
            }
            currentRowKey = ""
            currentRowVal = ""

            val thisRow = it.split('=', limit = 2)
            currentRowKey = thisRow[0]
                .trim(' ', '|')
                .replace('_', ' ')
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
            currentRowVal = thisRow[1].trim()
        } else {
            currentRowVal += '\n' + it
        }
    }

    if (currentRowVal.isNotBlank()) {
        rows.add(
            Pair(
                currentRowKey.toWikitextAnnotatedStringSafe(
                    colorScheme,
                    typography,
                    loadPage,
                    fontSize,
                    showRef = showRef
                ),
                currentRowVal.trim('\n', ' ', '}').toWikitextAnnotatedStringSafe(
                    colorScheme,
                    typography,
                    loadPage,
                    fontSize,
                    showRef = showRef
                )
            )
        )
    }

    rows.fastFilter { !it.first.matches(imageMetaKeyRegex) && it.second.isNotBlank() }
}

private val imageRowRegex = ".{1,6}:.+".toRegex()

/**
 * Keys whose values are picture metadata, not infobox rows. Covers `image`, `image2`, …, the
 * many spellings of their captions (`image caption`, `image_caption`, `caption2`, …), alt text
 * and size hints. These used to surface as raw `File:…` text rows.
 */
private val imageMetaKeyRegex = Regex(
    "^(?:image|photo|img|logo|map|picture)\\s*\\d*$|" +
            "^(?:image|photo|img|logo|map|picture)?\\s*\\d*\\s*caption\\s*\\d*$|" +
            "^(?:image|photo|img|logo|map|picture)?\\s*alt\\s*\\d*$|" +
            "^(?:image|photo|img|logo|map|picture)\\s*(?:size|upright|width)\\s*\\d*$|" +
            "^(?:caption|alt)\\s*\\d*$",
    RegexOption.IGNORE_CASE
)
