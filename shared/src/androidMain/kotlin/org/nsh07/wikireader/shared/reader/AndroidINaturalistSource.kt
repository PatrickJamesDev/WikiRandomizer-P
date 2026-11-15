package org.nsh07.wikireader.shared.reader

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class AndroidINaturalistSource : INaturalistSource {
    override suspend fun taxonId(title: String): Long? = withContext(Dispatchers.IO) {
        val connection = URL(INaturalistUrls.autocompleteUrl(title))
            .openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", INaturalistUrls.USER_AGENT)
            if (connection.responseCode !in 200..299) return@withContext null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parseINaturalistTaxonId(body)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            null
        } finally {
            connection.disconnect()
        }
    }
}
