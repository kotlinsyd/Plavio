package com.pulsestream.app.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.pulsestream.app.MainActivity
import com.pulsestream.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps downloads running when the app is in the
 * background. Shows a progress notification per active download.
 *
 * Android 8+ requires a foreground service with a notification for
 * long-running background work like file downloads.
 */
class DownloadService : Service() {

    companion object {
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID_BASE = 1000

        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DownloadService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var manager: DownloadManager
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        manager = DownloadManager.get(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start foreground immediately with a placeholder notification
        startForeground(NOTIFICATION_ID_BASE, buildSummaryNotification(0, 0))

        // Watch active downloads and update notifications
        scope.launch {
            manager.activeDownloads.collect { active ->
                if (active.isEmpty()) {
                    // No active downloads — stop the service
                    delay(2000) // grace period for quick pause/resume
                    if (manager.activeDownloads.value.isEmpty()) {
                        stopSelf()
                    }
                } else {
                    updateNotifications(active)
                }
            }
        }
        return START_STICKY
    }

    private fun updateNotifications(active: Map<String, DownloadManager.DownloadProgress>) {
        // Summary notification
        val totalDownloaded = active.values.sumOf { it.downloadedBytes }
        val totalSize = active.values.sumOf { if (it.totalBytes > 0) it.totalBytes else 0L }
        notificationManager.notify(
            NOTIFICATION_ID_BASE,
            buildSummaryNotification(active.size, totalDownloaded, totalSize)
        )

        // Per-download notifications would be noisy; the summary is enough.
        // Individual progress is visible in the Downloads screen.
    }

    private fun buildSummaryNotification(
        count: Int,
        downloaded: Long = 0,
        total: Long = 0
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bolt)
            .setContentTitle(
                if (count == 0) "Preparing download…"
                else "Downloading $count file${if (count > 1) "s" else ""}"
            )
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (total > 0) {
            val pct = ((downloaded * 100) / total).toInt().coerceIn(0, 100)
            builder.setContentText("$pct% • ${manager.formatBytes(downloaded)} of ${manager.formatBytes(total)}")
                .setProgress(100, pct, false)
        } else {
            builder.setContentText(manager.formatBytes(downloaded))
                .setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows download progress"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
