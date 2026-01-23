package com.example.podder.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import android.content.Intent
import com.example.podder.player.PodcastPlayerService
import com.example.podder.parser.Episode
import com.example.podder.ui.components.PlayerControls
import com.example.podder.ui.screens.PodcastUiState
import com.example.podder.ui.screens.PodcastViewModel
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import androidx.compose.runtime.DisposableEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: PodcastViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentProgress by remember { mutableStateOf(0f) }
    var currentEpisodeTitle by remember { mutableStateOf("No episode playing") }
    var playerVisible by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val playbackStateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                isPlaying = intent.getBooleanExtra(PodcastPlayerService.EXTRA_IS_PLAYING, false)
                currentEpisodeTitle = intent.getStringExtra(PodcastPlayerService.EXTRA_EPISODE_TITLE) ?: "No episode playing"
            }
        }
        val progressReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val currentPosition = intent.getIntExtra(PodcastPlayerService.EXTRA_CURRENT_POSITION, 0)
                val duration = intent.getIntExtra(PodcastPlayerService.EXTRA_DURATION, 0)
                currentProgress = if (duration > 0) currentPosition.toFloat() / duration else 0f
            }
        }

        LocalBroadcastManager.getInstance(context).registerReceiver(
            playbackStateReceiver, IntentFilter(PodcastPlayerService.ACTION_UPDATE_PLAYBACK_STATE)
        )
        LocalBroadcastManager.getInstance(context).registerReceiver(
            progressReceiver, IntentFilter(PodcastPlayerService.ACTION_UPDATE_PROGRESS)
        )

        onDispose {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(playbackStateReceiver)
            LocalBroadcastManager.getInstance(context).unregisterReceiver(progressReceiver)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Podder") }) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
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
                            item {
                                Text(
                                    text = state.podcast.title,
                                    style = MaterialTheme.typography.headlineMedium,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                            items(state.podcast.episodes) { episode ->
                                ListItem(
                                    headlineContent = { Text(episode.title) },
                                    supportingContent = { Text(episode.description ?: "", maxLines = 2) },
                                    modifier = Modifier.clickable {
                                        episode.audioUrl?.let { url ->
                                            val intent = Intent(context, PodcastPlayerService::class.java)
                                            intent.action = PodcastPlayerService.ACTION_PLAY
                                            intent.putExtra(PodcastPlayerService.EPISODE_URL, url)
                                            intent.putExtra(PodcastPlayerService.EXTRA_EPISODE_TITLE, episode.title)
                                            context.startService(intent)
                                            playerVisible = true
                                        }
                                    }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
            if (playerVisible) {
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
                            val intent = Intent(context, PodcastPlayerService::class.java)
                            if (isPlaying) {
                                intent.action = PodcastPlayerService.ACTION_PAUSE
                            } else {
                                intent.action = PodcastPlayerService.ACTION_PLAY
                            }
                            context.startService(intent)
                        },
                        onNext = { /* TODO: Implement next episode logic */ },
                        onPrevious = { /* TODO: Implement previous episode logic */ }
                    )
                }
            }
        }
    }
}