package org.nsh07.wikireader.shared.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArticleInfoSourceTest {
    @Test
    fun parsesTalkWikitext() {
        val body = """{"parse":{"title":"Talk:Café","wikitext":"== Notes ==\nHello"}}"""
        assertEquals("== Notes ==\nHello", parseTalkWikitext(body))
        assertNull(parseTalkWikitext("{}"))
    }

    @Test
    fun parsesRecentRevisions() {
        val body = """{"query":{"pages":[{"title":"Café","revisions":[
            {"user":"Ada","timestamp":"2026-01-02T03:04:05Z","comment":"fix","size":1234},
            {"user":"Grace","timestamp":"2026-01-01T00:00:00Z","size":1200}
        ]}]}}"""
        val revisions = parseArticleRevisions(body)
        assertEquals(2, revisions.size)
        assertEquals("Ada", revisions[0].user)
        assertEquals(1234, revisions[0].size)
        assertEquals("", revisions[1].comment)
    }
}
