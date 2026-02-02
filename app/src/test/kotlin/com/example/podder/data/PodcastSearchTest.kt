package com.example.podder.data

import com.example.podder.data.local.PodcastDao
import com.example.podder.data.local.SubscriptionDao
import com.example.podder.data.network.PodcastSearchService
import com.example.podder.data.network.SearchResponse
import com.example.podder.data.network.SearchResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * TDD Tests for Podcast Search Feature.
 *
 * Tests the searchPodcasts() function behavior:
 * - searchPodcasts(query) calls PodcastSearchService.search(query)
 * - Returns SearchResult objects from the API
 * - Returns empty list when no results found
 * - Handles null values gracefully
 */
@ExperimentalCoroutinesApi
class PodcastSearchTest {

    private lateinit var mockPodcastDao: PodcastDao
    private lateinit var mockSubscriptionDao: SubscriptionDao
    private lateinit var mockSearchService: PodcastSearchService
    private lateinit var repository: PodcastRepository

    @Before
    fun setUp() {
        mockPodcastDao = mock()
        mockSubscriptionDao = mock()
        mockSearchService = mock()

        repository = PodcastRepository(
            podcastDao = mockPodcastDao,
            subscriptionDao = mockSubscriptionDao,
            context = null,
            searchService = mockSearchService
        )
    }

    @Test
    fun `searchPodcasts returns mapped podcasts from search API`() = runTest {
        // Given: Mock search service returns a list of search results
        val searchResults = listOf(
            SearchResult(
                collectionId = 12345L,
                collectionName = "Kotlin Weekly Podcast",
                artistName = "Kotlin Foundation",
                artworkUrl = "https://example.com/kotlin-art.jpg",
                feedUrl = "https://feeds.example.com/kotlin-weekly",
                genre = "Technology"
            ),
            SearchResult(
                collectionId = 67890L,
                collectionName = "Android Developers Backstage",
                artistName = "Android Team",
                artworkUrl = "https://example.com/android-art.jpg",
                feedUrl = "https://feeds.example.com/android-backstage",
                genre = "Technology"
            )
        )
        val searchResponse = SearchResponse(resultCount = 2, results = searchResults)
        whenever(mockSearchService.search("Kotlin")).thenReturn(searchResponse)

        // When: Search for podcasts with query "Kotlin"
        val result = repository.searchPodcasts("Kotlin")

        // Then: Repository returns SearchResult objects
        assertEquals(2, result.size)
        assertEquals("Kotlin Weekly Podcast", result[0].collectionName)
        assertEquals("Android Developers Backstage", result[1].collectionName)
    }

    @Test
    fun `searchPodcasts returns empty list when no results found`() = runTest {
        // Given: Mock search service returns empty results
        val emptyResponse = SearchResponse(resultCount = 0, results = emptyList())
        whenever(mockSearchService.search("NonExistentPodcast")).thenReturn(emptyResponse)

        // When: Search for a podcast that doesn't exist
        val result = repository.searchPodcasts("NonExistentPodcast")

        // Then: Repository returns empty list
        assertTrue(result.isEmpty())
    }

    @Test
    fun `searchPodcasts maps artwork URL to imageUrl`() = runTest {
        // Given: Mock search service returns result with artwork
        val searchResults = listOf(
            SearchResult(
                collectionId = 11111L,
                collectionName = "Test Podcast",
                artistName = "Test Artist",
                artworkUrl = "https://example.com/large-artwork.jpg",
                feedUrl = "https://feeds.example.com/test",
                genre = "Comedy"
            )
        )
        val searchResponse = SearchResponse(resultCount = 1, results = searchResults)
        whenever(mockSearchService.search("test")).thenReturn(searchResponse)

        // When: Search for podcast
        val result = repository.searchPodcasts("test")

        // Then: Artwork URL is returned in result
        assertEquals(1, result.size)
        assertEquals("https://example.com/large-artwork.jpg", result[0].artworkUrl)
    }

    @Test
    fun `searchPodcasts handles null feedUrl gracefully`() = runTest {
        // Given: Mock search service returns result with null feedUrl
        val searchResults = listOf(
            SearchResult(
                collectionId = 22222L,
                collectionName = "Podcast Without Feed",
                artistName = "Unknown Artist",
                artworkUrl = null,
                feedUrl = null,
                genre = null
            )
        )
        val searchResponse = SearchResponse(resultCount = 1, results = searchResults)
        whenever(mockSearchService.search("incomplete")).thenReturn(searchResponse)

        // When: Search for podcast
        val result = repository.searchPodcasts("incomplete")

        // Then: Result is returned with null values handled
        assertEquals(1, result.size)
        assertEquals("Podcast Without Feed", result[0].collectionName)
    }

    @Test
    fun `searchPodcasts trims whitespace from query`() = runTest {
        // Given: Mock search service set up for trimmed query
        val searchResults = listOf(
            SearchResult(
                collectionId = 33333L,
                collectionName = "Trimmed Search Result",
                artistName = "Trimmer",
                artworkUrl = null,
                feedUrl = "https://feeds.example.com/trimmed",
                genre = "Education"
            )
        )
        val searchResponse = SearchResponse(resultCount = 1, results = searchResults)
        whenever(mockSearchService.search("android")).thenReturn(searchResponse)

        // When: Search with whitespace-padded query
        val result = repository.searchPodcasts("  android  ")

        // Then: Whitespace is trimmed and search succeeds
        assertEquals(1, result.size)
    }
}
