package org.nsh07.wikireader.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedArticleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(savedArticle: SavedArticle)

    @Query("DELETE FROM saved_article WHERE pageId = :pageId AND lang = :lang")
    suspend fun delete(pageId: Int, lang: String)

    @Query("DELETE FROM saved_article")
    suspend fun deleteAll()

    @Query("SELECT EXISTS(SELECT * FROM saved_article WHERE pageId = :pageId AND lang = :lang)")
    suspend fun isSaved(pageId: Int, lang: String): Boolean

    @Query("SELECT * FROM saved_article WHERE pageId = :pageId AND lang = :lang LIMIT 1")
    suspend fun getSavedArticle(pageId: Int, lang: String): SavedArticle?

    /** Small columns only: safe to read even when the stored article text is too big for a CursorWindow. */
    @Query("SELECT pageId, lang, langName, title, thumbnail, description, savedAt, topic FROM saved_article WHERE pageId = :pageId AND lang = :lang LIMIT 1")
    suspend fun getSavedArticleInfo(pageId: Int, lang: String): ArticleInfo?

    @Query("SELECT DISTINCT lang, langName FROM saved_article")
    fun getSavedArticleLanguages(): Flow<List<LanguageInfo>>

    @Query("SELECT pageId, lang, langName, title, thumbnail, description, savedAt, topic FROM saved_article ORDER BY savedAt DESC, title")
    fun getAllSavedArticles(): Flow<List<ArticleInfo>>

    /** Old rows saved before topic sorting existed; used for a one-time lazy backfill. */
    @Query("SELECT pageId, lang, langName, title, thumbnail, description, savedAt, topic FROM saved_article WHERE topic IS NULL")
    suspend fun getSavedArticlesWithoutTopic(): List<ArticleInfo>

    /** Local search in the reader's saved library (title and short description). */
    @Query(
        "SELECT pageId, lang, langName, title, thumbnail, description, savedAt, topic FROM saved_article " +
                "WHERE title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' " +
                "ORDER BY savedAt DESC, title LIMIT 25"
    )
    suspend fun search(query: String): List<ArticleInfo>

    @Query("UPDATE saved_article SET topic = :topic WHERE pageId = :pageId AND lang = :lang")
    suspend fun setTopic(pageId: Int, lang: String, topic: String)

    @Query("SELECT COUNT(*) FROM saved_article")
    suspend fun count(): Int
}