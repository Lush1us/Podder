package com.example.podder.ui

data class EpisodeUiModel(
    val title: String,
    val podcastTitle: String,
    val pubDate: String, // Formatted date string
    val audioUrl: String,
    val description: String,
    val imageUrl: String // Podcast image
)
