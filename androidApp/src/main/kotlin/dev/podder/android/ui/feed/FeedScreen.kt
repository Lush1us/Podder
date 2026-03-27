package dev.podder.android.ui.feed

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.podder.android.download.DownloadProgress
import dev.podder.android.queue.QueueEntry
import dev.podder.android.ui.download.DownloadViewModel
import dev.podder.android.ui.playback.PlaybackViewModel
import dev.podder.android.ui.podcast.PodcastListViewModel
import dev.podder.android.ui.queue.QueueViewModel
import dev.podder.data.repository.FeedEpisode
import dev.podder.domain.model.DownloadFilter
import dev.podder.domain.model.FeedFilter
import dev.podder.domain.model.FeedSort
import dev.podder.domain.model.PlayedFilter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onEpisodeTap: (episodeId: String) -> Unit,
    modifier: Modifier = Modifier,
    showFilterBar: Boolean = false,
) {
    val vm: FeedViewModel = koinViewModel()
    val playbackVm: PlaybackViewModel = koinViewModel()
    val podcastVm: PodcastListViewModel = koinViewModel()
    val downloadVm: DownloadViewModel = koinViewModel()
    val queueVm: QueueViewModel = koinViewModel()

    val episodes by vm.episodes.collectAsState()
    val isRefreshing by vm.isRefreshing.collectAsState()
    val isLoadingMore by vm.isLoadingMore.collectAsState()
    val hasMore by vm.hasMore.collectAsState()
    val filter by vm.filter.collectAsState()
    val isImporting by podcastVm.isImporting.collectAsState()
    val progressMap by downloadVm.progressMap.collectAsState()

    val context = LocalContext.current

    val opmlLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
        if (content != null) podcastVm.importOpml(content)
    }

    val lazyListState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            episodes.isNotEmpty() && lastVisible >= episodes.size - 50
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && hasMore && !isLoadingMore) vm.loadMore()
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh    = { vm.refresh() },
        indicator    = {},
        modifier     = modifier.fillMaxSize(),
    ) {
        Column(Modifier.fillMaxSize()) {
            AnimatedVisibility(visible = isImporting) { AnimatedDotsBanner("Importing") }
            AnimatedVisibility(visible = isRefreshing) { AnimatedDotsBanner("Refreshing") }
            if (showFilterBar) {
                FilterBar(filter = filter, onFilterChange = { vm.setFilter(it) })
            }
            Box(Modifier.weight(1f)) {
                if (episodes.isEmpty() && !isRefreshing && !isImporting) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text("Nothing to see here.", style = MaterialTheme.typography.bodyLarge)
                            Button(onClick = { opmlLauncher.launch("*/*") }) {
                                Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Import OPML")
                            }
                        }
                    }
                } else {
                    LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize()) {
                        items(episodes, key = { it.id }) { episode ->
                            SwipeableEpisodeRow(
                                onSwipePlay  = { playbackVm.play(episode.id, episode.url) },
                                onSwipeQueue = {
                                    queueVm.addToQueue(QueueEntry(
                                        episodeId    = episode.id,
                                        url          = episode.url,
                                        title        = episode.title,
                                        artworkUrl   = episode.artworkUrl,
                                        podcastTitle = episode.podcastTitle,
                                        durationMs   = episode.durationMs,
                                    ))
                                },
                                onLongPress  = { onEpisodeTap(episode.id) },
                                ) {
                                FeedEpisodeRow(
                                    episode          = episode,
                                    resumeMs         = playbackVm.resumePosition(episode.id),
                                    downloadProgress = progressMap[episode.id],
                                    onDownloadClick  = { downloadVm.startDownload(episode.id, episode.url) },
                                )
                            }
                            HorizontalDivider()
                        }
                        if (isLoadingMore || hasMore) {
                            item(key = "loading_more") { AnimatedDotsBanner("Loading") }
                        }
                    }
                }
            }
        }
    }
}

// ─── Filter bar ──────────────────────────────────────────────────────────────

@Composable
private fun FilterBar(filter: FeedFilter, onFilterChange: (FeedFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = filter.played == PlayedFilter.ALL,
            onClick  = { onFilterChange(filter.copy(played = PlayedFilter.ALL)) },
            label    = { Text("All") },
        )
        FilterChip(
            selected = filter.played == PlayedFilter.UNPLAYED,
            onClick  = { onFilterChange(filter.copy(played = PlayedFilter.UNPLAYED)) },
            label    = { Text("Unplayed") },
        )
        FilterChip(
            selected = filter.played == PlayedFilter.PLAYED,
            onClick  = { onFilterChange(filter.copy(played = PlayedFilter.PLAYED)) },
            label    = { Text("Played") },
        )
        FilterChip(
            selected = filter.downloaded == DownloadFilter.DOWNLOADED,
            onClick  = {
                val next = if (filter.downloaded == DownloadFilter.DOWNLOADED) DownloadFilter.ALL else DownloadFilter.DOWNLOADED
                onFilterChange(filter.copy(downloaded = next))
            },
            label = { Text("Downloaded") },
        )
        var showSortMenu by remember { mutableStateOf(false) }
        Box {
            TextButton(onClick = { showSortMenu = true }) {
                Text(when (filter.sort) {
                    FeedSort.DATE_DESC    -> "Date ↓"
                    FeedSort.DURATION_ASC -> "Shortest"
                    FeedSort.DURATION_DESC -> "Longest"
                    FeedSort.PODCAST_NAME -> "Podcast"
                })
            }
            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                DropdownMenuItem(text = { Text("Date (newest first)") }, onClick = { onFilterChange(filter.copy(sort = FeedSort.DATE_DESC)); showSortMenu = false })
                DropdownMenuItem(text = { Text("Shortest first") },      onClick = { onFilterChange(filter.copy(sort = FeedSort.DURATION_ASC)); showSortMenu = false })
                DropdownMenuItem(text = { Text("Longest first") },       onClick = { onFilterChange(filter.copy(sort = FeedSort.DURATION_DESC)); showSortMenu = false })
                DropdownMenuItem(text = { Text("Podcast name") },        onClick = { onFilterChange(filter.copy(sort = FeedSort.PODCAST_NAME)); showSortMenu = false })
            }
        }
    }
}

// ─── Swipeable wrapper ───────────────────────────────────────────────────────

// Artwork width (unfinished episodes) — defines the icon reveal zone width.
private val ARTWORK_DP    = 48.dp
// Horizontal padding on each side of the row.
private val H_PADDING_DP  = 12.dp
// Drag distance to fully reveal the icon (= artwork width).
private val REVEAL_DP     = ARTWORK_DP
// Drag distance to activate (reveal + both-side paddings as an extra commit pull).
private val ACTIVATION_DP = REVEAL_DP + H_PADDING_DP * 1.5f  // 66 dp

@Composable
private fun SwipeableEpisodeRow(
    onSwipePlay: () -> Unit,
    onSwipeQueue: () -> Unit,
    onLongPress: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scope   = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val density = LocalDensity.current
    val view    = LocalView.current
    val context = LocalContext.current

    // Vibrator — used for the activation buzz (50 ms, full amplitude).
    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
    }

    val revealPx     = with(density) { REVEAL_DP.toPx() }
    val activationPx = with(density) { ACTIVATION_DP.toPx() }

    val currentOnPlay      by rememberUpdatedState(onSwipePlay)
    val currentOnQueue     by rememberUpdatedState(onSwipeQueue)
    val currentOnLongPress by rememberUpdatedState(onLongPress)

    var playActivated  by remember { mutableStateOf(false) }
    var queueActivated by remember { mutableStateOf(false) }

    val ox    = offsetX.value
    val absOx = kotlin.math.abs(ox)

    val iconAlpha = (absOx / revealPx).coerceIn(0f, 1f)
    val iconScale = 1f + 0.2f * ((absOx - revealPx) / (activationPx - revealPx)).coerceIn(0f, 1f)

    val playColor  = Color(0xFF4CAF50)
    val queueColor = Color(0xFFFF9800)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        val longPressBuzz = VibrationEffect.createWaveform(
                            longArrayOf(0L, 20L, 0L, 20L, 0L, 20L),
                            intArrayOf(0, 50, 0, 80, 0, 35),
                            -1,
                        )
                        vibrator.vibrate(longPressBuzz)
                        currentOnLongPress()
                    },
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    when {
                                        playActivated  -> currentOnPlay()
                                        queueActivated -> currentOnQueue()
                                    }
                                    playActivated  = false
                                    queueActivated = false
                                    offsetX.animateTo(
                                        0f,
                                        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
                                    )
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    playActivated  = false
                                    queueActivated = false
                                    offsetX.animateTo(0f)
                                }
                            },
                            onHorizontalDrag = { _, delta ->
                                scope.launch {
                                    // Progressive resistance: full speed → 40 % at the limit.
                                    val currentAbs = kotlin.math.abs(offsetX.value)
                                    val resistance = (1f - (currentAbs / activationPx) * 0.6f)
                                        .coerceAtLeast(0.4f)
                                    val clamped = (offsetX.value + delta * resistance)
                                        .coerceIn(-activationPx, activationPx)
                                    offsetX.snapTo(clamped)

                                    val absC = kotlin.math.abs(clamped)

                                    val cancelPx = activationPx * 0.20f
                                    val activationBuzz = VibrationEffect.createWaveform(
                                        longArrayOf(0L, 20L, 0L, 20L, 0L, 20L),
                                        intArrayOf(0, 50, 0, 80, 0, 35),
                                        -1,
                                    )
                                    val cancelBuzz = VibrationEffect.createOneShot(20L, 26)

                                    // Activation: buzz on first crossing the threshold.
                                    if (absC >= activationPx - 0.5f) {
                                        if (clamped > 0f && !playActivated) {
                                            playActivated = true
                                            vibrator.vibrate(activationBuzz)
                                        } else if (clamped < 0f && !queueActivated) {
                                            queueActivated = true
                                            vibrator.vibrate(activationBuzz)
                                        }
                                    }

                                    // Cancel: pulled back past 50 % of activation distance.
                                    if (playActivated && clamped < cancelPx) {
                                        playActivated = false
                                        vibrator.vibrate(cancelBuzz)
                                    }
                                    if (queueActivated && clamped > -cancelPx) {
                                        queueActivated = false
                                        vibrator.vibrate(cancelBuzz)
                                    }
                                }
                            },
                )
            }
    ) {
        // ── Colored button — artwork-zone sized, revealed under the card ───
        if (ox > 1f) {
            Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterStart) {
                Box(
                    modifier = Modifier
                        .padding(start = H_PADDING_DP)
                        .size(ARTWORK_DP)
                        .alpha(iconAlpha)
                        .clip(RoundedCornerShape(4.dp))
                        .background(playColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.PlayArrow, null,
                        tint     = Color.White,
                        modifier = Modifier.size(24.dp).scale(iconScale),
                    )
                }
            }
        } else if (ox < -1f) {
            Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
                Box(
                    modifier = Modifier
                        .padding(end = H_PADDING_DP)
                        .size(ARTWORK_DP)
                        .alpha(iconAlpha)
                        .clip(RoundedCornerShape(4.dp))
                        .background(queueColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.PlaylistAdd, null,
                        tint     = Color.White,
                        modifier = Modifier.size(24.dp).scale(iconScale),
                    )
                }
            }
        }

        // ── Sliding content (card stays its natural color) ─────────────────
        Box(Modifier.offset { IntOffset(ox.roundToInt(), 0) }) {
            content()
        }
    }
}

// ─── Episode row ─────────────────────────────────────────────────────────────

@Composable
private fun FeedEpisodeRow(
    episode: FeedEpisode,
    resumeMs: Long,
    downloadProgress: DownloadProgress?,
    onDownloadClick: () -> Unit,
) {
    val isFinished = episode.playCount > 0
    val subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant
    val artworkSize = if (isFinished) 28.dp else 48.dp
    val vertPadding = if (isFinished) 3.dp else 8.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isFinished) 0.4f else 1f)
            .padding(horizontal = 12.dp, vertical = vertPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model              = episode.artworkUrl,
            contentDescription = null,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier
                .size(artworkSize)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )

        Spacer(Modifier.width(10.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text     = episode.title,
                style    = if (isFinished) MaterialTheme.typography.bodySmall
                           else MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!isFinished) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DownloadIndicator(
                        progress = downloadProgress,
                        tint     = subtitleColor,
                        onClick  = onDownloadClick,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text     = episode.podcastTitle,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = subtitleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (!isFinished) {
            Spacer(Modifier.width(8.dp))
            val timeText = if (resumeMs > 0L) {
                "${feedFormatTime(resumeMs)} / ${feedFormatTime(episode.durationMs)}"
            } else {
                "${episode.durationMs / 60_000} min"
            }
            Text(
                text      = timeText,
                style     = MaterialTheme.typography.bodySmall,
                color     = subtitleColor,
                textAlign = TextAlign.End,
            )
        }
    }
}

// ─── Download indicator ───────────────────────────────────────────────────────

@Composable
private fun DownloadIndicator(
    progress: DownloadProgress?,
    tint: Color,
    onClick: () -> Unit,
) {
    val iconMod = Modifier.size(14.dp)
    when (progress) {
        is DownloadProgress.Queued,
        is DownloadProgress.Downloading -> CircularProgressIndicator(
            modifier    = iconMod,
            strokeWidth = 1.5.dp,
            color       = tint,
        )
        is DownloadProgress.Downloaded -> Icon(
            imageVector        = Icons.Default.CheckCircle,
            contentDescription = "Downloaded",
            tint               = Color(0xFF4CAF50),
            modifier           = iconMod,
        )
        else -> Icon(
            imageVector        = Icons.Default.Download,
            contentDescription = "Download",
            tint               = tint,
            modifier           = iconMod.clickable(onClick = onClick),
        )
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

@Composable
private fun AnimatedDotsBanner(label: String) {
    var dotCount by remember { mutableIntStateOf(1) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(400)
            dotCount = if (dotCount >= 3) 1 else dotCount + 1
        }
    }
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.bodySmall
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Row(modifier = Modifier.padding(vertical = 4.dp)) {
            Text(label, color = textColor, style = style)
            Text(".", color = textColor, style = style, modifier = Modifier.alpha(if (dotCount >= 1) 1f else 0f))
            Text(".", color = textColor, style = style, modifier = Modifier.alpha(if (dotCount >= 2) 1f else 0f))
            Text(".", color = textColor, style = style, modifier = Modifier.alpha(if (dotCount >= 3) 1f else 0f))
        }
    }
}

private fun feedFormatTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "$h:%02d:%02d".format(m, s)
    else "%d:%02d".format(m, s)
}
