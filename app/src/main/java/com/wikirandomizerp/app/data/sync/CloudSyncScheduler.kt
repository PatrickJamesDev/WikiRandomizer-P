package com.wikirandomizerp.app.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.wikirandomizerp.app.WikiRandomizerPApplication
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

object CloudSyncScheduler {
    private const val PERIODIC_WORK = "cloud_sync_periodic"

    fun update(context: Context, enabled: Boolean) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        if (!enabled) {
            workManager.cancelUniqueWork(PERIODIC_WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<CloudSyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun networkConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
}

class CloudSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val application = applicationContext as? WikiRandomizerPApplication
            ?: return Result.success()
        val store = application.container.localSyncStore
        if (!store.autoSync) return Result.success()
        return try {
            store.syncNowIfAllowed()
            Result.success()
        } catch (_: SyncException) {
            Result.success()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            if (runAttemptCount >= 3) Result.success() else Result.retry()
        }
    }
}
