package com.wikirandomizerp.app.ui.homeScreen

import org.junit.Assert.assertEquals
import org.junit.Test
import com.wikirandomizerp.app.ui.homeScreen.viewModel.normalizeArticleLinkTitle

class ArticleLinkTitleTest {
    @Test
    fun normalizesFirstLetterLikeMediaWiki() {
        assertEquals("Photosynthesis", normalizeArticleLinkTitle("photosynthesis"))
    }

    @Test
    fun stripsUnderscoresAndFragment() {
        assertEquals(
            "Insectivorous plant",
            normalizeArticleLinkTitle("insectivorous_plant#Etymology")
        )
    }

    @Test
    fun stripsLeadingColonAndWhitespace() {
        assertEquals("Photosynthesis", normalizeArticleLinkTitle("  :photosynthesis  "))
    }

    @Test
    fun keepsCaseAfterFirstLetter() {
        assertEquals("US Open", normalizeArticleLinkTitle("US Open"))
    }
}
