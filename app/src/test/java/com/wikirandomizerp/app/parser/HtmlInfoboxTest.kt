package com.wikirandomizerp.app.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HTML mode used to delete every infobox, so the taxobox of a plant article ("Scientific
 * classification", "Binomial name", "Synonyms") never showed. These tests feed REST-HTML-shaped
 * taxoboxes through the real pipeline (splitHtmlSections + splitHtmlMediaBlocks) and read the
 * resulting card block back.
 */
class HtmlInfoboxTest {

    private val echeveria = """
        <html><body class="mw-parser-output">
        <section data-mw-section-id="0">
        <table class="infobox biota" style="text-align: left; width: 200px; font-size: 100%" about="#mwt1" typeof="mw:Transclusion" data-mw='{"parts":[{"template":{"target":{"wt":"Speciesbox"},"params":{"image":{"wt":"a > b.jpg"}}},"i":0}]}'>
        <tbody>
        <tr><th colspan="2" style="text-align: center; background-color: rgb(180,250,180)"><i>Echeveria agavoides</i></th></tr>
        <tr><td colspan="2" style="text-align: center"><span class="mw-default-size" typeof="mw:File/Frameless"><a href="./File:Echeveria_agavoides_-_Prague.jpg" class="mw-file-description"><img alt="Echeveria agavoides" resource="./File:Echeveria_agavoides_-_Prague.jpg" src="//upload.wikimedia.org/wikipedia/commons/thumb/a/a0/220px-Echeveria.jpg" width="220" height="330" class="mw-file-element"/></a></span><div style="font-size:88%">Lower photo: from <a rel="mw:WikiLink" href="./Prague" title="Prague">Prague</a>, <a rel="mw:WikiLink" href="./Czech_Republic" title="Czech Republic">Czech Repub.</a></div></td></tr>
        <tr><th colspan="2" style="min-width:15em; text-align: center; background-color: rgb(180,250,180)"><a rel="mw:WikiLink" href="./Taxonomy_(biology)" title="Taxonomy (biology)">Scientific classification</a> <span class="plainlinks"><a rel="mw:ExtLink" href="https://en.wikipedia.org/w/index.php?title=Template:Taxonomy/Echeveria&amp;action=edit" class="external text"><span class="noprint"><span class="mw-ui-icon mw-ui-icon-wikimedia-edit-base20"></span>Edit this classification</span></a></span></th></tr>
        <tr><td style="text-align:left; vertical-align:top">Kingdom:</td><td style="text-align:left; vertical-align:top"><a href="./Plant" title="Plant">Plantae</a></td></tr>
        <tr><td><i>Clade</i>:</td><td><a href="./Tracheophyte">Tracheophytes</a></td></tr>
        <tr><td><i>Clade</i>:</td><td><a href="./Flowering_plant">Angiosperms</a></td></tr>
        <tr><td><i>Clade</i>:</td><td><a href="./Eudicots">Eudicots</a></td></tr>
        <tr><td>Order:</td><td><a href="./Saxifragales">Saxifragales</a></td></tr>
        <tr><td>Family:</td><td><a href="./Crassulaceae">Crassulaceae</a></td></tr>
        <tr><td>Genus:</td><td><a href="./Echeveria"><i>Echeveria</i></a></td></tr>
        <tr><td>Species:</td><td><b><i>E. agavoides</i></b></td></tr>
        <tr><th colspan="2" style="text-align:center; background-color: rgb(180,250,180)">Binomial name</th></tr>
        <tr><td colspan="2" style="text-align:center"><b><i>Echeveria agavoides</i></b><br/><a href="./Charles_Lemaire">Lem.</a></td></tr>
        <tr><th colspan="2" style="text-align:center; background-color: rgb(180,250,180)">Synonyms<sup class="mw-ref reference" typeof="mw:Extension/ref"><a href="./Echeveria_agavoides#cite_note-1"><span class="mw-reflink-text"><span class="cite-bracket">[</span>1<span class="cite-bracket">]</span></span></a></sup></th></tr>
        <tr><td colspan="2" style="text-align:left"><ul><li><i>Cotyledon agavoides</i> Baker</li>
        <li><i>Echeveria obscura</i> (Rose) A.Berger</li>
        <li><i>Echeveria yuccoides</i> É.Morren</li>
        <li><i>Urbinia agavoides</i> (Lem.) Rose</li>
        <li><i>Urbinia obscura</i> Rose</li></ul></td></tr>
        </tbody></table>
        <p><b><i>Echeveria agavoides</i></b> is a species of flowering plant.</p>
        </section>
        <section data-mw-section-id="1"><h2 id="Description">Description</h2><p>Body text.</p></section>
        </body></html>
    """.trimIndent()

    private fun introBlocks(html: String): List<String> =
        splitHtmlMediaBlocks(splitHtmlSections(html).first())

    private fun cardOf(blocks: List<String>): HtmlInfobox? =
        blocks.firstOrNull { it.startsWith(HTML_INFOBOX_MARKER) }?.let { parseHtmlInfoboxBlock(it) }

    private fun plain(row: HtmlInfoboxRow): String = when (row) {
        is HtmlInfoboxRow.Header -> plainText(row.html)
        is HtmlInfoboxRow.Field -> plainText(row.labelHtml) + " | " + plainText(row.valueHtml)
        is HtmlInfoboxRow.Wide -> plainText(row.html)
        is HtmlInfoboxRow.Item -> "* " + plainText(row.html)
    }

    @Test
    fun taxoboxBecomesACardWithClassificationBinomialNameAndSynonyms() {
        val section = splitHtmlSections(echeveria).first()
        val blocks = splitHtmlMediaBlocks(section)

        // The picture keeps its caption and is drawn outside the card.
        assertTrue(
            "picture line expected in $blocks",
            blocks.contains("[[File:Echeveria_agavoides_-_Prague.jpg|Lower photo: from Prague, Czech Repub.]]")
        )
        assertFalse(section.contains("<img"))
        assertFalse("edit link text leaked", section.contains("Edit this classification"))
        assertTrue(blocks.any { it.contains("is a species of flowering plant.") })

        val card = cardOf(blocks)
        assertNotNull("no infobox card block in $blocks", card)
        card!!
        assertTrue(card.taxobox)
        assertEquals("Echeveria agavoides", plainText(card.titleHtml))
        assertEquals(0xB4FAB4, card.titleColor)

        assertEquals(
            listOf(
                "Scientific classification",
                "Kingdom: | Plantae",
                "Clade : | Tracheophytes",
                "Clade : | Angiosperms",
                "Clade : | Eudicots",
                "Order: | Saxifragales",
                "Family: | Crassulaceae",
                "Genus: | Echeveria",
                "Species: | E. agavoides",
                "Binomial name",
                "Echeveria agavoides Lem.",
                "Synonyms [1]",
                "* Cotyledon agavoides Baker",
                "* Echeveria obscura (Rose) A.Berger",
                "* Echeveria yuccoides \u00c9.Morren",
                "* Urbinia agavoides (Lem.) Rose",
                "* Urbinia obscura Rose"
            ),
            card.rows.map { plain(it) }
        )
        // Header bands carry the website colour.
        val headers = card.rows.filterIsInstance<HtmlInfoboxRow.Header>()
        assertEquals(3, headers.size)
        assertTrue(headers.all { it.color == 0xB4FAB4 })
        // Links survive so they can be tapped.
        val kingdom = card.rows[1] as HtmlInfoboxRow.Field
        assertTrue(kingdom.valueHtml.contains("<a href=\"./Plant\">"))
    }

    @Test
    fun cardBlockIsOneLineWithoutRawAngleBrackets() {
        val section = splitHtmlSections(echeveria).first()
        val line = section.lines().single { it.startsWith(HTML_INFOBOX_MARKER) }
        assertFalse(line.contains('<'))
        assertFalse(line.contains('>'))
        // ...so the sanitiser and the block splitter cannot damage it.
        val again = splitHtmlMediaBlocks(section).filter { it.startsWith(HTML_INFOBOX_MARKER) }
        assertEquals(listOf(line), again)
    }

    @Test
    fun taxoboxWithoutNameRowKeepsClassificationAsABand() {
        val html = """
            <html><body class="mw-parser-output">
            <table class="infobox biota"><tbody>
              <tr><td colspan="2"><img resource="./File:A.jpg" width="200" src="//u/A.jpg"><div style="font-size: 88%">cap</div></td></tr>
              <tr><th colspan="2" style="background-color: lightgreen">Scientific classification</th></tr>
              <tr><td>Kingdom:</td><td>Plantae</td></tr>
            </tbody></table>
            </body></html>
        """.trimIndent()
        val blocks = introBlocks(html)
        assertTrue(blocks.contains("[[File:A.jpg|cap]]"))
        val card = cardOf(blocks)
        assertNotNull(card)
        assertEquals("", card!!.titleHtml)
        val first = card.rows[0] as HtmlInfoboxRow.Header
        assertEquals("Scientific classification", plainText(first.html))
        assertEquals(0x90EE90, first.color)
    }

    @Test
    fun pictureOnlyInfoboxStillGivesNoCardButKeepsPictureAndCaption() {
        val html = """
            <html><body class="mw-parser-output">
            <p>Before.</p>
            <table class="infobox biota"><tbody>
              <tr><td><img resource="./File:Example flowers.jpg" width="250"
                     src="//upload.wikimedia.org/250px-Example_flowers.jpg"></td></tr>
              <tr><td style="text-align: center; font-size: 88%">Example flowers</td></tr>
            </tbody></table>
            <p>After.</p>
            </body></html>
        """.trimIndent()
        val section = splitHtmlSections(html).first()
        assertTrue(section.contains("[[File:Example flowers.jpg|Example flowers]]"))
        assertFalse(section.contains(HTML_INFOBOX_MARKER))
        assertTrue(section.contains("Before."))
        assertTrue(section.contains("After."))
    }

    @Test
    fun captionInSeparateRowFillsAnEmptyPictureCaption() {
        // The caption row has no class and no 88% style the generic search could pair, but it
        // sits directly under the picture and is written as a small cell.
        val html = """
            <html><body class="mw-parser-output">
            <table class="infobox"><tbody>
              <tr><td colspan="2"><img resource="./File:B.jpg" width="200" src="//u/B.jpg"></td></tr>
              <tr><td colspan="2" class="infobox-caption">A caption below</td></tr>
              <tr><th>Born</th><td>1815</td></tr>
            </tbody></table>
            </body></html>
        """.trimIndent()
        val blocks = introBlocks(html)
        assertTrue(blocks.toString(), blocks.contains("[[File:B.jpg|A caption below]]"))
        val card = cardOf(blocks)
        assertNotNull(card)
        // The caption must not be repeated inside the card.
        assertEquals(listOf("Born | 1815"), card!!.rows.map { plain(it) })
    }

    @Test
    fun genericInfoboxKeepsTitleFieldsListsAndBandsAndDropsChrome() {
        val html = """
            <html><body class="mw-parser-output">
            <table class="infobox vcard"><caption class="infobox-title fn">Ada Lovelace</caption><tbody>
              <tr><th scope="row" class="infobox-label">Born</th><td class="infobox-data">10 December 1815<br/>London</td></tr>
              <tr><th scope="row" class="infobox-label">Known for</th><td class="infobox-data"><div class="plainlist"><ul><li>Analytical Engine<ul><li>notes</li></ul></li><li>Programs</li></ul></div></td></tr>
              <tr><th colspan="2" class="infobox-header" style="background:#ccf">Career</th></tr>
              <tr><td colspan="2" class="infobox-full-data">Mathematician</td></tr>
              <tr style="display:none"><th>Hidden</th><td>nope</td></tr>
              <tr><th>Empty</th><td></td></tr>
              <tr><td colspan="2"><div class="navbar plainlinks hlist"><ul><li><a href="x">v</a></li></ul></div></td></tr>
            </tbody></table>
            </body></html>
        """.trimIndent()
        val card = cardOf(introBlocks(html))
        assertNotNull(card)
        card!!
        assertFalse(card.taxobox)
        assertEquals("Ada Lovelace", plainText(card.titleHtml))
        assertEquals(
            listOf(
                "Born | 10 December 1815 London",
                "Known for | Analytical Engine notes Programs",
                "Career",
                "Mathematician"
            ),
            card.rows.map { plain(it) }
        )
        assertEquals(0xCCCCFF, (card.rows[2] as HtmlInfoboxRow.Header).color)
    }

    @Test
    fun citationMarkersBecomePlainBracketsAndRedLinksLoseTheirLink() {
        val html = """
            <html><body class="mw-parser-output">
            <table class="infobox"><tbody>
              <tr><th>Founded</th><td>1999<sup class="reference"><a href="#cite_note-1">[1]</a></sup><sup class="noprint Inline-Template">[<i>citation needed</i>]</sup></td></tr>
              <tr><th>Founder</th><td><a href="./Foo?action=edit&amp;redlink=1">Foo</a> and <a href="./Bar">Bar</a></td></tr>
            </tbody></table>
            </body></html>
        """.trimIndent()
        val card = cardOf(introBlocks(html))
        assertNotNull(card)
        val founded = card!!.rows[0] as HtmlInfoboxRow.Field
        assertEquals("1999<sup>[1]</sup>", founded.valueHtml)
        val founder = card.rows[1] as HtmlInfoboxRow.Field
        assertEquals("Foo and <a href=\"./Bar\">Bar</a>", founder.valueHtml)
    }

    @Test
    fun codecRoundTripsMarkupColoursAndSeparators() {
        val box = HtmlInfobox(
            taxobox = true,
            titleColor = 0x123456,
            titleHtml = "T<i>q</i>",
            rows = listOf(
                HtmlInfoboxRow.Header(null, "a<b>x</b>"),
                HtmlInfoboxRow.Field("l", "v \u001F \u001E }} |"),
                HtmlInfoboxRow.Wide("w"),
                HtmlInfoboxRow.Item("i")
            )
        )
        val line = encodeHtmlInfobox(box)
        assertFalse(line.contains('\n'))
        assertFalse(line.contains('<'))
        assertFalse(line.contains('>'))
        val back = parseHtmlInfoboxBlock(line)
        assertNotNull(back)
        back!!
        assertTrue(back.taxobox)
        assertEquals(0x123456, back.titleColor)
        assertEquals("T<i>q</i>", back.titleHtml)
        assertNull((back.rows[0] as HtmlInfoboxRow.Header).color)
        assertEquals("a<b>x</b>", (back.rows[0] as HtmlInfoboxRow.Header).html)
        assertEquals("v" + " ".repeat(5) + "}} |", (back.rows[1] as HtmlInfoboxRow.Field).valueHtml)
        assertEquals(4, back.rows.size)
        assertNull(parseHtmlInfoboxBlock("{{clade|1=A|2=B}}"))
    }

    @Test
    fun truncatedAndMangledHtmlNeverThrows() {
        val source = echeveria
        var seed = 12345L
        fun next(bound: Int): Int {
            seed = seed * 6364136223846793005L + 1442695040888963407L
            return ((seed ushr 33) % bound).toInt()
        }
        repeat(400) { round ->
            val cut = source.substring(0, next(source.length))
            splitHtmlSections(cut)
            val from = next(source.length)
            val to = minOf(source.length, from + next(80))
            splitHtmlSections(source.substring(0, from) + source.substring(to))
            if (round % 4 == 0) {
                val chars = "<>\"'/ =".toCharArray()
                val mangled = StringBuilder(source)
                repeat(20) { mangled.setCharAt(next(mangled.length), chars[next(chars.size)]) }
                splitHtmlSections(mangled.toString())
            }
        }
    }

    @Test
    fun largeInfoboxIsConvertedQuickly() {
        val rows = StringBuilder()
        for (i in 0 until 400) {
            rows.append("<tr><td>Label ").append(i).append(":</td><td><a href=\"./P").append(i)
                .append("\">Value <i>").append(i).append("</i></a></td></tr>")
            rows.append("<tr><td colspan=\"2\"><ul><li>a").append(i).append("</li><li>b")
                .append(i).append("</li></ul></td></tr>")
        }
        val html = "<html><body class=\"mw-parser-output\"><table class=\"infobox biota\"><tbody>" +
                rows + "</tbody></table><p>Text.</p></body></html>"
        val start = System.nanoTime()
        val blocks = introBlocks(html)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertNotNull(cardOf(blocks))
        assertTrue("infobox conversion took $ms ms", ms < 2000)
    }
}
