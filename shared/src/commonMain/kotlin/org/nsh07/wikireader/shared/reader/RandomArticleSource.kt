package org.nsh07.wikireader.shared.reader

import org.nsh07.wikireader.shared.ArticleSummary

/**
 * Platform-neutral boundary for random-article discovery.
 *
 * Android can keep its Retrofit/Room topic engine behind this interface while iOS uses Ktor.
 * The shared Compose UI only depends on this contract, so both platforms render the same screens.
 */
interface RandomArticleSource {
    suspend fun randomSummary(language: String): ArticleSummary

    /**
     * Optional category-aware discovery. Platforms that cannot browse categories can keep the
     * default fallback, which still returns a usable random article.
     */
    suspend fun randomSummaryForTopic(language: String, category: String): ArticleSummary =
        randomSummary(language)
}
