package com.example.podder.player

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.os.Handler
import android.os.Looper
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player

class PodcastPlayerService : Service(), Player.Listener {

    private var exoPlayer: ExoPlayer? = null
    private var episodeUrl: String? = null
    private var episodeTitle: String? = null
    private var isPlaying: Boolean = false
    private val handler = Handler(Looper.getMainLooper())
    private val updateProgressAction = object : Runnable {
        override fun run() {
            if (exoPlayer != null && isPlaying) {
                val currentPosition = exoPlayer!!.currentPosition.toInt()
                val duration = exoPlayer!!.duration.toInt()
                sendProgressBroadcast(currentPosition, duration)
                handler.postDelayed(this, 1000) // Update every second
            }
        }
    }

    companion object {
        const val ACTION_PLAY = "com.example.podder.player.ACTION_PLAY"
        const val ACTION_PAUSE = "com.example.podder.player.ACTION_PAUSE"
        const val ACTION_STOP = "com.example.podder.player.ACTION_STOP"
        const val ACTION_UPDATE_PLAYBACK_STATE = "com.example.podder.player.ACTION_UPDATE_PLAYBACK_STATE"
        const val ACTION_UPDATE_PROGRESS = "com.example.podder.player.ACTION_UPDATE_PROGRESS"
        const val EPISODE_URL = "EPISODE_URL"
        const val EXTRA_IS_PLAYING = "EXTRA_IS_PLAYING"
        const val EXTRA_EPISODE_TITLE = "EXTRA_EPISODE_TITLE"
        const val EXTRA_CURRENT_POSITION = "EXTRA_CURRENT_POSITION"
        const val EXTRA_DURATION = "EXTRA_DURATION"
        const val NOTIFICATION_CHANNEL_ID = "podcast_player_channel"
        const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        exoPlayer = ExoPlayer.Builder(this).build()
        exoPlayer?.addListener(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("PodcastPlayerService", "onStartCommand: action=${intent?.action}, episodeUrl=${intent?.getStringExtra(EPISODE_URL)}, episodeTitle=${intent?.getStringExtra(EXTRA_EPISODE_TITLE)}")
        when (intent?.action) {
            ACTION_PLAY -> {
                val newEpisodeUrl = intent.getStringExtra(EPISODE_URL)
                val newEpisodeTitle = intent.getStringExtra(EXTRA_EPISODE_TITLE)
                if (newEpisodeUrl != null && newEpisodeUrl != episodeUrl) {
                    episodeUrl = newEpisodeUrl
                    episodeTitle = newEpisodeTitle
                    playEpisode(episodeUrl!!)
                } else if (exoPlayer?.isPlaying == false) {
                    exoPlayer?.play()
                    isPlaying = true
                    sendPlaybackStateBroadcast()
                    handler.post(updateProgressAction)
                    startForeground(NOTIFICATION_ID, createNotification("Playing: $episodeTitle").build())
                }
            }
            ACTION_PAUSE -> pauseEpisode()
            ACTION_STOP -> stopEpisode()
        }
        return START_NOT_STICKY
    }

    private fun playEpisode(url: String) {
        Log.d("PodcastPlayerService", "playEpisode: url=$url")
        val mediaItem = MediaItem.fromUri(url)
        exoPlayer?.setMediaItem(mediaItem)
        exoPlayer?.prepare()
        exoPlayer?.play()
        isPlaying = true
        sendPlaybackStateBroadcast()
        handler.post(updateProgressAction)
        startForeground(NOTIFICATION_ID, createNotification("Playing: $episodeTitle").build())
    }

    private fun pauseEpisode() {
        exoPlayer?.pause()
        isPlaying = false
        sendPlaybackStateBroadcast()
        handler.removeCallbacks(updateProgressAction)
        startForeground(NOTIFICATION_ID, createNotification("Paused: $episodeTitle").build())
    }

    private fun stopEpisode() {
        exoPlayer?.stop()
        exoPlayer?.release()
        exoPlayer = null
        isPlaying = false
        episodeTitle = null
        sendPlaybackStateBroadcast()
        handler.removeCallbacks(updateProgressAction)
        stopForeground(true)
        stopSelf()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) {
            stopEpisode()
        }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        isPlaying = playWhenReady
        sendPlaybackStateBroadcast()
        if (playWhenReady) {
            handler.post(updateProgressAction)
        } else {
            handler.removeCallbacks(updateProgressAction)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
        isPlaying = false
        episodeTitle = null
        sendPlaybackStateBroadcast()
        handler.removeCallbacks(updateProgressAction)
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

    private fun sendPlaybackStateBroadcast() {
        val intent = Intent(ACTION_UPDATE_PLAYBACK_STATE)
        intent.putExtra(EXTRA_IS_PLAYING, isPlaying)
        intent.putExtra(EXTRA_EPISODE_TITLE, episodeTitle)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun sendProgressBroadcast(currentPosition: Int, duration: Int) {
        val intent = Intent(ACTION_UPDATE_PROGRESS)
        intent.putExtra(EXTRA_CURRENT_POSITION, currentPosition)
        intent.putExtra(EXTRA_DURATION, duration)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
}
