package com.wikirandomizerp.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WikiApiSearchResults(
    val query: WikiSearchResultsQuery = WikiSearchResultsQuery()
)

@Serializable
data class WikiSearchResultsQuery(
    val pages: List<WikiSearchResult> = emptyList()
)

@Serializable
data class WikiSearchResult(
    val ns: Int = 0,
    val title: String,
    // Defaults: one result missing an optional field must not make the whole search response fail
    @SerialName("titlesnippet") val titleSnippet: String = "",
    @SerialName("pageid") val pageId: Int,
    val snippet: String = "",
    val index: Int = 0,
    @SerialName("redirecttitle") val redirectTitle: String? = null,
    val thumbnail: WikiPhoto? = null
)

@Serializable
data class WikiApiPrefixSearchResults(
    val query: WikiPrefixSearchResultsQuery = WikiPrefixSearchResultsQuery()
)

@Serializable
data class WikiPrefixSearchResultsQuery(
    val pages: List<WikiPrefixSearchResult> = emptyList()
)

@Serializable
data class WikiPrefixSearchResult(
    @SerialName("pageid") val pageId: Int,
    val ns: Int = 0,
    val title: String,
    val index: Int = 0,
    val thumbnail: WikiPhoto? = null,
    val terms: WikiPrefixSearchPageTerms? = null
)

@Serializable
data class WikiPrefixSearchPageTerms(
    // Optional in the API response (a page can have terms without a description): a required field
    // made the whole search-suggestion response fail to parse.
    val description: List<String> = emptyList()
)