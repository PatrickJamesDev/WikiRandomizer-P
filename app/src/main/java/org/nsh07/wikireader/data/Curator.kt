package org.nsh07.wikireader.data

import kotlinx.serialization.Serializable

/**
 * A community curator whose reading list is hosted on GitHub. Keeping this data outside the app
 * means the list can grow without shipping a new APK, and every entry can be reviewed in public.
 */
@Serializable
data class CuratorFeed(
    val version: Int = 1,
    val curators: List<Curator> = emptyList()
)

@Serializable
data class Curator(
    val id: String = "",
    val name: String = "",
    val bio: String = "",
    /** GitHub profile, opened when the user taps the person's name. */
    val github: String = "",
    /** Optional avatar URL (any HTTPS image). */
    val avatar: String? = null,
    val picks: List<CuratorPick> = emptyList()
)

@Serializable
data class CuratorPick(
    val title: String = "",
    val lang: String = "en",
    /** Short reason this article is worth reading. */
    val note: String = ""
)
