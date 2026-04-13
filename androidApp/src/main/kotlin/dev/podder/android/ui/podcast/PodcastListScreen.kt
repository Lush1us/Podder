package com.lush1us.podder.ui.podcast

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.podder.data.repository.PodcastSummary
import org.koin.androidx.compose.koinViewModel

@Composable
fun PodcastListScreen(modifier: Modifier = Modifier, onPodcastClick: (String) -> Unit) {
    val vm: PodcastListViewModel = koinViewModel()
    val podcasts by vm.podcasts.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val opmlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val content = context.contentResolver.openInputStream(uri)
            ?.bufferedReader()
            ?.readText()
        if (content != null) vm.importOpml(content)
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SmallFloatingActionButton(onClick = { opmlLauncher.launch("*/*") }) {
                    Icon(Icons.Default.FileOpen, contentDescription = "Import OPML")
                }
                FloatingActionButton(onClick = { showDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add podcast")
                }
            }
        }
    ) { padding ->
        if (podcasts.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No podcasts yet. Tap + to add one.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(podcasts, key = { it.id }) { podcast ->
                    PodcastRow(podcast = podcast, onClick = { onPodcastClick(podcast.id) })
                    HorizontalDivider()
                }
            }
        }
    }

    if (showDialog) {
        AddPodcastDialog(
            onConfirm = { url ->
                vm.addPodcast(url)
                showDialog = false
            },
            onDismiss = { showDialog = false },
        )
    }
}

@Composable
private fun PodcastRow(podcast: PodcastSummary, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(podcast.title.ifBlank { podcast.rssUrl }) },
        supportingContent = if (podcast.rssUrl.isNotBlank()) {
            { Text(podcast.rssUrl, maxLines = 1, style = MaterialTheme.typography.bodySmall) }
        } else null,
    )
}

@Composable
private fun AddPodcastDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("Add Podcast") },
        text    = {
            OutlinedTextField(
                value         = url,
                onValueChange = { url = it },
                label         = { Text("RSS Feed URL") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (url.isNotBlank()) onConfirm(url.trim()) }) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
