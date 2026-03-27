package dev.podder.android.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.podder.data.discovery.DiscoveryCategory
import dev.podder.data.discovery.DiscoveryRepository
import dev.podder.data.repository.PodcastRepository
import dev.podder.data.search.SearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DiscoverUiState {
    data object Loading : DiscoverUiState
    data class Discovery(
        val trending: List<SearchResult>,
        val categories: List<DiscoveryCategory>,
    ) : DiscoverUiState
    data object Searching : DiscoverUiState
    data class SearchResults(val results: List<SearchResult>, val query: String) : DiscoverUiState
    data class SearchEmpty(val query: String) : DiscoverUiState
    data class Error(val message: String) : DiscoverUiState
}

class DiscoverViewModel(
    private val discoveryRepository: DiscoveryRepository,
    private val podcastRepository: PodcastRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _uiState = MutableStateFlow<DiscoverUiState>(DiscoverUiState.Loading)
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    private val _categoryDetail = MutableStateFlow<CategoryDetail?>(null)
    val categoryDetail: StateFlow<CategoryDetail?> = _categoryDetail.asStateFlow()

    private var searchJob: Job? = null

    data class CategoryDetail(
        val category: DiscoveryCategory,
        val podcasts: List<SearchResult>,
        val isLoading: Boolean,
    )

    init {
        loadDiscovery()
    }

    private fun loadDiscovery() {
        viewModelScope.launch {
            _uiState.value = DiscoverUiState.Loading
            try {
                val trending   = discoveryRepository.trending()
                val categories = discoveryRepository.categories()
                _uiState.value = DiscoverUiState.Discovery(trending, categories)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = DiscoverUiState.Error(e.message ?: "Failed to load discovery")
            }
        }
    }

    fun onQueryChange(q: String) {
        _query.value = q
        if (q.isBlank()) {
            searchJob?.cancel()
            loadDiscovery()
        }
    }

    fun search() {
        val query = _query.value.trim()
        if (query.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.value = DiscoverUiState.Searching
            try {
                val results = discoveryRepository.mergedSearch(query)
                _uiState.value = if (results.isEmpty()) {
                    DiscoverUiState.SearchEmpty(query)
                } else {
                    DiscoverUiState.SearchResults(results, query)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = DiscoverUiState.Error(e.message ?: "Search failed")
            }
        }
    }

    fun loadCategory(category: DiscoveryCategory) {
        _categoryDetail.value = CategoryDetail(category, emptyList(), isLoading = true)
        viewModelScope.launch {
            val podcasts = runCatching { discoveryRepository.podcastsByCategory(category.id) }.getOrElse { emptyList() }
            _categoryDetail.value = CategoryDetail(category, podcasts, isLoading = false)
        }
    }

    fun clearCategory() {
        _categoryDetail.value = null
    }

    fun subscribe(rssUrl: String) {
        viewModelScope.launch {
            runCatching { podcastRepository.subscribeToPodcast(rssUrl) }
        }
    }

    fun retry() {
        if (_query.value.isBlank()) loadDiscovery() else search()
    }
}
