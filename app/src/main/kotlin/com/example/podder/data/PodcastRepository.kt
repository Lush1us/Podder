package com.example.podder.data

import com.example.podder.parser.Podcast
import com.example.podder.parser.RssFeed
import nl.adaptivity.xmlutil.serialization.XML
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET
import retrofit2.http.Url
import kotlinx.serialization.decodeFromString

interface PodcastService {
    @GET
    suspend fun getFeed(@Url url: String): String
}

class PodcastRepository {
    private val service: PodcastService

    private val xml = XML {
        defaultPolicy {
            ignoreUnknownChildren()
        }
    }

    init {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://example.com/")
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()

        service = retrofit.create(PodcastService::class.java)
    }

    suspend fun getPodcast(feedUrl: String): Podcast? {
        return try {
            val xmlString = service.getFeed(feedUrl)
            println("XML String: $xmlString")
            xml.decodeFromString<RssFeed>(xmlString).channel
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}