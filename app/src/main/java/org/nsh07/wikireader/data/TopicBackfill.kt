package org.nsh07.wikireader.data

import android.content.Context
import kotlinx.coroutines.CancellationException

/**
 * Fills in the topic of saved/history rows written before topic sorting existed.
 *
 * This is deliberately lazy and best-effort: the screens call it once when they open, it only
 * touches rows whose topic is still null, and a row that no longer belongs to an enabled topic
 * simply stays null (shown as "Other topics"). New rows are tagged at insert time, so after the
 * first pass this is a cheap empty query.
 */
object TopicBackfill {
    suspend fun saved(context: Context, database: DatabaseRepository) {
        val rows = try {
            database.getSavedArticlesWithoutTopic()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DebugLog.w("TopicBackfill", "could not read saved rows: ${e.message}")
            return
        }
        for (row in rows) {
            val topic = topicFor(context, row.lang, row.title) ?: continue
            try {
                database.setSavedArticleTopic(row.pageId, row.lang, topic)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DebugLog.w("TopicBackfill", "could not tag saved '${row.title}': ${e.message}")
            }
        }
    }

    suspend fun history(context: Context, database: DatabaseRepository) {
        val rows = try {
            database.getViewHistoryWithoutTopic()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DebugLog.w("TopicBackfill", "could not read history rows: ${e.message}")
            return
        }
        for (row in rows) {
            val topic = topicFor(context, row.lang, row.title) ?: continue
            try {
                database.setViewHistoryTopic(row.time, topic)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DebugLog.w("TopicBackfill", "could not tag history '${row.title}': ${e.message}")
            }
        }
    }

    private fun topicFor(context: Context, lang: String, title: String): String? = try {
        TopicIndex.get(context, lang).topicsForTitle(title).firstOrNull()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        DebugLog.w("TopicBackfill", "could not open topic index for '$lang': ${e.message}")
        null
    }
}
