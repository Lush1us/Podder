package com.example.podder.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for podcast search API.
 * Used to discover new podcasts from external search services.
 */
interface PodcastSearchService {
    @GET("search?media=podcast&entity=podcast")
    suspend fun search(@Query("term") term: String): SearchResponse
}

/**
 * Response wrapper from the podcast search API.
 */
@Serializable
data class SearchResponse(
    @SerialName("resultCount")
    val resultCount: Int = 0,
    @SerialName("results")
    val results: List<SearchResult> = emptyList()
)

/**
 * Individual podcast search result from the API.
 */
@Serializable
data class SearchResult(
    @SerialName("collectionId")
    val collectionId: Long,
    @SerialName("collectionName")
    val collectionName: String,
    @SerialName("artistName")
    val artistName: String? = null,
    @SerialName("artworkUrl100")
    val artworkUrl: String? = null,
    @SerialName("feedUrl")
    val feedUrl: String? = null,
    @SerialName("primaryGenreName")
    val genre: String? = null
)
