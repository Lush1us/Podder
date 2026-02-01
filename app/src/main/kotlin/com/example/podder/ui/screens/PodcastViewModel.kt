package com.example.podder.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.podder.core.PodcastAction
import com.example.podder.data.PodcastRepository
import com.example.podder.data.local.EpisodeWithPodcast
import com.example.podder.player.PlayerController
import com.example.podder.player.PlayerUiState
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

enum class DownloadState {
    DOWNLOADING,
    COMPLETED
}

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(val feed: List<EpisodeWithPodcast>) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

class PodcastViewModel(
    private val repository: PodcastRepository,
    private val playerController: PlayerController
) : ViewModel() {

    private var lastSavedPosition: Long = 0L
    private var lastSavedGuid: String? = null

    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    val uiState: StateFlow<HomeUiState> = repository.homeFeed
        .map { HomeUiState.Success(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeUiState.Loading
        )

    val playerUiState: StateFlow<PlayerUiState> = playerController.playerUiState

    fun getEpisodesByPodcast(podcastUrl: String) = repository.getEpisodesByPodcast(podcastUrl)

    init {
        observePlayerForProgressSaving()
    }

    private fun observePlayerForProgressSaving() {
        viewModelScope.launch {
            playerController.playerUiState.collect { state ->
                val guid = state.currentEpisodeGuid ?: return@collect
                val position = state.currentPositionMillis
                val duration = state.durationMillis

                // If GUID changed, reset tracking - don't save stale position from old episode
                if (guid != lastSavedGuid) {
                    Log.d(TAG, "Episode changed to: $guid, resetting progress tracking")
                    lastSavedGuid = guid
                    lastSavedPosition = position
                    return@collect
                }

                // Only save if we have valid duration (media is loaded)
                if (duration <= 0) return@collect

                // Save on pause or every 10 seconds of playback
                val shouldSave = (!state.isPlaying && position > 0) ||
                    (position - lastSavedPosition >= 10_000)

                if (shouldSave) {
                    Log.d(TAG, "Saving progress: guid=$guid, position=${position}ms")
                    // Use NonCancellable to ensure DB write survives app death
                    launch(NonCancellable) {
                        repository.saveProgress(guid, position)
                    }
                    lastSavedPosition = position
                }
            }
        }
    }

    fun initializeSubscriptions(inputStream: InputStream) {
        viewModelScope.launch {
            if (!repository.hasSubscriptions()) {
                repository.importOpml(inputStream)
            }
            repository.updatePodcasts()
        }
    }

    fun process(action: PodcastAction) {
        Log.d(TAG, "Action: ${action::class.simpleName} from ${action.source}")

        when (action) {
            is PodcastAction.FetchPodcasts -> {
                viewModelScope.launch { repository.updatePodcasts() }
            }
            is PodcastAction.Play -> {
                Log.d(TAG, "  Play: ${action.title} (guid=${action.guid})")
                viewModelScope.launch {
                    // Fetch stored progress before playing
                    val episode = repository.getEpisode(action.guid)
                    val startPosition = episode?.progressInMillis ?: 0L

                    // Reset progress tracking for new episode
                    lastSavedGuid = action.guid
                    lastSavedPosition = startPosition

                    // Check for local file - use it if exists
                    val localPath = episode?.localFilePath
                    val playUrl = if (localPath != null && File(localPath).exists()) {
                        Log.d(TAG, "  Playing from local file: $localPath")
                        "file://$localPath"
                    } else {
                        Log.d(TAG, "  Playing from network: ${action.url}")
                        action.url
                    }
                    Log.d(TAG, "  Resuming from ${startPosition}ms")

                    playerController.play(
                        guid = action.guid,
                        url = playUrl,
                        title = action.title,
                        artist = action.artist,
                        imageUrl = action.imageUrl,
                        description = action.description,
                        podcastUrl = action.podcastUrl,
                        startPosition = startPosition
                    )
                }
            }
            is PodcastAction.TogglePlayPause -> {
                viewModelScope.launch {
                    playerController.togglePlayPause()
                }
            }
            is PodcastAction.Pause -> {
                viewModelScope.launch {
                    playerController.pause()
                }
            }
            is PodcastAction.SeekBack -> {
                viewModelScope.launch {
                    playerController.seekBack()
                }
            }
            is PodcastAction.SeekForward -> {
                viewModelScope.launch {
                    playerController.seekForward()
                }
            }
            is PodcastAction.FetchPodcast -> { /* Not used */ }
            is PodcastAction.MarkAsFinished -> {
                viewModelScope.launch {
                    // Set progress to duration * 1000 (convert seconds to millis) to mark as finished
                    repository.saveProgress(action.guid, action.durationSeconds * 1000)
                }
            }
            is PodcastAction.SeekTo -> {
                viewModelScope.launch {
                    playerController.seekTo(action.positionMillis)
                }
            }
            is PodcastAction.Download -> {
                Log.d(TAG, "  Download: ${action.title} (guid=${action.guid})")
                // Set downloading state
                _downloadStates.value = _downloadStates.value + (action.guid to DownloadState.DOWNLOADING)

                // Start download and observe completion
                repository.downloadEpisode(action.guid, action.url, action.title)

                // Poll for completion (check if localFilePath is set)
                viewModelScope.launch {
                    while (true) {
                        delay(500)
                        val episode = repository.getEpisode(action.guid)
                        if (episode?.localFilePath != null) {
                            // Download complete
                            _downloadStates.value = _downloadStates.value + (action.guid to DownloadState.COMPLETED)
                            delay(2000)
                            // Remove from tracking
                            _downloadStates.value = _downloadStates.value - action.guid
                            break
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "Podder"
    }
}
