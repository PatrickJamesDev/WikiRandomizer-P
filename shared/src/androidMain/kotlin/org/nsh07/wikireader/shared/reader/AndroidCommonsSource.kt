package org.nsh07.wikireader.shared.reader

import com.wikirandomizerp.app.data.CommonsImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.nsh07.wikireader.shared.WikiUrls
import java.net.HttpURLConnection
import java.net.URL

class AndroidCommonsSource : CommonsSource {
    override suspend fun images(language: String, title: String): List<CommonsImage> =
        withContext(Dispatchers.IO) {
            val connection = URL(WikiUrls.commonsImagesUrl(language, title))
                .openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 20_000
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", USER_AGENT)
                if (connection.responseCode !in 200..299) return@withContext emptyList()
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                parseCommonsImages(body)
            } finally {
                connection.disconnect()
            }
        }

    private companion object {
        const val USER_AGENT =
            "WikiReader-Android/1.0 (https://github.com/PatrickJamesDev/WikiRandomizer-P)"
    }
}
