package com.wikirandomizerp.app.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.wikirandomizerp.app.MainActivity
import com.wikirandomizerp.app.R
import com.wikirandomizerp.app.WikiRandomizerPApplication
import com.wikirandomizerp.app.data.AppLaunchExtras
import com.wikirandomizerp.app.data.DebugLog
import com.wikirandomizerp.app.data.LanguagePinnedWikipediaRepository
import com.wikirandomizerp.app.data.TopicIndex
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Optional daily "article of the day from your topics" notification. It is a developer preview,
 * disabled by default, and only runs while [isEnabled] is true.
 */
object DailyArticleScheduler {
    private const val PERIODIC_WORK = "daily_article_notification"
    private const val PREFS = "daily_article_notification"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LANG = "lang"

    fun isEnabled(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun language(context: Context, fallback: String): String =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANG, null)
            ?.takeIf { it.isNotBlank() }
            ?: fallback

    fun update(context: Context, enabled: Boolean, lang: String) {
        val appContext = context.applicationContext
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putString(KEY_LANG, lang)
            .apply()

        val workManager = WorkManager.getInstance(appContext)
        if (!enabled) {
            workManager.cancelUniqueWork(PERIODIC_WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<DailyArticleWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInitialDelay(minutesUntilNextMorning(), TimeUnit.MINUTES)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** First run around 09:00 local time; later runs use the same 24-hour cadence. */
    private fun minutesUntilNextMorning(): Long {
        val now = Calendar.getInstance()
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return ((next.timeInMillis - now.timeInMillis) / 60_000L).coerceAtLeast(1L)
    }
}

class DailyArticleWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!DailyArticleScheduler.isEnabled(context)) return Result.success()
        val application = context as? WikiRandomizerPApplication ?: return Result.success()
        val prefs = application.container.preferencesStateMutableFlow.value
        if (!prefs.dailyNotification) return Result.success()
        val lang = DailyArticleScheduler.language(context, prefs.lang)
        return try {
            val index = TopicIndex.get(context, lang)
            val topics = index.completeEnabledTopics()
            val title = if (topics.isNotEmpty()) {
                index.pick(
                    topics = topics,
                    imagesOnly = false,
                    minBytes = 0,
                    qualityOnly = false,
                    limit = 1
                ).firstOrNull()?.title
            } else {
                null
            } ?: randomUnseenTitle(application, lang, index)
            if (!title.isNullOrBlank() &&
                DailyArticleNotifier.notify(context, title, lang)
            ) {
                // The notification is the only place this article was shown; remember it so the
                // next daily pick cannot offer the same title again.
                index.markSeen(title)
            }
            Result.success()
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: Throwable) {
            DebugLog.w("DailyArticle", "could not prepare the daily notification: ${t.message}")
            if (runAttemptCount >= 3) Result.success() else Result.retry()
        }
    }
}

/**
 * General-random fallback for the daily notification. Pins the request to [lang] and skips the
 * permanent seen list, because the plain random endpoint has no concept of read articles.
 */
private suspend fun randomUnseenTitle(
    application: WikiRandomizerPApplication,
    lang: String,
    index: TopicIndex
): String? {
    val repository = LanguagePinnedWikipediaRepository(
        application.container.wikipediaRepository,
        application.container.interceptor,
        lang
    )
    repeat(4) {
        val pages = repository.getRandomResult().query?.pages.orEmpty()
            .filter { it.title.isNotBlank() }
        if (pages.isEmpty()) return@repeat
        val seen = index.seenAmong(pages.map { it.title })
        pages.firstOrNull { it.title !in seen }?.let { return it.title }
    }
    return null
}

object DailyArticleNotifier {
    private const val CHANNEL_ID = "daily_article"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notificationChannelName),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notificationChannelDesc)
            }
        )
    }

    /** @return true when a notification was actually posted. */
    fun notify(context: Context, title: String, lang: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        ensureChannel(context)
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AppLaunchExtras.EXTRA_ARTICLE_TITLE, title)
            putExtra(AppLaunchExtras.EXTRA_ARTICLE_LANG, lang)
        }
        val pending = PendingIntent.getActivity(
            context,
            1001,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.leaf)
            .setContentTitle(context.getString(R.string.dailyArticleNotificationTitle))
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(title))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?: return false
        return runCatching {
            manager.notify(NOTIFICATION_ID, notification)
            true
        }.getOrDefault(false)
    }

    private const val NOTIFICATION_ID = 4201
}
