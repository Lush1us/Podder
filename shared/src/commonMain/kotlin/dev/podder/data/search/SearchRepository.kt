package dev.podder.data.search

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

private const val ITUNES_SEARCH_URL = "https://itunes.apple.com/search"
private const val PAGE_SIZE = 40

interface SearchRepository {
    suspend fun search(query: String): List<SearchResult>
    suspend fun loadNextPage(): List<SearchResult>
    fun getCachedResults(): List<SearchResult>
    fun clearCache()
}

class SearchRepositoryImpl(private val httpClient: HttpClient) : SearchRepository {

    private val mutex = Mutex()
    private var currentQuery: String? = null
    private val cachedResults: MutableList<SearchResult> = mutableListOf()
    private var currentOffset: Int = 0
    private var isExhausted: Boolean = false

    override suspend fun search(query: String): List<SearchResult> = mutex.withLock {
        if (query == currentQuery) return@withLock cachedResults.toList()
        currentQuery = query
        cachedResults.clear()
        currentOffset = 0
        isExhausted = false
        fetchPage(query, 0)
        cachedResults.toList()
    }

    override suspend fun loadNextPage(): List<SearchResult> = mutex.withLock {
        val query = currentQuery
        if (isExhausted || query == null) return@withLock cachedResults.toList()
        fetchPage(query, currentOffset)
        cachedResults.toList()
    }

    override fun getCachedResults(): List<SearchResult> = cachedResults.toList()

    override fun clearCache() {
        cachedResults.clear()
        currentQuery = null
        currentOffset = 0
        isExhausted = false
    }

    private suspend fun fetchPage(query: String, offset: Int) {
        try {
            val text = httpClient.get(ITUNES_SEARCH_URL) {
                parameter("term", query)
                parameter("entity", "podcast")
                parameter("limit", PAGE_SIZE)
                parameter("offset", offset)
            }.bodyAsText()
            val response = json.decodeFromString<ItunesSearchResponse>(text)
            val results = response.results.mapNotNull { it.toSearchResultOrNull() }
            cachedResults.addAll(results)
            currentOffset += PAGE_SIZE
            isExhausted = results.size < PAGE_SIZE
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e
        }
    }
}
