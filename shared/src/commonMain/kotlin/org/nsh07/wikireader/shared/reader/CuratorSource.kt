package org.nsh07.wikireader.shared.reader

import com.wikirandomizerp.app.data.CuratorFeed

/** Result of a curator-list load. [fromFallback] is true when a bundled copy was used. */
data class CuratorLoad(
    val feed: CuratorFeed,
    val fromFallback: Boolean = false
)

/**
 * Platform-neutral boundary for the community curator list.
 *
 * Android uses its JDK client; iOS uses Ktor. The shared screen only sees this contract so the
 * same curator UI and follow behaviour work on both platforms.
 */
interface CuratorSource {
    suspend fun load(): CuratorLoad
}

object CuratorLinks {
    const val FEED_URL =
        "https://raw.githubusercontent.com/PatrickJamesDev/WikiRandomizer-P/main/curators.json"
    const val FEED_PAGE =
        "https://github.com/PatrickJamesDev/WikiRandomizer-P/blob/main/curators.json"
}
