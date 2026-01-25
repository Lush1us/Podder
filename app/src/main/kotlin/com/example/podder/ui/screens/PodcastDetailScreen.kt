package com.example.podder.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.podder.core.PodcastAction
import com.example.podder.parser.Podcast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastDetailScreen(
    podcast: Podcast,
    navController: NavController,
    viewModel: PodcastViewModel
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(podcast.title) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            item {
                Text(
                    text = podcast.title,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
            items(podcast.episodes) { episode ->
                ListItem(
                    headlineContent = { Text(episode.title) },
                    supportingContent = { Text(episode.description ?: "", maxLines = 2) },
                    modifier = Modifier.clickable {
                        episode.enclosure?.url?.let { url ->
                            viewModel.process(
                                PodcastAction.Play(
                                    url = url,
                                    title = episode.title,
                                    artist = podcast.title,
                                    imageUrl = podcast.imageUrl,
                                    source = "PodcastDetailScreen",
                                    timestamp = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                )
                HorizontalDivider()
            }
        }
    }
}
