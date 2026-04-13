package com.lush1us.podder.download

sealed class DownloadProgress {
    object NotDownloaded : DownloadProgress()
    object Queued : DownloadProgress()
    data class Downloading(val percent: Int) : DownloadProgress()
    object Downloaded : DownloadProgress()
    object Failed : DownloadProgress()
}
