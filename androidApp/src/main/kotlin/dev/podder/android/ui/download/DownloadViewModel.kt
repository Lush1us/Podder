package dev.podder.android.ui.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.podder.android.download.DownloadInfo
import dev.podder.android.download.DownloadProgress
import dev.podder.android.download.DownloadRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DownloadViewModel(
    private val repository: DownloadRepository,
) : ViewModel() {

    /** All currently tracked downloads (queued + downloading + done). Polled every 1s. */
    val downloads: StateFlow<List<DownloadInfo>> = repository.observeDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Map of episodeId → DownloadProgress for fast lookups in the episode lists. */
    val progressMap: StateFlow<Map<String, DownloadProgress>> = downloads
        .map { list -> list.associate { it.episodeId to it.progress } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** True when at least one episode is fully downloaded (drives the top bar icon). */
    val hasDownloads: StateFlow<Boolean> = downloads
        .map { list -> list.any { it.progress is DownloadProgress.Downloaded } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** True while any download is queued or in progress. */
    val isAnyDownloading: StateFlow<Boolean> = downloads
        .map { list -> list.any { it.progress is DownloadProgress.Queued || it.progress is DownloadProgress.Downloading } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Only the completed downloads — shown in the Downloads screen. */
    val completedDownloads: StateFlow<List<DownloadInfo>> = downloads
        .map { list -> list.filter { it.progress is DownloadProgress.Downloaded } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun startDownload(episodeId: String, url: String) {
        viewModelScope.launch { repository.startManualDownload(episodeId, url) }
    }

    fun cancelDownload(episodeId: String) {
        viewModelScope.launch { repository.cancelDownload(episodeId) }
    }
}
