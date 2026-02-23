package dev.podder.domain.model

data class Album(
    val id: String,
    val artistId: String,
    val title: String,
    val artworkUrl: String?,
)
