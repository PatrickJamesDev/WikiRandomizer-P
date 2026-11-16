package org.nsh07.wikireader.shared.reader

import com.wikirandomizerp.app.data.FeedApiResponse

/** Platform-neutral boundary for Wikipedia's daily featured feed. */
interface FeedSource {
    suspend fun load(language: String): FeedApiResponse
}
