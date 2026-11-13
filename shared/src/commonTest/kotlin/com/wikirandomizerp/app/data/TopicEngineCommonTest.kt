package com.wikirandomizerp.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** These classes live in commonMain, so the topic engine is exercised on iOS too. */
class TopicEngineCommonTest {
    @Test
    fun naturalLanguageBecomesWikipediaCategoryPhrase() {
        val variants = InterestQuery.variants("all F1 drivers from Finland")
        assertTrue(variants.contains("Finnish Formula One drivers"))
        assertTrue(variants.any { it.equals("Formula One drivers from Finland", ignoreCase = true) })
    }

    @Test
    fun regionGuardRejectsAnotherContinentButKeepsMixedPages() {
        assertTrue(TopicRegionGuard.conflicts("History of Africa", "South American history"))
        assertFalse(TopicRegionGuard.conflicts("History of Brazil", "South American history"))
        assertFalse(TopicRegionGuard.conflicts("African diaspora in South America", "South American history"))
        assertFalse(TopicRegionGuard.conflicts("History of Africa", "History of art"))
    }

    @Test
    fun parserHandlesLongWhitespaceRuns() {
        InterestParser.parse(" ".repeat(20_000))
        InterestParser.parse("i am" + " ".repeat(20_000) + "interested")
        InterestParser.parse("show me" + " ".repeat(20_000))
    }

    @Test
    fun unrelatedArticleGetsZeroCoverage() {
        val docs = listOf(
            RelDoc("Moche", "Moche culture", "Archaeological culture in Peru", "The Moche were an Andean civilization."),
            RelDoc("Inca", "Inca Empire", "Empire in South America", "The Inca Empire was in South America."),
            RelDoc("Egypt", "Ancient Egypt", "Civilization in Africa", "Ancient Egypt was in Africa."),
            RelDoc("Caesar", "Julius Caesar", "Roman general", "Caesar was a Roman general.")
        )
        val result = RelevanceModel.score(docs, listOf("South American civilizations"), emptyList())
        assertEquals(0, result.rel["Caesar"])
        assertEquals(0, result.cov["Caesar"])
        assertTrue(result.cov.getValue("Inca") > result.cov.getValue("Egypt"))
    }
}
