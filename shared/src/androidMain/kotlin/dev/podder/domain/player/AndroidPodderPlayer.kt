package dev.podder.domain.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * ExoPlayer-backed [PodderPlayer] used by the shared module.
 *
 * NOTE: Podder is a podcast app — real playback runs in a foreground
 * [androidx.media3.session.MediaSessionService] so audio survives when the UI
 * is backgrounded or the process is trimmed. A raw [ExoPlayer] instance owned
 * in-process is fine for the shared abstraction contract and for unit-style
 * use, but the UI must not consume it directly or background playback breaks.
 *
 * Follow-up: replace this with a `MediaController`-backed actual that binds to
 * `PodderMediaService` so commands route through the foreground service.
 */
internal class AndroidPodderPlayer(context: Context) : PodderPlayer {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val exo: ExoPlayer = ExoPlayer.Builder(context).build()

    private val _events = MutableSharedFlow<PlayerEvent>(
        replay = 0,
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: Flow<PlayerEvent> = _events.asSharedFlow()

    private var currentTrackId: String = ""

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (exo.isPlaying && currentTrackId.isNotEmpty()) {
                _events.tryEmit(
                    PlayerEvent.PositionTick(
                        trackId = currentTrackId,
                        positionMs = exo.currentPosition.coerceAtLeast(0L),
                        durationMs = exo.duration.coerceAtLeast(0L),
                    ),
                )
            }
            mainHandler.postDelayed(this, TICK_INTERVAL_MS)
        }
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            val id = currentTrackId
            if (id.isEmpty()) return
            when (state) {
                Player.STATE_BUFFERING -> _events.tryEmit(PlayerEvent.Buffering(id))
                Player.STATE_READY -> emitPlayingOrPaused(id)
                Player.STATE_ENDED -> _events.tryEmit(PlayerEvent.Ended(id))
                Player.STATE_IDLE -> Unit
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val id = currentTrackId
            if (id.isEmpty()) return
            emitPlayingOrPaused(id)
        }

        override fun onPlayerError(error: PlaybackException) {
            val id = currentTrackId.ifEmpty { "unknown" }
            _events.tryEmit(PlayerEvent.Error(id, error.message ?: error.errorCodeName))
        }
    }

    init {
        exo.addListener(listener)
        mainHandler.post(tickRunnable)
    }

    private fun emitPlayingOrPaused(trackId: String) {
        val pos = exo.currentPosition.coerceAtLeast(0L)
        val dur = exo.duration.coerceAtLeast(0L)
        val event = if (exo.isPlaying) {
            PlayerEvent.Playing(trackId, pos, dur)
        } else {
            PlayerEvent.Paused(trackId, pos, dur)
        }
        _events.tryEmit(event)
    }

    override fun prepare(trackId: String, url: String, startPositionMs: Long) {
        runOnMain {
            currentTrackId = trackId
            exo.setMediaItem(MediaItem.fromUri(url))
            exo.prepare()
            if (startPositionMs > 0L) exo.seekTo(startPositionMs)
            _events.tryEmit(PlayerEvent.Buffering(trackId))
        }
    }

    override fun play() = runOnMain { exo.play() }
    override fun pause() = runOnMain { exo.pause() }
    override fun stop() = runOnMain { exo.stop() }
    override fun seekTo(positionMs: Long) = runOnMain { exo.seekTo(positionMs) }
    override fun setSpeed(speed: Float) = runOnMain {
        exo.playbackParameters = PlaybackParameters(speed.coerceIn(0.5f, 3.0f))
    }

    override fun release() {
        runOnMain {
            mainHandler.removeCallbacks(tickRunnable)
            exo.removeListener(listener)
            exo.release()
        }
    }

    private inline fun runOnMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post { block() }
    }

    private companion object {
        const val TICK_INTERVAL_MS = 1_000L
    }
}
