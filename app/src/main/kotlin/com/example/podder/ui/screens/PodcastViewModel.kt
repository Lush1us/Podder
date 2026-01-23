package com.example.podder.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.podder.parser.Podcast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.example.podder.core.PodcastAction
import com.example.podder.domain.PodcastUseCase
import kotlin.onSuccess
import kotlin.onFailure


sealed class PodcastUiState {
    data object Loading : PodcastUiState()
    data class Success(val podcasts: List<Podcast>) : PodcastUiState()
    data class Error(val message: String) : PodcastUiState()
}

class PodcastViewModel(private val podcastUseCase: PodcastUseCase) : ViewModel() {
    private val _uiState = MutableStateFlow<PodcastUiState>(PodcastUiState.Loading)
    val uiState: StateFlow<PodcastUiState> = _uiState.asStateFlow()

    fun process(action: PodcastAction) {
        // Log the action (as per GEMINI.md)
        println("Action received: ${action.javaClass.simpleName} from ${action.source} at ${action.timestamp}")

        when (action) {
            is PodcastAction.FetchPodcast -> {
                viewModelScope.launch {
                    _uiState.value = PodcastUiState.Loading
                    podcastUseCase.getPodcast(action.url)
                        .onSuccess { podcast ->
                            _uiState.value = PodcastUiState.Success(listOf(podcast))
                        }
                        .onFailure { error ->
                            _uiState.value = PodcastUiState.Error(error.message ?: "Unknown error")
                        }
                }
            }
            is PodcastAction.FetchPodcasts -> {
                viewModelScope.launch {
                    _uiState.value = PodcastUiState.Loading
                    val podcasts = mutableListOf<Podcast>()
                    var errorMessage: String? = null
                    for (url in action.urls) {
                        podcastUseCase.getPodcast(url)
                            .onSuccess { podcast ->
                                podcasts.add(podcast)
                            }
                            .onFailure { error ->
                                errorMessage = error.message
                                // Continue fetching other podcasts even if one fails
                            }
                    }
                    if (podcasts.isNotEmpty()) {
                        _uiState.value = PodcastUiState.Success(podcasts)
                    } else {
                        _uiState.value = PodcastUiState.Error(errorMessage ?: "Failed to load podcasts")
                    }
                }
            }
            is PodcastAction.Play -> { /* TODO: Implement Play logic */ }
            is PodcastAction.Pause -> { /* TODO: Implement Pause logic */ }
        }
    }
}