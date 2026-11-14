package org.nsh07.wikireader.shared.ios

import com.wikirandomizerp.app.data.CuratorFeed
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
import org.nsh07.wikireader.shared.reader.CuratorLinks
import org.nsh07.wikireader.shared.reader.CuratorLoad
import org.nsh07.wikireader.shared.reader.CuratorSource

internal class KtorCuratorSource(
    private val client: HttpClient = defaultClient()
) : CuratorSource {

    override suspend fun load(): CuratorLoad =
        CuratorLoad(
            client.get(CuratorLinks.FEED_URL) {
                header(HttpHeaders.Accept, "application/json")
                header(HttpHeaders.UserAgent, USER_AGENT)
            }.body<CuratorFeed>()
        )

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
