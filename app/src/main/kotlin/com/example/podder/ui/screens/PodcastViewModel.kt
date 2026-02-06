package com.example.podder.ui.screens

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.podder.core.PodcastAction
import com.example.podder.data.PlaybackStore
import com.example.podder.data.PodcastRepository
import com.example.podder.data.local.EpisodeWithPodcast
import com.example.podder.data.local.PodcastEntity
import com.example.podder.data.network.SearchResult
import com.example.podder.player.PlayerController
import com.example.podder.player.PlayerUiState
import com.example.podder.utils.AppLogger
import com.example.podder.utils.Originator
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
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
    private val application: Application,
    private val repository: PodcastRepository,
    private val playerController: PlayerController,
    private val playbackStore: PlaybackStore
) : ViewModel() {

    private var lastSavedPosition: Long = 0L
    private var lastSavedGuid: String? = null

    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    val uiState: StateFlow<HomeUiState> = repository.homeFeed
        .map { HomeUiState.Success(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeUiState.Loading
        )

    val subscribedUrls: StateFlow<Set<String>> = repository.subscribedUrls
        .map { it.toSet() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    val subscribedPodcasts: StateFlow<List<PodcastEntity>> = repository.subscribedPodcasts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val playerUiState: StateFlow<PlayerUiState> = playerController.playerUiState

    fun getEpisodesByPodcast(podcastUrl: String) = repository.getEpisodesByPodcast(podcastUrl)

    init {
        observePlayerForProgressSaving()
        cleanupExpiredDownloads()
        restoreLastPlayed()
    }

    private fun cleanupExpiredDownloads() {
        viewModelScope.launch {
            repository.cleanupExpiredDownloads()
            AppLogger.log(
                application,
                Originator.APP,
                "Cleanup",
                "Downloads Cleaned"
            )
        }
    }

    private fun restoreLastPlayed() {
        Log.d(TAG, "restoreLastPlayed() called")
        viewModelScope.launch {
            try {
                Log.d(TAG, "Reading from PlaybackStore...")
                val lastPlayed = playbackStore.lastPlayed.first()
                Log.d(TAG, "PlaybackStore returned: $lastPlayed")

                if (lastPlayed == null) {
                    Log.d(TAG, "No last played found in DataStore")
                    return@launch
                }
                val (guid, _) = lastPlayed

                Log.d(TAG, "Looking up episode with guid: $guid")
                val episode = repository.getEpisode(guid)
                if (episode == null) {
                    Log.d(TAG, "Episode not found in database for guid: $guid")
                    return@launch
                }

                Log.d(TAG, "Looking up podcast with url: ${episode.podcastUrl}")
                val podcast = repository.getPodcast(episode.podcastUrl)
                if (podcast == null) {
                    Log.d(TAG, "Podcast not found in database for url: ${episode.podcastUrl}")
                    return@launch
                }

                // Check for local file - use it if exists
                val localPath = episode.localFilePath
                val playUrl = if (localPath != null && File(localPath).exists()) {
                    Log.d(TAG, "Restoring from local file: $localPath")
                    "file://$localPath"
                } else {
                    Log.d(TAG, "Restoring from network: ${episode.audioUrl}")
                    episode.audioUrl
                }

                Log.d(TAG, "Restoring last played: ${episode.title} at ${episode.progressInMillis}ms")
                playerController.restore(
                    guid = episode.guid,
                    url = playUrl,
                    title = episode.title,
                    artist = podcast.title,
                    imageUrl = podcast.imageUrl,
                    description = episode.description,
                    podcastUrl = episode.podcastUrl,
                    startPosition = episode.progressInMillis
                )
                AppLogger.log(
                    application,
                    Originator.APP,
                    "SessionRestore",
                    "Session Restored",
                    "Title: ${episode.title}, Position: ${episode.progressInMillis}ms"
                )
                Log.d(TAG, "Restore complete")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore last played: ${e.message}", e)
            }
        }
    }

    private fun observePlayerForProgressSaving() {
        viewModelScope.launch {
            playerController.playerUiState.collect { state ->
                val guid = state.currentEpisodeGuid ?: return@collect
                val position = state.currentPositionMillis
                val duration = state.durationMillis

                // If GUID changed, reset tracking and save to PlaybackStore
                if (guid != lastSavedGuid) {
                    Log.d(TAG, "Episode changed to: $guid, resetting progress tracking")
                    lastSavedGuid = guid
                    lastSavedPosition = position
                    // Save last played episode to DataStore
                    launch(NonCancellable) {
                        playbackStore.saveLastPlayed(guid, state.podcastUrl)
                    }
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
                        AppLogger.log(
                            application,
                            Originator.APP,
                            "ProgressSaver",
                            "Progress Saved",
                            "GUID: $guid, Position: ${position}ms"
                        )
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

        // Centralized entry log for all actions
        AppLogger.log(
            application,
            Originator.USER,
            action.javaClass.simpleName,
            "Processing"
        )

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

                    // Save last played to DataStore for session restoration
                    playbackStore.saveLastPlayed(action.guid, action.podcastUrl)

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

                    AppLogger.log(
                        application,
                        Originator.USER,
                        "Play",
                        "Playback Started",
                        "Title: ${action.title}, GUID: ${action.guid}, Position: ${startPosition}ms"
                    )
                }
            }
            is PodcastAction.TogglePlayPause -> {
                val wasPlaying = playerController.playerUiState.value.isPlaying
                viewModelScope.launch {
                    playerController.togglePlayPause()
                    AppLogger.log(
                        application,
                        Originator.USER,
                        "TogglePlayPause",
                        "Toggled Player",
                        "State: ${if (wasPlaying) "Paused" else "Playing"}"
                    )
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
                    // Set progress to duration * 1000 (convert seconds to millis) and set finishedAt
                    repository.markAsFinished(action.guid, action.durationSeconds * 1000)
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

                AppLogger.log(
                    application,
                    Originator.USER,
                    "Download",
                    "Work Enqueued",
                    "GUID: ${action.guid}, Title: ${action.title}"
                )

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
            is PodcastAction.DeleteDownload -> {
                Log.d(TAG, "  Delete download: ${action.guid}")
                viewModelScope.launch {
                    repository.deleteDownload(action.guid, action.localFilePath)
                }
            }
            is PodcastAction.Search -> {
                Log.d(TAG, "  Search: ${action.query}")
                viewModelScope.launch {
                    _isSearching.value = true
                    try {
                        _searchResults.value = repository.searchPodcasts(action.query)
                    } catch (e: Exception) {
                        Log.e(TAG, "Search failed: ${e.message}", e)
                        _searchResults.value = emptyList()
                    } finally {
                        _isSearching.value = false
                    }
                }
            }
            is PodcastAction.Subscribe -> {
                Log.d(TAG, "  Subscribe: ${action.podcast.collectionName}")
                viewModelScope.launch {
                    action.podcast.feedUrl?.let { feedUrl ->
                        repository.subscribe(feedUrl)
                        // Refresh feed after subscribing
                        repository.updatePodcasts()

                        AppLogger.log(
                            application,
                            Originator.USER,
                            "Subscribe",
                            "Subscribed",
                            "Podcast: ${action.podcast.collectionName}, URL: $feedUrl"
                        )
                    }
                }
            }
            is PodcastAction.Unsubscribe -> {
                Log.d(TAG, "  Unsubscribe: ${action.url}")
                viewModelScope.launch {
                    repository.unsubscribe(action.url)

                    AppLogger.log(
                        application,
                        Originator.USER,
                        "Unsubscribe",
                        "Unsubscribed",
                        "URL: ${action.url}"
                    )
                }
            }
            is PodcastAction.ClearSearch -> {
                Log.d(TAG, "  Clear search")
                _searchResults.value = emptyList()
                _isSearching.value = false
            }
        }
    }

    companion object {
        private const val TAG = "Podder"
    }
}
