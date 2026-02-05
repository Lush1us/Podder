package com.example.podder.ui.screens

import com.example.podder.core.PodcastAction
import com.example.podder.data.PodcastRepository
import com.example.podder.data.local.EpisodeEntity
import com.example.podder.data.local.EpisodeWithPodcast
import com.example.podder.data.local.PodcastEntity
import com.example.podder.player.PlayerController
import com.example.podder.player.PlayerUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class PodcastViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockPodcastRepository: PodcastRepository
    private lateinit var mockPlayerController: PlayerController
    private lateinit var viewModel: PodcastViewModel
    private val playerUiStateFlow = MutableStateFlow(PlayerUiState())

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockPodcastRepository = mock()
        mockPlayerController = mock()

        // Mock the playerUiState flow
        whenever(mockPlayerController.playerUiState).thenReturn(playerUiStateFlow)
        whenever(mockPodcastRepository.homeFeed).thenReturn(flowOf(emptyList()))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): PodcastViewModel {
        return PodcastViewModel(mockPodcastRepository, mockPlayerController)
    }

    @Test
    fun `init updates uiState to Success on successful feed emission`() = runTest(testDispatcher) {
        // Given
        val dummyPodcast = PodcastEntity("url1", "Podcast Title", "image1")
        val dummyEpisode = EpisodeEntity(
            guid = "guid1",
            podcastUrl = "url1",
            title = "Episode Title",
            description = "Description",
            pubDate = 123L,
            audioUrl = "audio1",
            duration = 100L,
            progressInMillis = 0L
        )
        val dummyFeed = listOf(EpisodeWithPodcast(dummyEpisode, dummyPodcast))

        whenever(mockPodcastRepository.homeFeed).thenReturn(flowOf(dummyFeed))

        // When
        viewModel = createViewModel()

        // Start collecting the StateFlow to trigger subscription
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        testScheduler.advanceUntilIdle()

        // Then
        val uiState = viewModel.uiState.value
        assertTrue("Expected Success state but got ${uiState::class.simpleName}", uiState is HomeUiState.Success)
        if (uiState is HomeUiState.Success) {
            assertEquals(dummyFeed, uiState.feed)
        }

        collectJob.cancel()
    }

    @Test
    fun `FetchPodcasts action calls updatePodcasts on repository`() = runTest(testDispatcher) {
        // Given
        viewModel = createViewModel()

        // When
        viewModel.process(PodcastAction.FetchPodcasts(source = "test", timestamp = System.currentTimeMillis()))
        testScheduler.advanceUntilIdle()

        // Then
        verify(mockPodcastRepository).updatePodcasts()
    }

    @Test
    fun `process_Play_usesLocalFile_ifAvailable`() = runTest(testDispatcher) {
        // Given: An episode with localFilePath set
        val guid = "local-file-guid"
        val localFilePath = "/data/data/com.example.podder/files/episode.mp3"
        val networkUrl = "https://example.com/episode.mp3"

        val episodeWithLocalFile = EpisodeEntity(
            guid = guid,
            podcastUrl = "https://example.com/feed",
            title = "Test Episode",
            description = "Description",
            pubDate = System.currentTimeMillis(),
            audioUrl = networkUrl,
            duration = 3600L,
            progressInMillis = 1000L,
            localFilePath = localFilePath
        )

        // Setup: Mock Repository to return an Episode where localFilePath is set
        whenever(mockPodcastRepository.getEpisode(guid)).thenReturn(episodeWithLocalFile)

        viewModel = createViewModel()

        // When: Play action is triggered
        viewModel.process(
            PodcastAction.Play(
                guid = guid,
                url = networkUrl,
                title = "Test Episode",
                artist = "Test Artist",
                imageUrl = "https://example.com/image.jpg",
                description = "Description",
                podcastUrl = "https://example.com/feed",
                source = "test",
                timestamp = System.currentTimeMillis()
            )
        )
        testScheduler.advanceUntilIdle()

        // Then: Verify PlayerController.play was called with a Uri starting with file://
        val urlCaptor = argumentCaptor<String>()
        verify(mockPlayerController).play(
            guid = eq(guid),
            url = urlCaptor.capture(),
            title = any(),
            artist = any(),
            imageUrl = anyOrNull(),
            description = anyOrNull(),
            podcastUrl = anyOrNull(),
            startPosition = any()
        )

        assertTrue(
            "Play URL should start with file:// when local file exists",
            urlCaptor.firstValue.startsWith("file://")
        )
        assertTrue(
            "Play URL should contain the local file path",
            urlCaptor.firstValue.contains(localFilePath)
        )
    }

    @Test
    fun `process_Play_usesNetworkUrl_ifNoLocalFile`() = runTest(testDispatcher) {
        // Given: An episode without localFilePath
        val guid = "network-guid"
        val networkUrl = "https://example.com/episode.mp3"

        val episodeWithoutLocalFile = EpisodeEntity(
            guid = guid,
            podcastUrl = "https://example.com/feed",
            title = "Test Episode",
            description = "Description",
            pubDate = System.currentTimeMillis(),
            audioUrl = networkUrl,
            duration = 3600L,
            progressInMillis = 0L,
            localFilePath = null // No local file
        )

        whenever(mockPodcastRepository.getEpisode(guid)).thenReturn(episodeWithoutLocalFile)

        viewModel = createViewModel()

        // When: Play action is triggered
        viewModel.process(
            PodcastAction.Play(
                guid = guid,
                url = networkUrl,
                title = "Test Episode",
                artist = "Test Artist",
                imageUrl = null,
                description = null,
                podcastUrl = "https://example.com/feed",
                source = "test",
                timestamp = System.currentTimeMillis()
            )
        )
        testScheduler.advanceUntilIdle()

        // Then: Verify PlayerController.play was called with the network URL
        val urlCaptor = argumentCaptor<String>()
        verify(mockPlayerController).play(
            guid = eq(guid),
            url = urlCaptor.capture(),
            title = any(),
            artist = any(),
            imageUrl = anyOrNull(),
            description = anyOrNull(),
            podcastUrl = anyOrNull(),
            startPosition = any()
        )

        assertEquals(
            "Play URL should be the network URL when no local file exists",
            networkUrl,
            urlCaptor.firstValue
        )
    }

    @Test
    fun `process_Play_resumesFromSavedProgress`() = runTest(testDispatcher) {
        // Given: An episode with saved progress
        val guid = "progress-guid"
        val savedProgress = 120000L // 2 minutes

        val episodeWithProgress = EpisodeEntity(
            guid = guid,
            podcastUrl = "https://example.com/feed",
            title = "Test Episode",
            description = "Description",
            pubDate = System.currentTimeMillis(),
            audioUrl = "https://example.com/episode.mp3",
            duration = 3600L,
            progressInMillis = savedProgress,
            localFilePath = null
        )

        whenever(mockPodcastRepository.getEpisode(guid)).thenReturn(episodeWithProgress)

        viewModel = createViewModel()

        // When: Play action is triggered
        viewModel.process(
            PodcastAction.Play(
                guid = guid,
                url = "https://example.com/episode.mp3",
                title = "Test Episode",
                artist = "Test Artist",
                imageUrl = null,
                description = null,
                podcastUrl = "https://example.com/feed",
                source = "test",
                timestamp = System.currentTimeMillis()
            )
        )
        testScheduler.advanceUntilIdle()

        // Then: Verify PlayerController.play was called with the saved progress as startPosition
        val startPositionCaptor = argumentCaptor<Long>()
        verify(mockPlayerController).play(
            guid = any(),
            url = any(),
            title = any(),
            artist = any(),
            imageUrl = anyOrNull(),
            description = anyOrNull(),
            podcastUrl = anyOrNull(),
            startPosition = startPositionCaptor.capture()
        )

        assertEquals(
            "Player should resume from saved progress",
            savedProgress,
            startPositionCaptor.firstValue
        )
    }

    @Test
    fun `process_Download_triggersRepositoryDownload`() = runTest(testDispatcher) {
        // Given
        viewModel = createViewModel()

        val guid = "download-guid"
        val url = "https://example.com/episode.mp3"
        val title = "Download Test Episode"

        // When: Download action is triggered
        viewModel.process(
            PodcastAction.Download(
                guid = guid,
                url = url,
                title = title,
                source = "test",
                timestamp = System.currentTimeMillis()
            )
        )
        testScheduler.advanceUntilIdle()

        // Then: Verify Repository.downloadEpisode was called
        verify(mockPodcastRepository).downloadEpisode(
            guid = eq(guid),
            url = eq(url),
            title = eq(title)
        )
    }

    @Test
    fun `process_Download_updatesDownloadState`() = runTest(testDispatcher) {
        // Given
        viewModel = createViewModel()

        val guid = "download-state-guid"

        // When: Download action is triggered
        viewModel.process(
            PodcastAction.Download(
                guid = guid,
                url = "https://example.com/episode.mp3",
                title = "Test Episode",
                source = "test",
                timestamp = System.currentTimeMillis()
            )
        )

        // Collect download states
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.downloadStates.collect {}
        }

        // Allow initial state update
        testScheduler.advanceUntilIdle()

        // Then: Verify download state is set to DOWNLOADING
        val downloadStates = viewModel.downloadStates.value
        assertEquals(
            "Download state should be DOWNLOADING",
            DownloadState.DOWNLOADING,
            downloadStates[guid]
        )

        collectJob.cancel()
    }

    @Test
    fun `process_DeleteDownload_callsRepository`() = runTest(testDispatcher) {
        // Given
        viewModel = createViewModel()

        val guid = "delete-guid"
        val localPath = "/data/files/episode.mp3"

        // When: DeleteDownload action is triggered
        viewModel.process(
            PodcastAction.DeleteDownload(
                guid = guid,
                localFilePath = localPath,
                source = "test",
                timestamp = System.currentTimeMillis()
            )
        )
        testScheduler.advanceUntilIdle()

        // Then: Verify Repository.deleteDownload was called
        verify(mockPodcastRepository).deleteDownload(guid, localPath)
    }

    @Test
    fun `process_MarkAsFinished_callsRepository`() = runTest(testDispatcher) {
        // Given
        viewModel = createViewModel()

        val guid = "finished-guid"
        val durationSeconds = 3600L

        // When: MarkAsFinished action is triggered
        viewModel.process(
            PodcastAction.MarkAsFinished(
                guid = guid,
                durationSeconds = durationSeconds,
                source = "test",
                timestamp = System.currentTimeMillis()
            )
        )
        testScheduler.advanceUntilIdle()

        // Then: Verify Repository.markAsFinished was called with duration in millis
        verify(mockPodcastRepository).markAsFinished(guid, durationSeconds * 1000)
    }

    @Test
    fun `process_SeekTo_callsPlayerController`() = runTest(testDispatcher) {
        // Given
        viewModel = createViewModel()

        val positionMillis = 90000L // 1.5 minutes

        // When: SeekTo action is triggered
        viewModel.process(
            PodcastAction.SeekTo(
                positionMillis = positionMillis,
                source = "test",
                timestamp = System.currentTimeMillis()
            )
        )
        testScheduler.advanceUntilIdle()

        // Then: Verify PlayerController.seekTo was called
        verify(mockPlayerController).seekTo(positionMillis)
    }

    @Test
    fun `process_SeekBack_callsPlayerController`() = runTest(testDispatcher) {
        // Given
        viewModel = createViewModel()

        // When: SeekBack action is triggered
        viewModel.process(
            PodcastAction.SeekBack(
                source = "test",
                timestamp = System.currentTimeMillis()
            )
        )
        testScheduler.advanceUntilIdle()

        // Then: Verify PlayerController.seekBack was called
        verify(mockPlayerController).seekBack()
    }

    @Test
    fun `process_SeekForward_callsPlayerController`() = runTest(testDispatcher) {
        // Given
        viewModel = createViewModel()

        // When: SeekForward action is triggered
        viewModel.process(
            PodcastAction.SeekForward(
                source = "test",
                timestamp = System.currentTimeMillis()
            )
        )
        testScheduler.advanceUntilIdle()

        // Then: Verify PlayerController.seekForward was called
        verify(mockPlayerController).seekForward()
    }

    @Test
    fun `process_TogglePlayPause_callsPlayerController`() = runTest(testDispatcher) {
        // Given
        viewModel = createViewModel()

        // When: TogglePlayPause action is triggered
        viewModel.process(
            PodcastAction.TogglePlayPause(
                source = "test",
                timestamp = System.currentTimeMillis()
            )
        )
        testScheduler.advanceUntilIdle()

        // Then: Verify PlayerController.togglePlayPause was called
        verify(mockPlayerController).togglePlayPause()
    }

    @Test
    fun `process_Pause_callsPlayerController`() = runTest(testDispatcher) {
        // Given
        viewModel = createViewModel()

        // When: Pause action is triggered
        viewModel.process(
            PodcastAction.Pause(
                source = "test",
                timestamp = System.currentTimeMillis()
            )
        )
        testScheduler.advanceUntilIdle()

        // Then: Verify PlayerController.pause was called
        verify(mockPlayerController).pause()
    }
}
