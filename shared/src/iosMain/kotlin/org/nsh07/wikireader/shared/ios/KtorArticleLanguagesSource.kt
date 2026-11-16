package org.nsh07.wikireader.shared.ios

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import org.nsh07.wikireader.shared.WikiUrls
import org.nsh07.wikireader.shared.reader.ArticleLanguageLink
import org.nsh07.wikireader.shared.reader.ArticleLanguagesSource
import org.nsh07.wikireader.shared.reader.parseArticleLanguages

internal class KtorArticleLanguagesSource(
    private val client: HttpClient = defaultClient()
) : ArticleLanguagesSource {
    override suspend fun languages(
        language: String,
        title: String
    ): List<ArticleLanguageLink> = try {
        val body = client.get(WikiUrls.languageLinksUrl(language, title)) {
            header(HttpHeaders.Accept, "application/json")
            header(HttpHeaders.UserAgent, USER_AGENT)
        }.bodyAsText()
        parseArticleLanguages(body)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        emptyList()
    }

    private companion object {
        const val USER_AGENT =
            "WikiReader-iOS/1.0 (https://github.com/PatrickJamesDev/WikiRandomizer-P)"

        fun defaultClient(): HttpClient = HttpClient(Darwin) {
            expectSuccess = true
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 20_000
            }
        }
    }
}
