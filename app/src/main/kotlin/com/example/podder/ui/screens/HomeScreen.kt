package com.example.podder.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.podder.R
import com.example.podder.core.PodcastAction
import com.example.podder.data.local.EpisodeWithPodcast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: PodcastViewModel,
    navController: NavController
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        // Initialize subscriptions from OPML file
        val stream = context.resources.openRawResource(R.raw.subscriptions)
        viewModel.initializeSubscriptions(stream)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Podder Feed") }) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val state = uiState) {
                is HomeUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is HomeUiState.Error -> {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is HomeUiState.Success -> {
                    EpisodeList(episodes = state.feed)
                }
            }
        }
    }
}

@Composable
fun EpisodeList(episodes: List<EpisodeWithPodcast>) {
    LazyColumn {
        items(episodes) { item ->
            ListItem(
                headlineContent = { Text(item.episode.title) },
                supportingContent = { Text(item.podcast.title) },
                trailingContent = { Text(item.episode.duration) }
            )
            HorizontalDivider()
        }
    }
}