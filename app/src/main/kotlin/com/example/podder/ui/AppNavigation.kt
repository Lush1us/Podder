package com.example.podder.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.podder.core.PodcastAction
import com.example.podder.ui.screens.ChannelScreen
import com.example.podder.ui.screens.EpisodeScreen
import com.example.podder.ui.screens.HomeScreen
import com.example.podder.ui.screens.PodcastDetailScreen
import com.example.podder.ui.screens.PodcastViewModel
import com.example.podder.ui.screens.SubscriptionsScreen
import java.net.URLDecoder
import java.net.URLEncoder
import kotlinx.serialization.Serializable

@Serializable
object Home

@Serializable
object Episode

@Serializable
object Subscriptions

@Serializable
data class PodcastDetails(val title: String)

@Serializable
data class Channel(val podcastUrl: String)

@Composable
fun AppNavigation(viewModel: PodcastViewModel) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Home) {
        composable<Home> {
            HomeScreen(viewModel = viewModel, navController = navController)
        }
        composable<Episode> {
            val playerState by viewModel.playerUiState.collectAsStateWithLifecycle()
            EpisodeScreen(
                playerState = playerState,
                onBack = { navController.popBackStack() },
                onPlayPause = {
                    viewModel.process(PodcastAction.TogglePlayPause("EpisodeScreen", System.currentTimeMillis()))
                },
                onSeekBack = {
                    viewModel.process(PodcastAction.SeekBack("EpisodeScreen", System.currentTimeMillis()))
                },
                onSeekForward = {
                    viewModel.process(PodcastAction.SeekForward("EpisodeScreen", System.currentTimeMillis()))
                },
                onSeekTo = { positionMillis ->
                    viewModel.process(PodcastAction.SeekTo(positionMillis, "EpisodeScreen", System.currentTimeMillis()))
                },
                onChannelClick = { podcastUrl ->
                    navController.navigate(Channel(podcastUrl))
                }
            )
        }
        composable<Channel> { backStackEntry ->
            val channel = backStackEntry.toRoute<Channel>()
            ChannelScreen(
                podcastUrl = channel.podcastUrl,
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable<Subscriptions> {
            SubscriptionsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onPodcastClick = { podcastUrl ->
                    navController.navigate(Channel(podcastUrl))
                }
            )
        }
        /*
        composable<PodcastDetails> { backStackEntry ->
            val podcastDetails = backStackEntry.toRoute<PodcastDetails>()
            val podcast = (viewModel.uiState.value as? PodcastUiState.Success)?.podcasts?.find { it.title == podcastDetails.title }
            if (podcast != null) {
                PodcastDetailScreen(podcast = podcast, navController = navController, viewModel = viewModel)
            }
        }
        */
    }
}