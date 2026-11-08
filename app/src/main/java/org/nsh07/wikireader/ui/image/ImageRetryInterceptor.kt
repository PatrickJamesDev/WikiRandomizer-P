package org.nsh07.wikireader.ui.image

import coil3.intercept.Interceptor
import coil3.network.HttpException
import coil3.request.ErrorResult
import coil3.request.ImageResult
import kotlinx.coroutines.delay
import java.io.IOException

internal class ImageRetryInterceptor(
    private val maxRetries: Int = 2
) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        var result = chain.proceed()
        var attempt = 0
        while (result is ErrorResult && attempt < maxRetries && result.throwable.isRetryable()) {
            delay(350L * (attempt + 1))
            attempt++
            result = chain.proceed()
        }
        return result
    }

    private fun Throwable?.isRetryable(): Boolean {
        var cause = this
        while (cause != null) {
            when (cause) {
                is IOException -> return true
                is HttpException -> {
                    val code = cause.response.code
                    return code == 408 || code == 429 || code in 500..599
                }
            }
            cause = cause.cause
        }
        return false
    }
}
