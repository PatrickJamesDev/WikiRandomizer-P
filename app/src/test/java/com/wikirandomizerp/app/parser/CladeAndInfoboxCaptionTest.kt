package com.wikirandomizerp.app.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regressions for the two problems users still saw in v17: cladograms that disappeared and
 * pictures that lost their captions (especially taxobox/`{{Speciesbox}}` pictures and HTML mode).
 */
class CladeAndInfoboxCaptionTest {

    // ---------------------------------------------------------------- wikitext: clades

    @Test
    fun cladeInsideAnotherTemplateIsLiftedIntoItsOwnBlock() {
        val text = """
            Before the quote.

            {{quote|1={{clade
              |label1=Root
              |1={{clade
                |1=Alpha
                |2=Beta
              }}
            }}}}

            After the quote.
        """.trimIndent()

        val blocks = splitWikitextBlocks(text)

        val clades = blocks.filter {
            it.raw && it.text.trimStart().startsWith("{{clade", ignoreCase = true)
        }
        assertEquals("the nested tree must become its own raw block", 1, clades.size)
        assertNotNull(parseClade(clades.single().text))
        assertFalse(
            "no running-text block may still contain {{clade",
            blocks.any { !it.raw && it.text.contains("{{clade", ignoreCase = true) }
        )
        assertTrue(blocks.any { !it.raw && it.text.contains("Before the quote.") })
        assertTrue(blocks.any { !it.raw && it.text.contains("After the quote.") })
    }

    @Test
    fun cladeWithSubLabelsBecomesItsOwnBlock() {
        val text = "{{clade\n|label1=Rosaceae\n|sublabel1=65 Mya\n" +
                "|1={{clade|1=Rosoideae|2=Dryadoideae}}}}"

        val block = blockTemplateAt(text, 0)
        assertNotNull(block)
        val tree = parseClade(block!!.blocks.single())
        assertNotNull(tree)
        assertEquals("Rosaceae", tree!!.children.single().label)
        assertEquals("65 Mya", tree.children.single().sublabel)
    }

    // --------------------------------------------------------------- wikitext: pictures

    @Test
    fun speciesboxPicturesAreEmittedWithTheirCaptions() {
        val text = """
            {{Short description|Species of plant}}
            {{Speciesbox
            | image = Example flowers.jpg
            | image_caption = ''Example'' flowers
            | image2 = Example plants.jpg
            | image2_caption = ''Example'' plants
            | image_alt = some alt text
            | genus = Example
            }}

            '''''Example''''' is a plant.
        """.trimIndent()

        val blocks = splitWikitextBlocks(text)
        val pictures = blocks.filter { it.raw && it.text.startsWith("[[File:") }

        assertEquals(2, pictures.size)
        assertEquals(
            "[[File:Example flowers.jpg|''Example'' flowers]]",
            pictures[0].text
        )
        assertEquals(
            "[[File:Example plants.jpg|''Example'' plants]]",
            pictures[1].text
        )
        // The card itself must still be emitted (the picture parameters are filtered by
        // parseInfobox, they are not shown as raw File: rows).
        assertTrue(blocks.any { it.text.trimStart().startsWith("{{Speciesbox") })
    }

    @Test
    fun infoboxPicturesUnderstandsImageCaptionNumbering() {
        val template = """
            {{Infobox person
            | image = Alice.jpg
            | image caption = Alice in 2020
            | image2 = Bob.jpg
            | image 2 caption = Bob in 2021
            | image_alt = alt text
            | caption = not a picture caption
            | name = Alice
            }}
        """.trimIndent()

        val pictures = infoboxPictures(template)
        assertEquals(2, pictures.size)
        assertEquals("Alice.jpg", pictures[0].name)
        assertEquals("Alice in 2020", pictures[0].caption)
        assertEquals("Bob.jpg", pictures[1].name)
        assertEquals("Bob in 2021", pictures[1].caption)
    }

    @Test
    fun imageMetadataIsNotTreatedAsAPicture() {
        // ImageSize / Image alt are formatting parameters, not files.
        val template = """
            {{Speciesbox
            | image = Real picture.jpg
            | image_size = 220
            | image_alt = a description
            | genus = Example
            }}
        """.trimIndent()

        val names = infoboxPictures(template).map { it.name }
        assertEquals(listOf("Real picture.jpg"), names)
    }

    // ----------------------------------------------------------------- HTML: pictures

    @Test
    fun htmlFigureBecomesAPictureBlockWithItsCaption() {
        val html = """
            <html><body class="mw-parser-output">
            <p>Before.</p>
            <figure typeof="mw:File/Thumb">
              <a href="./File:Example.jpg"><img resource="./File:Example.jpg"
                 width="250" src="//upload.wikimedia.org/250px-Example.jpg"></a>
              <figcaption>A real caption.</figcaption>
            </figure>
            <p>After.</p>
            </body></html>
        """.trimIndent()

        val section = splitHtmlSections(html).first()
        val blocks = splitHtmlMediaBlocks(section)

        assertTrue(blocks.any { it == "[[File:Example.jpg|A real caption.]]" })
        assertTrue(blocks.any { it.contains("Before.") })
        assertTrue(blocks.any { it.contains("After.") })
        assertFalse(section.contains("<img"))
    }

    @Test
    fun htmlInfoboxPictureGetsItsCaptionAndTheChromeIsRemoved() {
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

        assertTrue(
            "infobox picture + caption expected, got: $section",
            section.contains("[[File:Example flowers.jpg|Example flowers]]")
        )
        assertFalse(section.contains("Infobox"))
        assertFalse(section.contains("<img"))
        assertTrue(section.contains("Before."))
        assertTrue(section.contains("After."))
    }

    @Test
    fun htmlGalleryKeepsOnePicturePerItemAndItsCaption() {
        val html = """
            <html><body class="mw-parser-output">
            <ul class="gallery mw-gallery-traditional">
              <li class="gallerybox">
                <div class="thumb"><span typeof="mw:File"><a href="./File:One.png">
                  <img resource="./File:One.png" width="120" src="//x/120px-One.png"></a></span></div>
                <div class="gallerytext">First picture</div>
              </li>
              <li class="gallerybox">
                <div class="thumb"><span typeof="mw:File"><a href="./File:Two.png">
                  <img resource="./File:Two.png" width="120" src="//x/120px-Two.png"></a></span></div>
                <div class="gallerytext">Second picture</div>
              </li>
            </ul>
            </body></html>
        """.trimIndent()

        val section = splitHtmlSections(html).first()

        assertTrue(section.contains("[[File:One.png|First picture]]"))
        assertTrue(section.contains("[[File:Two.png|Second picture]]"))
        assertFalse(section.contains("<img"))
    }

    @Test
    fun htmlSublabelRowIsKeptOnTheChildAboveIt() {
        // Wikipedia writes the edge sublabel in its own <tr> below the leaf row.
        val html = """
            <html><body><div class="mw-parser-output">
            <table class="clade"><tbody>
              <tr>
                <td class="clade-label first">Root</td>
                <td rowspan="2" class="clade-leaf">Alpha</td>
              </tr>
              <tr><td class="clade-slabel">65 Mya</td></tr>
              <tr>
                <td class="clade-label first"></td>
                <td class="clade-leaf">Beta</td>
              </tr>
            </tbody></table>
            </div></body></html>
        """.trimIndent()

        val tree = splitHtmlCladeBlocks(splitHtmlSections(html).first())
            .firstOrNull { it.trimStart().startsWith("{{clade") }
        assertNotNull(tree)
        val treeText = tree!!
        assertTrue("sub-label was dropped: $treeText", treeText.contains("|sublabel1=65 Mya"))
        val parsed = parseClade(treeText)
        assertNotNull(parsed)
        assertEquals("65 Mya", parsed!!.children[0].sublabel)
        assertEquals("Root", parsed.children[0].label)
    }
}
