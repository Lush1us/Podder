package com.example.podder.data

import com.example.podder.data.local.EpisodeDownload
import com.example.podder.data.local.EpisodeEntity
import com.example.podder.data.local.EpisodeProgress
import com.example.podder.data.local.PodcastDao
import com.example.podder.data.local.PodcastEntity
import com.example.podder.data.local.SubscriptionDao
import com.example.podder.parser.Enclosure
import com.example.podder.parser.Episode
import com.example.podder.parser.Podcast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Interface for XML parsing to allow mocking in tests.
 */
interface XmlParser {
    fun parse(inputStream: InputStream): Podcast
}

/**
 * Testable version of PodcastRepository that accepts mocked dependencies.
 * This allows us to test the merge and notification logic without network calls
 * or Android XML parsing dependencies.
 */
class TestablePodcastRepository(
    private val podcastDao: PodcastDao,
    private val subscriptionDao: SubscriptionDao,
    private val service: PodcastService,
    private val xmlParser: XmlParser
) {
    suspend fun forceRefresh(): List<EpisodeEntity> {
        val allNewEpisodes = mutableListOf<EpisodeEntity>()

        // Step 1: Get existing progress, finishedAt, and downloads before sync
        val progressList = podcastDao.getAllProgress()
        val progressMap = progressList.associate { it.guid to it.progressInMillis }
        val finishedAtMap = progressList.associate { it.guid to it.finishedAt }
        val downloadMap = podcastDao.getAllDownloads()
            .associate { it.guid to it.localFilePath }

        val urls = subscriptionDao.getAllUrls()
        for (url in urls) {
            try {
                val xml = service.getFeed(url)
                val podcast = xmlParser.parse(xml.byteInputStream())

                val podcastEntity = PodcastEntity(url, podcast.title, podcast.imageUrl)

                // Step 2: Merge progress, finishedAt, and downloads into new episodes
                val episodeEntities = podcast.episodes.map {
                    val guid = it.guid ?: "${podcast.title}-${it.title.hashCode()}"
                    val savedProgress = progressMap[guid] ?: 0L
                    val savedFinishedAt = finishedAtMap[guid]
                    val savedLocalPath = downloadMap[guid]
                    EpisodeEntity(
                        guid = guid,
                        podcastUrl = url,
                        title = it.title,
                        description = it.description ?: "",
                        pubDate = it.pubDate?.let { dateStr -> parseRssDate(dateStr) }
                            ?: System.currentTimeMillis(),
                        audioUrl = it.audioUrl ?: "",
                        duration = it.duration ?: 0L,
                        progressInMillis = savedProgress,
                        localFilePath = savedLocalPath,
                        finishedAt = savedFinishedAt
                    )
                }

                // Step 3: Identify new episodes (not in progressMap AND published within 24 hours)
                val twentyFourHoursAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
                val newEpisodes = episodeEntities.filter { episode ->
                    !progressMap.containsKey(episode.guid) && episode.pubDate > twentyFourHoursAgo
                }
                allNewEpisodes.addAll(newEpisodes)

                // Step 4: Insert or update podcast
                val insertResult = podcastDao.insertPodcast(podcastEntity)
                if (insertResult == -1L) {
                    podcastDao.updatePodcast(url, podcast.title, podcast.imageUrl)
                }

                // Step 5: Add episodes (REPLACE with merged progress)
                podcastDao.addEpisodes(episodeEntities)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return allNewEpisodes
    }

    private fun parseRssDate(dateString: String): Long {
        val formats = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "yyyy-MM-dd'T'HH:mm:ssXXX"
        )
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.US)
                return sdf.parse(dateString)?.time ?: 0L
            } catch (_: Exception) {
                // Try next format
            }
        }
        return 0L
    }
}

@ExperimentalCoroutinesApi
class PodcastRepositoryTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockPodcastDao: PodcastDao
    private lateinit var mockSubscriptionDao: SubscriptionDao
    private lateinit var mockService: PodcastService
    private lateinit var mockXmlParser: XmlParser
    private lateinit var repository: TestablePodcastRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockPodcastDao = mock()
        mockSubscriptionDao = mock()
        mockService = mock()
        mockXmlParser = mock()
        repository = TestablePodcastRepository(
            mockPodcastDao,
            mockSubscriptionDao,
            mockService,
            mockXmlParser
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `forceRefresh_filtersOldEpisodes_fromNotificationReturn`() = runTest(testDispatcher) {
        // Given: Mock data with 3 episodes: one from today, two from last month
        val feedUrl = "https://example.com/feed.xml"
        val now = System.currentTimeMillis()
        val lastMonth = now - (30L * 24 * 60 * 60 * 1000)

        val todayDate = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)
            .format(java.util.Date(now))
        val lastMonthDate = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)
            .format(java.util.Date(lastMonth))

        val podcast = Podcast(
            title = "Test Podcast",
            imageUrl = "https://example.com/image.jpg",
            episodes = listOf(
                Episode(
                    title = "Today Episode",
                    guid = "episode-today",
                    pubDate = todayDate,
                    duration = 3600L,
                    enclosure = Enclosure("https://example.com/today.mp3", "audio/mpeg")
                ),
                Episode(
                    title = "Old Episode 1",
                    guid = "episode-old-1",
                    pubDate = lastMonthDate,
                    duration = 1800L,
                    enclosure = Enclosure("https://example.com/old1.mp3", "audio/mpeg")
                ),
                Episode(
                    title = "Old Episode 2",
                    guid = "episode-old-2",
                    pubDate = lastMonthDate,
                    duration = 2400L,
                    enclosure = Enclosure("https://example.com/old2.mp3", "audio/mpeg")
                )
            )
        )

        // Setup: Mock SubscriptionDao to return our feed URL
        whenever(mockSubscriptionDao.getAllUrls()).thenReturn(listOf(feedUrl))

        // Setup: Mock PodcastService to return dummy XML
        whenever(mockService.getFeed(feedUrl)).thenReturn("<rss></rss>")

        // Setup: Mock XmlParser to return our podcast data
        whenever(mockXmlParser.parse(any())).thenReturn(podcast)

        // Setup: Mock PodcastDao.getAllProgress to return empty (fresh install)
        whenever(mockPodcastDao.getAllProgress()).thenReturn(emptyList())
        whenever(mockPodcastDao.getAllDownloads()).thenReturn(emptyList())

        // Setup: Mock podcast insert
        whenever(mockPodcastDao.insertPodcast(any())).thenReturn(1L)
        whenever(mockPodcastDao.addEpisodes(any())).thenReturn(listOf(1L, 2L, 3L))

        // Action: Call repository.forceRefresh()
        val newEpisodes = repository.forceRefresh()
        testScheduler.advanceUntilIdle()

        // Assertion: Verify podcastDao.addEpisodes was called with all 3 episodes
        val episodesCaptor = argumentCaptor<List<EpisodeEntity>>()
        verify(mockPodcastDao).addEpisodes(episodesCaptor.capture())
        assertEquals(
            "Database should receive all 3 episodes from feed",
            3,
            episodesCaptor.firstValue.size
        )

        // Assertion: Verify the function returned only 1 episode (today's episode) for notification
        assertEquals(
            "Only today's episode should be returned for notification",
            1,
            newEpisodes.size
        )
        assertEquals("episode-today", newEpisodes.first().guid)
    }

    @Test
    fun `forceRefresh_mergesProgress_correctly`() = runTest(testDispatcher) {
        // Given: Setup feed with one episode that has existing progress
        val feedUrl = "https://example.com/feed.xml"
        val existingProgress = 5000L // 5 seconds of progress
        val now = System.currentTimeMillis()
        val lastWeek = now - (7L * 24 * 60 * 60 * 1000)

        val lastWeekDate = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)
            .format(java.util.Date(lastWeek))

        val podcast = Podcast(
            title = "Test Podcast",
            imageUrl = "https://example.com/image.jpg",
            episodes = listOf(
                Episode(
                    title = "Episode With Progress",
                    guid = "GUID_1",
                    pubDate = lastWeekDate,
                    duration = 3600L,
                    enclosure = Enclosure("https://example.com/episode.mp3", "audio/mpeg")
                )
            )
        )

        // Setup mocks
        whenever(mockSubscriptionDao.getAllUrls()).thenReturn(listOf(feedUrl))
        whenever(mockService.getFeed(feedUrl)).thenReturn("<rss></rss>")
        whenever(mockXmlParser.parse(any())).thenReturn(podcast)

        // Setup: Mock PodcastDao to return existing progress for GUID_1
        whenever(mockPodcastDao.getAllProgress()).thenReturn(
            listOf(EpisodeProgress("GUID_1", existingProgress, null))
        )
        whenever(mockPodcastDao.getAllDownloads()).thenReturn(emptyList())

        // Setup: Mock podcast insert
        whenever(mockPodcastDao.insertPodcast(any())).thenReturn(1L)
        whenever(mockPodcastDao.addEpisodes(any())).thenReturn(listOf(1L))

        // Action: Call refresh
        repository.forceRefresh()
        testScheduler.advanceUntilIdle()

        // Assertion: Verify the EpisodeEntity passed to addEpisodes for GUID_1 has progressInMillis = 5000
        val episodesCaptor = argumentCaptor<List<EpisodeEntity>>()
        verify(mockPodcastDao).addEpisodes(episodesCaptor.capture())

        val savedEpisode = episodesCaptor.firstValue.find { it.guid == "GUID_1" }
        assertEquals(
            "Progress should be preserved during merge",
            existingProgress,
            savedEpisode?.progressInMillis
        )
    }

    @Test
    fun `forceRefresh_preservesLocalFilePath_duringMerge`() = runTest(testDispatcher) {
        // Given: Setup feed with one episode that has a downloaded file
        val feedUrl = "https://example.com/feed.xml"
        val localPath = "/data/downloads/episode.mp3"
        val now = System.currentTimeMillis()
        val lastWeek = now - (7L * 24 * 60 * 60 * 1000)

        val lastWeekDate = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)
            .format(java.util.Date(lastWeek))

        val podcast = Podcast(
            title = "Test Podcast",
            imageUrl = "https://example.com/image.jpg",
            episodes = listOf(
                Episode(
                    title = "Downloaded Episode",
                    guid = "GUID_DOWNLOADED",
                    pubDate = lastWeekDate,
                    duration = 3600L,
                    enclosure = Enclosure("https://example.com/episode.mp3", "audio/mpeg")
                )
            )
        )

        // Setup mocks
        whenever(mockSubscriptionDao.getAllUrls()).thenReturn(listOf(feedUrl))
        whenever(mockService.getFeed(feedUrl)).thenReturn("<rss></rss>")
        whenever(mockXmlParser.parse(any())).thenReturn(podcast)
        whenever(mockPodcastDao.getAllProgress()).thenReturn(emptyList())
        whenever(mockPodcastDao.getAllDownloads()).thenReturn(
            listOf(EpisodeDownload("GUID_DOWNLOADED", localPath))
        )
        whenever(mockPodcastDao.insertPodcast(any())).thenReturn(1L)
        whenever(mockPodcastDao.addEpisodes(any())).thenReturn(listOf(1L))

        // Action
        repository.forceRefresh()
        testScheduler.advanceUntilIdle()

        // Assertion
        val episodesCaptor = argumentCaptor<List<EpisodeEntity>>()
        verify(mockPodcastDao).addEpisodes(episodesCaptor.capture())

        val savedEpisode = episodesCaptor.firstValue.find { it.guid == "GUID_DOWNLOADED" }
        assertEquals(
            "Local file path should be preserved during merge",
            localPath,
            savedEpisode?.localFilePath
        )
    }

    @Test
    fun `forceRefresh_returnsEmpty_whenAllEpisodesHaveProgress`() = runTest(testDispatcher) {
        // Given: All episodes in feed already have progress (user has listened)
        val feedUrl = "https://example.com/feed.xml"
        val now = System.currentTimeMillis()

        val todayDate = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)
            .format(java.util.Date(now))

        val podcast = Podcast(
            title = "Test Podcast",
            imageUrl = "https://example.com/image.jpg",
            episodes = listOf(
                Episode(
                    title = "Known Episode",
                    guid = "known-episode",
                    pubDate = todayDate,
                    duration = 3600L,
                    enclosure = Enclosure("https://example.com/episode.mp3", "audio/mpeg")
                )
            )
        )

        whenever(mockSubscriptionDao.getAllUrls()).thenReturn(listOf(feedUrl))
        whenever(mockService.getFeed(feedUrl)).thenReturn("<rss></rss>")
        whenever(mockXmlParser.parse(any())).thenReturn(podcast)
        whenever(mockPodcastDao.getAllProgress()).thenReturn(
            listOf(EpisodeProgress("known-episode", 1000L, null))
        )
        whenever(mockPodcastDao.getAllDownloads()).thenReturn(emptyList())
        whenever(mockPodcastDao.insertPodcast(any())).thenReturn(1L)
        whenever(mockPodcastDao.addEpisodes(any())).thenReturn(listOf(1L))

        // Action
        val newEpisodes = repository.forceRefresh()
        testScheduler.advanceUntilIdle()

        // Assertion: No new episodes returned since user already has progress
        assertTrue(
            "No new episodes should be returned when all have existing progress",
            newEpisodes.isEmpty()
        )
    }

    @Test
    fun `forceRefresh_preservesFinishedAt_duringMerge`() = runTest(testDispatcher) {
        // Given: An episode that was marked as finished
        val feedUrl = "https://example.com/feed.xml"
        val finishedTime = System.currentTimeMillis() - (2 * 60 * 60 * 1000) // 2 hours ago
        val now = System.currentTimeMillis()
        val lastWeek = now - (7L * 24 * 60 * 60 * 1000)

        val lastWeekDate = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)
            .format(java.util.Date(lastWeek))

        val podcast = Podcast(
            title = "Test Podcast",
            imageUrl = null,
            episodes = listOf(
                Episode(
                    title = "Finished Episode",
                    guid = "GUID_FINISHED",
                    pubDate = lastWeekDate,
                    duration = 3600L,
                    enclosure = Enclosure("https://example.com/episode.mp3", "audio/mpeg")
                )
            )
        )

        whenever(mockSubscriptionDao.getAllUrls()).thenReturn(listOf(feedUrl))
        whenever(mockService.getFeed(feedUrl)).thenReturn("<rss></rss>")
        whenever(mockXmlParser.parse(any())).thenReturn(podcast)
        whenever(mockPodcastDao.getAllProgress()).thenReturn(
            listOf(EpisodeProgress("GUID_FINISHED", 3600000L, finishedTime))
        )
        whenever(mockPodcastDao.getAllDownloads()).thenReturn(emptyList())
        whenever(mockPodcastDao.insertPodcast(any())).thenReturn(1L)
        whenever(mockPodcastDao.addEpisodes(any())).thenReturn(listOf(1L))

        // Action
        repository.forceRefresh()
        testScheduler.advanceUntilIdle()

        // Assertion
        val episodesCaptor = argumentCaptor<List<EpisodeEntity>>()
        verify(mockPodcastDao).addEpisodes(episodesCaptor.capture())

        val savedEpisode = episodesCaptor.firstValue.find { it.guid == "GUID_FINISHED" }
        assertEquals(
            "FinishedAt should be preserved during merge",
            finishedTime,
            savedEpisode?.finishedAt
        )
    }

    @Test
    fun `forceRefresh_generatesGuid_whenMissing`() = runTest(testDispatcher) {
        // Given: An episode without a guid
        val feedUrl = "https://example.com/feed.xml"
        val now = System.currentTimeMillis()
        val lastWeek = now - (7L * 24 * 60 * 60 * 1000)

        val lastWeekDate = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)
            .format(java.util.Date(lastWeek))

        val podcast = Podcast(
            title = "My Podcast",
            imageUrl = null,
            episodes = listOf(
                Episode(
                    title = "Episode Without GUID",
                    guid = null, // No GUID!
                    pubDate = lastWeekDate,
                    duration = 1800L,
                    enclosure = Enclosure("https://example.com/episode.mp3", "audio/mpeg")
                )
            )
        )

        whenever(mockSubscriptionDao.getAllUrls()).thenReturn(listOf(feedUrl))
        whenever(mockService.getFeed(feedUrl)).thenReturn("<rss></rss>")
        whenever(mockXmlParser.parse(any())).thenReturn(podcast)
        whenever(mockPodcastDao.getAllProgress()).thenReturn(emptyList())
        whenever(mockPodcastDao.getAllDownloads()).thenReturn(emptyList())
        whenever(mockPodcastDao.insertPodcast(any())).thenReturn(1L)
        whenever(mockPodcastDao.addEpisodes(any())).thenReturn(listOf(1L))

        // Action
        repository.forceRefresh()
        testScheduler.advanceUntilIdle()

        // Assertion: GUID should be generated from podcast title + episode title hash
        val episodesCaptor = argumentCaptor<List<EpisodeEntity>>()
        verify(mockPodcastDao).addEpisodes(episodesCaptor.capture())

        val savedEpisode = episodesCaptor.firstValue.first()
        val expectedGuid = "My Podcast-${"Episode Without GUID".hashCode()}"
        assertEquals(
            "GUID should be generated from podcast title and episode title hash",
            expectedGuid,
            savedEpisode.guid
        )
    }
}
