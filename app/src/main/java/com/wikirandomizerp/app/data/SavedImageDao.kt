package com.wikirandomizerp.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedImageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(image: SavedImage): Long

    @Delete
    suspend fun delete(image: SavedImage)

    @Query("DELETE FROM saved_image")
    suspend fun deleteAll()

    @Query("DELETE FROM saved_image WHERE imageUrl = :imageUrl AND articleTitle = :articleTitle AND articleLang = :articleLang")
    suspend fun deduplicate(imageUrl: String, articleTitle: String, articleLang: String)

    @Query("SELECT * FROM saved_image ORDER BY savedAt DESC")
    fun getSavedImages(): Flow<List<SavedImage>>

    @Query("SELECT COUNT(*) FROM saved_image")
    suspend fun count(): Int
}
