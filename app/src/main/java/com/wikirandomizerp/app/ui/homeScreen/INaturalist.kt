package com.wikirandomizerp.app.ui.homeScreen

import android.net.Uri
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import com.wikirandomizerp.app.parser.HTML_INFOBOX_MARKER
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Helpers for the "View on iNaturalist" action. iNaturalist is a citizen-science site for
 * observations of living organisms, so the action only appears on articles that are about a
 * taxon (plants, animals, fungi).
 */
private val taxoboxTemplates = listOf(
    "{{taxobox", "{{automatic taxobox", "{{speciesbox", "{{subspeciesbox",
    "{{infraspeciesbox", "{{hybridbox", "{{ichnobox", "{{oobox", "{{paraphyletic group"
)

private val organismDescriptions = listOf(
    "species of", "subspecies of", "genus of", "variety of", "form of"
)

/** Package name of the official iNaturalist Android app. */
const val INATURALIST_PACKAGE = "org.inaturalist.android"

fun inaturalistTaxonUrl(id: Long): String = "https://www.inaturalist.org/taxa/$id"

/** Kept for callers that only have a name; the chip itself opens the resolved taxon page. */
fun inaturalistSearchUrl(title: String): String =
    "https://www.inaturalist.org/taxa/search?q=" + Uri.encode(title.trim())

/**
 * Resolves a Wikipedia article title to an iNaturalist taxon id. iNaturalist's autocomplete
 * endpoint accepts scientific and common names, so "Capsicum annuum" and "Dog" both work.
 * Results (including misses) are cached for the session; the app never scrapes the website.
 */
object INaturalistTaxa {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    private val ids = ConcurrentHashMap<String, Long>()
    private val misses = ConcurrentHashMap.newKeySet<String>()

    suspend fun resolveId(title: String): Long? = withContext(Dispatchers.IO) {
        val query = title.trim()
        if (query.isEmpty()) return@withContext null
        ids[query]?.let { return@withContext it }
        if (query in misses) return@withContext null

        val url = "https://api.inaturalist.org/v1/taxa/autocomplete".toHttpUrl()
            .newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("per_page", "1")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "WikiRandomizer-P (iNaturalist taxon lookup)")
            .build()
        val id = runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                val results = JSONObject(body).optJSONArray("results")
                val first = results?.optJSONObject(0) ?: return@use null
                first.optLong("id", -1L).takeIf { it > 0L }
            }
        }.getOrNull()
        if (id != null) ids[query] = id else misses.add(query)
        id
    }
}

/**
 * True when [extract] contains a taxobox (the REST HTML path and any remaining wikitext one) or
 * [description] reads like a taxonomic short description ("Species of flowering plant").
 */
fun isOrganismArticle(
    extract: List<List<AnnotatedString>>,
    description: String?
): Boolean {
    for (section in extract) {
        for (part in section) {
            val text = part.text.trimStart()
            if (text.startsWith(HTML_INFOBOX_MARKER, ignoreCase = true) &&
                text.contains("\u001Ftaxobox", ignoreCase = true)
            ) return true
            val lower = text.take(64).lowercase()
            if (taxoboxTemplates.any { lower.startsWith(it) }) return true
        }
    }
    val desc = description?.trim()?.lowercase() ?: return false
    return organismDescriptions.any { desc.startsWith(it) || desc.contains(" $it") }
}
