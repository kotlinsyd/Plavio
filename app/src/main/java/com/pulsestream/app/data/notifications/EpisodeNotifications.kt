package com.pulsestream.app.data.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.pulsestream.app.MainActivity
import com.pulsestream.app.R
import com.pulsestream.app.data.SettingsStore
import com.pulsestream.app.ui.nav.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.TimeUnit

/**
 * Holds a detail-page route requested by a notification tap. MainActivity
 * publishes the intent extra here; the nav graph consumes it once the app
 * is past splash/onboarding.
 */
object NotificationNav {
    val pendingRoute = MutableStateFlow<String?>(null)
}

/**
 * Episode-release notifications: channel setup, WorkManager scheduling,
 * and notification posting for new episodes of watchlisted shows.
 */
object EpisodeNotifications {

    const val CHANNEL_ID = "episode_releases"
    const val WORK_NAME = "episode_check"
    const val CHECK_INTERVAL_HOURS = 12L

    /** Create the notification channel (no-op on pre-Oreo, safe to call often). */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_episodes),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notif_channel_episodes_desc)
            }
        )
    }

    /**
     * Schedule the periodic episode check. Idempotent (KEEP): safe to call
     * on every cold start and when the setting is toggled on. Does nothing
     * when the user disabled episode notifications.
     */
    fun ensureScheduled(context: Context) {
        val appContext = context.applicationContext
        if (!SettingsStore(appContext).episodeNotifications) return
        ensureChannel(appContext)
        val request = PeriodicWorkRequestBuilder<EpisodeCheckWorker>(
            CHECK_INTERVAL_HOURS, TimeUnit.HOURS
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .addTag(WORK_NAME)
            .build()
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** Stop the periodic check (called when the user disables the setting). */
    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
    }

    /**
     * Post a "new episode" notification. Silently skipped when the user
     * disabled alerts or revoked the notification permission at OS level.
     */
    fun postNewEpisode(
        context: Context,
        addonId: String,
        metaId: String,
        showName: String,
        season: Int,
        episode: Int,
        episodeTitle: String?
    ) {
        val appContext = context.applicationContext
        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) return
        ensureChannel(appContext)

        val route = Routes.detail(addonId, "series", metaId)
        val intent = Intent(appContext, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(MainActivity.EXTRA_OPEN_ROUTE, route)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        // One notification per show: same requestCode replaces the previous one.
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            route.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val epLabel = "S$season E$episode"
        val text = if (episodeTitle.isNullOrBlank()) {
            appContext.getString(R.string.notif_new_episode_text, epLabel)
        } else {
            appContext.getString(R.string.notif_new_episode_text_titled, epLabel, episodeTitle)
        }

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pulse_logo)
            .setContentTitle(showName)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(appContext).notify(route.hashCode(), notification)
        } catch (_: SecurityException) {
            // Permission can be revoked between the check and notify().
        }
    }
}
