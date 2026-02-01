package com.example.podder.core

sealed interface PodcastAction : Traceable {
    data class FetchPodcast(val url: String, override val source: String, override val timestamp: Long) : PodcastAction
    data class FetchPodcasts(override val source: String, override val timestamp: Long) : PodcastAction

    data class Play(
        val guid: String,
        val url: String,
        val title: String,
        val artist: String,
        val imageUrl: String?,
        val description: String?,
        val podcastUrl: String?,
        override val source: String,
        override val timestamp: Long
    ) : PodcastAction

    data class Pause(override val source: String, override val timestamp: Long) : PodcastAction
    data class TogglePlayPause(override val source: String, override val timestamp: Long) : PodcastAction
    data class SeekBack(override val source: String, override val timestamp: Long) : PodcastAction
    data class SeekForward(override val source: String, override val timestamp: Long) : PodcastAction
    data class MarkAsFinished(val guid: String, val durationSeconds: Long, override val source: String, override val timestamp: Long) : PodcastAction
    data class SeekTo(val positionMillis: Long, override val source: String, override val timestamp: Long) : PodcastAction
    data class Download(val guid: String, val url: String, val title: String, override val source: String, override val timestamp: Long) : PodcastAction
}
