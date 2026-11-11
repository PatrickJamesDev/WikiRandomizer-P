package com.wikirandomizerp.app.parser

import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserQuickWinsTest {

    @Test
    fun commonHtmlEntitiesAreDecodedWithoutTheHtmlParser() {
        assertEquals("\u00A0", decodeHtmlEntity("&nbsp;"))
        assertEquals("\u2013", decodeHtmlEntity("&ndash;"))
        assertEquals("\u2033", decodeHtmlEntity("&Prime;"))
        assertEquals("A", decodeHtmlEntity("&#65;"))
        assertEquals("\u00A0", decodeHtmlEntity("&#xA0;"))
        assertNull(decodeHtmlEntity("&not-a-real-entity;"))
        assertNull(decodeHtmlEntity("plain text"))
    }

    @Test
    fun buildRefListAcceptsCaseAndSpacesAroundTheName() {
        ReferenceData.refList.clear()
        val text = """
            <REF NAME = "Alpha">first</REF>
            <ref name='Beta'>second</ref>
            <ref name=Gamma>third</ref>
            <ref name="SelfClosing" />
        """.trimIndent()

        text.buildRefList()

        assertEquals("first", ReferenceData.refList["Alpha"])
        assertEquals("second", ReferenceData.refList["Beta"])
        assertEquals("third", ReferenceData.refList["Gamma"])
        assertNull(ReferenceData.refList["SelfClosing"])
        ReferenceData.refList.clear()
    }

    @Test
    fun multilineCommentsDoNotSurviveCleanup() {
        val raw = "A<!--\n-->{{clade|1=Leaf}}B"
        assertEquals("A{{clade|1=Leaf}}B", cleanUpWikitext(raw))
    }

    @Test
    fun referenceAndEntityOnlyTextStillRenders() {
        val out = "A<ref>note</ref>&nbsp;B".toWikitextAnnotatedStringSafe(
            colorScheme = lightColorScheme(),
            typography = Typography(),
            loadPage = {},
            fontSize = 16,
            showRef = {}
        )
        assertTrue(out.text.contains("A"))
        assertTrue(out.text.contains("1"))
        assertTrue(out.text.contains("\u00A0B"))
    }

    @Test
    fun sfracAndRpUseDirectStyles() {
        val out = "x{{sfrac|1|2}}y{{rp|3}}z".toWikitextAnnotatedStringSafe(
            colorScheme = lightColorScheme(),
            typography = Typography(),
            loadPage = {},
            fontSize = 16,
            showRef = {}
        )
        assertTrue(out.text.contains("1/2"))
        assertTrue(out.text.contains(":3"))
    }
}
