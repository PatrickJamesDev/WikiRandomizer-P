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
import com.wikirandomizerp.app.data.DebugLog
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

object DriveSyncScheduler {
    private const val PERIODIC_WORK = "drive_sync_periodic"

    fun update(context: Context, enabled: Boolean) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        if (!enabled) {
            workManager.cancelUniqueWork(PERIODIC_WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<DriveSyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}

class DriveSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val application = applicationContext as? WikiRandomizerPApplication
            ?: return Result.success()
        val store = application.container.driveSyncStore
        if (!store.autoSync || !store.connected) return Result.success()
        return try {
            store.syncIfAllowed()
            Result.success()
        } catch (reconnect: DriveReconnectRequiredException) {
            // Background work cannot show the consent screen. Keep the schedule; the next
            // foreground sync or the Settings dialog will reconnect.
            DebugLog.w("DriveSync", "background sync needs reconnection: ${reconnect.message}")
            Result.success()
        } catch (error: SyncException) {
            DebugLog.w("DriveSync", "background sync failed: ${error.message}")
            Result.success()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            if (runAttemptCount >= 3) Result.success() else Result.retry()
        }
    }
}
