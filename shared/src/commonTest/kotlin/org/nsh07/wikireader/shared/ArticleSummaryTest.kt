package org.nsh07.wikireader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ArticleSummaryTest {
    @Test
    fun parsesRestSummaryAndIgnoresUnknownFields() {
        val article = assertNotNull(ArticleSummaryJson.parse(
            """
            {
              "type": "standard",
              "title": "Köln",
              "pageid": 123,
              "description": "City in Germany",
              "extract": "Cologne is the largest city of Germany's most populous state.",
              "thumbnail": {
                "source": "https://upload.wikimedia.org/example.jpg",
                "width": 320,
                "height": 200
              },
              "content_urls": {
                "desktop": { "page": "https://en.wikipedia.org/wiki/Köln" },
                "mobile": { "page": "https://en.m.wikipedia.org/wiki/Köln" }
              },
              "new_api_field": true
            }
            """.trimIndent()
        ))

        assertEquals("Köln", article.title)
        assertEquals(123, article.pageid)
        assertEquals("City in Germany", article.summaryDescription)
        assertEquals("https://upload.wikimedia.org/example.jpg", article.thumbnail?.source)
        assertEquals(320, article.thumbnail?.width)
        assertEquals("https://en.wikipedia.org/wiki/Köln", article.contentUrls?.desktop?.page)
    }

    @Test
    fun acceptsSummariesWithoutOptionalFields() {
        val article = assertNotNull(ArticleSummaryJson.parse(
            """{"title":"Empty","extract":"Only required fields."}"""
        ))

        assertNull(article.summaryDescription)
        assertNull(article.thumbnail)
        assertNull(article.contentUrls)
    }

    @Test
    fun rejectsBlankAndMalformedJson() {
        assertNull(ArticleSummaryJson.parse(""))
        assertNull(ArticleSummaryJson.parse("   "))
        assertNull(ArticleSummaryJson.parse("""{"title":42}"""))
        assertNull(ArticleSummaryJson.parse("not json"))
    }
}
