package com.example.podder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.podder.ui.screens.HomeScreen
import com.example.podder.ui.theme.PodderTheme
import com.example.podder.parser.Episode
import com.example.podder.ui.screens.PodcastViewModel
import com.example.podder.data.PodcastRepository
import com.example.podder.domain.PodcastUseCase
import com.example.podder.domain.PodcastUseCaseImpl

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val podcastRepository = PodcastRepository()
        val podcastUseCase: PodcastUseCase = PodcastUseCaseImpl(podcastRepository)
        val podcastViewModel = PodcastViewModel(podcastUseCase)

        setContent {
            PodderTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HomeScreen(viewModel = podcastViewModel, onEpisodeClick = {})
                }
            }
        }
    }
}
