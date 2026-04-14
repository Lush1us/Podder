package dev.podder.domain.player

import kotlinx.coroutines.flow.Flow

/**
 * Platform-agnostic playback control surface. Actuals wrap a native player
 * (ExoPlayer on Android, AVPlayer on iOS).
 *
 * Emits [PlayerEvent]s that callers (typically `PlaybackStateMachine`) translate
 * into their own state. The player does not own state — it reports facts.
 */
interface PodderPlayer {
    val events: Flow<PlayerEvent>

    fun prepare(trackId: String, url: String, startPositionMs: Long)
    fun play()
    fun pause()
    fun stop()
    fun seekTo(positionMs: Long)
    fun setSpeed(speed: Float)
    fun release()
}

sealed interface PlayerEvent {
    data class Buffering(val trackId: String) : PlayerEvent
    data class Playing(val trackId: String, val positionMs: Long, val durationMs: Long) : PlayerEvent
    data class Paused(val trackId: String, val positionMs: Long, val durationMs: Long) : PlayerEvent
    data class Ended(val trackId: String) : PlayerEvent
    data class Error(val trackId: String, val message: String) : PlayerEvent
    data class PositionTick(val trackId: String, val positionMs: Long, val durationMs: Long) : PlayerEvent
}

expect class PodderPlayerFactory {
    fun create(): PodderPlayer
}
