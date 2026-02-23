package dev.podder.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import dev.podder.android.ui.download.DownloadActionButton
import dev.podder.android.ui.download.DownloadViewModel
import dev.podder.android.ui.episode.EpisodeDetailScreen
import dev.podder.android.ui.feed.FeedScreen
import dev.podder.android.ui.playback.MiniPlayerBar
import dev.podder.android.ui.podcast.EpisodeListScreen
import dev.podder.android.ui.screens.DownloadsScreen
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    var podcastChannelId      by rememberSaveable { mutableStateOf<String?>(null) }
    var episodeDetailId       by rememberSaveable { mutableStateOf<String?>(null) }
    var showDownloads         by rememberSaveable { mutableStateOf(false) }
    var selectedEpisodeId     by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedEpisodeUrl    by rememberSaveable { mutableStateOf<String?>(null) }

    val downloadVm: DownloadViewModel = koinViewModel()
    val progressMap by downloadVm.progressMap.collectAsState()
    val hasDownloads by downloadVm.hasDownloads.collectAsState()

    BackHandler(enabled = podcastChannelId != null) { podcastChannelId = null }
    BackHandler(enabled = episodeDetailId != null)  { episodeDetailId  = null }
    BackHandler(enabled = showDownloads)            { showDownloads    = false }

    when {
        episodeDetailId != null -> {
            EpisodeDetailScreen(
                episodeId = episodeDetailId!!,
                onBack    = { episodeDetailId = null },
            )
        }
        podcastChannelId != null -> {
            EpisodeListScreen(
                podcastId = podcastChannelId!!,
                onBack    = { podcastChannelId = null },
            )
        }
        showDownloads -> {
            DownloadsScreen(onBack = { showDownloads = false })
        }
        else -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Podder", style = MaterialTheme.typography.titleLarge) },
                        actions = {
                            // Downloads page icon — only when there are completed downloads
                            if (hasDownloads) {
                                IconButton(onClick = { showDownloads = true }) {
                                    Icon(Icons.Default.DownloadForOffline, contentDescription = "Downloads")
                                }
                            }
                            // Contextual download button — only when an episode is long-pressed
                            val epId  = selectedEpisodeId
                            val epUrl = selectedEpisodeUrl
                            if (epId != null && epUrl != null) {
                                DownloadActionButton(
                                    episodeId       = epId,
                                    url             = epUrl,
                                    progress        = progressMap[epId] ?: dev.podder.android.download.DownloadProgress.NotDownloaded,
                                    onStartDownload = { id, url -> downloadVm.startDownload(id, url) },
                                    onCancelDownload = { id -> downloadVm.cancelDownload(id) },
                                )
                            }
                        },
                    )
                },
                bottomBar = {
                    MiniPlayerBar(
                        onArtworkClick = { id -> podcastChannelId = id },
                        onMiddleClick  = { id -> episodeDetailId  = id },
                    )
                },
            ) { padding ->
                FeedScreen(
                    modifier = Modifier.padding(padding),
                    onEpisodeLongPress = { id, url ->
                        selectedEpisodeId  = id
                        selectedEpisodeUrl = url
                    },
                )
            }
        }
    }
}
