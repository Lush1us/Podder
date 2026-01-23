package com.example.podder.player

import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.IBinder
import android.util.Log
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat

class PodcastPlayerService : Service(), MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener {

    private var mediaPlayer: MediaPlayer? = null
    private var episodeUrl: String? = null

    companion object {
        const val ACTION_PLAY = "com.example.podder.player.ACTION_PLAY"
        const val ACTION_PAUSE = "com.example.podder.player.ACTION_PAUSE"
        const val ACTION_STOP = "com.example.podder.player.ACTION_STOP"
        const val EPISODE_URL = "EPISODE_URL"
        const val NOTIFICATION_CHANNEL_ID = "podcast_player_channel"
        const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                episodeUrl = intent.getStringExtra(EPISODE_URL)
                if (episodeUrl != null) {
                    playEpisode(episodeUrl!!)
                }
            }
            ACTION_PAUSE -> pauseEpisode()
            ACTION_STOP -> stopEpisode()
        }
        return START_NOT_STICKY
    }

    private fun playEpisode(url: String) {
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer()
            mediaPlayer?.setOnPreparedListener(this)
            mediaPlayer?.setOnErrorListener(this)
        } else {
            mediaPlayer?.reset()
        }

        try {
            mediaPlayer?.setDataSource(url)
            mediaPlayer?.prepareAsync() // prepare async to not block main thread
            startForeground(NOTIFICATION_ID, createNotification("Playing: $url").build())
        } catch (e: Exception) {
            Log.e("PodcastPlayerService", "Error setting data source: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun pauseEpisode() {
        mediaPlayer?.pause()
        stopForeground(false)
        startForeground(NOTIFICATION_ID, createNotification("Paused: $episodeUrl").build())
    }

    private fun stopEpisode() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        stopForeground(true)
        stopSelf()
    }

    override fun onPrepared(mp: MediaPlayer?) {
        mp?.start()
        startForeground(NOTIFICATION_ID, createNotification("Playing: $episodeUrl").build())
    }

    override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        Log.e("PodcastPlayerService", "MediaPlayer error: what=$what, extra=$extra")
        stopEpisode()
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Podcast Player Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(contentText: String): NotificationCompat.Builder {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Podcast Player")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
    }
}
