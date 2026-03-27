package dev.podder.android.ui.episode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.podder.data.repository.EpisodeSummary
import dev.podder.data.repository.PodcastRepository
import dev.podder.data.repository.PodcastSummary
import dev.podder.domain.model.PlaybackState
import dev.podder.domain.player.PlaybackStateMachine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EpisodeDetailViewModel(
    val episodeId: String,
    private val stateMachine: PlaybackStateMachine,
    private val repository: PodcastRepository,
) : ViewModel() {

    val playbackState: StateFlow<PlaybackState> = stateMachine.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaybackState.Idle)

    private val _episode  = MutableStateFlow<EpisodeSummary?>(null)
    val episode: StateFlow<EpisodeSummary?> = _episode.asStateFlow()

    private val _podcast  = MutableStateFlow<PodcastSummary?>(null)
    val podcast: StateFlow<PodcastSummary?> = _podcast.asStateFlow()

    private val _speed = MutableStateFlow(1.0f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    init {
        viewModelScope.launch {
            val ep = repository.episodeById(episodeId)
            _episode.value = ep
            if (ep != null) {
                _podcast.value = repository.podcastById(ep.podcastId)
            }
        }
        viewModelScope.launch {
            stateMachine.speed.collect { _speed.value = it }
        }
    }

    fun play() {
        val ep = _episode.value ?: return
        stateMachine.play(ep.id, ep.url)
    }

    fun pause()  = stateMachine.pause()
    fun resume() = stateMachine.resume()

    fun seekTo(positionMs: Long) = stateMachine.seekTo(positionMs)
    fun setScrubbing(enabled: Boolean) = stateMachine.setScrubbing(enabled)

    fun setSpeed(speed: Float) {
        _speed.value = speed
        stateMachine.setPlaybackSpeed(speed)
    }
}
