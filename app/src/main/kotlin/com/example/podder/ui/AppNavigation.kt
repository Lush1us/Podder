package com.example.podder.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.podder.ui.screens.HomeScreen
import com.example.podder.ui.screens.PodcastDetailScreen
import com.example.podder.ui.screens.PodcastViewModel
import kotlinx.serialization.Serializable

@Serializable
object Home

@Serializable
data class PodcastDetails(val title: String)

@Composable
fun AppNavigation(viewModel: PodcastViewModel) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Home) {
        composable<Home> {
            HomeScreen(viewModel = viewModel, navController = navController)
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