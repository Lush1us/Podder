package dev.podder.domain.model

data class Track(
    val id: String,
    val title: String,
    val url: String,
    val mediaType: MediaType,
    val artistId: String?,
    val albumId: String?,
    val durationMs: Long,
    val playCount: Int,
    val isDownloaded: Boolean,
    val publicationDateUtc: Long,
)
