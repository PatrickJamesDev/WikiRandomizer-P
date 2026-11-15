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
import org.nsh07.wikireader.shared.reader.ArticleInfoSource
import org.nsh07.wikireader.shared.reader.ArticleRevision
import org.nsh07.wikireader.shared.reader.parseArticleRevisions
import org.nsh07.wikireader.shared.reader.parseTalkWikitext

internal class KtorArticleInfoSource(
    private val client: HttpClient = defaultClient()
) : ArticleInfoSource {
    override suspend fun talkWikitext(language: String, title: String): String? =
        fetch(WikiUrls.talkWikitextUrl(language, title))?.let(::parseTalkWikitext)

    override suspend fun revisions(language: String, title: String): List<ArticleRevision> =
        fetch(WikiUrls.revisionsUrl(language, title))?.let(::parseArticleRevisions).orEmpty()

    private suspend fun fetch(url: String): String? = try {
        client.get(url) {
            header(HttpHeaders.Accept, "application/json")
            header(HttpHeaders.UserAgent, USER_AGENT)
        }.bodyAsText()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        null
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
