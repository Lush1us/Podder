package dev.podder.domain.player

import dev.podder.data.store.KVStore
import dev.podder.domain.model.PlaybackState
import dev.podder.logging.LogEvent
import dev.podder.logging.LogLevel
import dev.podder.logging.PodderLogger
import dev.podder.logging.Subsystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job

private const val KEY_RESUME_ID  = "playback_resume_id"
private const val KEY_RESUME_POS = "playback_resume_pos"

class PlaybackStateMachine(
    private val kvStore: KVStore,
    private val logger: PodderLogger,
) {

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

    private fun stateName(s: PlaybackState): String = when (s) {
        is PlaybackState.Idle      -> "Idle"
        is PlaybackState.Buffering -> "Buffering"
        is PlaybackState.Playing   -> "Playing"
        is PlaybackState.Paused    -> "Paused"
        is PlaybackState.Error     -> "Error"
    }

    private fun transition(trackId: String, to: String) {
        val from = stateName(_state.value)
        if (from != to) {
            logger.log(LogLevel.INFO, Subsystem.PLAYBACK,
                LogEvent.Playback.StateChanged(trackId, from, to))
        }
    }

    fun onBuffering(trackId: String) {
        transition(trackId, "Buffering")
        _state.value = PlaybackState.Buffering(trackId)
    }

    fun onPlaying(trackId: String, positionMs: Long, durationMs: Long) {
        val prev = _state.value
        if (prev !is PlaybackState.Playing) {
            transition(trackId, "Playing")
            logger.log(LogLevel.INFO, Subsystem.PLAYBACK,
                LogEvent.Playback.Started(trackId, positionMs))
        }
        _state.value = PlaybackState.Playing(trackId, positionMs, durationMs)
    }

    fun onPaused(trackId: String, positionMs: Long, durationMs: Long) {
        transition(trackId, "Paused")
        logger.log(LogLevel.INFO, Subsystem.PLAYBACK,
            LogEvent.Playback.Paused(trackId, positionMs))
        _state.value = PlaybackState.Paused(trackId, positionMs, durationMs)
        persistResumePosition(trackId, positionMs)
    }

    fun onError(trackId: String, message: String) {
        transition(trackId, "Error")
        logger.log(LogLevel.ERROR, Subsystem.PLAYBACK,
            LogEvent.Playback.Error(trackId, message))
        _state.value = PlaybackState.Error(trackId, message)
    }

    fun onStopped() {
        val current = _state.value
        val trackId = when (current) {
            is PlaybackState.Playing   -> current.trackId
            is PlaybackState.Paused    -> current.trackId
            is PlaybackState.Buffering -> current.trackId
            else -> ""
        }
        if (trackId.isNotBlank()) {
            logger.log(LogLevel.INFO, Subsystem.PLAYBACK, LogEvent.Playback.Stopped(trackId))
            // Clear stored resume position so next play starts from the beginning
            persistResumePosition(trackId, 0L)
        }
        _state.value = PlaybackState.Idle
    }

    /** UI position update (called every 1 second). Only updates StateFlow — no KV write, no log. */
    fun onPositionUpdateUi(trackId: String, positionMs: Long) {
        val current = _state.value
        if (current is PlaybackState.Playing && current.trackId == trackId) {
            _state.value = PlaybackState.Playing(trackId, positionMs, current.durationMs)
        }
    }

    /** Persist position update (called every 10 seconds). Updates StateFlow and writes to KV synchronously. */
    fun onPositionUpdate(trackId: String, positionMs: Long) {
        val current = _state.value
        if (current is PlaybackState.Playing && current.trackId == trackId) {
            _state.value = PlaybackState.Playing(trackId, positionMs, current.durationMs)
            persistResumePosition(trackId, positionMs)
        }
    }

    /** Cancel any pending coroutine work on the state machine's scope (e.g. deferred persists).
     *  Called during service teardown to prevent stale writes from overwriting a final position save. */
    fun cancelPendingPersists() {
        scope.coroutineContext[Job]?.children?.forEach { it.cancel() }
    }

    // In-memory only — these only need to survive within the same process.
    private var pendingTrackId: String = ""
    private var pendingUrl: String = ""
    private var pendingPos: Long = 0L

    fun play(trackId: String, url: String, startPositionMs: Long = resumePosition(trackId)) {
        pendingTrackId = trackId
        pendingUrl = url
        pendingPos = startPositionMs
        // Defensive: clear scrubbing in case a previous gesture was interrupted mid-scrub
        _scrubbing.value = false
        transition(trackId, "Buffering")
        _state.value = PlaybackState.Buffering(trackId)
    }

    fun pause() {
        val current = _state.value
        if (current is PlaybackState.Playing) {
            transition(current.trackId, "Paused")
            logger.log(LogLevel.INFO, Subsystem.PLAYBACK,
                LogEvent.Playback.Paused(current.trackId, current.positionMs))
            _state.value = PlaybackState.Paused(current.trackId, current.positionMs, current.durationMs)
            persistResumePosition(current.trackId, current.positionMs)
        }
    }

    fun resume() {
        val current = _state.value
        if (current is PlaybackState.Paused) {
            logger.log(LogLevel.INFO, Subsystem.PLAYBACK,
                LogEvent.Playback.Resumed(current.trackId))
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
        val current = _state.value
        val trackId = when (current) {
            is PlaybackState.Playing -> current.trackId
            is PlaybackState.Paused  -> current.trackId
            else -> null
        }
        if (trackId != null) {
            val fromMs = when (current) {
                is PlaybackState.Playing -> current.positionMs
                is PlaybackState.Paused  -> current.positionMs
                else -> 0L
            }
            logger.log(LogLevel.DEBUG, Subsystem.PLAYBACK,
                LogEvent.Playback.SeekRequested(trackId, fromMs, positionMs))
        }
        _pendingSeek.value = positionMs
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
        val clamped = speed.coerceIn(0.5f, 3.0f)
        val current = _state.value
        val trackId = when (current) {
            is PlaybackState.Playing -> current.trackId
            is PlaybackState.Paused  -> current.trackId
            else -> ""
        }
        if (trackId.isNotBlank()) {
            logger.log(LogLevel.INFO, Subsystem.PLAYBACK,
                LogEvent.Playback.SpeedChanged(trackId, clamped))
        }
        _speed.value = clamped
    }

    fun setScrubbing(enabled: Boolean) {
        _scrubbing.value = enabled
    }

    fun resumePosition(trackId: String): Long = kvStore.getLong("resume_$trackId", 0L)

    fun pendingPlay(): Triple<String, String, Long>? =
        if (pendingTrackId.isNotBlank() && pendingUrl.isNotBlank())
            Triple(pendingTrackId, pendingUrl, pendingPos)
        else null

    private fun persistResumePosition(trackId: String, positionMs: Long) {
        kvStore.putLong("resume_$trackId", positionMs)
        kvStore.putString(KEY_RESUME_ID, trackId)
        kvStore.putLong(KEY_RESUME_POS, positionMs)
    }
}
