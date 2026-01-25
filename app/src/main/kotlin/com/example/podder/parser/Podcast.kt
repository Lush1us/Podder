package com.example.podder.parser

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("channel")
data class Podcast(
    @SerialName("title")
    val title: String = "awesome podcast",
    @SerialName("description")
    val description: String? = null,
    @SerialName("imageUrl")
    val imageUrl: String? = null, // Added imageUrl
    @SerialName("item")
    val episodes: List<Episode> = emptyList()
)

@Serializable
@SerialName("item")
data class Episode(
    @SerialName("title")
    val title: String = "awesome title",
    @SerialName("description")
    val description: String? = null,
    @SerialName("pubDate")
    val pubDate: String? = null,
    @SerialName("guid")
    val guid: String? = null, // Added guid
    @SerialName("duration")
    val duration: Long? = null, // Added duration
    @SerialName("enclosure")
    val enclosure: Enclosure? = null
) {
    val audioUrl: String?
        get() = enclosure?.url
}

@Serializable
@SerialName("enclosure")
data class Enclosure(
    @SerialName("url")
    val url: String,
    @SerialName("type")
    val type: String
)