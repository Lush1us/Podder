package dev.podder.domain.model

data class Station(
    val id: String,
    val name: String,
    val streamUrl: String,
    val genre: String?,
    val isHls: Boolean,
)
