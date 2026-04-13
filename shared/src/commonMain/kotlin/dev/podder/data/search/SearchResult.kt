package dev.podder.data.search

data class SearchResult(
    val id: String,
    val title: String,
    val author: String,
    val artworkUrl: String,
    val episodeCount: Int,
    val rssUrl: String,
)

fun ItunesPodcastDto.toSearchResultOrNull(): SearchResult? {
    val rss = feedUrl ?: return null
    return SearchResult(
        id           = collectionId?.toString() ?: rss.hashCode().toString(),
        title        = collectionName ?: "Unknown",
        author       = artistName ?: "",
        artworkUrl   = artworkUrl600 ?: "",
        episodeCount = trackCount ?: 0,
        rssUrl       = rss,
    )
}
