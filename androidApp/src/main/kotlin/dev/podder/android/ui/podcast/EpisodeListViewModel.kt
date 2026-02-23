package dev.podder.android.ui.podcast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.podder.data.repository.EpisodeSummary
import dev.podder.data.repository.PodcastRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EpisodeListViewModel(
    private val podcastId: String,
    private val repository: PodcastRepository,
) : ViewModel() {

    val episodes: StateFlow<List<EpisodeSummary>> = repository
        .episodes(podcastId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
