package dev.podder.data.repository

import dev.podder.db.PodderDatabase
import dev.podder.domain.parser.parseOpmlUrls
import dev.podder.domain.parser.parseRssFeed
import dev.podder.util.time.platformNowUtcEpoch
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class PodcastRepositoryImpl(
    private val db: PodderDatabase,
    private val httpClient: HttpClient,
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
    override suspend fun refreshAll() = withContext(Dispatchers.IO) {
        val podcasts = db.podcastsQueries.selectAll().executeAsList()
        podcasts.forEach { podcast ->
            runCatching {
                val xml  = httpClient.get(podcast.rssUrl).bodyAsText()
                val feed = parseRssFeed(xml)
                feed.items.forEach { item ->
                    db.episodesQueries.insert(
                        id                 = item.guid.ifBlank { Uuid.random().toString() },
                        podcastId          = podcast.id,
                        title              = item.title,
                        url                = item.url,
                        durationMs         = item.durationMs,
                        playCount          = 0L,
                        isDownloaded       = 0L,
                        publicationDateUtc = item.pubDateUtc,
                        description        = item.description,
                    )
                }
                db.podcastsQueries.updateLastFetched(
                    lastFetchedUtc = platformNowUtcEpoch(),
                    id             = podcast.id,
                )
            }
        }
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

    override fun allEpisodes(): Flow<List<FeedEpisode>> =
        db.episodesQueries.selectAllNewestFirst()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows ->
                rows.map { e ->
                    FeedEpisode(
                        id                 = e.id,
                        podcastId          = e.podcastId,
                        podcastTitle       = e.podcastTitle,
                        title              = e.title,
                        url                = e.url,
                        durationMs         = e.durationMs,
                        playCount          = e.playCount.toInt(),
                        publicationDateUtc = e.publicationDateUtc,
                        description        = e.description,
                    )
                }
            }
}
