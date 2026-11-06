package org.nsh07.wikireader.parser

import org.nsh07.wikireader.parser.ReferenceData.infoboxTemplates
import kotlin.math.min

/**
 * One block of a wikitext section.
 *
 * [raw] blocks are handed to the renderer unchanged (pictures, galleries, tables, infoboxes,
 * math and cladograms are drawn by dedicated composables). Everything else is running text and
 * goes through [toWikitextAnnotatedStringSafe].
 */
class WikitextBlock(val text: String, val raw: Boolean)

/**
 * Splits a wikitext section into the blocks the article renderer consumes.
 *
 * This used to live inside `HomeScreenViewModel.parseWikitext`, which made it impossible to test
 * against a real article. It is pure Kotlin now, so regressions (a table swallowing the following
 * picture, a cladogram disappearing inside a layout table, a template leaking into the prose) can
 * be caught by unit tests.
 */
fun splitWikitextBlocks(wikitext: String): List<WikitextBlock> {
    val out = ArrayList<WikitextBlock>()
    val currSb = StringBuilder()
    var i = 0
    var stack = 0

    fun flush() {
        if (currSb.isNotEmpty()) {
            out.add(WikitextBlock(currSb.toString(), raw = false))
            currSb.clear()
        }
    }

    while (i < wikitext.length) {
        if (wikitext[i] == '{') stack++
        else if (wikitext[i] == '}' && stack > 0) stack--

        if (wikitext[i] == '<') {
            when {
                wikitext.startsWith("<math display", i) -> {
                    val end = wikitext.indexOf("</math>", i).let { if (it < 0) wikitext.length else it }
                    val block = wikitext.substring(i, end)
                    flush()
                    out.add(WikitextBlock(block, raw = true))
                    i += block.length + 7
                }

                wikitext.startsWith("<imagemap", i) -> {
                    val end = wikitext.indexOf("</imagemap>", i).let { if (it < 0) wikitext.length else it }
                    val block = wikitext.substring(i, end)
                    val picture = imagemapBlock(block)
                    if (picture != null) {
                        flush()
                        out.add(WikitextBlock(picture, raw = true))
                    }
                    i += block.length + 11
                }

                wikitext.startsWith("<gallery", i) -> {
                    val end = wikitext.indexOf("</gallery>", i).let { if (it < 0) wikitext.length else it }
                    val block = wikitext.substring(i, end)
                    flush()
                    out.add(WikitextBlock(block, raw = true))
                    i += block.length + 10
                }

                else -> currSb.append(wikitext[i])
            }
        } else if (stack == 0 && wikitext[i] == '[' && wikitext.getOrNull(i + 1) == '[') {
            val link = wikitext.substringMatchingParen('[', ']', i)
            // Pictures become their own block, complete with caption. Non-picture links stay in
            // the running text so the text parser can draw them as links.
            val image = wikitextFileLinkBlock(link)
            if (image != null) {
                flush()
                out.add(WikitextBlock(image, raw = true))
                i += link.length - 1
            } else {
                currSb.append(wikitext[i])
            }
        } else if (wikitext[i] == '{') {
            if (wikitext.getOrNull(i + 1) == '|') {
                val table = wikitext.substringMatchingParen('{', '}', i)
                flush()
                // Plant/animal cladograms often live inside a layout table. Emit the trees as
                // their own blocks; otherwise the table renderer swallows them whole. Scanning
                // the whole table also finds trees in nested tables.
                val clades = extractCladeBlocks(table)
                if (clades.isNotEmpty()) {
                    clades.forEach { out.add(WikitextBlock(it, raw = true)) }
                } else {
                    out.add(WikitextBlock(table, raw = true))
                }
                i += table.length - 1
                if (stack > 0) stack--
            } else if (
                stack <= 2 && wikitext.getOrNull(i + 1) == '{' &&
                wikitext.substring(i, min(i + 24, wikitext.length)).let { sub ->
                    infoboxTemplates.any { sub.startsWith(it, true) }
                }
            ) {
                val template = wikitext.substringMatchingParen('{', '}', i)
                flush()
                // Pictures inside the infobox are the only images on many species articles, and
                // AsyncInfobox draws its card collapsed. Emit them as ordinary picture blocks so
                // they appear (with captions) above the card. `parseInfobox` filters the image
                // parameters out of the card itself.
                infoboxPictures(template).forEach { picture ->
                    out.add(WikitextBlock(pictureBlock(picture.name, picture.caption), raw = true))
                }
                out.add(WikitextBlock(template, raw = true))
                i += template.length - 1
                stack = (stack - 2).coerceAtLeast(0)
            } else if (
                stack <= 2 && wikitext.getOrNull(i + 1) == '{' &&
                isBlockTemplateStart(wikitext, i)
            ) {
                // Picture templates ({{Multiple image}}, {{Gallery}}...) and {{Clade}} trees.
                val blockTemplate = blockTemplateAt(wikitext, i)
                if (blockTemplate != null) {
                    flush()
                    blockTemplate.blocks.forEach { out.add(WikitextBlock(it, raw = true)) }
                    i += blockTemplate.length - 1
                    stack = (stack - 2).coerceAtLeast(0)
                } else {
                    currSb.append(wikitext[i])
                }
            } else {
                currSb.append(wikitext[i])
            }
        } else {
            currSb.append(wikitext[i])
        }
        i++
    }

    flush()
    return splitCladesInText(out)
}

/**
 * A clade nested inside another template (`{{quote|{{clade…}}}}`, `{{blockquote|…}}`) can pass
 * the depth guard in the main loop and end up in a running-text block, where the text renderer
 * discards it. Lift every remaining tree out of the text here so it always reaches [Cladogram].
 */
private fun splitCladesInText(blocks: List<WikitextBlock>): List<WikitextBlock> {
    if (blocks.none { !it.raw && it.text.contains("{{") }) return blocks
    val out = ArrayList<WikitextBlock>(blocks.size)
    for (block in blocks) {
        if (block.raw || !block.text.contains("{{")) {
            out.add(block)
            continue
        }
        val pieces = splitCladeTemplates(block.text)
        if (pieces.none { it.first }) {
            out.add(block)
            continue
        }
        for ((isClade, text) in pieces) {
            if (text.isBlank()) continue
            out.add(WikitextBlock(if (isClade) text.trim() else text, raw = isClade))
        }
    }
    return out
}
