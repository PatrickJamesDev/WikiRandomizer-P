package com.wikirandomizerp.app.data

/** Per-topic breakdown shown in the UI. */
data class TopicStat(
    val topic: String,
    val resolved: String,
    val discovered: Int,
    val indexed: Int,
    val withImages: Int,
    val good: Int,
    val featured: Int,
    val builtAt: Long,
    /** False while article details are still being collected (build interrupted / running). */
    val complete: Boolean,
    /** Whether this topic takes part in random picks. Disabling keeps its downloaded library. */
    val enabled: Boolean = true,
    val readCount: Int = 0
) {
    val remaining: Int get() = (indexed - readCount).coerceAtLeast(0)
}

data class LibraryStats(
    /** Size of the library database on disk. */
    val sizeBytes: Long = 0L,
    val totalArticles: Int,
    /** Articles never shown yet (not in the permanent "seen" list). */
    val unseen: Int,
    val topics: List<TopicStat>
)

/** One indexed article. [tier]: 0 = normal, 1 = Good article, 2 = Featured article. */
data class ArticleRow(
    val title: String,
    val bytes: Int,
    val hasImage: Boolean,
    val tier: Int,
    /** Shortest category distance from the topic's own category (0 = directly in it). */
    val depth: Int = 0,
    /** Wikidata short description, used to judge relevance. null = not fetched yet. */
    val descr: String? = null,
    /** How many of the topic's key words the article's title/description matches; -1 = unknown. */
    val rel: Int = -1,
    /** Absolute topic-word coverage, 0..100; -1 = not scored yet (passes every threshold). */
    val cov: Int = -1,
    /** Views in the last 30 days; -1 = unknown. Used to prefer obscure or popular articles. */
    val views: Int = -1,
    /** One selected topic this article belongs to (used for learned topic affinity). */
    val topic: String = ""
)

/** Membership row used by the relevance pass. */
data class MemberDescr(val title: String, val depth: Int, val descr: String?)
