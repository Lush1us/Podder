package com.example.podder.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.podder.core.PodcastAction
import com.example.podder.data.PodcastRepository
import com.example.podder.data.local.EpisodeWithPodcast
import com.example.podder.player.PlayerController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
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

    // Hot flow observing the DB
    val uiState: StateFlow<HomeUiState> = repository.homeFeed
        .map { HomeUiState.Success(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeUiState.Loading
        )

    fun initializeSubscriptions(inputStream: InputStream) {
        viewModelScope.launch {
            // Check if subscriptions already exist
            if (!repository.hasSubscriptions()) {
                // Import OPML if no subscriptions exist
                repository.importOpml(inputStream)
            }
            // Fetch podcasts after ensuring subscriptions are loaded
            repository.updatePodcasts()
        }
    }

    fun process(action: PodcastAction) {
        println("Action: ${action}") // Traceability
        when (action) {
            is PodcastAction.FetchPodcasts -> {
                viewModelScope.launch {
                    repository.updatePodcasts()
                }
            }
            is PodcastAction.Play -> {
                viewModelScope.launch {
                    playerController.play(action.podcastId)
                }
            }
            else -> {}
        }
    }
}
