package dev.podder.domain.parser

data class RssFeed(
    val title: String,
    val description: String,
    val imageUrl: String?,
    val items: List<RssItem>,
)

data class RssItem(
    val guid: String,
    val title: String,
    val url: String,           // enclosure url
    val description: String,
    val pubDateUtc: Long,      // epoch seconds
    val durationMs: Long,      // from itunes:duration, converted to ms
)
