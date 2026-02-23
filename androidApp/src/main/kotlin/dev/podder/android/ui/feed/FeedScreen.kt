package dev.podder.android.ui.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import dev.podder.android.ui.playback.PlaybackViewModel
import dev.podder.data.repository.FeedEpisode
import org.koin.androidx.compose.koinViewModel

@Composable
fun FeedScreen(modifier: Modifier = Modifier) {
    val vm: FeedViewModel = koinViewModel()
    val playbackVm: PlaybackViewModel = koinViewModel()
    val episodes by vm.episodes.collectAsState()

    LazyColumn(modifier.fillMaxSize()) {
        items(episodes, key = { it.id }) { episode ->
            FeedEpisodeRow(
                episode = episode,
                onClick = { playbackVm.play(episode.id, episode.url) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun FeedEpisodeRow(episode: FeedEpisode, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent   = { Text(episode.title) },
        supportingContent = {
            val mins = episode.durationMs / 60_000
            Text(
                "${episode.podcastTitle} · ${mins} min · ${if (episode.playCount > 0) "Played" else "New"}",
                style = MaterialTheme.typography.bodySmall,
            )
        },
    )
}
