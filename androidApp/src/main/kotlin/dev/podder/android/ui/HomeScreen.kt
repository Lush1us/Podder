package dev.podder.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import dev.podder.android.ui.episode.EpisodeDetailScreen
import dev.podder.android.ui.feed.FeedScreen
import dev.podder.android.ui.playback.MiniPlayerBar
import dev.podder.android.ui.podcast.EpisodeListScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    var podcastChannelId by rememberSaveable { mutableStateOf<String?>(null) }
    var episodeDetailId  by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(enabled = podcastChannelId != null) { podcastChannelId = null }
    BackHandler(enabled = episodeDetailId != null)  { episodeDetailId  = null }

    when {
        // Episode detail overlay (full screen — implemented in session 033)
        episodeDetailId != null -> {
            EpisodeDetailScreen(
                episodeId = episodeDetailId!!,
                onBack    = { episodeDetailId = null },
            )
        }
        // Podcast channel page
        podcastChannelId != null -> {
            EpisodeListScreen(
                podcastId = podcastChannelId!!,
                onBack    = { podcastChannelId = null },
            )
        }
        // Default: feed + mini player
        else -> {
            Scaffold(
                topBar = {
                    TopAppBar(title = { Text("Podder", style = MaterialTheme.typography.titleLarge) })
                },
                bottomBar = {
                    MiniPlayerBar(
                        onArtworkClick = { id -> podcastChannelId = id },
                        onMiddleClick  = { id -> episodeDetailId  = id },
                    )
                },
            ) { padding ->
                FeedScreen(modifier = Modifier.padding(padding))
            }
        }
    }
}
