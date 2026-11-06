package org.nsh07.wikireader.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A word the reader saved from an article, with the definition shown when it was saved. */
@Entity(
    tableName = "vocabulary_word",
    indices = [Index(value = ["word", "lang"], unique = true)]
)
data class VocabularyWord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val lang: String,
    val definition: String? = null,
    val articleTitle: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
