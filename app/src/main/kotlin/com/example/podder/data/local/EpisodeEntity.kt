package com.example.podder.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "episodes")
data class EpisodeEntity(
    @PrimaryKey val guid: String,
    val podcastUrl: String, // Foreign Key
    val title: String,
    val description: String,
    val pubDate: Long,
    val audioUrl: String,
    val duration: String
)