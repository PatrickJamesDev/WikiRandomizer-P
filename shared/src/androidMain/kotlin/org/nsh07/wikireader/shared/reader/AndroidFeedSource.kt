package org.nsh07.wikireader.shared.reader

import com.wikirandomizerp.app.data.FeedApiResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.nsh07.wikireader.shared.WikiUrls
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** JDK-backed featured feed for the shared Android preview, with a yesterday fallback. */
class AndroidFeedSource(
    private val json: Json = Json { ignoreUnknownKeys = true }
) : FeedSource {

    override suspend fun load(language: String): FeedApiResponse = withContext(Dispatchers.IO) {
        try {
            fetch(WikiUrls.feedUrl(language, today()))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            // The feed for the device's local date may not be published yet shortly after midnight.
            fetch(WikiUrls.feedUrl(language, yesterday()))
        }
    }

    private fun fetch(url: String): FeedApiResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 20_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            val code = connection.responseCode
            if (code !in 200..299) error("Wikipedia returned HTTP $code")
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            json.decodeFromString<FeedApiResponse>(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun today(): String =
        LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))

    private fun yesterday(): String =
        LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))

    private companion object {
        const val USER_AGENT =
            "WikiReader-Android/1.0 (https://github.com/PatrickJamesDev/WikiRandomizer-P)"
    }
}
