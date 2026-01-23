package com.example.podder.data

import com.example.podder.parser.Podcast
import com.example.podder.parser.MyXmlParser
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET
import retrofit2.http.Url
import kotlinx.serialization.decodeFromString
import java.io.ByteArrayInputStream

interface PodcastService {
    @GET
    suspend fun getFeed(@Url url: String): String
}

class PodcastRepository {
    private val service: PodcastService
    private val xmlParser = MyXmlParser()

    init {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://example.com/")
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()

        service = retrofit.create(PodcastService::class.java)
    }

    suspend fun getPodcast(feedUrl: String): Result<Podcast> {
        return try {
            val xmlString = service.getFeed(feedUrl)
            println("XML String: $xmlString")
            val podcast = xml.decodeFromString<RssFeed>(xmlString).channel
            Result.success(podcast)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}