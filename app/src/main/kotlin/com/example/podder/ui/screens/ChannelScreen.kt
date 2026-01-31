package com.example.podder.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.podder.core.PodcastAction
import com.example.podder.data.local.EpisodeWithPodcast

@Composable
fun ChannelScreen(
    podcastUrl: String,
    viewModel: PodcastViewModel,
    onBack: () -> Unit
) {
    val episodes by viewModel.getEpisodesByPodcast(podcastUrl).collectAsState(initial = emptyList())
    val configuration = LocalConfiguration.current
    val headerHeight = (configuration.screenHeightDp * 0.20).dp

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header with podcast info (20vh)
        item {
            val podcast = episodes.firstOrNull()?.podcast
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(headerHeight)
            ) {
                // Background image
                AsyncImage(
                    model = podcast?.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Gray)
                )
                // Gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                )
                // Back button
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .padding(8.dp)
                        .statusBarsPadding()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .background(Color.Gray.copy(alpha = 0.5f), CircleShape)
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                // Podcast title
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text(
                        text = podcast?.title ?: "",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${episodes.size} episodes",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // Episodes list (already in reverse chronological order from DAO)
        items(episodes) { item ->
            val isFinished = item.episode.duration > 0 &&
                item.episode.progressInMillis >= item.episode.duration * 1000

            if (isFinished) {
                ChannelFinishedEpisodeRow(item, viewModel)
            } else {
                ChannelEpisodeRow(item, viewModel)
            }
            HorizontalDivider()
        }

        // Bottom spacing for mini player
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun ChannelFinishedEpisodeRow(item: EpisodeWithPodcast, viewModel: PodcastViewModel) {
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
                        podcastUrl = item.podcast.url,
                        source = "ChannelScreen",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.episode.title,
            style = MaterialTheme.typography.bodyMedium,
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
private fun ChannelEpisodeRow(item: EpisodeWithPodcast, viewModel: PodcastViewModel) {
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
                        podcastUrl = item.podcast.url,
                        source = "ChannelScreen",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.episode.title,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 20.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatChannelPubDate(item.episode.pubDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.episode.duration > 0) {
                    Text(
                        text = formatChannelDuration(item.episode.duration, item.episode.progressInMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatChannelPubDate(pubDateMillis: Long): String {
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

private fun formatChannelDuration(durationSeconds: Long, progressMillis: Long): String {
    if (durationSeconds <= 0) return ""
    val totalMinutes = (durationSeconds / 60).toInt()
    return if (progressMillis > 0) {
        val elapsedMinutes = (progressMillis / 60000).toInt()
        "${elapsedMinutes}/${totalMinutes}m"
    } else {
        "${totalMinutes}m"
    }
}
