package org.nsh07.wikireader.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nsh07.wikireader.data.parseSections

class WikitextBlocksTest {

    private fun blocksOf(text: String): List<WikitextBlock> = splitWikitextBlocks(text)

    @Test
    fun plantArticleEmitsRealCladeBlocksFromInsideTables() {
        val wikitext = javaClass.classLoader!!.getResource("athyriaceae-wikitext.txt")!!.readText()
        val sections = parseSections(wikitext)
        val allBlocks = sections.flatMap { blocksOf(it) }
        val clades = allBlocks.filter {
            it.raw && it.text.trimStart().startsWith("{{clade", ignoreCase = true)
        }
        assertTrue("expected both Athyriaceae trees, found ${clades.size}", clades.size >= 2)
        // A cladogram must never be left inside a running-text block (it would render as source).
        assertFalse(
            allBlocks.any { !it.raw && it.text.contains("{{clade", ignoreCase = true) }
        )
    }

    @Test
    fun pictureAfterATableIsStillSeparated() {
        val text = """
            {| class="wikitable"
            | cell
            |}
            [[File:Pea.jpg|thumb|Peas in a pod]]
            Some prose after the picture.
        """.trimIndent()
        val blocks = blocksOf(text)
        assertTrue(blocks.any { it.raw && it.text.startsWith("{|") })
        val image = blocks.firstOrNull { it.raw && it.text.startsWith("[[File:") }
        assertEquals("[[File:Pea.jpg|Peas in a pod]]", image?.text)
        assertTrue(blocks.any { !it.raw && it.text.contains("Some prose after") })
    }

    @Test
    fun templateSourceNeverLeaksIntoProse() {
        val text = "Before {{convert|100|mg|grain|abbr=off}} after."
        val blocks = blocksOf(text)
        assertEquals(1, blocks.size)
        assertFalse(blocks[0].raw)
        assertTrue(blocks[0].text.contains("{{convert"))
    }
}
