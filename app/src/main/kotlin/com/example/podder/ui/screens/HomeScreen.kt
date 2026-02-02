package com.example.podder.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.podder.R
import com.example.podder.core.PodcastAction
import com.example.podder.data.local.EpisodeWithPodcast
import com.example.podder.data.network.SearchResult
import com.example.podder.ui.Channel
import com.example.podder.ui.Episode
import com.example.podder.ui.components.PlayerControls
import com.example.podder.ui.components.PodcastSearchBar
import androidx.compose.foundation.clickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: PodcastViewModel,
    navController: NavController
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by viewModel.playerUiState.collectAsStateWithLifecycle()
    val downloadStates by viewModel.downloadStates.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val subscribedUrls by viewModel.subscribedUrls.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Search mode state
    var isSearchMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Selection state - store just the GUID to avoid stale references
    var selectedGuid by remember { mutableStateOf<String?>(null) }

    // Optimistic UI: track episodes being marked as finished (before DB update)
    var pendingFinishedGuids by remember { mutableStateOf(setOf<String>()) }

    // Look up the current episode from the list to get fresh data (including localFilePath)
    val selectedEpisode = remember(uiState, selectedGuid) {
        if (selectedGuid == null) null
        else (uiState as? HomeUiState.Success)?.feed?.find { it.episode.guid == selectedGuid }
    }

    // Get download state for selected episode
    val selectedDownloadState = selectedGuid?.let { downloadStates[it] }

    LaunchedEffect(Unit) {
        // Initialize subscriptions from OPML file
        val stream = context.resources.openRawResource(R.raw.subscriptions)
        viewModel.initializeSubscriptions(stream)
    }

    Scaffold(
        topBar = {
            if (selectedEpisode != null) {
                // Selection toolbar
                TopAppBar(
                    title = { Text("1 selected") },
                    navigationIcon = {
                        IconButton(onClick = { selectedGuid = null }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear selection")
                        }
                    },
                    actions = {
                        // Download button with state
                        when (selectedDownloadState) {
                            DownloadState.DOWNLOADING -> {
                                Box(
                                    modifier = Modifier.size(48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                            DownloadState.COMPLETED -> {
                                Box(
                                    modifier = Modifier.size(48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.DownloadDone,
                                        contentDescription = "Download complete",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            null -> {
                                val localPath = selectedEpisode?.episode?.localFilePath
                                if (localPath != null) {
                                    // Show delete button if already downloaded
                                    IconButton(
                                        onClick = {
                                            selectedEpisode?.let { episode ->
                                                viewModel.process(
                                                    PodcastAction.DeleteDownload(
                                                        guid = episode.episode.guid,
                                                        localFilePath = localPath,
                                                        source = "HomeScreen",
                                                        timestamp = System.currentTimeMillis()
                                                    )
                                                )
                                                selectedGuid = null
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete download")
                                    }
                                } else {
                                    // Show download button if not downloaded
                                    IconButton(
                                        onClick = {
                                            selectedEpisode?.let { episode ->
                                                viewModel.process(
                                                    PodcastAction.Download(
                                                        guid = episode.episode.guid,
                                                        url = episode.episode.audioUrl,
                                                        title = episode.episode.title,
                                                        source = "HomeScreen",
                                                        timestamp = System.currentTimeMillis()
                                                    )
                                                )
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Filled.Download, contentDescription = "Download episode")
                                    }
                                }
                            }
                        }
                        // Mark as finished button
                        IconButton(
                            onClick = {
                                selectedEpisode?.let { episode ->
                                    // Optimistic UI update - show as finished immediately
                                    pendingFinishedGuids = pendingFinishedGuids + episode.episode.guid

                                    // Then update database
                                    viewModel.process(
                                        PodcastAction.MarkAsFinished(
                                            guid = episode.episode.guid,
                                            durationSeconds = episode.episode.duration,
                                            source = "HomeScreen",
                                            timestamp = System.currentTimeMillis()
                                        )
                                    )
                                    selectedGuid = null
                                }
                            }
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = "Mark as finished")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            } else if (isSearchMode) {
                // Search toolbar
                TopAppBar(
                    title = {
                        PodcastSearchBar(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onSearch = { query ->
                                viewModel.process(
                                    PodcastAction.Search(
                                        query = query,
                                        source = "HomeScreen",
                                        timestamp = System.currentTimeMillis()
                                    )
                                )
                            },
                            onClose = {
                                isSearchMode = false
                                searchQuery = ""
                                viewModel.process(
                                    PodcastAction.ClearSearch(
                                        source = "HomeScreen",
                                        timestamp = System.currentTimeMillis()
                                    )
                                )
                            }
                        )
                    }
                )
            } else {
                // Normal toolbar
                TopAppBar(
                    title = { Text("Podder") },
                    actions = {
                        IconButton(onClick = { isSearchMode = true }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                    }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Show search results when in search mode, otherwise show feed
            if (isSearchMode) {
                if (isSearching) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                } else if (searchResults.isNotEmpty()) {
                    SearchResultsList(
                        results = searchResults,
                        subscribedUrls = subscribedUrls,
                        onSubscribe = { result ->
                            viewModel.process(
                                PodcastAction.Subscribe(
                                    podcast = result,
                                    source = "HomeScreen",
                                    timestamp = System.currentTimeMillis()
                                )
                            )
                        },
                        onUnsubscribe = { feedUrl ->
                            viewModel.process(
                                PodcastAction.Unsubscribe(
                                    url = feedUrl,
                                    source = "HomeScreen",
                                    timestamp = System.currentTimeMillis()
                                )
                            )
                        },
                        onNavigateToChannel = { feedUrl ->
                            isSearchMode = false
                            searchQuery = ""
                            viewModel.process(
                                PodcastAction.ClearSearch(
                                    source = "HomeScreen",
                                    timestamp = System.currentTimeMillis()
                                )
                            )
                            navController.navigate(Channel(feedUrl))
                        }
                    )
                } else if (searchQuery.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "No results found",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            } else {
                when (val state = uiState) {
                    is HomeUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                    }
                    is HomeUiState.Error -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                    is HomeUiState.Success -> {
                        EpisodeList(
                            episodes = state.feed,
                            viewModel = viewModel,
                            selectedEpisode = selectedEpisode,
                            onEpisodeSelected = { selectedGuid = it.episode.guid },
                            onClearSelection = { selectedGuid = null },
                            pendingFinishedGuids = pendingFinishedGuids
                        )
                    }
                }
            }

            // Floating Mini Player
            AnimatedVisibility(
                visible = playerState.currentTitle.isNotEmpty(),
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                PlayerControls(
                    modifier = Modifier,
                    title = playerState.currentTitle,
                    description = playerState.description,
                    imageUrl = playerState.imageUrl,
                    progress = playerState.progress,
                    elapsedMillis = playerState.currentPositionMillis,
                    durationMillis = playerState.durationMillis,
                    isPlaying = playerState.isPlaying,
                    onPlayPause = {
                        viewModel.process(
                            PodcastAction.TogglePlayPause(
                                source = "HomeScreen",
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    },
                    onPlayerClick = {
                        navController.navigate(Episode)
                    },
                    onSeekTo = { positionMillis ->
                        viewModel.process(
                            PodcastAction.SeekTo(
                                positionMillis = positionMillis,
                                source = "HomeScreen",
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun EpisodeList(
    episodes: List<EpisodeWithPodcast>,
    viewModel: PodcastViewModel,
    selectedEpisode: EpisodeWithPodcast?,
    onEpisodeSelected: (EpisodeWithPodcast) -> Unit,
    onClearSelection: () -> Unit,
    pendingFinishedGuids: Set<String> = emptySet()
) {
    LazyColumn {
        items(episodes, key = { it.episode.guid }) { item ->
            // Check both database state AND optimistic UI state
            val isFinishedInDb = item.episode.duration > 0 &&
                item.episode.progressInMillis >= item.episode.duration * 1000
            val isFinished = isFinishedInDb || pendingFinishedGuids.contains(item.episode.guid)
            val isSelected = selectedEpisode?.episode?.guid == item.episode.guid

            if (isFinished) {
                FinishedEpisodeRow(
                    item = item,
                    viewModel = viewModel,
                    isSelected = isSelected,
                    onLongPress = { onEpisodeSelected(item) },
                    onClearSelection = onClearSelection
                )
            } else {
                RegularEpisodeRow(
                    item = item,
                    viewModel = viewModel,
                    isSelected = isSelected,
                    onLongPress = { onEpisodeSelected(item) },
                    onClearSelection = onClearSelection
                )
            }
            HorizontalDivider()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FinishedEpisodeRow(
    item: EpisodeWithPodcast,
    viewModel: PodcastViewModel,
    isSelected: Boolean,
    onLongPress: () -> Unit,
    onClearSelection: () -> Unit
) {
    val grayColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .combinedClickable(
                onClick = {
                    if (isSelected) {
                        onClearSelection()
                    } else {
                        viewModel.process(
                            PodcastAction.Play(
                                guid = item.episode.guid,
                                url = item.episode.audioUrl,
                                title = item.episode.title,
                                artist = item.podcast.title,
                                imageUrl = item.podcast.imageUrl,
                                description = item.episode.description,
                                podcastUrl = item.podcast.url,
                                source = "HomeScreen",
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    }
                },
                onLongClick = onLongPress
            )
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = item.podcast.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.Gray)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = item.episode.title,
            style = MaterialTheme.typography.bodySmall,
            color = grayColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        if (item.episode.localFilePath != null) {
            Icon(
                imageVector = Icons.Filled.DownloadDone,
                contentDescription = "Downloaded",
                tint = grayColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = "Finished",
            tint = grayColor,
            modifier = Modifier.size(16.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RegularEpisodeRow(
    item: EpisodeWithPodcast,
    viewModel: PodcastViewModel,
    isSelected: Boolean,
    onLongPress: () -> Unit,
    onClearSelection: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .combinedClickable(
                onClick = {
                    if (isSelected) {
                        onClearSelection()
                    } else {
                        viewModel.process(
                            PodcastAction.Play(
                                guid = item.episode.guid,
                                url = item.episode.audioUrl,
                                title = item.episode.title,
                                artist = item.podcast.title,
                                imageUrl = item.podcast.imageUrl,
                                description = item.episode.description,
                                podcastUrl = item.podcast.url,
                                source = "HomeScreen",
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    }
                },
                onLongClick = onLongPress
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = item.podcast.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Gray)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.episode.title,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.podcast.title,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatPubDate(item.episode.pubDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.episode.localFilePath != null) {
                        Icon(
                            imageVector = Icons.Filled.DownloadDone,
                            contentDescription = "Downloaded",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = formatDurationWithProgress(item.episode.duration, item.episode.progressInMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatPubDate(pubDateMillis: Long): String {
    if (pubDateMillis <= 0) return ""
    val now = System.currentTimeMillis()
    val today = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    val yesterday = today - 24 * 60 * 60 * 1000

    return when {
        pubDateMillis >= today -> "Today"
        pubDateMillis >= yesterday -> "Yesterday"
        else -> {
            val sdf = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
            sdf.format(java.util.Date(pubDateMillis))
        }
    }
}

private fun formatDurationWithProgress(durationSeconds: Long, progressMillis: Long): String {
    if (durationSeconds <= 0) return ""
    val totalMinutes = (durationSeconds / 60).toInt()
    return if (progressMillis > 0) {
        val elapsedMinutes = (progressMillis / 60000).toInt()
        "${elapsedMinutes}/${totalMinutes}m"
    } else {
        "${totalMinutes}m"
    }
}

@Composable
private fun SearchResultsList(
    results: List<SearchResult>,
    subscribedUrls: Set<String>,
    onSubscribe: (SearchResult) -> Unit,
    onUnsubscribe: (String) -> Unit,
    onNavigateToChannel: (String) -> Unit
) {
    LazyColumn {
        items(results, key = { it.collectionId }) { result ->
            val isSubscribed = result.feedUrl?.let { subscribedUrls.contains(it) } ?: false
            SearchResultRow(
                result = result,
                isSubscribed = isSubscribed,
                onToggleSubscription = {
                    if (isSubscribed) {
                        result.feedUrl?.let { onUnsubscribe(it) }
                    } else {
                        onSubscribe(result)
                    }
                },
                onNavigateToChannel = {
                    result.feedUrl?.let { onNavigateToChannel(it) }
                }
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun SearchResultRow(
    result: SearchResult,
    isSubscribed: Boolean,
    onToggleSubscription: () -> Unit,
    onNavigateToChannel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateToChannel() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = result.artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Gray)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.collectionName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            result.artistName?.let { artist ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            result.genre?.let { genre ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = genre,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (result.feedUrl != null) {
            IconButton(onClick = onToggleSubscription) {
                Icon(
                    imageVector = if (isSubscribed) Icons.Filled.Check else Icons.Filled.Add,
                    contentDescription = if (isSubscribed) "Unsubscribe" else "Subscribe",
                    tint = if (isSubscribed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
