package com.wikirandomizerp.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedHighlightDao {
    @Insert
    suspend fun insert(highlight: SavedHighlight): Long

    @Delete
    suspend fun delete(highlight: SavedHighlight)

    @Query("DELETE FROM saved_highlight WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM saved_highlight")
    suspend fun deleteAll()

    @Query("SELECT * FROM saved_highlight ORDER BY createdAt DESC, id DESC")
    fun getAll(): Flow<List<SavedHighlight>>

    @Query("SELECT * FROM saved_highlight WHERE title = :title AND lang = :lang ORDER BY createdAt DESC, id DESC")
    fun getForArticle(title: String, lang: String): Flow<List<SavedHighlight>>

    /** One-shot read used when an article is opened, so its saved passages can be tinted. */
    @Query("SELECT * FROM saved_highlight WHERE title = :title AND lang = :lang ORDER BY createdAt DESC, id DESC")
    suspend fun getForArticleOnce(title: String, lang: String): List<SavedHighlight>

    /** Used before inserting an automatically saved passage, so the same selection is kept once. */
    @Query(
        "SELECT * FROM saved_highlight WHERE title = :title AND lang = :lang AND text = :text " +
                "ORDER BY createdAt DESC, id DESC LIMIT 1"
    )
    suspend fun find(title: String, lang: String, text: String): SavedHighlight?

    /** Local search used by the search overlay; small LIMIT keeps it instant on big libraries. */
    @Query(
        "SELECT * FROM saved_highlight " +
                "WHERE text LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%' " +
                "ORDER BY createdAt DESC, id DESC LIMIT 30"
    )
    suspend fun search(query: String): List<SavedHighlight>

    @Query("SELECT COUNT(*) FROM saved_highlight")
    suspend fun count(): Int
}
