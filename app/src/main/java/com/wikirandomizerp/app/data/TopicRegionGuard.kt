package com.wikirandomizerp.app.data

/**
 * Keeps a geographic topic from leaking into another continent through loosely named category
 * branches. "South American history" must not serve "History of Africa" just because both contain
 * the generic word "history".
 *
 * This is deliberately conservative: it only acts when the topic itself names a region. A
 * candidate may mention several regions (for example "African diaspora in South America"); such a
 * page is kept because it also mentions the topic's region.
 */
object TopicRegionGuard {

    private data class Region(val id: Int, val words: List<String>)

    private val regions = listOf(
        Region(0, listOf("africa", "african", "africans")),
        Region(1, listOf("asia", "asian", "asians")),
        Region(2, listOf("europe", "european", "europeans")),
        Region(3, listOf("north america", "north american", "north americans")),
        Region(4, listOf("south america", "south american", "south americans")),
        Region(5, listOf("central america", "central american", "central americans")),
        Region(6, listOf("oceania", "oceanian", "australia", "australian", "australians", "new zealand")),
        Region(7, listOf("antarctica", "antarctic")),
        Region(8, listOf("caribbean")),
        Region(9, listOf("middle east", "middle eastern"))
    )

    /** Region ids mentioned in [text]. */
    fun regionsIn(text: String): Set<Int> {
        if (text.isBlank()) return emptySet()
        val normalized = text.lowercase()
            .replace('_', ' ')
            .replace('-', ' ')
            .replace(Regex("\\s+"), " ")
        val out = HashSet<Int>()
        for (region in regions) {
            if (region.words.any { containsWholePhrase(normalized, it) }) out.add(region.id)
        }
        return out
    }

    /**
     * True when [candidate] clearly belongs to a different region than [topic].
     * A candidate that also mentions the topic's region is not a conflict.
     */
    fun conflicts(candidate: String, topic: String): Boolean {
        val topicRegions = regionsIn(topic)
        if (topicRegions.isEmpty()) return false
        val candidateRegions = regionsIn(candidate)
        if (candidateRegions.isEmpty()) return false
        return candidateRegions.none { it in topicRegions }
    }

    private fun containsWholePhrase(text: String, phrase: String): Boolean {
        var from = 0
        while (true) {
            val at = text.indexOf(phrase, from)
            if (at < 0) return false
            val beforeOk = at == 0 || !text[at - 1].isLetterOrDigit()
            val end = at + phrase.length
            val afterOk = end >= text.length || !text[end].isLetterOrDigit()
            if (beforeOk && afterOk) return true
            from = at + 1
        }
    }
}
