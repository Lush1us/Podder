package com.lush1us.podder.service

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.lush1us.podder.download.DownloadRepository
import com.lush1us.podder.queue.QueueRepository
import dev.podder.data.repository.PodcastRepository
import dev.podder.domain.model.PlaybackState
import dev.podder.domain.player.PlaybackStateMachine
import dev.podder.logging.LogEvent
import dev.podder.logging.LogLevel
import dev.podder.logging.PodderLogger
import dev.podder.logging.Subsystem
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
    private val simpleCache: SimpleCache by inject()
    private val downloadRepository: DownloadRepository by inject()
    private val podcastRepository: PodcastRepository by inject()
    private val queueRepository: QueueRepository by inject()
    private val logger: PodderLogger by inject()

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var uiTickJob: Job? = null
    private var persistTickJob: Job? = null
    // Track which episodeId has already been marked finished to avoid repeat DB writes
    private var finishedMarkId: String? = null
    // Guard against emit storm re-preparing the same URL multiple times
    private var lastPreparedUrl: String? = null
    // Player listener reference so we can remove it before release
    private var playerListener: Player.Listener? = null
    // Set during onDestroy to prevent listener callbacks from overwriting saved position
    private var releasing = false

    override fun onCreate() {
        super.onCreate()
        logger.log(LogLevel.INFO, Subsystem.APP, LogEvent.AppLifecycle.Created)
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .setUsage(C.USAGE_MEDIA)
                    .build(), true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .experimentalSetDynamicSchedulingEnabled(true)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .build()
        mediaSession = MediaSession.Builder(this, player).build()

        // Register session + notification so the OS treats this as a foreground media service.
        // Without this, Pixel's power-saving monitor kills the service after ~1 minute.
        addSession(mediaSession)
        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this).build())

        observeStateMachine()
        observePlayer()
        observeSeek()
        observeSpeed()

        observeResume()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    override fun onDestroy() {
        releasing = true
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
        // Cancel deferred persist writes on the state machine's scope so they don't
        // overwrite the position we just saved.
        stateMachine.cancelPendingPersists()
        scope.cancel()
        // Remove player listener BEFORE release so release-triggered callbacks
        // (onIsPlayingChanged etc.) can't overwrite the saved position.
        playerListener?.let { player.removeListener(it) }
        removeSession(mediaSession)
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
                                // Guard: rapid Buffering→Idle cycles from a bad cache entry can
                                // queue many emissions here; each re-reads state.value which is
                                // now Buffering(newUrl). Skip prepare if we already prepared this
                                // URL and ExoPlayer is still loading it (not STATE_IDLE).
                                if (url == lastPreparedUrl && player.playbackState != androidx.media3.common.Player.STATE_IDLE) return@let
                                lastPreparedUrl = url
                                val item = MediaItem.Builder()
                                    .setUri(url)
                                    .setCustomCacheKey(trackId) // stable key independent of URL rotation
                                    .build()
                                player.setMediaItem(item, pos)
                                player.prepare()
                                player.play()
                                // Kick off a full-episode download in parallel so the file is saved to disk
                                // even if the user loses network mid-playback. DownloadManager shares the
                                // same SimpleCache + cache key, so this doesn't double-fetch streamed bytes.
                                // startAutoCache is idempotent — no-op if a download for this id already exists.
                                downloadRepository.startAutoCache(trackId, url)
                            }
                        }
                        is PlaybackState.Paused -> player.pause()
                        is PlaybackState.Idle   -> {
                            lastPreparedUrl = null
                            player.stop()
                        }
                        else -> Unit
                    }
                }
        }
    }

    private fun observePlayer() {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (releasing) return
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        val trackId = currentOrPendingTrackId() ?: return
                        stateMachine.onBuffering(trackId)
                        // Pause DB writes while hardware/network is loading; ticks resume at STATE_READY.
                        stopTicks()
                    }

                    Player.STATE_READY -> {
                        // The gatekeeper — only now does ExoPlayer have a real position/duration,
                        // so this is the single source of truth for Playing/Paused transitions.
                        val trackId = currentOrPendingTrackId() ?: return
                        val pos = player.currentPosition
                        val dur = player.duration.coerceAtLeast(0L)
                        if (player.playWhenReady) {
                            stateMachine.onPlaying(trackId, pos, dur)
                            startTicks(trackId)
                        } else {
                            stateMachine.onPaused(trackId, pos, dur)
                            stopTicks()
                        }
                    }

                    Player.STATE_ENDED -> {
                    val endedTrackId = when (val s = stateMachine.state.value) {
                        is PlaybackState.Playing   -> s.trackId
                        is PlaybackState.Paused    -> s.trackId
                        is PlaybackState.Buffering -> s.trackId
                        else -> null
                    }
                    // Guard: if ExoPlayer ended after playing < 2 s the cached content is bad/expired.
                    // Stop cleanly but don't mark finished, remove from queue, or trigger autoplay.
                    // Use currentPosition (not duration — duration returns TIME_UNSET at STATE_ENDED).
                    val playedMs = player.currentPosition
                    if (playedMs < 2_000L) {
                        logger.log(LogLevel.WARN, Subsystem.PLAYBACK,
                            LogEvent.Playback.Error(
                                trackId = endedTrackId ?: "unknown",
                                message = "Premature STATE_ENDED (pos=${playedMs}ms) — bad cache or expired URL",
                            ))
                        // Evict the corrupt cache entry so the next play fetches fresh from network.
                        // Key matches customCacheKey set during prepare (= trackId = episodeId).
                        val evictKey = endedTrackId
                            ?: player.currentMediaItem?.localConfiguration?.uri?.toString()
                        evictKey?.let { try { simpleCache.removeResource(it) } catch (_: Exception) { } }
                        lastPreparedUrl = null
                        stateMachine.onStopped()
                        return
                    }
                    if (endedTrackId != null) {
                        logger.log(LogLevel.INFO, Subsystem.PLAYBACK,
                            LogEvent.Playback.EpisodeFinished(endedTrackId))
                    }
                    stateMachine.onStopped()
                    if (queueRepository.autoplay.value) {
                        // Remove the just-finished episode from the queue (no-op if not queued).
                        // We only peek at the next item — it stays in queue until it finishes too.
                        if (endedTrackId != null) queueRepository.removeFromQueue(endedTrackId)
                        val next = queueRepository.peekNext()
                        if (next != null) {
                            logger.log(LogLevel.INFO, Subsystem.PLAYBACK,
                                LogEvent.Playback.AutoplayTriggered(
                                    endedId = endedTrackId ?: "",
                                    nextId  = next.episodeId,
                                    source  = "queue",
                                ))
                            stateMachine.play(next.episodeId, next.url)
                        } else if (endedTrackId != null) {
                            // Queue empty — fall back to the next episode in the feed
                            scope.launch {
                                podcastRepository.nextEpisodeInFeed(endedTrackId)?.let { ep ->
                                    logger.log(LogLevel.INFO, Subsystem.PLAYBACK,
                                        LogEvent.Playback.AutoplayTriggered(
                                            endedId = endedTrackId,
                                            nextId  = ep.id,
                                            source  = "feed",
                                        ))
                                    stateMachine.play(ep.id, ep.url)
                                }
                            }
                        }
                    }
                    }

                    else -> Unit // STATE_IDLE — no-op; observed during player.stop()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (releasing) return
                if (player.playbackState != Player.STATE_READY) return
                val trackId = currentOrPendingTrackId() ?: return
                val pos = player.currentPosition
                val dur = player.duration.coerceAtLeast(0L)
                if (isPlaying) {
                    stateMachine.onPlaying(trackId, pos, dur)
                    startTicks(trackId)
                } else {
                    stateMachine.onPaused(trackId, pos, dur)
                    stopTicks()
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                if (releasing) return
                val trackId = (stateMachine.state.value as? PlaybackState.Buffering)?.trackId
                    ?: (stateMachine.state.value as? PlaybackState.Playing)?.trackId
                    ?: (stateMachine.state.value as? PlaybackState.Paused)?.trackId
                    ?: "unknown"
                stateMachine.onError(trackId, error.message ?: "Unknown playback error")
            }
        }
        playerListener = listener
        player.addListener(listener)
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

    private fun currentOrPendingTrackId(): String? = when (val s = stateMachine.state.value) {
        is PlaybackState.Buffering -> s.trackId
        is PlaybackState.Playing   -> s.trackId
        is PlaybackState.Paused    -> s.trackId
        else -> stateMachine.pendingPlay()?.first
    }

    private fun startTicks(trackId: String) {
        stopTicks()
        // 1-second UI tick — updates StateFlow only, no KV write
        uiTickJob = scope.launch {
            while (isActive) {
                delay(1_000)
                if (player.isPlaying) {
                    stateMachine.onPositionUpdateUi(trackId, player.currentPosition)
                    // Mark episode finished when ≤15 s remain (once per episode)
                    val dur = player.duration
                    if (dur > 0L && player.currentPosition >= dur - 15_000L && finishedMarkId != trackId) {
                        finishedMarkId = trackId
                        podcastRepository.markEpisodeFinished(trackId)
                        // Remove from queue now — STATE_ENDED handler will also call this
                        // (idempotent) but this path handles the normal case first.
                        queueRepository.removeFromQueue(trackId)
                    }
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
