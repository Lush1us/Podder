package com.lush1us.podder.ui.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lush1us.podder.media.MediaControllerManager
import dev.podder.data.repository.PodcastRepository
import dev.podder.domain.model.PlaybackState
import dev.podder.domain.player.PlaybackStateMachine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class NowPlayingInfo(
    val episodeId: String,
    val podcastId: String,
    val title: String,
    val artworkUrl: String?,
    val durationMs: Long,
    val description: String,
)

class PlaybackViewModel(
    private val stateMachine: PlaybackStateMachine,
    private val repository: PodcastRepository,
    private val controllerManager: MediaControllerManager,
) : ViewModel() {

    val state: StateFlow<PlaybackState> = stateMachine.state

    @OptIn(ExperimentalCoroutinesApi::class)
    val nowPlayingInfo: StateFlow<NowPlayingInfo?> = stateMachine.state
        .map { s ->
            when (s) {
                is PlaybackState.Playing   -> s.trackId
                is PlaybackState.Paused    -> s.trackId
                is PlaybackState.Buffering -> s.trackId
                else -> null
            }
        }
        .flatMapLatest { trackId ->
            flow {
                if (trackId == null) { emit(null); return@flow }
                val episode = repository.episodeById(trackId)
                if (episode == null) { emit(null); return@flow }
                val podcast = repository.podcastById(episode.podcastId)
                val dur = when (val s = stateMachine.state.value) {
                    is PlaybackState.Playing -> s.durationMs
                    is PlaybackState.Paused  -> s.durationMs
                    else -> episode.durationMs
                }
                emit(NowPlayingInfo(
                    episodeId   = episode.id,
                    podcastId   = episode.podcastId,
                    title       = episode.title,
                    artworkUrl  = podcast?.artworkUrl,
                    durationMs  = dur,
                    description = episode.description,
                ))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun resumePosition(episodeId: String): Long = stateMachine.resumePosition(episodeId)

    /**
     * Initiates playback of a new track. Waits up to 2 s (non-blocking, via coroutine suspension)
     * for the MediaController to be connected — guaranteeing the service is alive — before
     * mutating the state machine. Falls back to a direct call if the controller never connects.
     */
    fun play(trackId: String, url: String) {
        viewModelScope.launch {
            withTimeoutOrNull(2_000L) {
                while (controllerManager.controller == null) delay(50)
            }
            stateMachine.play(trackId, url)
        }
    }

    /** Routes through the MediaController so the MediaSession drives ExoPlayer directly. */
    fun pause() {
        controllerManager.controller?.pause() ?: stateMachine.pause()
    }

    /** Routes through the MediaController; falls back to the state machine flag if not connected. */
    fun resume() {
        controllerManager.controller?.play() ?: stateMachine.resume()
    }

    fun stop() = stateMachine.stop()

    /**
     * Optimistic UI update via the state machine + actual seek via the MediaController.
     * If the controller is not yet connected, the state machine's pendingSeek path handles it.
     */
    fun seekTo(positionMs: Long) {
        stateMachine.seekTo(positionMs)
        controllerManager.controller?.seekTo(positionMs)
    }

    fun setPlaybackSpeed(speed: Float) {
        stateMachine.setPlaybackSpeed(speed)
        controllerManager.controller?.setPlaybackSpeed(speed)
    }

    fun setScrubbing(enabled: Boolean) = stateMachine.setScrubbing(enabled)
}
