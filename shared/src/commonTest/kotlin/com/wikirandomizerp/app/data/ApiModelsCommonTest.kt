package com.wikirandomizerp.app.data

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Network DTOs now live in commonMain, so their wire names are verified for iOS/Wasm too. */
class ApiModelsCommonTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun pageDataParsesSnakeCaseFields() {
        val page = json.decodeFromString<WikiApiPageData>(
            """{"query":{"pages":[{"title":"Test","extract":"Body","pageid":7,"original":{"source":"x","width":10,"height":20}}]}}"""
        )
        val parsed = assertNotNull(page.query?.pages?.firstOrNull())
        assertEquals("Test", parsed.title)
        assertEquals(7, parsed.pageId)
        assertEquals("x", parsed.photo?.source)
        assertEquals(20, parsed.photo?.height)
    }

    @Test
    fun searchResultsParseSnippetsAndContinuation() {
        val result = json.decodeFromString<WikiApiSearchResults>(
            """{"query":{"pages":[{"ns":0,"title":"A","titlesnippet":"<b>A</b>","pageid":3,"snippet":"s","index":1}]}}"""
        )
        val page = assertNotNull(result.query.pages.firstOrNull())
        assertEquals("<b>A</b>", page.titleSnippet)
        assertEquals(3, page.pageId)
    }

    @Test
    fun feedResponseParsesNestedImageAndMostRead() {
        val feed = json.decodeFromString<FeedApiResponse>(
            """{"tfa":{"titles":{"normalized":"Today"},"originalimage":{"source":"img","width":1,"height":2}},"mostread":{"articles":[{"views":5,"titles":{"normalized":"Read"}}]}}"""
        )
        assertEquals("Today", feed.tfa?.titles?.normalized)
        assertEquals("img", feed.tfa?.originalImage?.source)
        assertEquals(5, feed.mostRead?.articles?.firstOrNull()?.views)
    }

    @Test
    fun topicTreeIsAvailableToSharedConsumers() {
        assertTrue(TOPIC_GROUPS.isNotEmpty())
        assertTrue(TOPIC_GROUPS.any { it.subgroups.isNotEmpty() })
    }
}