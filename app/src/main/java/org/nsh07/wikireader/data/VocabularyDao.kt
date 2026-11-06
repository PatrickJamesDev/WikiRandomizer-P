package org.nsh07.wikireader.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VocabularyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(word: VocabularyWord): Long

    @Query("SELECT * FROM vocabulary_word ORDER BY createdAt DESC")
    fun getAll(): Flow<List<VocabularyWord>>

    @Query("SELECT * FROM vocabulary_word WHERE word = :word AND lang = :lang LIMIT 1")
    suspend fun find(word: String, lang: String): VocabularyWord?

    @Query("DELETE FROM vocabulary_word WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM vocabulary_word")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM vocabulary_word")
    suspend fun count(): Int
}
