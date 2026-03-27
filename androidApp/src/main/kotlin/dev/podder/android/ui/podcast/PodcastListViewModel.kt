package dev.podder.android.ui.podcast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.podder.android.precache.PreCacheManager
import dev.podder.data.repository.PodcastRepository
import dev.podder.data.repository.PodcastSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PodcastListViewModel(
    private val repository: PodcastRepository,
    private val preCacheManager: PreCacheManager,
) : ViewModel() {

    val podcasts: StateFlow<List<PodcastSummary>> = repository
        .podcasts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    fun addPodcast(rssUrl: String) {
        viewModelScope.launch {
            runCatching { repository.subscribeToPodcast(rssUrl) }
            runCatching { preCacheManager.preCacheRecent() }
        }
    }

    fun importOpml(content: String) {
        viewModelScope.launch {
            _isImporting.value = true
            runCatching { repository.importFromOpml(content) }
            _isImporting.value = false
            runCatching { preCacheManager.preCacheRecent() }
        }
    }
}
