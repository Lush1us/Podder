package com.lush1us.podder.worker

import android.content.Context
import androidx.media3.datasource.cache.SimpleCache
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lush1us.podder.download.DownloadRepository
import dev.podder.db.PodderDatabase
import dev.podder.logging.LogEvent
import dev.podder.logging.LogLevel
import dev.podder.logging.PodderLogger
import dev.podder.logging.Subsystem
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Daily charging-and-idle cleanup. Finds episodes marked as finished more than 24 h ago
 * and removes their cached audio from SimpleCache (via DownloadManager) to reclaim disk.
 *
 * Idempotent — re-running the same day is cheap because finished bytes are already gone.
 */
class CacheCleanupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

    private val database: PodderDatabase      by inject()
    private val downloads: DownloadRepository by inject()
    private val simpleCache: SimpleCache      by inject()
    private val logger: PodderLogger          by inject()

    override suspend fun doWork(): Result {
        val cutoffUtcSec = (System.currentTimeMillis() / 1000) - 24 * 60 * 60
        val stale = database.episodesQueries
            .selectFinishedBefore(cutoffUtcSec)
            .executeAsList()

        if (stale.isEmpty()) return Result.success()

        logger.log(LogLevel.INFO, Subsystem.DOWNLOAD,
            LogEvent.Download.ExpiredCleanup(stale.size))

        for (episodeId in stale) {
            // Remove any managed download — DownloadManager clears the cached bytes it owns.
            downloads.cancelDownload(episodeId)
            // Evict leftover streamed bytes that were never wrapped in a DownloadRequest.
            // (Harmless if the key was already removed above.)
            try { simpleCache.removeResource(episodeId) } catch (_: Exception) { }
        }

        return Result.success()
    }
}
