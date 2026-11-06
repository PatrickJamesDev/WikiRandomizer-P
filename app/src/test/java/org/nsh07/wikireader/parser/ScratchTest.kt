package org.nsh07.wikireader.parser

import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScratchTest {
    private fun render(s: String): String = s.toWikitextAnnotatedStringSafe(
        colorScheme = lightColorScheme(),
        typography = Typography(),
        loadPage = {},
        fontSize = 16,
        showRef = {}
    ).text

    @Test
    fun peaConversionMatchesWikipedia() {
        assertEquals(
            "The average pea weighs between 100 and 360 milligrams (1+1\u20442\u20135+1\u20442 grains).",
            render("The average pea weighs between {{convert|100|and(-)|360|mg|grain|frac=2|abbr=off}}.")
        )
    }

    @Test
    fun convertedRangesKeepTextAroundThem() {
        val out = render("before {{convert|10|to|20|km|mi|abbr=on}} after")
        assertTrue(out, out.startsWith("before 10 to 20 km ("))
        assertTrue(out, out.endsWith(") after"))
    }

    @Test
    fun imageCaptionKeepsWholeCaptionAndLinks() {
        assertEquals(
            "[[File:Pea.jpg|Peas in a pod, see [[Pea|the plant]]]]",
            wikitextFileLinkBlock("[[File:Pea.jpg|thumb|upright=1.2|alt=Green peas|Peas in a pod, see [[Pea|the plant]]]]")
        )
        assertEquals(
            "[[File:Pea.jpg|A caption|invert]]",
            wikitextFileLinkBlock("[[File:Pea.jpg|thumb|class=skin-invert-image|A caption]]")
        )
    }

    @Test
    fun peaArticleImagesHaveCaptions() {
        val wikitext = java.io.File("src/test/resources/pea.wikitext").takeIf { it.exists() }?.readText()
            ?: return
        var i = 0
        var count = 0
        while (i < wikitext.length) {
            val start = wikitext.indexOf("[[", i)
            if (start < 0) break
            val link = wikitext.substringMatchingParen('[', ']', start)
            val block = wikitextFileLinkBlock(link)
            if (block != null) {
                count++
            }
            i = start + maxOf(link.length, 2)
        }
        assertTrue("expected several images in the Pea article", count > 3)
    }

    @Test
    fun mammalCladeIsSplitIntoRenderableBlocks() {
        val wikitext = java.io.File("src/test/resources/mammal.wikitext").readText()
        val blocks = splitWikitextBlocks(wikitext)
        val clades = blocks.filter { it.raw && it.text.trimStart().startsWith("{{clade", true) }
        println("clade blocks: ${clades.size}")
        clades.forEachIndexed { index, block ->
            val parsed = parseClade(block.text)
            println("clade $index len=${block.text.length} parsed=${parsed?.children?.size}")
            assertTrue("clade $index did not parse: ${block.text.take(200)}", parsed != null)
        }
        assertTrue("expected at least one clade block, got ${clades.size}", clades.isNotEmpty())
    }
}
