package org.nsh07.wikireader.shared.ios

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import org.nsh07.wikireader.shared.reader.INaturalistSource
import org.nsh07.wikireader.shared.reader.INaturalistUrls
import org.nsh07.wikireader.shared.reader.parseINaturalistTaxonId

internal class KtorINaturalistSource(
    private val client: HttpClient = defaultClient()
) : INaturalistSource {
    override suspend fun taxonId(title: String): Long? = try {
        val body = client.get(INaturalistUrls.autocompleteUrl(title)) {
            header(HttpHeaders.Accept, "application/json")
            header(HttpHeaders.UserAgent, INaturalistUrls.USER_AGENT)
        }.bodyAsText()
        parseINaturalistTaxonId(body)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        null
    }

    private companion object {
        fun defaultClient(): HttpClient = HttpClient(Darwin) {
            expectSuccess = true
            install(HttpTimeout) {
                requestTimeoutMillis = 10_000
                connectTimeoutMillis = 8_000
                socketTimeoutMillis = 10_000
            }
        }
    }
}
