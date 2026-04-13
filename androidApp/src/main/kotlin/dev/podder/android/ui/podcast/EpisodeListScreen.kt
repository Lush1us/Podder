package com.lush1us.podder.ui.podcast

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.lush1us.podder.download.DownloadProgress
import com.lush1us.podder.ui.download.DownloadActionButton
import com.lush1us.podder.ui.download.DownloadViewModel
import com.lush1us.podder.ui.playback.PlaybackViewModel
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
    val downloadVm: DownloadViewModel = koinViewModel()
    val episodes by vm.episodes.collectAsState()
    val podcastTitle by vm.podcastTitle.collectAsState()
    val progressMap by downloadVm.progressMap.collectAsState()

    var selectedEpisodeId   by remember { mutableStateOf<String?>(null) }
    var selectedEpisodeUrl  by remember { mutableStateOf<String?>(null) }
    var showPodcastSettings by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = showPodcastSettings) { showPodcastSettings = false }

    if (showPodcastSettings) {
        PodcastSettingsScreen(
            podcastId    = podcastId,
            podcastTitle = podcastTitle,
            onBack       = { showPodcastSettings = false },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Episodes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val epId  = selectedEpisodeId
                    val epUrl = selectedEpisodeUrl
                    if (epId != null && epUrl != null) {
                        DownloadActionButton(
                            episodeId        = epId,
                            url              = epUrl,
                            progress         = progressMap[epId] ?: DownloadProgress.NotDownloaded,
                            onStartDownload  = { id, url -> downloadVm.startDownload(id, url) },
                            onCancelDownload = { id -> downloadVm.cancelDownload(id) },
                        )
                    }
                    IconButton(onClick = { showPodcastSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Podcast Settings")
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(episodes, key = { it.id }) { episode ->
                EpisodeRow(
                    episode     = episode,
                    onClick     = { playbackVm.play(episode.id, episode.url) },
                    onLongPress = {
                        selectedEpisodeId  = episode.id
                        selectedEpisodeUrl = episode.url
                    },
                )
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeRow(
    episode: EpisodeSummary,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    ListItem(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongPress),
        headlineContent  = { Text(episode.title) },
        supportingContent = {
            val mins = episode.durationMs / 60_000
            Text("${mins} min · ${if (episode.playCount > 0) "Played" else "Unplayed"}",
                style = MaterialTheme.typography.bodySmall)
        },
    )
}
