package dev.podder.domain.player

import dev.podder.data.store.KVStore
import dev.podder.domain.model.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val KEY_RESUME_ID  = "playback_resume_id"
private const val KEY_RESUME_POS = "playback_resume_pos"

class PlaybackStateMachine(private val kvStore: KVStore) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    /** Pending seek position: set by seekTo(), consumed and reset to null by the platform layer. */
    private val _pendingSeek = MutableStateFlow<Long?>(null)
    val pendingSeek: StateFlow<Long?> = _pendingSeek.asStateFlow()

    /** Requested playback speed (default 1.0). Platform layer observes this. */
    private val _speed = MutableStateFlow(1.0f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _scrubbing = MutableStateFlow(false)
    val scrubbing: StateFlow<Boolean> = _scrubbing.asStateFlow()

    private val _pendingResume = MutableStateFlow(false)
    val pendingResume: StateFlow<Boolean> = _pendingResume.asStateFlow()

    fun onBuffering(trackId: String) {
        _state.value = PlaybackState.Buffering(trackId)
    }

    fun onPlaying(trackId: String, positionMs: Long, durationMs: Long) {
        _state.value = PlaybackState.Playing(trackId, positionMs, durationMs)
    }

    fun onPaused(trackId: String, positionMs: Long, durationMs: Long) {
        _state.value = PlaybackState.Paused(trackId, positionMs, durationMs)
        persistResumePosition(trackId, positionMs)
    }

    fun onError(trackId: String, message: String) {
        _state.value = PlaybackState.Error(trackId, message)
    }

    fun onStopped() {
        _state.value = PlaybackState.Idle
    }

    /** UI position update (called every 1 second). Only updates StateFlow — no KV write. */
    fun onPositionUpdateUi(trackId: String, positionMs: Long) {
        val current = _state.value
        if (current is PlaybackState.Playing && current.trackId == trackId) {
            _state.value = PlaybackState.Playing(trackId, positionMs, current.durationMs)
        }
    }

    /** Persist position update (called every 10 seconds). Updates StateFlow and writes to KV. */
    fun onPositionUpdate(trackId: String, positionMs: Long) {
        val current = _state.value
        if (current is PlaybackState.Playing && current.trackId == trackId) {
            _state.value = PlaybackState.Playing(trackId, positionMs, current.durationMs)
            scope.launch { persistResumePosition(trackId, positionMs) }
        }
    }

    fun play(trackId: String, url: String, startPositionMs: Long = resumePosition(trackId)) {
        _state.value = PlaybackState.Buffering(trackId)
        kvStore.putString("pending_play_url", url)
        kvStore.putString("pending_play_id", trackId)
        kvStore.putLong("pending_play_pos", startPositionMs)
    }

    fun pause() {
        val current = _state.value
        if (current is PlaybackState.Playing) {
            _state.value = PlaybackState.Paused(current.trackId, current.positionMs, current.durationMs)
            persistResumePosition(current.trackId, current.positionMs)
        }
    }

    fun resume() {
        val current = _state.value
        if (current is PlaybackState.Paused) {
            _pendingResume.value = true
        }
    }

    fun consumePendingResume() {
        _pendingResume.value = false
    }

    fun stop() {
        val current = _state.value
        if (current is PlaybackState.Playing || current is PlaybackState.Paused) {
            val trackId = when (current) {
                is PlaybackState.Playing -> current.trackId
                is PlaybackState.Paused  -> current.trackId
                else -> null
            }
            if (trackId != null) persistResumePosition(trackId, 0L)
        }
        _state.value = PlaybackState.Idle
    }

    /** Request a seek to the given position. Platform layer observes pendingSeek and executes. */
    fun seekTo(positionMs: Long) {
        _pendingSeek.value = positionMs
        // Optimistically update the UI position
        val current = _state.value
        when (current) {
            is PlaybackState.Playing -> _state.value = PlaybackState.Playing(current.trackId, positionMs, current.durationMs)
            is PlaybackState.Paused  -> _state.value = PlaybackState.Paused(current.trackId, positionMs, current.durationMs)
            else -> Unit
        }
    }

    /** Acknowledge the pending seek (called by platform layer after executing it). */
    fun consumePendingSeek() {
        _pendingSeek.value = null
    }

    /** Set playback speed. Platform layer observes speed and applies it. */
    fun setPlaybackSpeed(speed: Float) {
        _speed.value = speed.coerceIn(0.5f, 3.0f)
    }

    fun setScrubbing(enabled: Boolean) {
        _scrubbing.value = enabled
    }

    fun resumePosition(trackId: String): Long = kvStore.getLong("resume_$trackId", 0L)

    fun pendingPlay(): Triple<String, String, Long>? {
        val id  = kvStore.getString("pending_play_id", "")
        val url = kvStore.getString("pending_play_url", "")
        val pos = kvStore.getLong("pending_play_pos", 0L)
        return if (id.isNotBlank() && url.isNotBlank()) Triple(id, url, pos) else null
    }

    private fun persistResumePosition(trackId: String, positionMs: Long) {
        kvStore.putLong("resume_$trackId", positionMs)
        kvStore.putString(KEY_RESUME_ID, trackId)
        kvStore.putLong(KEY_RESUME_POS, positionMs)
    }
}
