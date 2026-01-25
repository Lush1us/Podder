package com.example.podder.core

sealed interface PodcastAction : Traceable {
    data class FetchPodcast(val url: String, override val source: String, override val timestamp: Long) : PodcastAction
    data class FetchPodcasts(override val source: String, override val timestamp: Long) : PodcastAction
    data class Play(val podcastId: String, override val source: String, override val timestamp: Long) : PodcastAction
    data class Pause(override val source: String, override val timestamp: Long) : PodcastAction
}
