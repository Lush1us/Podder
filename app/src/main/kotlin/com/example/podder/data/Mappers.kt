package com.example.podder.data

import com.example.podder.data.local.EpisodeEntity
import com.example.podder.data.local.PodcastEntity
import com.example.podder.parser.Episode
import com.example.podder.parser.Podcast

// Extension to map Parser Object -> Database Entity
fun Podcast.toEntity(url: String): PodcastEntity {
    return PodcastEntity(
        url = url,
        title = this.title,
        imageUrl = this.imageUrl
    )
}

// Extension to map Parser Episode -> Database Entity
fun Episode.toEntity(podcastUrl: String): EpisodeEntity {
    return EpisodeEntity(
        guid = this.guid ?: "unknown-${System.currentTimeMillis()}-${Math.random()}", // Fallback if GUID missing
        podcastUrl = podcastUrl,
        title = this.title,
        description = this.description ?: "",
        pubDate = 0L, // TODO: Implement DateUtils parsing
        audioUrl = this.audioUrl ?: "",
        duration = this.duration ?: 0L
    )
}