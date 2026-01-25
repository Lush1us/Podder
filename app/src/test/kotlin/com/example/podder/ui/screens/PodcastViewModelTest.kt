package com.example.podder.ui.screens

import com.example.podder.core.PodcastAction
import com.example.podder.data.PodcastRepository
import com.example.podder.data.local.EpisodeWithPodcast
import com.example.podder.data.local.EpisodeEntity
import com.example.podder.data.local.PodcastEntity
import com.example.podder.player.PlayerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify

@ExperimentalCoroutinesApi
class PodcastViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockPodcastRepository: PodcastRepository
    private lateinit var mockPlayerController: PlayerController
    private lateinit var viewModel: PodcastViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockPodcastRepository = mock(PodcastRepository::class.java)
        mockPlayerController = mock(PlayerController::class.java)
        viewModel = PodcastViewModel(mockPodcastRepository, mockPlayerController)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init updates uiState to Success on successful feed emission`() = runTest(testDispatcher) {
        // Given
        val dummyPodcast = PodcastEntity("url1", "Podcast Title", "image1")
        val dummyEpisode = EpisodeEntity("guid1", "url1", "Episode Title", "Description", 123L, "audio1", "100")
        val dummyFeed = listOf(EpisodeWithPodcast(dummyEpisode, dummyPodcast))

        `when`(mockPodcastRepository.homeFeed).thenReturn(flowOf(dummyFeed))

        // When
        viewModel = PodcastViewModel(mockPodcastRepository, mockPlayerController) // Re-initialize to trigger init block

        // Start collecting the StateFlow to trigger subscription
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        testScheduler.advanceUntilIdle() // Allow coroutines to complete

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
        `when`(mockPodcastRepository.homeFeed).thenReturn(flowOf(emptyList())) // Mock initial state

        // When
        viewModel.process(PodcastAction.FetchPodcasts(source = "test", timestamp = System.currentTimeMillis()))
        testScheduler.advanceUntilIdle() // Allow coroutines to complete

        // Then
        verify(mockPodcastRepository).updatePodcasts()
    }
}