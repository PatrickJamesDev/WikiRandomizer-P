package org.nsh07.wikireader.data

/** The Commons category that matches an article, with a selection of its pictures. */
data class CommonsCategoryGallery(
    /** Category name without the "Category:" prefix, e.g. "Navel oranges". */
    val category: String,
    /** Address of the category page on Commons. */
    val categoryUrl: String,
    val images: List<CommonsGalleryImage>
)

/** A picture of a Commons category, ready for a thumbnail strip and for the full-screen viewer. */
data class CommonsGalleryImage(
    val title: String,
    val thumbnailUrl: String,
    /** Address to show in the full-screen viewer (a large standard-size thumbnail). */
    val viewUrl: String,
    val width: Int,
    val height: Int,
    val descriptionUrl: String?
)

/** One image from Wikimedia Commons, used by the article image gallery. */
data class CommonsImage(
    val title: String,
    val thumbnailUrl: String?,
    val fullUrl: String?,
    val descriptionUrl: String?
)
