package org.nsh07.wikireader.shared.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TopicCatalogTest {
    @Test
    fun catalogExposesLeafTopicsFromEveryGroup() {
        assertTrue(topicCatalog.isNotEmpty())
        assertTrue(topicCatalog.any { it.group == "Biology" })
        assertTrue(topicCatalog.all { it.topic.category.isNotBlank() })
    }

    @Test
    fun knownCategoryCanBeRestoredAfterRestart() {
        val orchid = findTopicByCategory("Orchids")
        assertEquals("Orchids", orchid?.label)
        assertEquals("Orchids", orchid?.category)
    }

    @Test
    fun unknownCategoryIsIgnored() {
        assertEquals(null, findTopicByCategory("definitely-not-a-real-topic"))
    }
}
