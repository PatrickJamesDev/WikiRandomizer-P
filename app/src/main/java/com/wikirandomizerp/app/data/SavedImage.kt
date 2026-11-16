package com.wikirandomizerp.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A picture the reader explicitly saved. A downloaded copy is written to the phone's gallery and
 * [localUri] points at it; "Save to gallery" entries keep [localUri] null and use the remote
 * thumbnail instead. [articleTitle]/[articleLang] let a tap reopen the Wikipedia article it came from.
 */
@Entity(tableName = "saved_image")
data class SavedImage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    /** Full-size remote address, used as the stable identity when saving twice. */
    val imageUrl: String,
    /** Phone-gallery content URI (or a file URI on older Android). */
    val localUri: String? = null,
    val thumbnailUrl: String? = null,
    val description: String? = null,
    val articleTitle: String = "",
    val articleLang: String = "",
    val pageUrl: String? = null,
    val savedAt: Long = System.currentTimeMillis()
)
