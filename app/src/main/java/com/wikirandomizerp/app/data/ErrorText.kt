package com.wikirandomizerp.app.data

import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** A short, human-readable reason for a failed request (instead of a raw exception message). */
fun friendlyError(e: Throwable): String = when (e) {
    is UnknownHostException -> "No internet connection."
    is ConnectException -> "Cannot reach Wikipedia right now."
    is SocketTimeoutException -> "The connection timed out. Try again."
    is HttpException -> when (e.code()) {
        429 -> "Wikipedia is rate-limiting requests. Wait a moment and try again."
        in 500..599 -> "Wikipedia had a problem (HTTP ${e.code()}). Try again later."
        else -> "Wikipedia answered with HTTP ${e.code()}."
    }
    is SerializationException -> "Wikipedia sent an invalid answer. Try again."
    is IOException -> "Network problem: ${e.message ?: "unknown error"}"
    else -> e.message ?: e.javaClass.simpleName
}
