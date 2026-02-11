package com.example.podder.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.podder.data.PlaybackStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlayerUiState(
    val isPlaying: Boolean = false,
    val currentTitle: String = "",
    val artist: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val progress: Float = 0f,
    val currentEpisodeGuid: String? = null,
    val currentPositionMillis: Long = 0L,
    val durationMillis: Long = 0L,
    val podcastUrl: String? = null
)

class PlayerController(
    private val context: Context,
    private val playbackStore: PlaybackStore
) {
    private var controller: MediaController? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null
    private var lastHeartbeatPosition: Long = 0L

    private val _playerUiState = MutableStateFlow(PlayerUiState())
    val playerUiState: StateFlow<PlayerUiState> = _playerUiState.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playerUiState.value = _playerUiState.value.copy(isPlaying = isPlaying)
            if (isPlaying) {
                startProgressUpdates()
            } else {
                stopProgressUpdates()
                // Save state on pause/stop
                saveStateToStore()
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Log.e(TAG, "Playback Error: ${error.message}", error)
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            _playerUiState.value = _playerUiState.value.copy(
                currentTitle = mediaMetadata.title?.toString() ?: "",
                artist = mediaMetadata.artist?.toString(),
                description = mediaMetadata.description?.toString(),
                imageUrl = mediaMetadata.artworkUri?.toString()
            )
        }
    }

    private fun saveStateToStore() {
        val state = _playerUiState.value
        val guid = state.currentEpisodeGuid ?: return
        scope.launch(NonCancellable) {
            playbackStore.saveState(guid, state.currentPositionMillis, state.isPlaying)
        }
    }

    private suspend fun ensureController(): MediaController = withContext(Dispatchers.Main) {
        controller ?: run {
            val sessionToken = SessionToken(
                context,
                ComponentName(context, PlaybackService::class.java)
            )
            MediaController.Builder(context, sessionToken)
                .buildAsync()
                .await()
                .also {
                    controller = it
                    it.addListener(playerListener)
                }
        }
    }

    suspend fun play(
        guid: String,
        url: String,
        title: String,
        artist: String,
        imageUrl: String?,
        description: String?,
        podcastUrl: String?,
        startPosition: Long = 0L
    ) = withContext(Dispatchers.Main) {
        try {
            val mediaController = ensureController()

            // Set current episode guid, podcast url, and reset position atomically
            _playerUiState.value = _playerUiState.value.copy(
                currentEpisodeGuid = guid,
                podcastUrl = podcastUrl,
                currentPositionMillis = startPosition,
                durationMillis = 0L,
                progress = 0f
            )
            lastHeartbeatPosition = startPosition

            // Build Metadata for Notification
            val metadata = MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setArtworkUri(imageUrl?.let { Uri.parse(it) })
                .setDescription(description)
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(url)
                .setMediaMetadata(metadata)
                .build()

            mediaController.setMediaItem(mediaItem, startPosition)
            mediaController.prepare()
            mediaController.play()
            Log.d(TAG, "Playing: $title by $artist from ${startPosition}ms")

            // Save state to PlaybackStore on new play
            scope.launch(NonCancellable) {
                playbackStore.saveState(guid, startPosition, true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun togglePlayPause() = withContext(Dispatchers.Main) {
        try {
            val mediaController = ensureController()
            if (mediaController.isPlaying) {
                mediaController.pause()
            } else {
                mediaController.play()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun pause() = withContext(Dispatchers.Main) {
        try {
            controller?.pause()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun seekBack() = withContext(Dispatchers.Main) {
        try {
            controller?.seekBack()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun seekForward() = withContext(Dispatchers.Main) {
        try {
            controller?.seekForward()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun seekTo(positionMillis: Long) = withContext(Dispatchers.Main) {
        try {
            controller?.seekTo(positionMillis)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Prepare a session from saved state. Loads the media and seeks to position.
     * Starts playing only if [autoPlay] is true (i.e., was playing when state was saved).
     */
    suspend fun prepareSession(
        guid: String,
        url: String,
        title: String,
        artist: String,
        imageUrl: String?,
        description: String?,
        podcastUrl: String?,
        position: Long,
        autoPlay: Boolean = false
    ) = withContext(Dispatchers.Main) {
        try {
            val mediaController = ensureController()

            // Set current episode state atomically
            _playerUiState.value = _playerUiState.value.copy(
                currentEpisodeGuid = guid,
                podcastUrl = podcastUrl,
                currentPositionMillis = position,
                durationMillis = 0L,
                progress = 0f,
                isPlaying = false
            )
            lastHeartbeatPosition = position

            // Build Metadata for Notification
            val metadata = MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setArtworkUri(imageUrl?.let { Uri.parse(it) })
                .setDescription(description)
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(url)
                .setMediaMetadata(metadata)
                .build()

            mediaController.setMediaItem(mediaItem, position)
            mediaController.prepare()

            if (autoPlay) {
                mediaController.play()
                Log.d(TAG, "Restored & playing: $title by $artist at ${position}ms")
            } else {
                Log.d(TAG, "Restored (paused): $title by $artist at ${position}ms")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                controller?.let { ctrl ->
                    val duration = ctrl.duration
                    val position = ctrl.currentPosition
                    if (duration > 0) {
                        val progress = (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                        _playerUiState.value = _playerUiState.value.copy(
                            progress = progress,
                            currentPositionMillis = position,
                            durationMillis = duration
                        )

                        // Heartbeat: save to PlaybackStore every ~10 seconds
                        if (position - lastHeartbeatPosition >= 10_000) {
                            val guid = _playerUiState.value.currentEpisodeGuid
                            if (guid != null) {
                                launch(NonCancellable) {
                                    playbackStore.saveState(guid, position, true)
                                }
                                lastHeartbeatPosition = position
                            }
                        }
                    }
                }
                delay(1000)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }

    fun release() {
        stopProgressUpdates()
        controller?.removeListener(playerListener)
        controller?.release()
        controller = null
    }

    companion object {
        private const val TAG = "PlayerController"
    }
}
