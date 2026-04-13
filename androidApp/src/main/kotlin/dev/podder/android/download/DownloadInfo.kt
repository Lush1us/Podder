package com.lush1us.podder.download

data class DownloadInfo(
    val episodeId: String,
    val episodeTitle: String,
    val podcastTitle: String,
    val artworkUrl: String?,
    val url: String,
    val progress: DownloadProgress,
)
