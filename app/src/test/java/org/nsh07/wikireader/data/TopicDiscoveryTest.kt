package org.nsh07.wikireader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicDiscoveryTest {

    @Test
    fun naturalLanguage_f1FinlandBecomesCategoryPhrase() {
        val variants = InterestQuery.variants("all F1 drivers from Finland")
        assertTrue(variants.contains("Finnish Formula One drivers"))
        assertTrue(variants.any { it.equals("Formula One drivers from Finland", ignoreCase = true) })
    }

    @Test
    fun naturalLanguage_stripsLeadingFillerAndExpandsSynonyms() {
        assertEquals("Formula One teams", InterestQuery.expandSynonyms("F1 teams"))
        assertTrue(InterestQuery.variants("every Formula 1 circuit").contains("Formula One circuit"))
        assertEquals("South American history", InterestQuery.clean("all South American history"))
    }

    @Test
    fun regionGuard_rejectsAnotherContinent() {
        assertTrue(
            TopicRegionGuard.conflicts("History of Africa", "South American history")
        )
        assertFalse(
            TopicRegionGuard.conflicts("History of Brazil", "South American history")
        )
    }

    @Test
    fun regionGuard_keepsPagesThatMentionBothRegions() {
        assertFalse(
            TopicRegionGuard.conflicts(
                "African diaspora in South America",
                "South American history"
            )
        )
        assertFalse(
            TopicRegionGuard.conflicts("History of Africa", "African history")
        )
    }

    @Test
    fun regionGuard_isInactiveForNonGeographicTopics() {
        assertFalse(
            TopicRegionGuard.conflicts("History of Africa", "History of art")
        )
    }
}
