package org.nsh07.wikireader.ui.common

import org.nsh07.wikireader.data.SEARCH_TOPIC_PREFIX

/** Sort order shared by Saved articles and History. */
enum class CollectionSort {
    DATE_NEW, DATE_OLD, TOPIC_AZ, TOPIC_ZA;

    val isDate: Boolean get() = this == DATE_NEW || this == DATE_OLD
}

/** List, or a grid with 2 / 3 / 4 columns. */
enum class CollectionLayout(val columns: Int) {
    LIST(1), GRID_2(2), GRID_3(3), GRID_4(4);

    val isGrid: Boolean get() = columns > 1
    fun next(): CollectionLayout = when (this) {
        LIST -> GRID_2
        GRID_2 -> GRID_3
        GRID_3 -> GRID_4
        GRID_4 -> LIST
    }
}

/** Human-readable label for a stored topic key ("Category:Plants", "search:chairs", null). */
fun topicDisplayName(topic: String?): String? {
    if (topic.isNullOrBlank()) return null
    return topic
        .removePrefix(SEARCH_TOPIC_PREFIX)
        .removePrefix("Category:")
        .replace('_', ' ')
        .trim()
        .ifBlank { null }
}
