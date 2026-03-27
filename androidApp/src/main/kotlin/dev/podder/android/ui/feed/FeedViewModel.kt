package dev.podder.android.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.podder.android.precache.PreCacheManager
import dev.podder.data.repository.FeedEpisode
import dev.podder.data.repository.PodcastRepository
import dev.podder.domain.model.DownloadFilter
import dev.podder.domain.model.FeedFilter
import dev.podder.domain.model.FeedSort
import dev.podder.domain.model.PlayedFilter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 100L

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModel(
    private val repository: PodcastRepository,
    private val preCacheManager: PreCacheManager,
) : ViewModel() {

    private val _limit = MutableStateFlow(PAGE_SIZE)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _filter = MutableStateFlow(FeedFilter())
    val filter: StateFlow<FeedFilter> = _filter.asStateFlow()

    fun setFilter(filter: FeedFilter) { _filter.value = filter }

    val episodes: StateFlow<List<FeedEpisode>> = combine(_limit, _filter) { limit, filter ->
        Pair(limit, filter)
    }
        .flatMapLatest { (limit, filter) ->
            repository.allEpisodes(limit).map { eps -> applyFilter(eps, filter) }
        }
        .onEach { _isLoadingMore.value = false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // true if the last fetch returned a full page (more may exist)
    val hasMore: StateFlow<Boolean> = combine(episodes, _limit) { eps, limit ->
        eps.size.toLong() >= limit
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        viewModelScope.launch {
            // Wait for the first non-empty podcast list, then refresh + pre-cache.
            // `first { }` returns immediately if podcasts already exist; otherwise it suspends
            // until they appear (e.g. after the user adds their first subscription).
            repository.podcasts().first { it.isNotEmpty() }
            refresh()
        }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            runCatching { repository.refreshAll() }
            _isRefreshing.value = false
            // Pre-cache in background — no UI impact if it fails
            launch { runCatching { preCacheManager.preCacheRecent() } }
        }
    }

    fun loadMore() {
        if (_isLoadingMore.value || !hasMore.value) return
        _isLoadingMore.value = true
        _limit.value += PAGE_SIZE
    }

    private fun applyFilter(episodes: List<FeedEpisode>, filter: FeedFilter): List<FeedEpisode> {
        var result = episodes
        result = when (filter.played) {
            PlayedFilter.UNPLAYED -> result.filter { it.playCount == 0 }
            PlayedFilter.PLAYED   -> result.filter { it.playCount > 0 }
            PlayedFilter.ALL      -> result
        }
        result = when (filter.downloaded) {
            DownloadFilter.DOWNLOADED -> result.filter { it.isDownloaded }
            DownloadFilter.ALL        -> result
        }
        result = when (filter.sort) {
            FeedSort.DATE_DESC    -> result.sortedByDescending { it.publicationDateUtc }
            FeedSort.DURATION_ASC -> result.sortedBy { it.durationMs }
            FeedSort.DURATION_DESC -> result.sortedByDescending { it.durationMs }
            FeedSort.PODCAST_NAME -> result.sortedBy { it.podcastTitle.lowercase() }
        }
        return result
    }
}
