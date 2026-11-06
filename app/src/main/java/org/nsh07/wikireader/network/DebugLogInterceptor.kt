package org.nsh07.wikireader.network

import okhttp3.Interceptor
import okhttp3.Response
import org.nsh07.wikireader.data.DebugLog
import java.io.IOException

/** Logs every HTTP request: URL, status, protocol, cache hit/miss, size and time to headers. */
class DebugLogInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (!DebugLog.enabled) return chain.proceed(chain.request())
        val request = chain.request()
        val t0 = System.nanoTime()
        try {
            val response = chain.proceed(request)
            val ms = (System.nanoTime() - t0) / 1_000_000
            val fromCache = response.cacheResponse != null && response.networkResponse == null
            val length = response.header("Content-Length") ?: "?"
            DebugLog.d(
                "HTTP",
                "${request.method} ${request.url} -> ${response.code} ${response.protocol} " +
                        "${ms}ms bytes=$length ${if (fromCache) "CACHE-HIT" else "network"}" +
                        (response.header("Retry-After")?.let { " Retry-After=$it" } ?: "")
            )
            return response
        } catch (e: IOException) {
            val ms = (System.nanoTime() - t0) / 1_000_000
            DebugLog.w("HTTP", "${request.method} ${request.url} FAILED after ${ms}ms: ${e.javaClass.simpleName}: ${e.message}")
            throw e
        }
    }
}
