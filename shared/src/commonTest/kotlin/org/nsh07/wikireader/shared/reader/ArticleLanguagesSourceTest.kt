package org.nsh07.wikireader.shared.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class ArticleLanguagesSourceTest {
    @Test
    fun parsesAndSortsLanguageLinks() {
        val body = """{"query":{"pages":[{"langlinks":[
            {"lang":"de","title":"Kaffee","autonym":"Deutsch"},
            {"lang":"fr","title":"Café","autonym":"français"},
            {"lang":"de","title":"Kaffee","autonym":"Deutsch"}
        ]}]}}"""
        val links = parseArticleLanguages(body)
        assertEquals(2, links.size)
        assertEquals("de", links[0].language)
        assertEquals("Deutsch", links[0].label)
        assertEquals("Café", links[1].title)
    }
}
