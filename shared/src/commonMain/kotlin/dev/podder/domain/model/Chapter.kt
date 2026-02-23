package dev.podder.domain.model

data class Chapter(
    val id: String,
    val parentId: String,
    val title: String,
    val startMs: Long,
    val endMs: Long,
)
