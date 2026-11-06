package org.nsh07.wikireader.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlSectionsTest {

    @Test
    fun ignoresParserOutputClassInsideInlineCssBeforeProse() {
        // Real REST HTML has citation CSS containing `.mw-parser-output` near the end of the page.
        // The class string inside <style> is not a wrapper and must never become the content start.
        val html = """
            <html><body class="mw-parser-output">
              <section>
                <p>All of the article prose belongs to the intro.</p>
                <style>.mw-parser-output cite.citation{font-style:inherit}</style>
                <h2>See also</h2><p>Related reading.</p>
                <h2>References</h2>
                <style>.mw-parser-output .citation{word-wrap:break-word}</style>
                <ol class="mw-references"><li>Source text.</li></ol>
              </section>
            </body></html>
        """.trimIndent()

        val sections = splitHtmlSections(html)

        assertTrue(sections[0].contains("All of the article prose belongs to the intro."))
        assertTrue(sections.any { it.contains("Related reading.") })
        assertEquals("References", sections[sections.lastIndex - 1])
        assertTrue(sections.last().contains("Source text."))
    }

    @Test
    fun findsRealParserOutputWrapperInSingleQuotedClassAttribute() {
        val html = """
            <html><body>
              <header>Chrome before the article</header>
              <div class='mw-parser-output extra'>
                <p>Wrapped prose.</p>
                <h2>History</h2><p>Body.</p>
              </div>
              <footer>Chrome after the article</footer>
            </body></html>
        """.trimIndent()

        val sections = splitHtmlSections(html)

        assertEquals(3, sections.size)
        assertTrue(sections[0].contains("Wrapped prose."))
        assertFalse(sections[0].contains("Chrome before the article"))
        assertFalse(sections[2].contains("Chrome after the article"))
    }

    @Test
    fun keepsProseAfterAnEmptyHeading() {
        val html = """
            <html><body>
              <p>Prose before an empty heading.</p>
              <h2 id="broken"></h2>
              <p>Prose that must not be lost.</p>
              <h2>History</h2><p>History body.</p>
            </body></html>
        """.trimIndent()

        val sections = splitHtmlSections(html)

        assertTrue(sections[0].contains("Prose before an empty heading."))
        assertTrue(sections[0].contains("Prose that must not be lost."))
        assertEquals("History", sections[1])
    }

    @Test
    fun convertsHtmlCladeTableBackIntoARenderableTree() {
        // Shape used by Wikipedia's {{Clade}} template: a clade is a table whose rows have a
        // label cell and a leaf cell; a leaf cell may contain another clade table.
        val html = """
            <html><body><div class="mw-parser-output">
              <p>Before tree.</p>
              <table class="clade"><tbody>
                <tr>
                  <td class="clade-label first">Aspleniineae</td>
                  <td rowspan="2" class="clade-leaf">
                    <table class="clade"><tbody>
                      <tr>
                        <td class="clade-label first">Athyriaceae</td>
                        <td class="clade-leaf">Athyrium</td>
                      </tr>
                      <tr>
                        <td class="clade-label">Woodsiaceae</td>
                        <td class="clade-leaf">Woodsia</td>
                      </tr>
                    </tbody></table>
                  </td>
                </tr>
                <tr>
                  <td class="clade-label">Polypodiales</td>
                  <td class="clade-leaf">Polypodium</td>
                </tr>
              </tbody></table>
              <p>After tree.</p>
            </div></body></html>
        """.trimIndent()

        val section = splitHtmlSections(html).first()
        val blocks = splitHtmlCladeBlocks(section)

        assertTrue(blocks.any { it.contains("Before tree.") })
        assertTrue(blocks.any { it.contains("After tree.") })
        val tree = blocks.firstOrNull { it.trimStart().startsWith("{{clade") }
        assertTrue("clade table should become a clade template, got: $blocks", tree != null)
        assertTrue(tree!!.contains("|label1=Aspleniineae"))
        assertTrue(tree.contains("Athyrium"))
        val parsed = parseClade(tree)
        assertTrue(parsed != null)
        assertEquals(2, parsed!!.children.size)
        assertTrue(parsed.children[0].node is CladeNode.Branch)
    }

    @Test
    fun splitsIntroAndH2SectionsAndKeepsReadableReferenceText() {
        val html = """
            <html><body>
            <div class="mw-parser-output">
              <style>.navbox { display:none }</style>
              <p>Alpha <b>bold</b> text.<sup class="reference">[1]</sup></p>
              <div class="navbox">Navigation junk</div>
              <h2><span class="mw-headline">History</span></h2>
              <p>Beta text.</p>
              <h2>References</h2>
              <ol class="mw-references"><li>Source text.</li></ol>
            </div>
            </body></html>
        """.trimIndent()

        val sections = splitHtmlSections(html)

        assertEquals(5, sections.size)
        assertTrue(sections[0].contains("Alpha <b>bold</b> text."))
        assertFalse(sections[0].contains("Navigation junk"))
        assertFalse(sections[0].contains("[1]"))
        assertEquals("History", sections[1])
        assertTrue(sections[2].contains("Beta text."))
        assertEquals("References", sections[3])
        assertTrue(sections[4].contains("Source text."))
    }

    @Test
    fun keepsPagesWithoutHeadingsAsASingleIntroduction() {
        val sections = splitHtmlSections("<p>Only one paragraph &amp; no headings.</p>")
        assertEquals(1, sections.size)
        assertTrue(sections[0].contains("Only one paragraph &amp; no headings."))
    }

    @Test
    fun quotedGreaterThanInAttributesDoesNotLeakOrTruncateProse() {
        // REST HTML puts raw ">" characters inside data-mw JSON. A regex like <[^>]+> stops at
        // that character, exposing the rest of the attribute as article text. The scanner must
        // end the tag at the real closing bracket and then strip the attribute entirely.
        val html = """
            <html><body class="mw-parser-output">
            <p data-mw='{"parts":[{"template":{"target":{"wt":"Taxonbar"},"params":{"x":"Q1 > Q2"}}}]}'>Visible <b>prose</b> before.</p>
            <span class="mw-empty-elt" data-mw='{"body":{"extsrc":"&lt;ref&gt;{{cite web|title=raw}}&lt;/ref&gt;"}}'></span>
            <h2>Description</h2>
            <p>Visible prose after.</p>
            </body></html>
        """.trimIndent()

        val sections = splitHtmlSections(html)

        assertEquals(3, sections.size)
        assertTrue(sections[0].contains("Visible <b>prose</b> before."))
        assertTrue(sections[2].contains("Visible prose after."))
        val all = sections.joinToString("\n")
        assertFalse(all.contains("Taxonbar"))
        assertFalse(all.contains("{{"))
        assertFalse(all.contains("data-mw"))
    }

    @Test
    fun balancedChromeRemovalDoesNotSwallowFollowingProse() {
        val html = """
            <div class="mw-parser-output">
              <div class="infobox">
                Infobox junk
                <table><tr><td>Nested infobox<table><tr><td>Inner junk</td></tr></table></td></tr></table>
              </div>
              <p>Paragraph after infobox.</p>
              <h2>History</h2>
              <div class="navbox"><div>Navigation junk</div><div>More navigation</div></div>
              <p>History prose.</p>
            </div>
        """.trimIndent()

        val sections = splitHtmlSections(html)

        assertEquals(3, sections.size)
        assertTrue(sections[0].contains("Paragraph after infobox."))
        assertFalse(sections[0].contains("Infobox junk"))
        assertFalse(sections[0].contains("Inner junk"))
        assertEquals("History", sections[1])
        assertTrue(sections[2].contains("History prose."))
        assertFalse(sections[2].contains("Navigation junk"))
        assertFalse(sections[2].contains("More navigation"))
    }

    @Test
    fun keepsCaptionsOrdinaryTablesAndReferenceLists() {
        val html = """
            <div class="mw-parser-output">
              <h2>Description</h2>
              <div class="thumb tright">
                <div class="thumbinner">
                  <img src="photo.jpg" alt="Plant"/>
                  <div class="thumbcaption">A caption is article text.</div>
                </div>
              </div>
              <table class="wikitable">
                <tr><th>Header cell</th></tr>
                <tr><td>Body cell</td></tr>
              </table>
              <p>Trailing paragraph.</p>
              <h2>References</h2>
              <ol class="mw-references references">
                <li><span class="mw-cite-backlink">^</span> A rendered citation.</li>
              </ol>
            </div>
        """.trimIndent()

        val sections = splitHtmlSections(html)

        assertEquals(5, sections.size)
        val description = sections[2]
        assertTrue(description.contains("A caption is article text."))
        assertTrue(description.contains("Header cell"))
        assertTrue(description.contains("Body cell"))
        assertTrue(description.contains("Trailing paragraph."))
        // The picture is lifted into a [[File:…]] block for the renderer, but the raw <img>
        // tag and its src URL must not survive sanitisation.
        assertTrue(description.contains("[[File:photo.jpg|A caption is article text.]]"))
        assertFalse(description.contains("<img"))
        assertFalse(description.contains("src="))
        assertEquals("References", sections[3])
        assertTrue(sections[4].contains("A rendered citation."))
    }

    @Test
    fun realChasmogamyHtmlKeepsWholeArticleAndReferences() {
        val html = checkNotNull(javaClass.getResource("/chasmogamy.html")).readText()
        val sections = splitHtmlSections(html)
        val all = sections.joinToString("\n")

        assertTrue(sections[0].contains("Chasmogamy"))
        assertTrue(sections[0].contains("cross-pollination"))
        assertTrue(sections[0].contains("open marriage"))
        assertTrue(sections[0].contains("Once chasmogamous flowers have reached maturity"))
        assertTrue(sections.any { it == "See also" })
        assertTrue(sections.any { it == "References" })
        assertTrue(sections.last().contains("Culley"))
        assertFalse(sections[0].contains("{{"))
        assertFalse(all.contains("<ref"))
        assertFalse(all.contains("data-mw"))
        assertFalse(all.contains("&lt;"))
    }

    @Test
    fun plainTextStripsMarkupAndDecodesEntities() {
        assertEquals("Fish & chips – 2", plainText("<i>Fish</i> &amp; chips &ndash; 2"))
    }

    @Test
    fun relativeArticleLinksBecomeAbsolute() {
        assertEquals(
            "<a href=\"https://en.wikipedia.org/wiki/Bromeliaceae\">Bromeliaceae</a> " +
                    "<a href=\"https://en.wikipedia.org/wiki/Plant\">Plant</a>",
            absolutizeHtmlLinks(
                "<a href=\"/wiki/Bromeliaceae\">Bromeliaceae</a> " +
                        "<a href=\"./Plant\">Plant</a>",
                "en"
            )
        )
    }
}
