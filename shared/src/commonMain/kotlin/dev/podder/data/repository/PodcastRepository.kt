package dev.podder.data.repository

import dev.podder.domain.model.Episode
import dev.podder.domain.model.Playlist
import kotlinx.coroutines.flow.Flow

interface PodcastRepository {
    /** Subscribe to a podcast by its RSS URL. Fetches and stores feed + episodes. */
    suspend fun subscribeToPodcast(rssUrl: String)

    /** Refresh episodes for all subscribed podcasts. */
    suspend fun refreshAll()

    /** Flow of all subscribed podcasts as simple data holders. */
    fun podcasts(): Flow<List<PodcastSummary>>

    /** Flow of episodes for a specific podcast, newest first. */
    fun episodes(podcastId: String): Flow<List<EpisodeSummary>>

    /** Import subscriptions from an OPML file's text content. Skips already-subscribed feeds. */
    suspend fun importFromOpml(opmlContent: String)

    /** Flow of all episodes across all subscribed podcasts, newest first. */
    fun allEpisodes(): Flow<List<FeedEpisode>>

    /** One-shot fetch of a single episode by id, or null if not found. */
    suspend fun episodeById(id: String): EpisodeSummary?

    /** One-shot fetch of a single podcast by id, or null if not found. */
    suspend fun podcastById(id: String): PodcastSummary?
}

data class PodcastSummary(
    val id: String,
    val title: String,
    val rssUrl: String,
    val artworkUrl: String?,
    val episodeCount: Int = 0,
)

data class EpisodeSummary(
    val id: String,
    val podcastId: String,
    val title: String,
    val url: String,
    val durationMs: Long,
    val playCount: Int,
    val isDownloaded: Boolean,
    val publicationDateUtc: Long,
    val description: String,
)

data class FeedEpisode(
    val id: String,
    val podcastId: String,
    val podcastTitle: String,
    val title: String,
    val url: String,
    val durationMs: Long,
    val playCount: Int,
    val publicationDateUtc: Long,
    val description: String,
)
