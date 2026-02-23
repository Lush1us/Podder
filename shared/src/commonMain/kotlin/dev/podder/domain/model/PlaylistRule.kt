package dev.podder.domain.model

data class PlaylistRule(
    val id: String,
    val playlistId: String,
    val field: String,
    val operator: String,
    val value: String,
)
