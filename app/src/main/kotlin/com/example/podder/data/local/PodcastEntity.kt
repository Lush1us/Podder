package com.example.podder.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "podcasts")
data class PodcastEntity(
    @PrimaryKey val url: String,
    val title: String,
    val imageUrl: String?
)