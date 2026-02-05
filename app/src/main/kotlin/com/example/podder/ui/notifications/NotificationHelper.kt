package com.example.podder.ui.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.podder.MainActivity
import com.example.podder.R
import com.example.podder.data.local.EpisodeEntity

object NotificationHelper {
    private const val TAG = "Podder"
    private const val CHANNEL_ID = "new_episodes"
    private const val DOWNLOAD_CHANNEL_ID = "downloads"
    private const val NOTIFICATION_ID = 101
    private const val DOWNLOAD_NOTIFICATION_ID = 102

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // New episodes channel
            val episodesChannel = NotificationChannel(
                CHANNEL_ID,
                "New Episodes",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for new podcast episodes"
            }
            manager.createNotificationChannel(episodesChannel)

            // Downloads channel
            val downloadsChannel = NotificationChannel(
                DOWNLOAD_CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Episode download progress"
            }
            manager.createNotificationChannel(downloadsChannel)
        }
    }

    fun showNewEpisodes(context: Context, newEpisodes: List<EpisodeEntity>) {
        Log.d(TAG, "showNewEpisodes called with ${newEpisodes.size} episodes")
        if (newEpisodes.isEmpty()) return

        // Check notification permission (required for Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            Log.d(TAG, "Notification permission granted: $hasPermission")
            if (!hasPermission) return
        }

        // Create intent to open MainActivity
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build notification with InboxStyle for multiple episodes
        val title = if (newEpisodes.size == 1) {
            "New Episode"
        } else {
            "${newEpisodes.size} New Episodes"
        }

        val inboxStyle = NotificationCompat.InboxStyle()
        newEpisodes.take(5).forEach { episode ->
            inboxStyle.addLine(episode.title)
        }
        if (newEpisodes.size > 5) {
            inboxStyle.setSummaryText("+${newEpisodes.size - 5} more")
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(newEpisodes.first().title)
            .setStyle(inboxStyle)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        Log.d(TAG, "Notification posted for ${newEpisodes.size} new episodes")
    }

    fun showDownloadStarted(context: Context, episodeTitle: String) {
        if (!hasNotificationPermission(context)) return

        val notification = NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Downloading")
            .setContentText(episodeTitle)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        NotificationManagerCompat.from(context).notify(DOWNLOAD_NOTIFICATION_ID, notification)
        Log.d(TAG, "Download started notification for: $episodeTitle")
    }

    fun showDownloadComplete(context: Context, episodeTitle: String) {
        if (!hasNotificationPermission(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Download Complete")
            .setContentText(episodeTitle)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(DOWNLOAD_NOTIFICATION_ID, notification)
        Log.d(TAG, "Download complete notification for: $episodeTitle")
    }

    fun cancelDownloadNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(DOWNLOAD_NOTIFICATION_ID)
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }
}
