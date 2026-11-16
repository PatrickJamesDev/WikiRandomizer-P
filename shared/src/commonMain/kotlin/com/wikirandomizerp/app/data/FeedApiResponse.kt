package com.wikirandomizerp.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FeedApiResponse(
    val tfa: FeedApiTFA? = null,
    @SerialName("mostread") val mostRead: FeedApiMostRead? = null,
    val image: FeedApiImage? = null,
    val news: List<FeedApiNews>? = null,
    @SerialName("onthisday") val onThisDay: List<FeedApiOTD>? = null,
)

@Serializable
data class FeedApiTFA(
    val titles: FeedTitles? = null,
    val thumbnail: FeedImageSource? = null,
    @SerialName("originalimage") val originalImage: FeedImageSource? = null,
    val lang: String? = null,
    val description: String? = null,
    val extract: String? = null,
    val timestamp: String? = null
)

@Serializable
data class FeedApiMostRead(
    val date: String? = null,
    val articles: List<MostReadArticle>? = null
)

@Serializable
data class FeedApiImage(
    val title: String? = null,
    val thumbnail: FeedImageSource? = null,
    val image: FeedImageSource? = null,
    val artist: FeedArtist? = null,
    val credit: FeedCredit? = null,
    val description: FeedDescription? = null,
    @SerialName("file_page") val filePage: String? = null
)

@Serializable
data class FeedApiNews(
    val links: List<FeedArticleSummary>? = null,
    val story: String? = null
)

@Serializable
data class FeedApiOTD(
    val text: String? = null,
    val pages: List<FeedArticleSummary>? = null,
    val year: Int? = null
)

@Serializable
data class FeedTitles(
    val canonical: String? = null,
    val normalized: String? = null
)

@Serializable
data class FeedImageSource(
    val source: String? = null,
    val width: Int? = null,
    val height: Int? = null
)

@Serializable
data class MostReadArticle(
    val views: Int? = null,
    val rank: Int? = null,
    @SerialName("view_history") val viewHistory: List<FeedViewHistory>? = null,
    val titles: FeedTitles? = null,
    val thumbnail: FeedImageSource? = null,
    @SerialName("originalimage") val originalImage: FeedImageSource? = null,
    val lang: String? = null,
    val description: String? = null,
    val extract: String? = null
)

@Serializable
data class FeedArticleSummary(
    val titles: FeedTitles? = null,
    val thumbnail: FeedImageSource? = null,
    @SerialName("originalimage") val originalImage: FeedImageSource? = null,
    val lang: String? = null,
    val description: String? = null,
    val extract: String? = null
)

@Serializable
data class FeedViewHistory(
    val date: String? = null,
    val views: Int? = null
)

@Serializable
data class FeedArtist(
    val text: String? = null,
    val name: String? = null
)

@Serializable
data class FeedCredit(
    val text: String? = null
)

@Serializable
data class FeedDescription(
    val text: String? = null,
    val lang: String? = null
)