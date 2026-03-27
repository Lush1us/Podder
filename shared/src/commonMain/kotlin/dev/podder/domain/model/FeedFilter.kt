package dev.podder.domain.model

enum class PlayedFilter { ALL, PLAYED, UNPLAYED }
enum class DownloadFilter { ALL, DOWNLOADED }
enum class FeedSort { DATE_DESC, DURATION_ASC, DURATION_DESC, PODCAST_NAME }

data class FeedFilter(
    val played: PlayedFilter = PlayedFilter.ALL,
    val downloaded: DownloadFilter = DownloadFilter.ALL,
    val sort: FeedSort = FeedSort.DATE_DESC,
)
