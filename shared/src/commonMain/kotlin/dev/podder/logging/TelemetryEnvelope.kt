package dev.podder.logging

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@Serializable
data class TelemetryEnvelope(
    val timestampMs: Long,
    val level: String,
    val subsystem: String,
    val event: LogEvent,
)

val telemetryJson: Json = Json {
    classDiscriminator = "eventType"
    encodeDefaults = true
    ignoreUnknownKeys = true
    serializersModule = SerializersModule {
        polymorphic(LogEvent::class) {
            // AppLifecycle
            subclass(LogEvent.AppLifecycle.Created::class)
            subclass(LogEvent.AppLifecycle.Foregrounded::class)
            subclass(LogEvent.AppLifecycle.Backgrounded::class)
            subclass(LogEvent.AppLifecycle.MemoryWarning::class)
            subclass(LogEvent.AppLifecycle.PreviousCrashContextFound::class)
            // FeedRefresh
            subclass(LogEvent.FeedRefresh.Started::class)
            subclass(LogEvent.FeedRefresh.PodcastFetchStarted::class)
            subclass(LogEvent.FeedRefresh.PodcastFetchCompleted::class)
            subclass(LogEvent.FeedRefresh.PodcastFetchFailed::class)
            subclass(LogEvent.FeedRefresh.PodcastParsed::class)
            subclass(LogEvent.FeedRefresh.DbWriteCompleted::class)
            subclass(LogEvent.FeedRefresh.Completed::class)
            subclass(LogEvent.FeedRefresh.Failed::class)
            // Playback
            subclass(LogEvent.Playback.StateChanged::class)
            subclass(LogEvent.Playback.Started::class)
            subclass(LogEvent.Playback.Paused::class)
            subclass(LogEvent.Playback.Resumed::class)
            subclass(LogEvent.Playback.Completed::class)
            subclass(LogEvent.Playback.Stopped::class)
            subclass(LogEvent.Playback.Error::class)
            subclass(LogEvent.Playback.SpeedChanged::class)
            subclass(LogEvent.Playback.SeekRequested::class)
            subclass(LogEvent.Playback.AutoplayTriggered::class)
            subclass(LogEvent.Playback.EpisodeFinished::class)
            // Cache
            subclass(LogEvent.Cache.PreCacheStarted::class)
            subclass(LogEvent.Cache.EpisodeCacheStarted::class)
            subclass(LogEvent.Cache.EpisodeCacheCompleted::class)
            subclass(LogEvent.Cache.EpisodeCacheFailed::class)
            subclass(LogEvent.Cache.PreCacheCompleted::class)
            // Queue
            subclass(LogEvent.Queue.EpisodeAdded::class)
            subclass(LogEvent.Queue.EpisodeRemoved::class)
            subclass(LogEvent.Queue.NextTaken::class)
            subclass(LogEvent.Queue.AutoplayToggled::class)
            subclass(LogEvent.Queue.Cleared::class)
            // Download
            subclass(LogEvent.Download.Started::class)
            subclass(LogEvent.Download.Cancelled::class)
            subclass(LogEvent.Download.ExpiredCleanup::class)
            // Network
            subclass(LogEvent.Network.RequestStarted::class)
            subclass(LogEvent.Network.RequestCompleted::class)
            subclass(LogEvent.Network.RequestFailed::class)
            // Performance
            subclass(LogEvent.Performance.JankDetected::class)
            subclass(LogEvent.Performance.AnrDetected::class)
            subclass(LogEvent.Performance.MemoryPressure::class)
            // AppError
            subclass(LogEvent.AppError::class)
        }
    }
}
