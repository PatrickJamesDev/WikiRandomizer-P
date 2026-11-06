package org.nsh07.wikireader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_article")
data class SavedArticle(
    @PrimaryKey
    val pageId: Int,
    val lang: String,
    val langName: String,
    val title: String,
    val thumbnail: String?,
    val description: String?,
    val apiResponse: String,
    val pageContent: String,
    /** When the article was saved; 0 for rows saved before 1.0 beta. */
    val savedAt: Long = System.currentTimeMillis(),
    /** The topic library this article came from, if any. */
    val topic: String? = null
)

data class ArticleInfo(
    val pageId: Int,
    val lang: String,
    val langName: String,
    val title: String,
    val thumbnail: String?,
    val description: String?,
    val savedAt: Long = 0L,
    val topic: String? = null
)

data class LanguageInfo(
    val lang: String,
    val langName: String
)