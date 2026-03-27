package dev.podder.data.discovery

import dev.podder.data.search.SearchResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class PodcastIndexTrendingResponse(
    @SerialName("feeds") val feeds: List<PodcastIndexFeedDto> = emptyList(),
)

@Serializable
internal data class PodcastIndexSearchResponse(
    @SerialName("feeds") val feeds: List<PodcastIndexFeedDto> = emptyList(),
)

@Serializable
internal data class PodcastIndexCategoriesResponse(
    @SerialName("feeds") val feeds: List<PodcastIndexCategoryDto> = emptyList(),
)

@Serializable
internal data class PodcastIndexCategoryDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("name") val name: String = "",
)

@Serializable
internal data class PodcastIndexFeedDto(
    @SerialName("id")           val id: Long?               = null,
    @SerialName("title")        val title: String?          = null,
    @SerialName("author")       val author: String?         = null,
    @SerialName("image")        val image: String?          = null,
    @SerialName("episodeCount") val episodeCount: Int?      = null,
    @SerialName("url")          val url: String?            = null,
    @SerialName("categories")   val categories: Map<String, String>? = null,
    @SerialName("language")     val language: String?       = null,
    @SerialName("description")  val description: String?    = null,
)

internal fun PodcastIndexFeedDto.toSearchResultOrNull(): SearchResult? {
    val rss = url ?: return null
    return SearchResult(
        id           = id?.toString() ?: rss.hashCode().toString(),
        title        = title ?: "Unknown",
        author       = author ?: "",
        artworkUrl   = image ?: "",
        episodeCount = episodeCount ?: 0,
        rssUrl       = rss,
    )
}
