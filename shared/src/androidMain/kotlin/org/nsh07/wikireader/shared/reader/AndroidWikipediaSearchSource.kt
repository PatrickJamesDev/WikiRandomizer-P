package org.nsh07.wikireader.shared.reader

import com.wikirandomizerp.app.data.WikiApiSearchResults
import com.wikirandomizerp.app.data.WikiSearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.nsh07.wikireader.shared.WikiUrls
import java.net.HttpURLConnection
import java.net.URL

/** JDK-backed search source for the Android shared-reader preview. */
class AndroidWikipediaSearchSource(
    private val json: Json = Json { ignoreUnknownKeys = true }
) : WikipediaSearchSource {

    override suspend fun search(language: String, query: String): List<WikiSearchResult> =
        withContext(Dispatchers.IO) {
            val connection = URL(WikiUrls.searchApiUrl(language, query))
                .openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 20_000
                connection.readTimeout = 30_000
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", USER_AGENT)
                val code = connection.responseCode
                if (code !in 200..299) error("Wikipedia returned HTTP $code")
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                json.decodeFromString<WikiApiSearchResults>(text)
                    .query
                    .pages
                    .filter { it.ns == 0 && it.title.isNotBlank() }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } finally {
                connection.disconnect()
            }
        }

    private companion object {
        const val USER_AGENT =
            "WikiReader-Android/1.0 (https://github.com/PatrickJamesDev/WikiRandomizer-P)"
    }
}
