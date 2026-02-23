package dev.podder.android.ui.podcast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.podder.data.repository.PodcastRepository
import dev.podder.data.repository.PodcastSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PodcastListViewModel(
    private val repository: PodcastRepository,
) : ViewModel() {

    val podcasts: StateFlow<List<PodcastSummary>> = repository
        .podcasts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addPodcast(rssUrl: String) {
        viewModelScope.launch {
            runCatching { repository.subscribeToPodcast(rssUrl) }
        }
    }

    fun importOpml(content: String) {
        viewModelScope.launch {
            runCatching { repository.importFromOpml(content) }
        }
    }
}
