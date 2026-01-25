package com.example.podder.data.local

import androidx.room.Embedded
import androidx.room.Relation

data class EpisodeWithPodcast(
    @Embedded val episode: EpisodeEntity,
    @Relation(
        parentColumn = "podcastUrl",
        entityColumn = "url"
    )
    val podcast: PodcastEntity
)
