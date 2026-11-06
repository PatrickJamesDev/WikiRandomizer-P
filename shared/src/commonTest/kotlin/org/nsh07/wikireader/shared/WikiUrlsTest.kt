package org.nsh07.wikireader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WikiUrlsTest {
    @Test
    fun normalizesSpacesAndRepeatedUnderscores() {
        assertEquals("Athyrium_filix-femina", WikiUrls.normalizeTitle("  Athyrium  filix-femina "))
        assertEquals("Main_Page", WikiUrls.normalizeTitle("Main__Page"))
    }

    @Test
    fun encodesUnicodeAndReservedCharacters() {
        assertEquals("Caf%C3%A9", WikiUrls.encodeTitle("Café"))
        assertEquals("A%26B", WikiUrls.encodeTitle("A&B"))
        assertEquals("100%25", WikiUrls.encodeTitle("100%"))
    }

    @Test
    fun articleUrlsUseTheRequestedLanguage() {
        assertEquals(
            "https://de.wikipedia.org/wiki/K%C3%B6ln",
            WikiUrls.articleUrl("DE", " Köln ")
        )
        assertEquals(
            "https://en.m.wikipedia.org/wiki/Main_Page",
            WikiUrls.mobileArticleUrl("en", "Main Page")
        )
    }

    @Test
    fun fileUrlsSupportOptionalResizeWidth() {
        assertEquals(
            "https://en.wikipedia.org/wiki/Special:FilePath/Example.jpg",
            WikiUrls.filePathUrl("en", "File:Example.jpg")
        )
        assertTrue(
            WikiUrls.filePathUrl("en", "Example.jpg", 640)
                .endsWith("?width=640")
        )
    }

    @Test
    fun textHelpersAreUsefulForCards() {
        assertEquals("Hello world", WikiText.plainText("<b>Hello</b>&nbsp;world"))
        assertEquals(1, WikiText.readingMinutes("one two three", wordsPerMinute = 3))
        assertTrue(WikiText.snippet("A sentence. Another sentence.", maxChars = 12).endsWith("…"))
    }
}
