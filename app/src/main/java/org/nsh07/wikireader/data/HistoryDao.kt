package org.nsh07.wikireader.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SearchHistoryItem)

    @Delete
    suspend fun delete(item: SearchHistoryItem)

    @Query("DELETE FROM search_history")
    suspend fun deleteAll()

    @Query("DELETE FROM search_history WHERE time in (SELECT time FROM search_history ORDER BY time DESC LIMIT 50 OFFSET 200)")
    suspend fun deleteOld()

    @Query("DELETE FROM search_history WHERE `query` = :query AND `lang` = :lang")
    suspend fun deduplicateSearch(query: String, lang: String)

    @Query("SELECT * FROM search_history ORDER BY time DESC LIMIT 200")
    fun getSearchHistory(): Flow<List<SearchHistoryItem>>
}

@Dao
interface ViewHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ViewHistoryItem)

    @Delete
    suspend fun delete(item: ViewHistoryItem)

    @Query("DELETE FROM view_history")
    suspend fun deleteAll()

    @Query("DELETE FROM view_history WHERE time in (SELECT time FROM view_history ORDER BY time DESC LIMIT 50 OFFSET 200)")
    suspend fun deleteOld()

    @Query("SELECT * FROM view_history ORDER BY time DESC LIMIT 200")
    fun getViewHistory(): Flow<List<ViewHistoryItem>>

    @Query("SELECT DISTINCT lang  FROM view_history ORDER BY time DESC LIMIT 4")
    fun getRecentLanguages(): Flow<List<String>>

    @Query("SELECT * FROM view_history ORDER BY time DESC LIMIT 1")
    suspend fun getLast(): ViewHistoryItem?

    @Query("DELETE FROM view_history WHERE title = :title AND lang = :lang")
    suspend fun deleteByTitle(title: String, lang: String)

    @Query("DELETE FROM view_history WHERE time = :time")
    suspend fun deleteByTime(time: Long)

    /** Returns all article titles the user has already viewed in [lang]. Used for deduplication. */
    @Query("SELECT title FROM view_history WHERE lang = :lang")
    suspend fun getViewedTitles(lang: String): List<String>

    /** Old rows written before topic sorting existed; used for a lazy backfill. */
    @Query("SELECT * FROM view_history WHERE topic IS NULL")
    suspend fun getViewHistoryWithoutTopic(): List<ViewHistoryItem>

    /** Local search in the reader's browsing history. */
    @Query(
        "SELECT * FROM view_history " +
                "WHERE title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' " +
                "ORDER BY time DESC LIMIT 25"
    )
    suspend fun search(query: String): List<ViewHistoryItem>

    @Query("UPDATE view_history SET topic = :topic WHERE time = :time")
    suspend fun setTopic(time: Long, topic: String)

    @Query("SELECT COUNT(*) FROM view_history")
    suspend fun count(): Int
}