package com.wikirandomizerp.app.data

/**
 * Topics whose key starts with this prefix have no category: their library is built from full-text
 * search alone ("search:mid-century modern chairs"). For niches Wikipedia has no category for.
 */
const val SEARCH_TOPIC_PREFIX = "search:"

enum class IndexPhase { CRAWLING, CHECKING, INTROS, VIEWS }

/** What the user said about a topic in their own words (natural-language interests). */
data class TopicHint(val phrase: String, val exclusions: List<String>)

/** Live snapshot, emitted after every single request so the UI can always show movement. */
data class IndexProgress(
    val phase: IndexPhase,
    val depth: Int,
    val maxDepth: Int,
    val categoriesDone: Int,
    val categoriesKnown: Int,
    val articlesFound: Int,
    val requests: Int,
    val checked: Int,
    val toCheck: Int,
    /** Non-null while the builder is retrying / waiting for the connection. */
    val status: String?
)

/** Outcome of building one topic, shown in the result screen and the library breakdown. */
data class TopicBuildResult(
    val topic: String,
    val label: String,
    val resolvedCategory: String,
    val discovered: Int,
    val indexed: Int,
    val withImages: Int,
    val good: Int,
    val featured: Int,
    val categoriesFailed: Int,
    val batchesFailed: Int,
    val durationMs: Long,
    val error: String? = null,
    /** Articles removed because their description had nothing to do with the topic. */
    val filteredOut: Int = 0
) {
    val ok: Boolean get() = error == null && indexed > 0
    val incomplete: Boolean get() = ok && (categoriesFailed > 0 || batchesFailed > 0)
}
