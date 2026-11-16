package org.nsh07.wikireader.shared.ios

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import com.wikirandomizerp.app.data.WikiApiCategoryResponse
import org.nsh07.wikireader.shared.ArticleSummary
import org.nsh07.wikireader.shared.WikiUrls
import org.nsh07.wikireader.shared.reader.RandomArticleSource
import kotlin.random.Random

internal class KtorRandomArticleSource(
    private val client: HttpClient = defaultClient()
) : RandomArticleSource {

    override suspend fun randomSummary(language: String): ArticleSummary =
        summary(client, WikiUrls.randomSummaryUrl(language))

    override suspend fun randomSummaryForTopic(language: String, category: String): ArticleSummary {
        val members = try {
            client.get(WikiUrls.categoryMembersUrl(language, category)) {
                header(HttpHeaders.Accept, "application/json")
                header(HttpHeaders.UserAgent, USER_AGENT)
            }.body<WikiApiCategoryResponse>()
                .query
                ?.categorymembers
                .orEmpty()
                .filter { it.ns == 0 && it.title.isNotBlank() }
                .map { it.title }
                .distinct()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            emptyList()
        }

        if (members.isEmpty()) return randomSummary(language)

        val start = Random.nextInt(members.size)
        for (offset in members.indices) {
            val title = members[(start + offset) % members.size]
            try {
                return summary(client, WikiUrls.summaryUrl(language, title))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                // Try another category member before falling back to a completely random page.
            }
        }
        return randomSummary(language)
    }

    private suspend fun summary(client: HttpClient, url: String): ArticleSummary =
        client.get(url) {
            header(HttpHeaders.Accept, "application/json")
            header(HttpHeaders.UserAgent, USER_AGENT)
        }.body()

    private companion object {
        const val USER_AGENT =
            "WikiReader-iOS/1.0 (https://github.com/PatrickJamesDev/WikiRandomizer-P)"

        fun defaultClient(): HttpClient = HttpClient(Darwin) {
            expectSuccess = true
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 20_000
                socketTimeoutMillis = 20_000
            }
        }
    }
}
