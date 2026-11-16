package com.wikirandomizerp.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MiscCommonTest {
    @Test
    fun countryFlagsUseRegionalIndicatorCodePoints() {
        assertEquals("\uD83C\uDDEE\uD83C\uDDF3", countryFlag("in"))
        assertEquals("", countryFlag(""))
    }

    @Test
    fun sectionParserDropsTrailingReferenceHeadings() {
        val sections = parseSections(
            "Intro\n== History ==\nBody\n== References ==\n"
        )
        assertTrue(sections.first().startsWith("Intro"))
        assertTrue(sections.none { it.trim() == "References" })
    }
}
