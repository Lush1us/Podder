package dev.podder.android.ui.podcast

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.podder.android.ui.playback.PlaybackViewModel
import dev.podder.data.repository.EpisodeSummary
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeListScreen(
    podcastId: String,
    onBack: () -> Unit,
) {
    val vm: EpisodeListViewModel = koinViewModel(parameters = { parametersOf(podcastId) })
    val playbackVm: PlaybackViewModel = koinViewModel()
    val episodes by vm.episodes.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Episodes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(episodes, key = { it.id }) { episode ->
                EpisodeRow(
                    episode = episode,
                    onClick = { playbackVm.play(episode.id, episode.url) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: EpisodeSummary, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent  = { Text(episode.title) },
        supportingContent = {
            val mins = episode.durationMs / 60_000
            Text("${mins} min · ${if (episode.playCount > 0) "Played" else "Unplayed"}",
                style = MaterialTheme.typography.bodySmall)
        },
    )
}
