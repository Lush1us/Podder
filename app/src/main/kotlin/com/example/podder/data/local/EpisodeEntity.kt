package com.example.podder.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "episodes",
    foreignKeys = [
        ForeignKey(
            entity = PodcastEntity::class,
            parentColumns = ["url"],
            childColumns = ["podcastUrl"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("podcastUrl")]
)
data class EpisodeEntity(
    @PrimaryKey val guid: String,
    val podcastUrl: String,
    val title: String,
    val description: String,
    val pubDate: Long,
    val audioUrl: String,
    val duration: Long,
    val progressInMillis: Long = 0L,
    val localFilePath: String? = null
)