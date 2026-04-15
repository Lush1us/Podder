package dev.podder.data.repository

import dev.podder.db.PodderDatabase
import dev.podder.domain.parser.parseOpmlUrls
import dev.podder.domain.parser.parseRssFeed
import dev.podder.logging.LogEvent
import dev.podder.logging.LogLevel
import dev.podder.logging.PodderLogger
import dev.podder.logging.Subsystem
import dev.podder.util.time.platformNowUtcEpoch
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class PodcastRepositoryImpl(
    private val db: PodderDatabase,
    private val httpClient: HttpClient,
    private val logger: PodderLogger?,
) : PodcastRepository {

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun subscribeToPodcast(rssUrl: String) = withContext(Dispatchers.IO) {
        val xml  = httpClient.get(rssUrl).bodyAsText()
        val feed = parseRssFeed(xml)

        val podcastId = Uuid.random().toString()

        db.podcastsQueries.insert(
            id             = podcastId,
            title          = feed.title,
            rssUrl         = rssUrl,
            artworkUrl     = feed.imageUrl,
            priority       = 0L,
            lastFetchedUtc = platformNowUtcEpoch(),
        )

        feed.items.forEach { item ->
            db.episodesQueries.insert(
                id                 = item.guid.ifBlank { Uuid.random().toString() },
                podcastId          = podcastId,
                title              = item.title,
                url                = item.url,
                durationMs         = item.durationMs,
                playCount          = 0L,
                isDownloaded       = 0L,
                publicationDateUtc = item.pubDateUtc,
                description        = item.description,
            )
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun refreshAll(): List<RefreshedEpisode> = withContext(Dispatchers.IO) {
        val podcasts = db.podcastsQueries.selectAll().executeAsList()

        logger?.log(LogLevel.INFO, Subsystem.NETWORK,
            LogEvent.FeedRefresh.Started(podcastCount = podcasts.size, isManual = false))

        data class FetchedFeed(
            val podcastId: String,
            val feed: dev.podder.domain.parser.RssFeed,
            val bytesReceived: Long,
            val latencyMs: Long,
        )

        // Limit concurrency to avoid holding all XML bodies in memory at once (OOM risk)
        val semaphore = Semaphore(8)
        val fetched: List<FetchedFeed> = coroutineScope {
            podcasts.map { podcast ->
                async {
                    semaphore.withPermit {
                        logger?.log(LogLevel.DEBUG, Subsystem.NETWORK,
                            LogEvent.FeedRefresh.PodcastFetchStarted(podcast.id, podcast.rssUrl))
                        val start = platformNowUtcEpoch()
                        runCatching {
                            val response = httpClient.get(podcast.rssUrl)
                            val xml = response.bodyAsText()
                            val latencyMs = platformNowUtcEpoch() - start
                            val bytes = xml.length.toLong()
                            logger?.log(LogLevel.INFO, Subsystem.NETWORK,
                                LogEvent.FeedRefresh.PodcastFetchCompleted(podcast.id, bytes, latencyMs))
                            val feed = parseRssFeed(xml)
                            logger?.log(LogLevel.INFO, Subsystem.NETWORK,
                                LogEvent.FeedRefresh.PodcastParsed(podcast.id, feed.items.size, feed.items.size))
                            FetchedFeed(podcast.id, feed, bytes, latencyMs)
                        }.getOrElse { e ->
                            logger?.log(LogLevel.ERROR, Subsystem.NETWORK,
                                LogEvent.FeedRefresh.PodcastFetchFailed(podcast.id, e.message ?: "unknown"))
                            null
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }

        var succeeded = 0
        var failed = podcasts.size - fetched.size
        val totalStart = platformNowUtcEpoch()

        val existingIds = db.episodesQueries.selectAll().executeAsList().map { it.id }.toHashSet()
        val podcastTitleMap = podcasts.associate { it.id to it.title }
        val newEpisodes = mutableListOf<RefreshedEpisode>()

        // Single transaction for the entire refresh batch — SQLDelight emits ONE flow
        // notification on commit instead of one per row, eliminating jank during refresh.
        val dbStart = platformNowUtcEpoch()
        runCatching {
            db.transaction {
                fetched.forEach { (podcastId, feed, _, _) ->
                    val podcastTitle = podcastTitleMap[podcastId] ?: ""
                    feed.items.forEach { item ->
                        val episodeId = item.guid.ifBlank { Uuid.random().toString() }
                        val isNew = episodeId !in existingIds
                        db.episodesQueries.insert(
                            id                 = episodeId,
                            podcastId          = podcastId,
                            title              = item.title,
                            url                = item.url,
                            durationMs         = item.durationMs,
                            playCount          = 0L,
                            isDownloaded       = 0L,
                            publicationDateUtc = item.pubDateUtc,
                            description        = item.description,
                        )
                        if (isNew) {
                            newEpisodes += RefreshedEpisode(
                                episodeId    = episodeId,
                                title        = item.title,
                                podcastId    = podcastId,
                                podcastTitle = podcastTitle,
                                isNew        = true,
                            )
                        }
                    }
                    db.podcastsQueries.updateLastFetched(
                        lastFetchedUtc = platformNowUtcEpoch(),
                        id             = podcastId,
                    )
                    succeeded++
                }
            }
            val dbLatencyMs = platformNowUtcEpoch() - dbStart
            logger?.log(LogLevel.DEBUG, Subsystem.NETWORK,
                LogEvent.FeedRefresh.DbWriteCompleted("batch", fetched.size, dbLatencyMs))
        }.onFailure { e ->
            logger?.log(LogLevel.ERROR, Subsystem.NETWORK,
                LogEvent.FeedRefresh.PodcastFetchFailed("batch-db", e.message ?: "db write failed"))
            failed += fetched.size
        }

        val totalMs = platformNowUtcEpoch() - totalStart
        logger?.log(LogLevel.INFO, Subsystem.NETWORK,
            LogEvent.FeedRefresh.Completed(podcasts.size, succeeded, failed, totalMs))
        newEpisodes
    }

    override suspend fun importFromOpml(opmlContent: String) = withContext(Dispatchers.IO) {
        val urls = parseOpmlUrls(opmlContent)
        val existing = db.podcastsQueries.selectAll().executeAsList()
            .map { it.rssUrl }
            .toSet()
        urls
            .filter { it !in existing }
            .forEach { url ->
                runCatching { subscribeToPodcast(url) }
            }
    }

    override fun podcasts(): Flow<List<PodcastSummary>> =
        db.podcastsQueries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows ->
                rows.map { p ->
                    PodcastSummary(
                        id         = p.id,
                        title      = p.title,
                        rssUrl     = p.rssUrl,
                        artworkUrl = p.artworkUrl,
                    )
                }
            }

    override fun episodes(podcastId: String): Flow<List<EpisodeSummary>> =
        db.episodesQueries.selectByPodcast(podcastId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows ->
                rows.map { e ->
                    EpisodeSummary(
                        id                 = e.id,
                        podcastId          = e.podcastId,
                        title              = e.title,
                        url                = e.url,
                        durationMs         = e.durationMs,
                        playCount          = e.playCount.toInt(),
                        isDownloaded       = e.isDownloaded != 0L,
                        publicationDateUtc = e.publicationDateUtc,
                        description        = e.description,
                    )
                }
            }

    override suspend fun podcastById(id: String): PodcastSummary? = withContext(Dispatchers.IO) {
        db.podcastsQueries.selectById(id).executeAsOneOrNull()?.let { p ->
            PodcastSummary(
                id         = p.id,
                title      = p.title,
                rssUrl     = p.rssUrl,
                artworkUrl = p.artworkUrl,
            )
        }
    }

    override suspend fun episodeById(id: String): EpisodeSummary? = withContext(Dispatchers.IO) {
        db.episodesQueries.selectById(id).executeAsOneOrNull()?.let { e ->
            EpisodeSummary(
                id                 = e.id,
                podcastId          = e.podcastId,
                title              = e.title,
                url                = e.url,
                durationMs         = e.durationMs,
                playCount          = e.playCount.toInt(),
                isDownloaded       = e.isDownloaded != 0L,
                publicationDateUtc = e.publicationDateUtc,
                description        = e.description,
            )
        }
    }

    override suspend fun markEpisodeFinished(episodeId: String) = withContext(Dispatchers.IO) {
        db.episodesQueries.markFinished(platformNowUtcEpoch(), episodeId)
    }

    override suspend fun nextEpisodeInFeed(currentEpisodeId: String): FeedEpisode? =
        withContext(Dispatchers.IO) {
            val all = allEpisodes(500L).first()
            val idx = all.indexOfFirst { it.id == currentEpisodeId }
            if (idx == -1 || idx + 1 >= all.size) null else all[idx + 1]
        }

    override fun allEpisodes(limit: Long): Flow<List<FeedEpisode>> =
        db.episodesQueries.selectAllNewestFirst(limit)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows ->
                rows.map { e ->
                    FeedEpisode(
                        id                 = e.id,
                        podcastId          = e.podcastId,
                        podcastTitle       = e.podcastTitle,
                        artworkUrl         = e.artworkUrl,
                        title              = e.title,
                        url                = e.url,
                        durationMs         = e.durationMs,
                        playCount          = e.playCount.toInt(),
                        publicationDateUtc = e.publicationDateUtc,
                        description        = e.description,
                        isDownloaded       = e.isDownloaded != 0L,
                    )
                }
            }
}
