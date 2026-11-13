package org.nsh07.wikireader.shared.reader

import com.wikirandomizerp.app.data.WikiApiCategoryResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.nsh07.wikireader.shared.ArticleSummary
import org.nsh07.wikireader.shared.ArticleSummaryJson
import org.nsh07.wikireader.shared.WikiUrls
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

/**
 * Minimal Android implementation used by the shared-reader preview host.
 *
 * It intentionally uses the JDK HTTP client instead of adding Ktor to the Android shared variant:
 * the production Android app already has its own Retrofit/Room stack, and this preview only needs
 * the same network contract as the iOS shell.
 */
class AndroidRandomArticleSource(
    private val json: Json = Json { ignoreUnknownKeys = true }
) : RandomArticleSource {

    override suspend fun randomSummary(language: String): ArticleSummary =
        withContext(Dispatchers.IO) {
            summary(language, null) ?: error("Could not parse a random Wikipedia summary")
        }

    override suspend fun randomSummaryForTopic(
        language: String,
        category: String
    ): ArticleSummary = withContext(Dispatchers.IO) {
        val members = attempt {
            json.decodeFromString<WikiApiCategoryResponse>(
                fetch(WikiUrls.categoryMembersUrl(language, category))
            ).query
                ?.categorymembers
                .orEmpty()
                .filter { it.ns == 0 && it.title.isNotBlank() }
                .map { it.title }
                .distinct()
        }.orEmpty()

        if (members.isEmpty()) {
            return@withContext summary(language, null)
                ?: error("Could not parse a random Wikipedia summary")
        }

        val start = Random.nextInt(members.size)
        for (offset in members.indices) {
            val title = members[(start + offset) % members.size]
            attempt { summary(language, title) }?.let { return@withContext it }
        }

        return@withContext summary(language, null)
            ?: error("Could not parse a random Wikipedia summary")
    }

    private fun summary(language: String, title: String?): ArticleSummary? {
        val url = if (title == null) {
            WikiUrls.randomSummaryUrl(language)
        } else {
            WikiUrls.summaryUrl(language, title)
        }
        return ArticleSummaryJson.parse(fetch(url))
    }

    private fun fetch(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 20_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            val code = connection.responseCode
            if (code !in 200..299) error("Wikipedia returned HTTP $code")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private inline fun <T> attempt(block: () -> T): T? = try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        null
    }

    private companion object {
        const val USER_AGENT =
            "WikiReader-Android/1.0 (https://github.com/PatrickJamesDev/WikiRandomizer-P)"
    }
}
