package org.nsh07.wikireader.shared.ios

import com.wikirandomizerp.app.data.FeedApiResponse
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
import org.nsh07.wikireader.shared.WikiUrls
import org.nsh07.wikireader.shared.reader.FeedSource
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale

internal class KtorFeedSource(
    private val client: HttpClient = defaultClient()
) : FeedSource {

    override suspend fun load(language: String): FeedApiResponse {
        return try {
            fetch(language, datePath(daysAgo = 0))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            fetch(language, datePath(daysAgo = 1))
        }
    }

    private suspend fun fetch(language: String, datePath: String): FeedApiResponse =
        client.get(WikiUrls.feedUrl(language, datePath)) {
            header(HttpHeaders.Accept, "application/json")
            header(HttpHeaders.UserAgent, USER_AGENT)
        }.body()

    private fun datePath(daysAgo: Int): String {
        val date = if (daysAgo == 0) NSDate() else NSDate(timeIntervalSinceNow = -86_400.0 * daysAgo)
        return NSDateFormatter().apply {
            locale = NSLocale(localeIdentifier = "en_US_POSIX")
            dateFormat = "yyyy/MM/dd"
        }.stringFromDate(date)
    }

    private companion object {
        const val USER_AGENT =
            "WikiReader-iOS/1.0 (https://github.com/PatrickJamesDev/WikiRandomizer-P)"

        fun defaultClient(): HttpClient = HttpClient(Darwin) {
            expectSuccess = true
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 20_000
                socketTimeoutMillis = 30_000
            }
        }
    }
}
