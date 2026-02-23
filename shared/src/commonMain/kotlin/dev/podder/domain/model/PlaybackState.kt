package dev.podder.domain.model

sealed class PlaybackState {
    object Idle : PlaybackState()
    data class Playing(val trackId: String, val positionMs: Long, val durationMs: Long) : PlaybackState()
    data class Paused(val trackId: String, val positionMs: Long, val durationMs: Long) : PlaybackState()
    data class Buffering(val trackId: String) : PlaybackState()
    data class Error(val trackId: String, val message: String) : PlaybackState()
}
