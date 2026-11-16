package com.wikirandomizerp.app.ui.image

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The article a picture is being shown in. Long-press save menus are nested several levels below
 * the article renderer; providing this once avoids threading title/language through every image
 * component, while still letting the saved-image Gallery reopen the right article.
 */
@Immutable
data class ArticleImageContext(
    val title: String = "",
    val lang: String = ""
)

val LocalArticleImageContext = staticCompositionLocalOf { ArticleImageContext() }
