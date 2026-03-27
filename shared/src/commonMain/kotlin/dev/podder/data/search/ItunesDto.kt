package dev.podder.data.search

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ItunesSearchResponse(
    @SerialName("resultCount") val resultCount: Int,
    @SerialName("results") val results: List<ItunesPodcastDto> = emptyList(),
)

@Serializable
data class ItunesPodcastDto(
    @SerialName("collectionId")   val collectionId: Long?    = null,
    @SerialName("collectionName") val collectionName: String? = null,
    @SerialName("artistName")     val artistName: String?    = null,
    @SerialName("artworkUrl600")  val artworkUrl600: String? = null,
    @SerialName("trackCount")     val trackCount: Int?       = null,
    @SerialName("feedUrl")        val feedUrl: String?       = null,
)
