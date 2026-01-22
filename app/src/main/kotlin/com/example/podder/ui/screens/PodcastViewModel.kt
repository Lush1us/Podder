package com.example.podder.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.podder.parser.Podcast
import com.example.podder.data.PodcastRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class PodcastUiState {
    data object Loading : PodcastUiState()
    data class Success(val podcast: Podcast) : PodcastUiState()
    data class Error(val message: String) : PodcastUiState()
}

class PodcastViewModel : ViewModel() {
    private val repository = PodcastRepository()

    private val _uiState = MutableStateFlow<PodcastUiState>(PodcastUiState.Loading)
    val uiState: StateFlow<PodcastUiState> = _uiState.asStateFlow()

    init {
        fetchPodcast("https://feeds.npr.org/510318/podcast.xml")
    }

    fun fetchPodcast(url: String) {
        viewModelScope.launch {
            _uiState.value = PodcastUiState.Loading
            val podcast = repository.getPodcast(url)
            _uiState.value = if (podcast != null) {
                PodcastUiState.Success(podcast)
            } else {
                PodcastUiState.Error("Failed to load podcast")
            }
        }
    }
}