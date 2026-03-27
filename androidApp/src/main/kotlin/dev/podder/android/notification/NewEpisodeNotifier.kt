package dev.podder.android.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dev.podder.android.MainActivity
import dev.podder.android.R
import dev.podder.db.PodderDatabase

data class NewEpisodeInfo(
    val episodeId: String,
    val title: String,
    val podcastTitle: String,
    val podcastId: String,
)

class NewEpisodeNotifier(
    private val context: Context,
    private val db: PodderDatabase,
) {
    companion object {
        const val CHANNEL_ID      = "new_episodes"
        const val NOTIFICATION_ID = 42
    }

    fun notify(newEpisodes: List<NewEpisodeInfo>) {
        if (newEpisodes.isEmpty()) return

        val mutedIds = db.podcastSettingsQueries.selectMutedPodcastIds().executeAsList().toSet()
        val filtered = newEpisodes.filter { it.podcastId !in mutedIds }
        if (filtered.isEmpty()) return

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val contentIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = if (filtered.size == 1) "1 new episode" else "${filtered.size} new episodes"

        val style = NotificationCompat.InboxStyle()
        filtered.take(7).forEach { ep ->
            style.addLine("${ep.podcastTitle}: ${ep.title}")
        }
        if (filtered.size > 7) style.setSummaryText("+${filtered.size - 7} more")

        val alreadyShowing = nm.activeNotifications.any { it.id == NOTIFICATION_ID }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(filtered.first().let { "${it.podcastTitle}: ${it.title}" })
            .setStyle(style)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(alreadyShowing)
            .build()

        nm.notify(NOTIFICATION_ID, notification)
    }
}
