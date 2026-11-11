package com.wikirandomizerp.app.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Coordinates the shared Wikipedia host used by [HostSelectionInterceptor].
 *
 * Retrofit's base URL is fixed, so the Wikipedia language is chosen by mutating one global host.
 * Two overlapping requests in different languages (for example a German feed loading while an
 * English article is restored) would otherwise be able to send one of them to the wrong
 * Wikipedia. Everything that changes the host for a request goes through this gate, including the
 * background topic-library builder.
 *
 * Requests for the *same* language run concurrently, which keeps the library builder fast. A
 * request for a different language waits until the current language has no in-flight requests,
 * then switches the host for its own batch.
 */
object HostGate {
    private val mutex = Mutex()
    private var currentLang: String? = null
    private var active = 0
    private var idle: CompletableDeferred<Unit>? = null

    suspend fun <T> withHost(
        interceptor: HostSelectionInterceptor,
        lang: String,
        block: suspend () -> T
    ): T {
        while (true) {
            var waitFor: CompletableDeferred<Unit>? = null
            var acquired = false
            mutex.withLock {
                if (active == 0 || currentLang == lang) {
                    currentLang = lang
                    interceptor.setHost("$lang.wikipedia.org")
                    active++
                    acquired = true
                } else {
                    waitFor = idle ?: CompletableDeferred<Unit>().also { idle = it }
                }
            }
            if (acquired) break
            waitFor?.await()
        }
        try {
            return block()
        } finally {
            mutex.withLock {
                active--
                if (active == 0) {
                    idle?.complete(Unit)
                    idle = null
                }
            }
        }
    }
}
