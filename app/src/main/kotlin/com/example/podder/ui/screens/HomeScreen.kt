package com.example.podder.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.podder.R
import com.example.podder.core.PodcastAction
import com.example.podder.data.local.EpisodeWithPodcast
import com.example.podder.ui.Episode
import com.example.podder.ui.components.PlayerControls

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: PodcastViewModel,
    navController: NavController
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by viewModel.playerUiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        // Initialize subscriptions from OPML file
        val stream = context.resources.openRawResource(R.raw.subscriptions)
        viewModel.initializeSubscriptions(stream)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Podder") }) },
        containerColor = MaterialTheme.colorScheme.background
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
                    EpisodeList(episodes = state.feed, viewModel = viewModel)
                }
            }

            // Floating Mini Player
            AnimatedVisibility(
                visible = playerState.currentTitle.isNotEmpty(),
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                PlayerControls(
                    modifier = Modifier,
                    title = playerState.currentTitle,
                    description = playerState.description,
                    imageUrl = playerState.imageUrl,
                    progress = playerState.progress,
                    elapsedMillis = playerState.currentPositionMillis,
                    durationMillis = playerState.durationMillis,
                    isPlaying = playerState.isPlaying,
                    onPlayPause = {
                        viewModel.process(
                            PodcastAction.TogglePlayPause(
                                source = "HomeScreen",
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    },
                    onPlayerClick = {
                        navController.navigate(Episode)
                    }
                )
            }
        }
    }
}

@Composable
fun EpisodeList(episodes: List<EpisodeWithPodcast>, viewModel: PodcastViewModel) {
    LazyColumn {
        items(episodes) { item ->
            ListItem(
                leadingContent = {
                    AsyncImage(
                        model = item.podcast.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Gray)
                    )
                },
                headlineContent = {
                    Text(
                        text = item.episode.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                supportingContent = {
                    Text(
                        text = item.episode.description ?: "",
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingContent = {
                    Text(
                        text = formatDuration(item.episode.duration),
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                modifier = Modifier.clickable {
                    viewModel.process(
                        PodcastAction.Play(
                            guid = item.episode.guid,
                            url = item.episode.audioUrl,
                            title = item.episode.title,
                            artist = item.podcast.title,
                            imageUrl = item.podcast.imageUrl,
                            description = item.episode.description,
                            source = "HomeScreen",
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
            )
            HorizontalDivider()
        }
    }
}

private fun formatDuration(durationSeconds: Long): String {
    if (durationSeconds <= 0) return ""
    val minutes = (durationSeconds / 60).toInt()
    return "${minutes}m"
}
