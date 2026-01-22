package com.example.podder.parser

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("rss")
data class RssFeed(
    @SerialName("channel")
    val channel: Podcast
)

@Serializable
@SerialName("channel")
data class Podcast(
    @SerialName("title")
    val title: String,
    @SerialName("description")
    val description: String? = null,
    @SerialName("item")
    val episodes: List<Episode> = emptyList()
)

@Serializable
@SerialName("item")
data class Episode(
    @SerialName("title")
    val title: String,
    @SerialName("description")
    val description: String? = null,
    @SerialName("pubDate")
    val pubDate: String? = null,
    @SerialName("enclosure")
    val enclosure: Enclosure? = null
)

@Serializable
@SerialName("enclosure")
data class Enclosure(
    @SerialName("url")
    val url: String,
    @SerialName("type")
    val type: String
)