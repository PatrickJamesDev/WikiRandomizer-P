package org.nsh07.wikireader.shared.reader

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.nsh07.wikireader.shared.WikiUrls
import java.net.HttpURLConnection
import java.net.URL

class AndroidDictionarySource : DictionarySource {
    override suspend fun define(word: String, language: String): String? =
        withContext(Dispatchers.IO) {
            lookup(language, word) ?: if (language.lowercase() != "en") {
                lookup("en", word)
            } else {
                null
            }
        }

    private fun lookup(language: String, word: String): String? {
        val connection = URL(WikiUrls.wiktionaryDefinitionUrl(language, word))
            .openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            if (connection.responseCode !in 200..299) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parseWiktionaryDefinition(body)
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
