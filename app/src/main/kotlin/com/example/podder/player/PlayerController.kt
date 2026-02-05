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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

class PlayerController(private val context: Context) {
    private var controller: MediaController? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null

    private val _playerUiState = MutableStateFlow(PlayerUiState())
    val playerUiState: StateFlow<PlayerUiState> = _playerUiState.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playerUiState.value = _playerUiState.value.copy(isPlaying = isPlaying)
            if (isPlaying) {
                startProgressUpdates()
            } else {
                stopProgressUpdates()
            }
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
            // This prevents the progress observer from seeing old position with new GUID
            _playerUiState.value = _playerUiState.value.copy(
                currentEpisodeGuid = guid,
                podcastUrl = podcastUrl,
                currentPositionMillis = startPosition,
                durationMillis = 0L,
                progress = 0f
            )

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
            Log.d("PlayerController", "Playing: $title by $artist from ${startPosition}ms")
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
     * Restore the player state without auto-playing.
     * Used on app launch to show the Mini Player with the last played episode.
     */
    suspend fun restore(
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

            // Set current episode state atomically
            _playerUiState.value = _playerUiState.value.copy(
                currentEpisodeGuid = guid,
                podcastUrl = podcastUrl,
                currentPositionMillis = startPosition,
                durationMillis = 0L,
                progress = 0f,
                isPlaying = false
            )

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
            // Do NOT call play() - leave paused so Mini Player shows but audio doesn't start
            Log.d("PlayerController", "Restored: $title by $artist at ${startPosition}ms (paused)")
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
}
