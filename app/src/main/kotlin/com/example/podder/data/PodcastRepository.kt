package com.example.podder.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.podder.data.local.PodcastDao
import com.example.podder.data.local.SubscriptionDao
import com.example.podder.data.local.SubscriptionEntity
import com.example.podder.data.local.PodcastEntity
import com.example.podder.data.local.EpisodeEntity
import com.example.podder.data.local.EpisodeWithPodcast
import com.example.podder.data.network.PodcastSearchService
import com.example.podder.data.network.SearchResult
import com.example.podder.parser.MyXmlParser
import com.example.podder.parser.Podcast
import com.example.podder.parser.OpmlParser
import com.example.podder.sync.DownloadEpisodeWorker
import com.example.podder.utils.DateUtils
import java.io.InputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET
import retrofit2.http.Url

interface PodcastService {
    @GET
    suspend fun getFeed(@Url url: String): String
}

class PodcastRepository(
    private val podcastDao: PodcastDao,
    private val subscriptionDao: SubscriptionDao,
    private val context: Context? = null,
    private val searchService: PodcastSearchService? = null,
    feedService: PodcastService? = null
) {
    private val service: PodcastService
    private val xmlParser = MyXmlParser()

    init {
        service = feedService ?: run {
            val retrofit = Retrofit.Builder()
                .baseUrl("https://example.com/") // Base URL is ignored for full URL requests
                .addConverterFactory(ScalarsConverterFactory.create())
                .build()
            retrofit.create(PodcastService::class.java)
        }
    }

    val homeFeed: Flow<List<EpisodeWithPodcast>> = podcastDao.getAllEpisodes()

    val subscribedUrls: Flow<List<String>> = subscriptionDao.getSubscribedUrls()

    val subscribedPodcasts: Flow<List<PodcastEntity>> = podcastDao.getSubscribedPodcasts()

    suspend fun importOpml(inputStream: InputStream) = withContext(Dispatchers.IO) {
        val subscriptions = OpmlParser.parse(inputStream)
        subscriptionDao.insertAll(subscriptions)
    }

    suspend fun hasSubscriptions(): Boolean = withContext(Dispatchers.IO) {
        subscriptionDao.getCount() > 0
    }

    suspend fun hasEpisodes(): Boolean = withContext(Dispatchers.IO) {
        podcastDao.getEpisodeCount() > 0
    }

    suspend fun saveProgress(guid: String, progress: Long) = withContext(Dispatchers.IO) {
        podcastDao.updateProgress(guid, progress)
    }

    suspend fun getEpisode(guid: String): EpisodeEntity? = withContext(Dispatchers.IO) {
        podcastDao.getEpisode(guid)
    }

    fun getEpisodesByPodcast(podcastUrl: String): Flow<List<EpisodeWithPodcast>> {
        return podcastDao.getEpisodesByPodcast(podcastUrl)
    }

    suspend fun getPodcast(url: String): PodcastEntity? = withContext(Dispatchers.IO) {
        podcastDao.getPodcast(url)
    }

    suspend fun updatePodcasts() = withContext(Dispatchers.IO) {
        // Always refresh - UI already shows cached data instantly via Room Flow
        forceRefresh()
    }

    suspend fun forceRefresh(): List<EpisodeEntity> = withContext(Dispatchers.IO) {
        val allNewEpisodes = mutableListOf<EpisodeEntity>()

        // Step 1: Get existing progress, finishedAt, and downloads before sync
        val progressList = podcastDao.getAllProgress()
        val progressMap = progressList.associate { it.guid to it.progressInMillis }
        val finishedAtMap = progressList.associate { it.guid to it.finishedAt }
        val downloadMap = podcastDao.getAllDownloads()
            .associate { it.guid to it.localFilePath }

        val urls = subscriptionDao.getAllUrls()
        for (url in urls) {
            try {
                val xml = service.getFeed(url)
                val podcastDto = xmlParser.parse(xml.byteInputStream())

                val podcastEntity = PodcastEntity(url, podcastDto.title, podcastDto.imageUrl)

                // Step 2: Merge progress, finishedAt, and downloads into new episodes
                val episodeEntities = podcastDto.episodes.map {
                    // Use podcast title + episode title hash as fallback to match UI GUID generation
                    val guid = it.guid ?: "${podcastDto.title}-${it.title.hashCode()}"
                    val savedProgress = progressMap[guid] ?: 0L
                    val savedFinishedAt = finishedAtMap[guid]
                    val savedLocalPath = downloadMap[guid]
                    EpisodeEntity(
                        guid = guid,
                        podcastUrl = url,
                        title = it.title,
                        description = it.description ?: "",
                        pubDate = it.pubDate?.let { dateStr -> DateUtils.parseRssDate(dateStr) } ?: System.currentTimeMillis(),
                        audioUrl = it.audioUrl ?: "",
                        duration = it.duration ?: 0L,
                        progressInMillis = savedProgress,
                        localFilePath = savedLocalPath,
                        finishedAt = savedFinishedAt
                    )
                }

                // Step 3: Identify new episodes (not in progressMap AND published within 24 hours)
                val twentyFourHoursAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
                val newEpisodes = episodeEntities.filter { episode ->
                    !progressMap.containsKey(episode.guid) && episode.pubDate > twentyFourHoursAgo
                }
                allNewEpisodes.addAll(newEpisodes)

                // Step 4: Insert or update podcast (IGNORE returns -1 if exists)
                val insertResult = podcastDao.insertPodcast(podcastEntity)
                if (insertResult == -1L) {
                    podcastDao.updatePodcast(url, podcastDto.title, podcastDto.imageUrl)
                }

                // Step 5: Add episodes (REPLACE with merged progress)
                podcastDao.addEpisodes(episodeEntities)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        allNewEpisodes
    }

    suspend fun setLocalFile(guid: String, path: String) = withContext(Dispatchers.IO) {
        podcastDao.updateLocalFile(guid, path)
    }

    suspend fun markAsFinished(guid: String, progressMillis: Long) = withContext(Dispatchers.IO) {
        podcastDao.updateProgress(guid, progressMillis)
        podcastDao.updateFinishedAt(guid, System.currentTimeMillis())
    }

    suspend fun deleteDownload(guid: String, localFilePath: String) = withContext(Dispatchers.IO) {
        java.io.File(localFilePath).delete()
        podcastDao.clearLocalFile(guid)
    }

    suspend fun cleanupExpiredDownloads() = withContext(Dispatchers.IO) {
        val cutoffTime = System.currentTimeMillis() - (24 * 60 * 60 * 1000) // 24 hours ago
        val expiredEpisodes = podcastDao.getExpiredDownloads(cutoffTime)
        for (episode in expiredEpisodes) {
            episode.localFilePath?.let { path ->
                java.io.File(path).delete()
                podcastDao.clearLocalFile(episode.guid)
            }
        }
    }

    fun downloadEpisode(guid: String, url: String, title: String) {
        val ctx = context ?: return

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val inputData = workDataOf(
            DownloadEpisodeWorker.KEY_GUID to guid,
            DownloadEpisodeWorker.KEY_AUDIO_URL to url,
            DownloadEpisodeWorker.KEY_EPISODE_TITLE to title
        )

        val downloadRequest = OneTimeWorkRequestBuilder<DownloadEpisodeWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(ctx).enqueueUniqueWork(
            "download_$guid",
            ExistingWorkPolicy.KEEP,
            downloadRequest
        )
    }

    /**
     * Search for podcasts by query term using iTunes Search API.
     * @param query The search term
     * @return List of SearchResult objects from the API
     */
    suspend fun searchPodcasts(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val service = searchService ?: return@withContext emptyList()
        val term = query.trim()
        if (term.isEmpty()) return@withContext emptyList()

        val response = service.search(term)
        response.results
    }

    /**
     * Subscribe to a podcast by its feed URL.
     * @param feedUrl The RSS feed URL of the podcast
     */
    suspend fun subscribe(feedUrl: String) = withContext(Dispatchers.IO) {
        val subscription = SubscriptionEntity(
            url = feedUrl,
            title = null,
            dateAdded = System.currentTimeMillis()
        )
        subscriptionDao.insert(subscription)
    }

    /**
     * Unsubscribe from a podcast by its feed URL.
     * @param feedUrl The RSS feed URL of the podcast
     */
    suspend fun unsubscribe(feedUrl: String) = withContext(Dispatchers.IO) {
        subscriptionDao.delete(feedUrl)
    }
}
