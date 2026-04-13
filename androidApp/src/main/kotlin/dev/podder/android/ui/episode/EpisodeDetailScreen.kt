package com.lush1us.podder.ui.episode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lush1us.podder.ui.playback.formatPlayerTime
import dev.podder.domain.model.PlaybackState
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

private val SPEEDS = listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeDetailScreen(episodeId: String, onBack: () -> Unit) {
    val vm: EpisodeDetailViewModel = koinViewModel(parameters = { parametersOf(episodeId) })
    val playbackState by vm.playbackState.collectAsState()
    val episode       by vm.episode.collectAsState()
    val podcast       by vm.podcast.collectAsState()
    val speed         by vm.speed.collectAsState()

    val positionMs = when (val s = playbackState) {
        is PlaybackState.Playing -> s.positionMs
        is PlaybackState.Paused  -> s.positionMs
        else -> 0L
    }
    val durationMs = when (val s = playbackState) {
        is PlaybackState.Playing -> s.durationMs
        is PlaybackState.Paused  -> s.durationMs
        else -> episode?.durationMs ?: 0L
    }
    val isPlaying = playbackState is PlaybackState.Playing
    val isBuffering = playbackState is PlaybackState.Buffering
    val isThisEpisode = when (val s = playbackState) {
        is PlaybackState.Playing   -> s.trackId == episodeId
        is PlaybackState.Paused    -> s.trackId == episodeId
        is PlaybackState.Buffering -> s.trackId == episodeId
        else -> false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            // ── Artwork ──────────────────────────────────────────────────────
            item {
                AsyncImage(
                    model              = podcast?.artworkUrl,
                    contentDescription = "Podcast artwork",
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)),
                )
            }

            // ── Title + Podcast ───────────────────────────────────────────────
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Text(
                        text     = episode?.title ?: "",
                        style    = MaterialTheme.typography.titleLarge,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text  = podcast?.title ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── Seek bar ─────────────────────────────────────────────────────
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    var isScrubbing by remember { mutableStateOf(false) }
                    var scrubFraction by remember { mutableStateOf(0f) }

                    val liveProgress = if (durationMs > 0L) positionMs.toFloat() / durationMs.toFloat() else 0f
                    val sliderValue = if (isScrubbing) scrubFraction else liveProgress
                    val displayPositionMs = if (isScrubbing) (scrubFraction * durationMs).toLong() else positionMs

                    Slider(
                        value         = sliderValue,
                        onValueChange = { fraction ->
                            scrubFraction = fraction
                            if (!isScrubbing) {
                                isScrubbing = true
                                vm.setScrubbing(true)
                            }
                        },
                        onValueChangeFinished = {
                            if (isScrubbing) {
                                // Clamp 1 second from end to prevent seekTo(durationMs) → immediate STATE_ENDED
                                val seekMs = (scrubFraction * durationMs).toLong()
                                    .coerceIn(0L, (durationMs - 1_000L).coerceAtLeast(0L))
                                vm.seekTo(seekMs)
                                vm.setScrubbing(false)
                                isScrubbing = false
                            }
                        },
                        modifier      = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatPlayerTime(displayPositionMs), style = MaterialTheme.typography.bodySmall)
                        Text(formatPlayerTime(durationMs), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // ── Play / Pause controls ─────────────────────────────────────────
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    when {
                        isBuffering && isThisEpisode -> CircularProgressIndicator()
                        isPlaying && isThisEpisode   -> {
                            IconButton(onClick = { vm.pause() }, modifier = Modifier.size(72.dp)) {
                                Icon(Icons.Default.Pause, contentDescription = "Pause", modifier = Modifier.size(48.dp))
                            }
                        }
                        else -> {
                            IconButton(
                                onClick = { if (isThisEpisode) vm.resume() else vm.play() },
                                modifier = Modifier.size(72.dp),
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(48.dp))
                            }
                        }
                    }
                }
            }

            // ── Speed chips ───────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    SPEEDS.forEach { s ->
                        FilterChip(
                            selected = speed == s,
                            onClick  = { vm.setSpeed(s) },
                            label    = { Text("${s}x") },
                        )
                    }
                }
            }

            // ── Description ───────────────────────────────────────────────────
            item {
                val raw = episode?.description ?: ""
                // Strip HTML tags for plain text display
                val text = raw.replace(Regex("<[^>]+>"), "").trim()
                if (text.isNotBlank()) {
                    Text(
                        text     = text,
                        style    = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}
