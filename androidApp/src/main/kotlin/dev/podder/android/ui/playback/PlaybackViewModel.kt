package dev.podder.android.ui.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.podder.data.repository.PodcastRepository
import dev.podder.domain.model.PlaybackState
import dev.podder.domain.player.PlaybackStateMachine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

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

    fun play(trackId: String, url: String) = stateMachine.play(trackId, url)
    fun pause()  = stateMachine.pause()
    fun resume() = stateMachine.resume()
    fun stop()   = stateMachine.stop()
    fun seekTo(positionMs: Long) = stateMachine.seekTo(positionMs)
    fun setPlaybackSpeed(speed: Float) = stateMachine.setPlaybackSpeed(speed)
    fun setScrubbing(enabled: Boolean) = stateMachine.setScrubbing(enabled)
}
