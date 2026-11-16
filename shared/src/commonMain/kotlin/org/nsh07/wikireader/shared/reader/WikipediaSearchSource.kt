package org.nsh07.wikireader.shared.reader

import com.wikirandomizerp.app.data.WikiSearchResult

/** Full-text Wikipedia search used by the shared Explore screen. */
interface WikipediaSearchSource {
    suspend fun search(language: String, query: String): List<WikiSearchResult>
}
