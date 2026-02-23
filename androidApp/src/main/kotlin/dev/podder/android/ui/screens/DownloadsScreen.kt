package dev.podder.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.podder.android.ui.download.DownloadViewModel
import dev.podder.android.download.DownloadInfo
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(onBack: () -> Unit) {
    val vm: DownloadViewModel = koinViewModel()
    val downloads by vm.completedDownloads.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        if (downloads.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("No downloads yet", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(downloads, key = { it.episodeId }) { info ->
                    DownloadRow(info = info, onDelete = { vm.cancelDownload(it) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(info: DownloadInfo, onDelete: (episodeId: String) -> Unit) {
    ListItem(
        leadingContent = {
            AsyncImage(
                model              = info.artworkUrl,
                contentDescription = null,
                modifier           = Modifier.size(48.dp),
            )
        },
        headlineContent   = { Text(info.episodeTitle, maxLines = 2) },
        supportingContent = { Text(info.podcastTitle, style = MaterialTheme.typography.bodySmall) },
        trailingContent   = {
            IconButton(onClick = { onDelete(info.episodeId) }) {
                Icon(
                    imageVector        = androidx.compose.material.icons.Icons.Default.Delete,
                    contentDescription = "Delete download",
                )
            }
        },
    )
}
