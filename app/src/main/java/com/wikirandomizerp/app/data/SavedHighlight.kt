package com.wikirandomizerp.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A piece of article text the reader chose to keep. Highlights are deliberately stored as plain
 * text plus the article they came from: they survive article changes, are searchable, and can be
 * reopened without keeping the whole parsed article in memory.
 */
@Entity(tableName = "saved_highlight")
data class SavedHighlight(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    /** Page id when known (saved/offline articles); used only as a convenience for reopening. */
    val pageId: Int? = null,
    val lang: String,
    val title: String,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
    val thumbnail: String? = null,
    val description: String? = null,
    /** Topic library this article belonged to, when known. */
    val topic: String? = null
)
