package com.example.podder.core

import com.example.podder.data.network.SearchResult

sealed interface PodcastAction : Traceable {
    data class FetchPodcast(val url: String, override val source: String, override val timestamp: Long) : PodcastAction
    data class FetchPodcasts(override val source: String, override val timestamp: Long) : PodcastAction

    data class Search(val query: String, override val source: String, override val timestamp: Long) : PodcastAction
    data class Subscribe(val podcast: SearchResult, override val source: String, override val timestamp: Long) : PodcastAction
    data class Unsubscribe(val url: String, override val source: String, override val timestamp: Long) : PodcastAction
    data class ClearSearch(override val source: String, override val timestamp: Long) : PodcastAction

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
    data class DeleteDownload(val guid: String, val localFilePath: String, override val source: String, override val timestamp: Long) : PodcastAction

    data class StartErrorReport(override val source: String, override val timestamp: Long) : PodcastAction
    data class SubmitErrorReport(val message: String, val startTimestamp: Long, override val source: String, override val timestamp: Long) : PodcastAction
}
