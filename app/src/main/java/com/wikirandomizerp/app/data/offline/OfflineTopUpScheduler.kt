package com.wikirandomizerp.app.data.offline

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object OfflineTopUpScheduler {
    private const val PERIODIC_WORK = "offline-top-up-periodic"
    private const val NOW_WORK = "offline-top-up-now"

    fun update(context: Context, target: Int) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        if (target <= 0) {
            workManager.cancelUniqueWork(PERIODIC_WORK)
            workManager.cancelUniqueWork(NOW_WORK)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val periodic = PeriodicWorkRequestBuilder<OfflineTopUpWorker>(12, TimeUnit.HOURS, 1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodic
        )
        val now = OneTimeWorkRequestBuilder<OfflineTopUpWorker>()
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniqueWork(NOW_WORK, ExistingWorkPolicy.REPLACE, now)
    }
}
