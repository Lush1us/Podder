package dev.podder.android.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.podder.data.repository.PodcastRepository
import dev.podder.data.search.SearchRepository
import dev.podder.data.search.SearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Success(
        val results: List<SearchResult>,
        val isPaginating: Boolean = false,
        val hasReachedEnd: Boolean = false,
    ) : SearchUiState
    data class Empty(val query: String) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

class SearchViewModel(
    private val searchRepository: SearchRepository,
    private val podcastRepository: PodcastRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        val cached = searchRepository.getCachedResults()
        if (cached.isNotEmpty()) {
            _uiState.value = SearchUiState.Success(cached)
        }
    }

    fun onQueryChange(q: String) {
        _query.value = q
        if (q.isBlank()) {
            _uiState.value = SearchUiState.Idle
            searchRepository.clearCache()
        }
    }

    fun search() {
        val query = _query.value.trim()
        if (query.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.value = SearchUiState.Loading
            try {
                val results = searchRepository.search(query)
                _uiState.value = if (results.isEmpty()) {
                    SearchUiState.Empty(query)
                } else {
                    SearchUiState.Success(results)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = SearchUiState.Error(e.message ?: "Search failed")
            }
        }
    }

    fun loadNextPage() {
        val current = _uiState.value as? SearchUiState.Success ?: return
        if (current.isPaginating || current.hasReachedEnd) return
        val previousSize = current.results.size
        _uiState.value = current.copy(isPaginating = true)
        viewModelScope.launch {
            try {
                val results = searchRepository.loadNextPage()
                val hasReachedEnd = results.size == previousSize
                _uiState.value = SearchUiState.Success(results, isPaginating = false, hasReachedEnd = hasReachedEnd)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = current.copy(isPaginating = false)
            }
        }
    }

    fun subscribe(rssUrl: String) {
        viewModelScope.launch {
            runCatching { podcastRepository.subscribeToPodcast(rssUrl) }
        }
    }
}
