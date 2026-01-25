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

    suspend fun updatePodcasts() = withContext(Dispatchers.IO) {
        val urls = subscriptionDao.getAllUrls()
        for (url in urls) {
            try {
                val xml = service.getFeed(url)
                val podcastDto = xmlParser.parse(xml.byteInputStream()) 
                
                val podcastEntity = PodcastEntity(url, podcastDto.title, podcastDto.imageUrl)
                
                val episodeEntities = podcastDto.episodes.map { 
                    EpisodeEntity(
                        guid = it.guid ?: "${url}-${it.title.hashCode()}", 
                        podcastUrl = url, 
                        title = it.title,
                        description = it.description ?: "",
                        // Use DateUtils to convert RSS string to Long timestamp
                        pubDate = it.pubDate?.let { dateStr -> DateUtils.parseRssDate(dateStr) } ?: System.currentTimeMillis(),
                        audioUrl = it.audioUrl ?: "",
                        duration = it.duration?.toString() ?: ""
                    ) 
                }

                podcastDao.insertPodcast(podcastEntity)
                podcastDao.addEpisodes(episodeEntities)
                
            } catch (e: Exception) {
                e.printStackTrace() 
            }
        }
    }
}
