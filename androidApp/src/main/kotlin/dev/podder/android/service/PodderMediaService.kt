package dev.podder.android.service

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dev.podder.android.download.DownloadRepository
import dev.podder.domain.model.PlaybackState
import dev.podder.domain.player.PlaybackStateMachine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class PodderMediaService : MediaSessionService() {

    private val stateMachine: PlaybackStateMachine by inject()
    private val cacheDataSourceFactory: CacheDataSource.Factory by inject()
    private val downloadRepository: DownloadRepository by inject()

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var uiTickJob: Job? = null
    private var persistTickJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .setUsage(C.USAGE_MEDIA)
                    .build(), true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .experimentalSetDynamicSchedulingEnabled(true)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .build()
        mediaSession = MediaSession.Builder(this, player).build()

        observeStateMachine()
        observePlayer()
        observeSeek()
        observeSpeed()
        observeScrubbing()
        observeResume()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    override fun onDestroy() {
        val finalState = stateMachine.state.value
        val finalTrackId = when (finalState) {
            is PlaybackState.Playing -> finalState.trackId
            is PlaybackState.Paused  -> finalState.trackId
            else -> null
        }
        if (finalTrackId != null) {
            stateMachine.onPaused(finalTrackId, player.currentPosition, player.duration.coerceAtLeast(0L))
        }
        uiTickJob?.cancel()
        persistTickJob?.cancel()
        scope.cancel()
        mediaSession.release()
        player.release()
        super.onDestroy()
    }

    private fun observeStateMachine() {
        scope.launch {
            stateMachine.state
                .map { it::class }
                .distinctUntilChanged()
                .collect {
                    when (val s = stateMachine.state.value) {
                        is PlaybackState.Buffering -> {
                            stateMachine.pendingPlay()?.let { (trackId, url, pos) ->
                                val item = MediaItem.fromUri(url)
                                player.setMediaItem(item, pos)
                                player.prepare()
                                player.play()
                                downloadRepository.startAutoCache(trackId, url)
                            }
                        }
                        is PlaybackState.Paused -> player.pause()
                        is PlaybackState.Idle   -> player.stop()
                        else -> Unit
                    }
                }
        }
    }

    private fun observePlayer() {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val current = stateMachine.state.value
                val trackId = when (current) {
                    is PlaybackState.Buffering -> current.trackId
                    is PlaybackState.Playing   -> current.trackId
                    is PlaybackState.Paused    -> current.trackId
                    else -> return
                }
                if (isPlaying) {
                    stateMachine.onPlaying(trackId, player.currentPosition, player.duration.coerceAtLeast(0L))
                    startTicks(trackId)
                } else {
                    stopTicks()
                    // Only report a real pause — not transient stops during seek/buffer
                    if (player.playbackState != Player.STATE_ENDED && !player.playWhenReady) {
                        stateMachine.onPaused(trackId, player.currentPosition, player.duration.coerceAtLeast(0L))
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    stateMachine.onStopped()
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val trackId = (stateMachine.state.value as? PlaybackState.Buffering)?.trackId
                    ?: (stateMachine.state.value as? PlaybackState.Playing)?.trackId
                    ?: "unknown"
                stateMachine.onError(trackId, error.message ?: "Unknown playback error")
            }
        })
    }

    private fun observeSeek() {
        scope.launch {
            stateMachine.pendingSeek
                .filterNotNull()
                .collect { positionMs ->
                    player.seekTo(positionMs)
                    stateMachine.consumePendingSeek()
                }
        }
    }

    private fun observeSpeed() {
        scope.launch {
            stateMachine.speed
                .collect { speed ->
                    player.setPlaybackParameters(PlaybackParameters(speed))
                }
        }
    }

    private fun observeScrubbing() {
        scope.launch {
            stateMachine.scrubbing.collect { enabled ->
                player.setScrubbingModeEnabled(enabled)
            }
        }
    }

    private fun observeResume() {
        scope.launch {
            stateMachine.pendingResume
                .filter { it }
                .collect {
                    player.play()
                    stateMachine.consumePendingResume()
                }
        }
    }

    private fun startTicks(trackId: String) {
        stopTicks()
        // 1-second UI tick — updates StateFlow only, no KV write
        uiTickJob = scope.launch {
            while (isActive) {
                delay(1_000)
                if (player.isPlaying) {
                    stateMachine.onPositionUpdateUi(trackId, player.currentPosition)
                }
            }
        }
        // 10-second persist tick — writes to KV store
        persistTickJob = scope.launch {
            while (isActive) {
                delay(10_000)
                if (player.isPlaying) {
                    stateMachine.onPositionUpdate(trackId, player.currentPosition)
                }
            }
        }
    }

    private fun stopTicks() {
        uiTickJob?.cancel()
        persistTickJob?.cancel()
    }
}
