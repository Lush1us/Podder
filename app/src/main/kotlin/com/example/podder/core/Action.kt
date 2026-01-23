package com.example.podder.core

import com.example.podder.core.Traceable

sealed class Action(override open val source: String, override open val timestamp: Long) : Traceable {
    // Placeholder for specific actions
    // Each specific action will be a data class or object extending Action
    // and providing its own source and timestamp.
}

sealed class PodcastAction(override val source: String, override val timestamp: Long) : Action(source, timestamp) {
    data class FetchPodcast(override val source: String, override val timestamp: Long, val url: String) : PodcastAction(source, timestamp)
    data class FetchPodcasts(override val source: String, override val timestamp: Long, val urls: List<String>) : PodcastAction(source, timestamp)
}
