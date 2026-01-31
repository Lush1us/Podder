package com.example.podder.data

import com.example.podder.data.local.PodcastDao
import com.example.podder.data.local.SubscriptionDao
import com.example.podder.data.local.PodcastEntity
import com.example.podder.data.local.EpisodeEntity
import com.example.podder.data.local.EpisodeWithPodcast
import com.example.podder.parser.MyXmlParser
import com.example.podder.parser.OpmlParser
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
    private val subscriptionDao: SubscriptionDao
) {
    private val service: PodcastService
    private val xmlParser = MyXmlParser()

    init {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://example.com/") // Base URL is ignored for full URL requests
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
        service = retrofit.create(PodcastService::class.java)
    }

    val homeFeed: Flow<List<EpisodeWithPodcast>> = podcastDao.getAllEpisodes()

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
        // Skip network fetch if we already have cached episodes
        if (podcastDao.getEpisodeCount() > 0) return@withContext
        forceRefresh()
    }

    suspend fun forceRefresh() = withContext(Dispatchers.IO) {
        // Step 1: Get existing progress before sync
        val progressMap = podcastDao.getAllProgress()
            .associate { it.guid to it.progressInMillis }

        val urls = subscriptionDao.getAllUrls()
        for (url in urls) {
            try {
                val xml = service.getFeed(url)
                val podcastDto = xmlParser.parse(xml.byteInputStream())

                val podcastEntity = PodcastEntity(url, podcastDto.title, podcastDto.imageUrl)

                // Step 2: Merge progress into new episodes
                val episodeEntities = podcastDto.episodes.map {
                    // Use podcast title + episode title hash as fallback to match UI GUID generation
                    val guid = it.guid ?: "${podcastDto.title}-${it.title.hashCode()}"
                    val savedProgress = progressMap[guid] ?: 0L
                    EpisodeEntity(
                        guid = guid,
                        podcastUrl = url,
                        title = it.title,
                        description = it.description ?: "",
                        pubDate = it.pubDate?.let { dateStr -> DateUtils.parseRssDate(dateStr) } ?: System.currentTimeMillis(),
                        audioUrl = it.audioUrl ?: "",
                        duration = it.duration ?: 0L,
                        progressInMillis = savedProgress
                    )
                }

                // Step 3: Insert or update podcast (IGNORE returns -1 if exists)
                val insertResult = podcastDao.insertPodcast(podcastEntity)
                if (insertResult == -1L) {
                    podcastDao.updatePodcast(url, podcastDto.title, podcastDto.imageUrl)
                }

                // Step 4: Add episodes (REPLACE with merged progress)
                podcastDao.addEpisodes(episodeEntities)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
