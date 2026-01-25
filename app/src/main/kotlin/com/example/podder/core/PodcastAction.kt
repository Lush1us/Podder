package com.example.podder.core

sealed interface PodcastAction : Traceable {
    data class FetchPodcast(val url: String, override val source: String, override val timestamp: Long) : PodcastAction
    data class FetchPodcasts(override val source: String, override val timestamp: Long) : PodcastAction

    data class Play(
        val url: String,
        val title: String,
        val artist: String,
        val imageUrl: String?,
        override val source: String,
        override val timestamp: Long
    ) : PodcastAction

    data class Pause(override val source: String, override val timestamp: Long) : PodcastAction
    data class TogglePlayPause(override val source: String, override val timestamp: Long) : PodcastAction
}
