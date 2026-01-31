package com.example.podder.player

import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class PlaybackService : MediaLibraryService() {
    private var mediaLibrarySession: MediaLibrarySession? = null
    private var player: Player? = null

    companion object {
        private const val SEEK_BACK_COMMAND = "SEEK_BACK"
        private const val SEEK_FORWARD_COMMAND = "SEEK_FORWARD"
    }

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .setUsage(C.USAGE_MEDIA)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true) // Handle audio focus
            .setSeekBackIncrementMs(10_000)  // 10 seconds rewind
            .setSeekForwardIncrementMs(30_000)  // 30 seconds fast forward
            .build()

        val seekBackButton = CommandButton.Builder(CommandButton.ICON_SKIP_BACK_10)
            .setDisplayName("Rewind 10s")
            .setSessionCommand(SessionCommand(SEEK_BACK_COMMAND, Bundle.EMPTY))
            .build()

        val seekForwardButton = CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_30)
            .setDisplayName("Forward 30s")
            .setSessionCommand(SessionCommand(SEEK_FORWARD_COMMAND, Bundle.EMPTY))
            .build()

        val callback = object : MediaLibrarySession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val connectionResult = super.onConnect(session, controller)
                val sessionCommands = connectionResult.availableSessionCommands
                    .buildUpon()
                    .add(SessionCommand(SEEK_BACK_COMMAND, Bundle.EMPTY))
                    .add(SessionCommand(SEEK_FORWARD_COMMAND, Bundle.EMPTY))
                    .build()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(sessionCommands)
                    .build()
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                when (customCommand.customAction) {
                    SEEK_BACK_COMMAND -> session.player.seekBack()
                    SEEK_FORWARD_COMMAND -> session.player.seekForward()
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }

        mediaLibrarySession = MediaLibrarySession.Builder(this, player!!, callback)
            .setCustomLayout(ImmutableList.of(seekBackButton, seekForwardButton))
            .build()

        // Configure notification provider
        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this).build())
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        // Stop playback when app is swiped away from recents
        player?.let {
            if (!it.playWhenReady || it.mediaItemCount == 0) {
                stopSelf()
            } else {
                it.stop()
                it.clearMediaItems()
                stopSelf()
            }
        }
        super.onTaskRemoved(rootIntent)
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
