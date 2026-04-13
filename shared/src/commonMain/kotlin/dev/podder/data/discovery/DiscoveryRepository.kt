package dev.podder.data.discovery

import dev.podder.data.search.SearchRepository
import dev.podder.data.search.SearchResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface DiscoveryRepository {
    suspend fun trending(): List<SearchResult>
    suspend fun categories(): List<DiscoveryCategory>
    suspend fun podcastsByCategory(categoryId: Int): List<SearchResult>
    suspend fun mergedSearch(query: String): List<SearchResult>
}

private const val TRENDING_TTL_MS    = 30 * 60 * 1000L
private const val CATEGORIES_TTL_MS  = 30 * 60 * 1000L
private const val CATEGORY_TTL_MS    = 15 * 60 * 1000L

class DiscoveryRepositoryImpl(
    private val podcastIndexApi: PodcastIndexApi,
    private val searchRepository: SearchRepository,
) : DiscoveryRepository {

    private val mutex = Mutex()

    private var cachedTrending: List<SearchResult>? = null
    private var trendingFetchedAt: Long = 0L

    private var cachedCategories: List<DiscoveryCategory>? = null
    private var categoriesFetchedAt: Long = 0L

    private val categoryCache = mutableMapOf<Int, Pair<Long, List<SearchResult>>>()

    override suspend fun trending(): List<SearchResult> = mutex.withLock {
        val now = currentEpochMs()
        val cached = cachedTrending
        if (cached != null && now - trendingFetchedAt < TRENDING_TTL_MS) return@withLock cached
        val fresh = podcastIndexApi.trending()
        cachedTrending = fresh
        trendingFetchedAt = now
        fresh
    }

    override suspend fun categories(): List<DiscoveryCategory> = mutex.withLock {
        val now = currentEpochMs()
        val cached = cachedCategories
        if (cached != null && now - categoriesFetchedAt < CATEGORIES_TTL_MS) return@withLock cached
        val fresh = podcastIndexApi.categories()
        cachedCategories = fresh
        categoriesFetchedAt = now
        fresh
    }

    override suspend fun podcastsByCategory(categoryId: Int): List<SearchResult> = mutex.withLock {
        val now = currentEpochMs()
        val entry = categoryCache[categoryId]
        if (entry != null && now - entry.first < CATEGORY_TTL_MS) return@withLock entry.second
        val fresh = podcastIndexApi.byCategory(categoryId)
        categoryCache[categoryId] = now to fresh
        fresh
    }

    override suspend fun mergedSearch(query: String): List<SearchResult> = coroutineScope {
        val itunesDeferred = async { runCatching { searchRepository.search(query) }.getOrElse { emptyList() } }
        val indexDeferred  = async { runCatching { podcastIndexApi.search(query) }.getOrElse { emptyList() } }
        val itunesResults = itunesDeferred.await()
        val indexResults  = indexDeferred.await()
        mergeAndDedup(itunesResults, indexResults)
    }

    private fun mergeAndDedup(
        primary: List<SearchResult>,
        secondary: List<SearchResult>,
    ): List<SearchResult> {
        val seen = LinkedHashSet<String>()
        val result = mutableListOf<SearchResult>()
        for (item in primary) {
            val key = normalizeRssUrl(item.rssUrl)
            if (seen.add(key)) result.add(item)
        }
        for (item in secondary) {
            val key = normalizeRssUrl(item.rssUrl)
            if (seen.add(key)) result.add(item)
        }
        return result
    }

    private fun normalizeRssUrl(url: String): String =
        url.lowercase().removePrefix("https://").removePrefix("http://").trimEnd('/')
}

private fun currentEpochMs(): Long = currentEpochSeconds() * 1000L
