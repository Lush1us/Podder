package dev.podder.android.download

import android.content.Context
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import android.net.Uri
import dev.podder.android.service.PodderDownloadService
import dev.podder.db.PodderDatabase
import dev.podder.data.repository.PodcastRepository
import dev.podder.logging.LogEvent
import dev.podder.logging.LogLevel
import dev.podder.logging.PodderLogger
import dev.podder.logging.Subsystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class DownloadRepository(
    private val context: Context,
    private val downloadManager: DownloadManager,
    private val database: PodderDatabase,
    private val podcastRepository: PodcastRepository,
    private val logger: PodderLogger,
) {
    /** Submit a manual (permanent) download. No-op if already queued/downloading/done. */
    fun startManualDownload(episodeId: String, url: String) {
        val existing = downloadManager.downloadIndex.getDownload(episodeId)
        if (existing != null && existing.state != Download.STATE_FAILED) return
        database.downloadsQueries.insertOrReplace(
            episodeId   = episodeId,
            url         = url,
            isAutoCache = 0L,
            startedAt   = System.currentTimeMillis(),
            expiresAt   = null,
        )
        val request = DownloadRequest.Builder(episodeId, Uri.parse(url)).build()
        DownloadService.sendAddDownload(context, PodderDownloadService::class.java, request, false)
        logger.log(LogLevel.INFO, Subsystem.DOWNLOAD,
            LogEvent.Download.Started(episodeId, isAutoCache = false))
    }

    /** Submit a low-footprint auto-cache download. No-op if already tracked. */
    fun startAutoCache(episodeId: String, url: String) {
        if (downloadManager.downloadIndex.getDownload(episodeId) != null) return
        val now = System.currentTimeMillis()
        database.downloadsQueries.insertOrReplace(
            episodeId   = episodeId,
            url         = url,
            isAutoCache = 1L,
            startedAt   = now,
            expiresAt   = now + 7L * 24 * 60 * 60 * 1000,
        )
        val request = DownloadRequest.Builder(episodeId, Uri.parse(url)).build()
        DownloadService.sendAddDownload(context, PodderDownloadService::class.java, request, false)
        logger.log(LogLevel.INFO, Subsystem.DOWNLOAD,
            LogEvent.Download.Started(episodeId, isAutoCache = true))
    }

    fun cancelDownload(episodeId: String) {
        DownloadService.sendRemoveDownload(context, PodderDownloadService::class.java, episodeId, false)
        database.downloadsQueries.deleteByEpisodeId(episodeId)
        logger.log(LogLevel.INFO, Subsystem.DOWNLOAD,
            LogEvent.Download.Cancelled(episodeId))
    }

    fun progressFor(episodeId: String): DownloadProgress {
        val dl = downloadManager.downloadIndex.getDownload(episodeId)
            ?: return DownloadProgress.NotDownloaded
        return when (dl.state) {
            Download.STATE_QUEUED      -> DownloadProgress.Queued
            Download.STATE_DOWNLOADING -> DownloadProgress.Downloading(dl.percentDownloaded.toInt())
            Download.STATE_COMPLETED   -> DownloadProgress.Downloaded
            Download.STATE_FAILED      -> DownloadProgress.Failed
            else                       -> DownloadProgress.NotDownloaded
        }
    }

    /** Polls DownloadManager every second, enriched with episode metadata from DB. */
    fun observeDownloads(): Flow<List<DownloadInfo>> = flow {
        while (true) {
            val infos = mutableListOf<DownloadInfo>()
            val cursor = downloadManager.downloadIndex.getDownloads()
            try {
                while (cursor.moveToNext()) {
                    val dl       = cursor.download
                    val epId     = dl.request.id
                    val url      = dl.request.uri.toString()
                    val episode  = podcastRepository.episodeById(epId)
                    val podcast  = episode?.let { podcastRepository.podcastById(it.podcastId) }
                    infos.add(DownloadInfo(
                        episodeId    = epId,
                        episodeTitle = episode?.title ?: epId,
                        podcastTitle = podcast?.title ?: "",
                        artworkUrl   = podcast?.artworkUrl,
                        url          = url,
                        progress     = progressFor(epId),
                    ))
                }
            } finally {
                cursor.close()
            }
            emit(infos)
            delay(1_000L)
        }
    }.flowOn(Dispatchers.IO)

    suspend fun cleanupExpiredAutoCache() {
        val now     = System.currentTimeMillis()
        val expired = database.downloadsQueries.selectExpired(now).executeAsList()
        if (expired.isNotEmpty()) {
            logger.log(LogLevel.INFO, Subsystem.DOWNLOAD,
                LogEvent.Download.ExpiredCleanup(expired.size))
        }
        for (row in expired) {
            DownloadService.sendRemoveDownload(
                context, PodderDownloadService::class.java, row.episodeId, false
            )
            database.downloadsQueries.deleteByEpisodeId(row.episodeId)
        }
    }
}
