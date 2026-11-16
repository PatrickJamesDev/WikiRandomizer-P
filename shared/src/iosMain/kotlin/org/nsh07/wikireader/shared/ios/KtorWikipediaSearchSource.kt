package org.nsh07.wikireader.shared.ios

import com.wikirandomizerp.app.data.WikiApiSearchResults
import com.wikirandomizerp.app.data.WikiSearchResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.nsh07.wikireader.shared.WikiUrls
import org.nsh07.wikireader.shared.reader.WikipediaSearchSource

internal class KtorWikipediaSearchSource(
    private val client: HttpClient = defaultClient()
) : WikipediaSearchSource {

    override suspend fun search(language: String, query: String): List<WikiSearchResult> =
        client.get(WikiUrls.searchApiUrl(language, query)) {
            header(HttpHeaders.Accept, "application/json")
            header(HttpHeaders.UserAgent, USER_AGENT)
        }.body<WikiApiSearchResults>()
            .query
            .pages
            .filter { it.ns == 0 && it.title.isNotBlank() }

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
