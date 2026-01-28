package com.example.podder.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.podder.core.PodcastAction
import com.example.podder.data.PodcastRepository
import com.example.podder.data.local.EpisodeWithPodcast
import com.example.podder.player.PlayerController
import com.example.podder.player.PlayerUiState
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(val feed: List<EpisodeWithPodcast>) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

class PodcastViewModel(
    private val repository: PodcastRepository,
    private val playerController: PlayerController
) : ViewModel() {

    private var lastSavedPosition: Long = 0L

    val uiState: StateFlow<HomeUiState> = repository.homeFeed
        .map { HomeUiState.Success(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeUiState.Loading
        )

    val playerUiState: StateFlow<PlayerUiState> = playerController.playerUiState

    init {
        observePlayerForProgressSaving()
    }

    private fun observePlayerForProgressSaving() {
        viewModelScope.launch {
            playerController.playerUiState.collect { state ->
                val guid = state.currentEpisodeGuid ?: return@collect
                val position = state.currentPositionMillis

                // Save on pause or every 10 seconds of playback
                val shouldSave = (!state.isPlaying && position > 0) ||
                    (position - lastSavedPosition >= 10_000)

                if (shouldSave) {
                    Log.d(TAG, "Saving progress: guid=$guid, position=${position}ms")
                    // Use NonCancellable to ensure DB write survives app death
                    launch(NonCancellable) {
                        repository.saveProgress(guid, position)
                    }
                    lastSavedPosition = position
                }
            }
        }
    }

    fun initializeSubscriptions(inputStream: InputStream) {
        viewModelScope.launch {
            if (!repository.hasSubscriptions()) {
                repository.importOpml(inputStream)
            }
            repository.updatePodcasts()
        }
    }

    fun process(action: PodcastAction) {
        Log.d(TAG, "Action: ${action::class.simpleName} from ${action.source}")

        when (action) {
            is PodcastAction.FetchPodcasts -> {
                viewModelScope.launch { repository.updatePodcasts() }
            }
            is PodcastAction.Play -> {
                Log.d(TAG, "  Play: ${action.title} (guid=${action.guid})")
                viewModelScope.launch {
                    // Fetch stored progress before playing
                    val episode = repository.getEpisode(action.guid)
                    val startPosition = episode?.progressInMillis ?: 0L
                    lastSavedPosition = startPosition
                    Log.d(TAG, "  Resuming from ${startPosition}ms")

                    playerController.play(
                        guid = action.guid,
                        url = action.url,
                        title = action.title,
                        artist = action.artist,
                        imageUrl = action.imageUrl,
                        description = action.description,
                        startPosition = startPosition
                    )
                }
            }
            is PodcastAction.TogglePlayPause -> {
                viewModelScope.launch {
                    playerController.togglePlayPause()
                }
            }
            is PodcastAction.Pause -> {
                viewModelScope.launch {
                    playerController.pause()
                }
            }
            else -> {}
        }
    }

    companion object {
        private const val TAG = "Podder"
    }
}
