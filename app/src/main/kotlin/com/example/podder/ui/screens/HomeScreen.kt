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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.unit.sp
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
            val isFinished = item.episode.duration > 0 &&
                item.episode.progressInMillis >= item.episode.duration * 1000

            if (isFinished) {
                FinishedEpisodeRow(item, viewModel)
            } else {
                RegularEpisodeRow(item, viewModel)
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun FinishedEpisodeRow(item: EpisodeWithPodcast, viewModel: PodcastViewModel) {
    val grayColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
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
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = item.podcast.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.Gray)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = item.episode.title,
            style = MaterialTheme.typography.bodySmall,
            color = grayColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = "Finished",
            tint = grayColor,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun RegularEpisodeRow(item: EpisodeWithPodcast, viewModel: PodcastViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = item.podcast.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Gray)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.episode.title,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.podcast.title,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatPubDate(item.episode.pubDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = formatDurationWithProgress(item.episode.duration, item.episode.progressInMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatPubDate(pubDateMillis: Long): String {
    if (pubDateMillis <= 0) return ""
    val now = System.currentTimeMillis()
    val today = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    val yesterday = today - 24 * 60 * 60 * 1000

    return when {
        pubDateMillis >= today -> "Today"
        pubDateMillis >= yesterday -> "Yesterday"
        else -> {
            val sdf = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
            sdf.format(java.util.Date(pubDateMillis))
        }
    }
}

private fun formatDurationWithProgress(durationSeconds: Long, progressMillis: Long): String {
    if (durationSeconds <= 0) return ""
    val totalMinutes = (durationSeconds / 60).toInt()
    return if (progressMillis > 0) {
        val elapsedMinutes = (progressMillis / 60000).toInt()
        "${elapsedMinutes}/${totalMinutes}m"
    } else {
        "${totalMinutes}m"
    }
}
