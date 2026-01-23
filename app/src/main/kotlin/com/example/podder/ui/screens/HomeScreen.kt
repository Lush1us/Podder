package com.example.podder.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.Intent
import com.example.podder.player.PodcastPlayerService
import com.example.podder.parser.Episode
import com.example.podder.ui.screens.PodcastUiState
import com.example.podder.ui.screens.PodcastViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: PodcastViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

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
                                            context.startService(intent)
                                        }
                                    }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    val intent = Intent(context, PodcastPlayerService::class.java)
                    intent.action = PodcastPlayerService.ACTION_PLAY
                    context.startService(intent)
                }, modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Text("Play")
                }
                Button(onClick = {
                    val intent = Intent(context, PodcastPlayerService::class.java)
                    intent.action = PodcastPlayerService.ACTION_PAUSE
                    context.startService(intent)
                }, modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Text("Pause")
                }
                Button(onClick = {
                    val intent = Intent(context, PodcastPlayerService::class.java)
                    intent.action = PodcastPlayerService.ACTION_STOP
                    context.startService(intent)
                }, modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Text("Stop")
                }
            }
        }
    }
}