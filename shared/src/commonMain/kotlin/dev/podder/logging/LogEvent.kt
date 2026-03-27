package dev.podder.logging

import kotlinx.serialization.Serializable

@Serializable
sealed class LogEvent {

    // ── App lifecycle ─────────────────────────────────────────────────────────
    @Serializable sealed class AppLifecycle : LogEvent() {
        @Serializable data object Created : AppLifecycle()
        @Serializable data object Foregrounded : AppLifecycle()
        @Serializable data object Backgrounded : AppLifecycle()
        @Serializable data class MemoryWarning(val level: Int, val levelName: String) : AppLifecycle()
        @Serializable data class PreviousCrashContextFound(val summary: String) : AppLifecycle()
    }

    // ── Feed refresh lifecycle ────────────────────────────────────────────────
    @Serializable sealed class FeedRefresh : LogEvent() {
        @Serializable data class Started(val podcastCount: Int, val isManual: Boolean) : FeedRefresh()
        @Serializable data class PodcastFetchStarted(val podcastId: String, val rssUrl: String) : FeedRefresh()
        @Serializable data class PodcastFetchCompleted(val podcastId: String, val bytesReceived: Long, val latencyMs: Long) : FeedRefresh()
        @Serializable data class PodcastFetchFailed(val podcastId: String, val reason: String) : FeedRefresh()
        @Serializable data class PodcastParsed(val podcastId: String, val episodeCount: Int, val newCount: Int) : FeedRefresh()
        @Serializable data class DbWriteCompleted(val podcastId: String, val rowsInserted: Int, val latencyMs: Long) : FeedRefresh()
        @Serializable data class Completed(val total: Int, val succeeded: Int, val failed: Int, val totalMs: Long) : FeedRefresh()
        @Serializable data class Failed(val reason: String) : FeedRefresh()
    }

    // ── Playback state machine ────────────────────────────────────────────────
    @Serializable sealed class Playback : LogEvent() {
        @Serializable data class StateChanged(val trackId: String, val from: String, val to: String) : Playback()
        @Serializable data class Started(val trackId: String, val startPositionMs: Long) : Playback()
        @Serializable data class Paused(val trackId: String, val positionMs: Long) : Playback()
        @Serializable data class Resumed(val trackId: String) : Playback()
        @Serializable data class Completed(val trackId: String) : Playback()
        @Serializable data class Stopped(val trackId: String) : Playback()
        @Serializable data class Error(val trackId: String, val message: String) : Playback()
        @Serializable data class SpeedChanged(val trackId: String, val speed: Float) : Playback()
        @Serializable data class SeekRequested(val trackId: String, val fromMs: Long, val toMs: Long) : Playback()
        @Serializable data class AutoplayTriggered(val endedId: String, val nextId: String, val source: String) : Playback()
        @Serializable data class EpisodeFinished(val trackId: String) : Playback()
    }

    // ── Cache operations ──────────────────────────────────────────────────────
    @Serializable sealed class Cache : LogEvent() {
        @Serializable data class PreCacheStarted(val episodeCount: Int) : Cache()
        @Serializable data class EpisodeCacheStarted(val episodeId: String, val url: String) : Cache()
        @Serializable data class EpisodeCacheCompleted(val episodeId: String) : Cache()
        @Serializable data class EpisodeCacheFailed(val episodeId: String, val reason: String) : Cache()
        @Serializable data class PreCacheCompleted(val succeeded: Int, val failed: Int) : Cache()
    }

    // ── Queue mutations ───────────────────────────────────────────────────────
    @Serializable sealed class Queue : LogEvent() {
        @Serializable data class EpisodeAdded(val episodeId: String, val queueSize: Int) : Queue()
        @Serializable data class EpisodeRemoved(val episodeId: String, val queueSize: Int) : Queue()
        @Serializable data class NextTaken(val episodeId: String, val remaining: Int) : Queue()
        @Serializable data class AutoplayToggled(val enabled: Boolean) : Queue()
        @Serializable data object Cleared : Queue()
    }

    // ── Downloads ─────────────────────────────────────────────────────────────
    @Serializable sealed class Download : LogEvent() {
        @Serializable data class Started(val episodeId: String, val isAutoCache: Boolean) : Download()
        @Serializable data class Cancelled(val episodeId: String) : Download()
        @Serializable data class ExpiredCleanup(val count: Int) : Download()
    }

    // ── Network requests ──────────────────────────────────────────────────────
    @Serializable sealed class Network : LogEvent() {
        @Serializable data class RequestStarted(val url: String, val method: String) : Network()
        @Serializable data class RequestCompleted(val url: String, val statusCode: Int, val latencyMs: Long, val bodyBytes: Long) : Network()
        @Serializable data class RequestFailed(val url: String, val reason: String) : Network()
    }

    // ── Performance telemetry ─────────────────────────────────────────────────
    @Serializable sealed class Performance : LogEvent() {
        @Serializable data class JankDetected(val droppedFrames: Int, val deltaMs: Long) : Performance()
        @Serializable data class AnrDetected(val blockedMs: Long, val mainThreadStack: String) : Performance()
        @Serializable data class MemoryPressure(val availMem: Long, val totalMem: Long, val isLowMemory: Boolean, val trimLevel: Int) : Performance()
    }

    // ── Generic error ─────────────────────────────────────────────────────────
    @Serializable data class AppError(
        val subsystem: String,
        val code: String,
        val message: String,
        val isRecoverable: Boolean,
        val stackTrace: String? = null,
    ) : LogEvent()
}
