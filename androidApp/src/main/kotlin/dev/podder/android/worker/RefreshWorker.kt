package com.lush1us.podder.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lush1us.podder.notification.NewEpisodeInfo
import com.lush1us.podder.notification.NewEpisodeNotifier
import com.lush1us.podder.precache.PreCacheManager
import dev.podder.data.repository.PodcastRepository
import dev.podder.logging.LogEvent
import dev.podder.logging.LogLevel
import dev.podder.logging.PodderLogger
import dev.podder.logging.Subsystem
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class RefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

    private val repository: PodcastRepository by inject()
    private val preCacheManager: PreCacheManager by inject()
    private val notifier: NewEpisodeNotifier by inject()
    private val logger: PodderLogger by inject()

    override suspend fun doWork(): Result {
        logger.log(LogLevel.INFO, Subsystem.APP, LogEvent.FeedRefresh.Started(podcastCount = 0, isManual = false))
        return try {
            val refreshed = repository.refreshAll()
            val newEpisodes = refreshed.filter { it.isNew }.map {
                NewEpisodeInfo(
                    episodeId    = it.episodeId,
                    title        = it.title,
                    podcastTitle = it.podcastTitle,
                    podcastId    = it.podcastId,
                )
            }
            notifier.notify(newEpisodes)
            preCacheManager.preCacheRecent()
            Result.success()
        } catch (e: Exception) {
            logger.log(LogLevel.ERROR, Subsystem.APP,
                LogEvent.FeedRefresh.Failed(e.message ?: "unknown"))
            Result.retry()
        }
    }
}
