package dev.podder.android.precache

import android.net.Uri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import java.io.IOException
import dev.podder.data.repository.PodcastRepository
import dev.podder.logging.LogEvent
import dev.podder.logging.LogLevel
import dev.podder.logging.PodderLogger
import dev.podder.logging.Subsystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class PreCacheManager(
    private val cacheDataSourceFactory: CacheDataSource.Factory,
    private val repository: PodcastRepository,
    private val logger: PodderLogger,
) {
    companion object {
        private const val MAX_EPISODES = 20
        private const val CACHE_BYTES = 2L * 1024L * 1024L // 2 MB ≈ 2 min at 128 kbps
    }

    suspend fun preCacheRecent() = withContext(Dispatchers.IO) {
        val episodes = repository.allEpisodes(MAX_EPISODES.toLong()).first()
        val batch = episodes.take(MAX_EPISODES)
        logger.log(LogLevel.INFO, Subsystem.CACHE,
            LogEvent.Cache.PreCacheStarted(batch.size))
        var succeeded = 0
        var failed = 0
        batch.forEach { episode ->
            logger.log(LogLevel.DEBUG, Subsystem.CACHE,
                LogEvent.Cache.EpisodeCacheStarted(episode.id, episode.url))
            runCatching { preCacheUrl(episode.id, episode.url) }
                .onSuccess {
                    logger.log(LogLevel.INFO, Subsystem.CACHE,
                        LogEvent.Cache.EpisodeCacheCompleted(episode.id))
                    succeeded++
                }
                .onFailure { e ->
                    logger.log(LogLevel.WARN, Subsystem.CACHE,
                        LogEvent.Cache.EpisodeCacheFailed(episode.id, e.message ?: "unknown"))
                    failed++
                }
        }
        logger.log(LogLevel.INFO, Subsystem.CACHE,
            LogEvent.Cache.PreCacheCompleted(succeeded, failed))
    }

    /** Thrown internally when the precache byte limit is reached. Not an error. */
    private class PrecacheLimitReached : IOException("Precache byte limit reached")

    private fun preCacheUrl(episodeId: String, url: String) {
        // Do NOT use DataSpec.setLength — that causes SimpleCache to record a wrong
        // content length, making ExoPlayer think the episode is only CACHE_BYTES long.
        // Instead, cancel via the progress listener after enough bytes are cached.
        val dataSpec = DataSpec.Builder()
            .setUri(Uri.parse(url))
            .setKey(episodeId)   // stable key independent of URL query params / CDN rotation
            .build()
        val dataSource = cacheDataSourceFactory.createDataSource() as CacheDataSource
        try {
            CacheWriter(dataSource, dataSpec, null) { _, bytesCached, _ ->
                if (bytesCached >= CACHE_BYTES) throw PrecacheLimitReached()
            }.cache()
        } catch (_: PrecacheLimitReached) {
            // Normal — we only wanted the first CACHE_BYTES
        }
    }
}
