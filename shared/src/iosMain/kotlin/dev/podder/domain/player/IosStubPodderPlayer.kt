package dev.podder.domain.player

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Stub iOS player. Emits an error on [prepare] so callers surface the
 * "not implemented" state instead of appearing to load silently forever.
 * AVFoundation integration will replace this in a later pass.
 */
internal class IosStubPodderPlayer : PodderPlayer {

    private val _events = MutableSharedFlow<PlayerEvent>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: Flow<PlayerEvent> = _events.asSharedFlow()

    override fun prepare(trackId: String, url: String, startPositionMs: Long) {
        _events.tryEmit(PlayerEvent.Error(trackId, "ios_player_not_implemented"))
    }

    override fun play() = Unit
    override fun pause() = Unit
    override fun stop() = Unit
    override fun seekTo(positionMs: Long) = Unit
    override fun setSpeed(speed: Float) = Unit
    override fun release() = Unit
}
