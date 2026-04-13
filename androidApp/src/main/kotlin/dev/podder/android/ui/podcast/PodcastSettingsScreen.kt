package com.lush1us.podder.ui.podcast

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastSettingsScreen(
    podcastId: String,
    podcastTitle: String,
    onBack: () -> Unit,
) {
    val vm: PodcastSettingsViewModel = koinViewModel(parameters = { parametersOf(podcastId) })
    val state by vm.state.collectAsState()

    val speedOptions: List<Pair<Float?, String>> = listOf(
        null   to "Default",
        0.75f  to "0.75×",
        1.0f   to "1.0×",
        1.25f  to "1.25×",
        1.5f   to "1.5×",
        1.75f  to "1.75×",
        2.0f   to "2.0×",
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(podcastTitle.ifBlank { "Podcast Settings" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                PodcastSettingsDropdownRow(
                    title   = "Playback speed",
                    options = speedOptions,
                    current = state.playbackSpeed,
                    onChange = { vm.save(state.copy(playbackSpeed = it)) },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Mute new episode notifications") },
                    trailingContent = {
                        Switch(
                            checked         = state.notificationsMuted,
                            onCheckedChange = { vm.save(state.copy(notificationsMuted = it)) },
                        )
                    },
                )
            }
            item {
                PodcastSettingsDropdownRow(
                    title   = "Auto-download",
                    options = listOf("global" to "Follow global setting", "always" to "Always", "never" to "Never"),
                    current = state.autoDownload,
                    onChange = { vm.save(state.copy(autoDownload = it)) },
                )
            }
            item {
                StepperRow(
                    title        = "Skip intro",
                    value        = state.skipIntroS,
                    onValueChange = { vm.save(state.copy(skipIntroS = it)) },
                )
            }
            item {
                StepperRow(
                    title        = "Skip outro",
                    value        = state.skipOutroS,
                    onValueChange = { vm.save(state.copy(skipOutroS = it)) },
                )
            }
        }
    }
}

@Composable
private fun <T> PodcastSettingsDropdownRow(
    title: String,
    options: List<Pair<T, String>>,
    current: T,
    onChange: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = options.firstOrNull { it.first == current }?.second ?: current.toString()
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = {
            Box {
                TextButton(onClick = { expanded = true }) { Text(label) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.forEach { (value, optLabel) ->
                        DropdownMenuItem(
                            text    = { Text(optLabel) },
                            onClick = { onChange(value); expanded = false },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun StepperRow(
    title: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    step: Int = 5,
    max: Int = 300,
) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick  = { onValueChange(maxOf(0, value - step)) },
                    enabled  = value > 0,
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease")
                }
                Text(
                    text      = "${value}s",
                    modifier  = Modifier.width(44.dp),
                    textAlign = TextAlign.Center,
                )
                IconButton(
                    onClick  = { onValueChange(minOf(max, value + step)) },
                    enabled  = value < max,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Increase")
                }
            }
        },
    )
}
