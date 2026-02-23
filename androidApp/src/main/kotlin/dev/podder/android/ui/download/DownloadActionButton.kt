package dev.podder.android.ui.download

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size

/**
 * Single icon button shown in the TopAppBar when an episode is selected.
 * States: NotDownloaded → Download icon
 *         Queued/Downloading → CircularProgressIndicator (tappable → dialog)
 *         Downloaded → CheckCircle icon
 *         Failed → ErrorOutline icon (retry on tap)
 */
@Composable
fun DownloadActionButton(
    episodeId: String,
    url: String,
    progress: DownloadProgress,
    onStartDownload: (episodeId: String, url: String) -> Unit,
    onCancelDownload: (episodeId: String) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }

    when (progress) {
        is DownloadProgress.NotDownloaded, DownloadProgress.Failed -> {
            IconButton(onClick = { onStartDownload(episodeId, url) }) {
                Icon(Icons.Default.Download, contentDescription = "Download episode")
            }
        }
        DownloadProgress.Queued -> {
            IconButton(onClick = { showDialog = true }) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
        is DownloadProgress.Downloading -> {
            IconButton(onClick = { showDialog = true }) {
                CircularProgressIndicator(
                    progress = { progress.percent / 100f },
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
        DownloadProgress.Downloaded -> {
            IconButton(onClick = { /* already downloaded */ }) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Downloaded")
            }
        }
    }

    if (showDialog) {
        val percent = (progress as? DownloadProgress.Downloading)?.percent ?: 0
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Downloading") },
            text  = { Text("$percent% complete") },
            confirmButton = {
                TextButton(onClick = {
                    onCancelDownload(episodeId)
                    showDialog = false
                }) { Text("Cancel download") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Dismiss") }
            },
        )
    }
}
