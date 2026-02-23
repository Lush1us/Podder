package dev.podder.domain.model

data class Episode(
    val id: String,
    val podcastId: String,
    val title: String,
    val url: String,
    val durationMs: Long,
    val playCount: Int,
    val isDownloaded: Boolean,
    val publicationDateUtc: Long,
)
