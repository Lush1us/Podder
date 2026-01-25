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

    val uiState: StateFlow<HomeUiState> = repository.homeFeed
        .map { HomeUiState.Success(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeUiState.Loading
        )

    fun initializeSubscriptions(inputStream: InputStream) {
        viewModelScope.launch {
            if (!repository.hasSubscriptions()) {
                repository.importOpml(inputStream)
            }
            repository.updatePodcasts()
        }
    }

    fun process(action: PodcastAction) {
        when (action) {
            is PodcastAction.FetchPodcasts -> {
                viewModelScope.launch { repository.updatePodcasts() }
            }
            is PodcastAction.Play -> {
                viewModelScope.launch {
                    playerController.play(
                        url = action.url,
                        title = action.title,
                        artist = action.artist,
                        imageUrl = action.imageUrl
                    )
                }
            }
            else -> {}
        }
    }
}
