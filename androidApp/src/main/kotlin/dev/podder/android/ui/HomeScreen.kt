package com.lush1us.podder.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.lush1us.podder.ui.episode.EpisodeDetailScreen
import com.lush1us.podder.ui.feed.FeedScreen
import com.lush1us.podder.ui.playback.MiniPlayerBar
import com.lush1us.podder.ui.podcast.EpisodeListScreen
import com.lush1us.podder.ui.queue.QueueScreen
import com.lush1us.podder.ui.queue.QueueViewModel
import com.lush1us.podder.ui.discover.DiscoverScreen
import com.lush1us.podder.ui.screens.DownloadsScreen
import com.lush1us.podder.ui.settings.SettingsScreen
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    themeMode: String = "system",
    onThemeChange: (String) -> Unit = {},
) {
    var podcastChannelId by rememberSaveable { mutableStateOf<String?>(null) }
    var episodeDetailId  by rememberSaveable { mutableStateOf<String?>(null) }
    var showDownloads    by rememberSaveable { mutableStateOf(false) }
    var showQueue        by rememberSaveable { mutableStateOf(false) }
    var showSearch       by rememberSaveable { mutableStateOf(false) }
    var showSettings     by rememberSaveable { mutableStateOf(false) }
    var showFilterBar    by rememberSaveable { mutableStateOf(false) }
    var showMenu         by remember { mutableStateOf(false) }

    val queueVm: QueueViewModel = koinViewModel()
    val autoplay by queueVm.autoplay.collectAsState()

    BackHandler(enabled = podcastChannelId != null) { podcastChannelId = null }
    BackHandler(enabled = episodeDetailId != null)  { episodeDetailId  = null }
    BackHandler(enabled = showDownloads)            { showDownloads    = false }
    BackHandler(enabled = showQueue)                { showQueue        = false }
    BackHandler(enabled = showSearch)               { showSearch       = false }
    BackHandler(enabled = showSettings)             { showSettings     = false }

    when {
        showSearch   -> DiscoverScreen(onBack = { showSearch = false })
        showSettings -> SettingsScreen(onBack = { showSettings = false })
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
        showDownloads -> DownloadsScreen(onBack = { showDownloads = false })
        showQueue     -> QueueScreen(onBack = { showQueue = false })
        else -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Podder", style = MaterialTheme.typography.titleLarge) },
                        actions = {
                            IconButton(onClick = { showFilterBar = !showFilterBar }) {
                                Icon(Icons.Default.FilterList, contentDescription = "Filter")
                            }
                            IconButton(onClick = { showSearch = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                                }
                                DropdownMenu(
                                    expanded         = showMenu,
                                    onDismissRequest = { showMenu = false },
                                ) {
                                    DropdownMenuItem(
                                        text        = { Text("Queue") },
                                        leadingIcon = { Icon(Icons.Default.Queue, contentDescription = null) },
                                        onClick     = { showQueue = true; showMenu = false },
                                    )
                                    DropdownMenuItem(
                                        text          = { Text("Downloads") },
                                        leadingIcon   = { Icon(Icons.Default.Download, contentDescription = null) },
                                        onClick       = { showDownloads = true; showMenu = false },
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text           = { Text("Autoplay") },
                                        trailingIcon   = if (autoplay) {
                                            { Icon(Icons.Default.Check, contentDescription = null) }
                                        } else null,
                                        onClick        = { queueVm.toggleAutoplay() },
                                    )
                                    DropdownMenuItem(
                                        text    = { Text("Theme: ${themeMode.replaceFirstChar { it.uppercase() }}") },
                                        onClick = {
                                            val next = when (themeMode) {
                                                "system" -> "light"
                                                "light"  -> "dark"
                                                else     -> "system"
                                            }
                                            onThemeChange(next)
                                            showMenu = false
                                        },
                                    )
                                    DropdownMenuItem(
                                        text        = { Text("Settings") },
                                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                        onClick     = { showSettings = true; showMenu = false },
                                    )
                                }
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
                    modifier      = Modifier.padding(padding),
                    onEpisodeTap  = { id -> episodeDetailId = id },
                    showFilterBar = showFilterBar,
                )
            }
        }
    }
}
