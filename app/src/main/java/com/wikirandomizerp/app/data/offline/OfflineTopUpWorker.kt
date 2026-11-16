package com.wikirandomizerp.app.data.offline

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wikirandomizerp.app.data.ArticleCache
import com.wikirandomizerp.app.data.DebugLog
import com.wikirandomizerp.app.data.DefaultAppContainer
import com.wikirandomizerp.app.data.TopicIndex
import com.wikirandomizerp.app.data.billing.BillingManager
import com.wikirandomizerp.app.network.HostGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OfflineTopUpWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val container = DefaultAppContainer(applicationContext)
        val prefs = container.appPreferencesRepository
        val supporterMode = prefs.readBooleanPreference("supporter-mode") == true
        val premium = BillingManager.isPremiumActive(applicationContext, supporterMode)
        val target = (prefs.readIntPreference("offline-top-up-target") ?: 0).coerceIn(0, 200)
        if (!premium || target <= 0) return@withContext Result.success()

        val lang = prefs.readStringPreference("lang") ?: "en"
        HostGate.project = if (prefs.readBooleanPreference("wikivoyage-enabled") == true) {
            "wikivoyage"
        } else {
            "wikipedia"
        }
        val cache = ArticleCache(applicationContext)
        val missing = target - cache.offlineStats().articles
        if (missing <= 0) return@withContext Result.success()

        val index = TopicIndex.get(applicationContext, HostGate.storageLanguage(lang))
        val topics = index.libraryStats().topics
            .filter { it.enabled && it.complete }
            .map { it.topic }
        if (topics.isEmpty()) return@withContext Result.success()

        val rows = linkedMapOf<String, String>()
        runCatching {
            index.pick(
                topics = topics,
                imagesOnly = false,
                minBytes = 0,
                qualityOnly = false,
                limit = missing * 6,
                includeSeen = false
            )
        }.getOrDefault(emptyList()).forEach { row ->
            if (rows.size < missing && !cache.isOffline(lang, row.title)) {
                rows[row.title] = row.title
            }
        }
        if (rows.isEmpty()) return@withContext Result.success()

        val kind = if (prefs.readStringPreference("render-mode") == "html") "html" else "wikitext"
        var stored = 0
        rows.keys.forEach { title ->
            try {
                HostGate.withHost(container.interceptor, lang) {
                    val pageData = container.wikipediaRepository.getPageData(title)
                    val page = pageData.query?.pages?.firstOrNull() ?: return@withHost
                    val content = if (kind == "html") {
                        container.wikipediaRepository.getPageHtml(page.title)
                    } else {
                        container.wikipediaRepository.getPageContent(page.title)
                    }
                    if (content.isNotBlank()) {
                        cache.put(
                            lang = lang,
                            title = page.title,
                            page = page,
                            content = content,
                            alsoAs = title,
                            contentKind = kind,
                            offline = true
                        )
                        stored++
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DebugLog.w("Offline", "top-up could not download '$title': ${e.message}")
            }
        }
        DebugLog.i("Offline", "top-up stored $stored articles (target $target)")
        Result.success()
    }
}
