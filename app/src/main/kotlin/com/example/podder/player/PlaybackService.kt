package com.example.podder.player

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession

class PlaybackService : MediaLibraryService() {
    private var mediaLibrarySession: MediaLibrarySession? = null
    private var player: Player? = null

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build()

        // Use a default callback that allows all playback actions
        val callback = object : MediaLibrarySession.Callback {}

        mediaLibrarySession = MediaLibrarySession.Builder(this, player!!, callback).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onDestroy() {
        mediaLibrarySession?.run {
            player.release()
            release()
            mediaLibrarySession = null
        }
        super.onDestroy()
    }
}
