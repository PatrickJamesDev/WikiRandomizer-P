package com.wikirandomizerp.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * Loads the public curator list from the project's GitHub repository.
 *
 * The JSON is the single source of truth, so curators can be added or corrected without an app
 * update. A bundled copy keeps the screen useful before the file has been pushed (or when the
 * device is offline); [CuratorLoad.fromFallback] tells the UI which one is on screen.
 */
class CuratorRepository(
    private val context: Context,
    private val url: String = FEED_URL,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    suspend fun load(): Result<CuratorLoad> = withContext(Dispatchers.IO) {
        val remote = runCatching { CuratorLoad(fetchRemote(), fromFallback = false) }
        remote.recoverCatching {
            val text = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
            CuratorLoad(json.decodeFromString<CuratorFeed>(text), fromFallback = true)
        }
    }

    private fun fetchRemote(): CuratorFeed {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "WikiRandomizer-P curator-list")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("GitHub returned HTTP $code")
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            return json.decodeFromString<CuratorFeed>(text)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val FEED_URL =
            "https://raw.githubusercontent.com/PatrickJamesDev/WikiRandomizer-P/main/curators.json"
        const val FEED_PAGE =
            "https://github.com/PatrickJamesDev/WikiRandomizer-P/blob/main/curators.json"
        private const val ASSET_NAME = "curators.json"
    }
}

data class CuratorLoad(
    val feed: CuratorFeed,
    val fromFallback: Boolean
)
