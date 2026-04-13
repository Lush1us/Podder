package com.lush1us.podder.service

import android.app.Notification
import com.lush1us.podder.R
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import org.koin.android.ext.android.inject

class PodderDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.download_channel_name,
    /* channelDescriptionResourceId= */ 0,
) {
    private val injectedDownloadManager: DownloadManager by inject()

    override fun getDownloadManager(): DownloadManager = injectedDownloadManager

    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int,
    ): Notification = DownloadNotificationHelper(this, CHANNEL_ID)
        .buildProgressNotification(
            this,
            android.R.drawable.stat_sys_download,
            /* contentIntent= */ null,
            /* message= */ null,
            downloads,
            notMetRequirements,
        )

    companion object {
        const val FOREGROUND_NOTIFICATION_ID = 2
        const val CHANNEL_ID = "podder_downloads"
    }
}
