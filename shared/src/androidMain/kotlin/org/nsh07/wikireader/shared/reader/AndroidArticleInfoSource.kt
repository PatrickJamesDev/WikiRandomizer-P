package org.nsh07.wikireader.shared.reader

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.nsh07.wikireader.shared.WikiUrls
import java.net.HttpURLConnection
import java.net.URL

class AndroidArticleInfoSource : ArticleInfoSource {
    override suspend fun talkWikitext(language: String, title: String): String? =
        fetch(WikiUrls.talkWikitextUrl(language, title))?.let(::parseTalkWikitext)

    override suspend fun revisions(language: String, title: String): List<ArticleRevision> =
        fetch(WikiUrls.revisionsUrl(language, title))?.let(::parseArticleRevisions).orEmpty()

    private suspend fun fetch(url: String): String? = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            if (connection.responseCode !in 200..299) return@withContext null
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val USER_AGENT =
            "WikiReader-Android/1.0 (https://github.com/PatrickJamesDev/WikiRandomizer-P)"
    }
}
