package org.nsh07.wikireader.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import org.nsh07.wikireader.R

/**
 * Keeps the app process alive (and its network running) while the article library is being
 * built. Building can take many minutes; without a foreground service Android freezes or kills
 * a backgrounded app and the download silently stalls. The download itself still runs in the
 * ViewModel - this service only holds the process priority and shows an ongoing notification
 * (when notifications are allowed).
 */
class IndexKeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            ensureChannel()
            val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
                PendingIntent.getActivity(
                    this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.download)
                .setContentTitle(getString(R.string.indexServiceTitle))
                .setContentText(getString(R.string.indexServiceText))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setProgress(0, 0, true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setContentIntent(open)
                .build()
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } catch (e: Exception) {
            DebugLog.w("KeepAlive", "could not enter the foreground: ${e.message}")
            stopSelf()
        }
        // If the system kills the process the build is gone anyway: do not restart empty.
        return START_NOT_STICKY
    }

    /** Android 15+ ends data-sync services after a few hours: stop cleanly instead of crashing. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf(startId)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, getString(R.string.indexServiceChannel), NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "library_build"
        private const val NOTIFICATION_ID = 7107

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(
                    context, Intent(context, IndexKeepAliveService::class.java)
                )
            } catch (e: Exception) {
                // e.g. not allowed to start from the background: the build still runs, just unprotected
                DebugLog.w("KeepAlive", "could not start the service: ${e.message}")
            }
        }

        fun stop(context: Context) {
            // A stop right after a start would race the service's own startForeground() call
            // (which Android requires within seconds): give it a moment.
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    context.stopService(Intent(context, IndexKeepAliveService::class.java))
                } catch (e: Exception) {
                    DebugLog.w("KeepAlive", "could not stop the service: ${e.message}")
                }
            }, 1500)
        }
    }
}
