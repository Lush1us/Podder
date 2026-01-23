package com.example.podder.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
<<<<<<< HEAD
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.podder.parser.Podcast
import com.example.podder.ui.components.PlayerControls
import com.example.podder.ui.screens.PodcastUiState
import com.example.podder.ui.screens.PodcastViewModel
import androidx.compose.runtime.LaunchedEffect
import com.example.podder.core.PodcastAction
import androidx.navigation.NavController
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.activity.ComponentActivity
import com.example.podder.ui.PodcastDetails
>>>>>>> f8fedc6 (Refactor Podder codebase to enforce Operator Pattern and Type-Safe Navigation, and fix Media3 service configuration.)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: PodcastViewModel,
    navController: NavController
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentProgress by remember { mutableStateOf(0f) }
    var currentEpisodeTitle by remember { mutableStateOf("No episode playing") }
    var showExitDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val podcastUrls = listOf(
            "https://feeds.npr.org/510318/podcast.xml", // Up First
            "https://feeds.npr.org/510289/podcast.xml", // Planet Money
            "https://feeds.npr.org/510308/podcast.xml"  // How I Built This
        )
        viewModel.process(PodcastAction.FetchPodcasts(urls = podcastUrls, source = "HomeScreen", timestamp = System.currentTimeMillis()))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Podder") }
            )
        }
    ) { padding ->
        Column(modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    if (dragAmount > 50) { // Swiping right on home screen
                        showExitDialog = true
                    }
                }
            }
        ) {
            Box(modifier = Modifier.weight(1f)) {
                when (val state = uiState) {
                    is PodcastUiState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    is PodcastUiState.Error -> {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    is PodcastUiState.Success -> {
                        LazyColumn {
                            items(state.podcasts) { podcast ->
                                ListItem(
                                    headlineContent = { Text(podcast.title) },
                                    modifier = Modifier.clickable {
                                        navController.navigate(PodcastDetails(podcast.title))
                                    }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
            Column {
                Text(
                    text = "Now Playing: $currentEpisodeTitle",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium
                )
                PlayerControls(
                    modifier = Modifier.fillMaxWidth().height(100.dp), // Approximately 20% screen height
                    isPlaying = isPlaying,
                    progress = currentProgress,
                    onPlayPause = {
                        if (isPlaying) {
                            viewModel.process(PodcastAction.Pause(source = "HomeScreen", timestamp = System.currentTimeMillis()))
                        } else {
                            // We need the episode ID to play. This will be handled in PodcastDetailScreen
                            // For now, we can't play from here without knowing which episode to play.
                            // This part of the UI should probably only be visible when an episode is already playing.
                        }
                    },
                    onNext = { /* TODO: Implement next episode logic */ },
                    onPrevious = { /* TODO: Implement previous episode logic */ }
                )
            }
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Exit App") },
            text = { Text("Are you sure you want to exit?") },
            confirmButton = {
                TextButton(onClick = {
                    (context as? ComponentActivity)?.finish()
                }) {
                    Text("Exit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}