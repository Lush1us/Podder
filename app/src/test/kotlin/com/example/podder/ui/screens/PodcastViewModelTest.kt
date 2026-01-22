package com.example.podder.ui.screens

import com.example.podder.core.Action
import com.example.podder.data.PodcastRepository
import com.example.podder.domain.PodcastUseCase
import com.example.podder.domain.PodcastUseCaseImpl
import com.example.podder.parser.Podcast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
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

@ExperimentalCoroutinesApi
class PodcastViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockPodcastRepository: PodcastRepository
    private lateinit var podcastUseCase: PodcastUseCase
    private lateinit var viewModel: PodcastViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockPodcastRepository = mock(PodcastRepository::class.java)
        podcastUseCase = PodcastUseCaseImpl(mockPodcastRepository)
        viewModel = PodcastViewModel(podcastUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `fetchPodcast action updates uiState to Success on successful fetch`() = runTest {
        // Given
        val dummyPodcast = Podcast(
            title = "Test Podcast",
            description = "A podcast for testing",
            link = "http://test.com",
            episodes = emptyList()
        )
        `when`(mockPodcastRepository.getPodcast("http://test.com/podcast.xml")).thenReturn(dummyPodcast)

        // When
        viewModel.processAction(PodcastAction.FetchPodcast(
            url = "http://test.com/podcast.xml",
            source = "test",
            timestamp = System.currentTimeMillis()
        ))
        advanceUntilIdle() // Allow coroutines to complete

        // Then
        val uiState = viewModel.uiState.first()
        assertTrue(uiState is PodcastUiState.Success)
        assertEquals(dummyPodcast, (uiState as PodcastUiState.Success).podcast)
    }

    @Test
    fun `fetchPodcast action updates uiState to Error on failed fetch`() = runTest {
        // Given
        `when`(mockPodcastRepository.getPodcast("http://test.com/podcast.xml")).thenReturn(null)

        // When
        viewModel.processAction(PodcastAction.FetchPodcast(
            url = "http://test.com/podcast.xml",
            source = "test",
            timestamp = System.currentTimeMillis()
        ))
        advanceUntilIdle() // Allow coroutines to complete

        // Then
        val uiState = viewModel.uiState.first()
        assertTrue(uiState is PodcastUiState.Error)
        assertEquals("Failed to load podcast", (uiState as PodcastUiState.Error).message)
    }
}
