package com.example.podder.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.podder.parser.Podcast
import com.example.podder.data.PodcastRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.example.podder.core.Action

sealed class PodcastUiState {
    data object Loading : PodcastUiState()
    data class Success(val podcast: Podcast) : PodcastUiState()
    data class Error(val message: String) : PodcastUiState()
}

sealed class PodcastAction : Action {
    data class FetchPodcast(override val source: String, override val timestamp: Long, val url: String) : PodcastAction()
}

class PodcastViewModel(private val podcastUseCase: PodcastUseCase) : ViewModel() {
    private val _uiState = MutableStateFlow<PodcastUiState>(PodcastUiState.Loading)
    val uiState: StateFlow<PodcastUiState> = _uiState.asStateFlow()

    init {
        processAction(PodcastAction.FetchPodcast(url = "https://feeds.npr.org/510318/podcast.xml", source = "init", timestamp = System.currentTimeMillis()))
    }

    fun processAction(action: PodcastAction) {
        // Log the action (as per GEMINI.md)
        println("Action received: ${action.javaClass.simpleName} from ${action.source} at ${action.timestamp}")

        when (action) {
            is PodcastAction.FetchPodcast -> handleFetchPodcast(action.url)
        }
    }

    private fun handleFetchPodcast(url: String) {
        viewModelScope.launch {
            _uiState.value = PodcastUiState.Loading
            val podcast = podcastUseCase.getPodcast(url)
            _uiState.value = if (podcast != null) {
                PodcastUiState.Success(podcast)
            } else {
                PodcastUiState.Error("Failed to load podcast")
            }
        }
    }
}