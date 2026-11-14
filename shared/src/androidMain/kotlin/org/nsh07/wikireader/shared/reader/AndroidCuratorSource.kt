package org.nsh07.wikireader.shared.reader

import com.wikirandomizerp.app.data.CuratorFeed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/** JDK-backed curator list for the shared Android preview. */
class AndroidCuratorSource(
    private val json: Json = Json { ignoreUnknownKeys = true }
) : CuratorSource {

    override suspend fun load(): CuratorLoad = withContext(Dispatchers.IO) {
        val connection = URL(CuratorLinks.FEED_URL).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 12_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            val code = connection.responseCode
            if (code !in 200..299) error("GitHub returned HTTP $code")
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            CuratorLoad(json.decodeFromString<CuratorFeed>(text))
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
