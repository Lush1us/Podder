package dev.podder.android.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
) {
    val vm: SettingsViewModel = koinViewModel()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val refreshInterval by vm.refreshIntervalHours.collectAsState()
    val defaultSpeed    by vm.defaultSpeed.collectAsState()
    val wifiOnly        by vm.wifiOnly.collectAsState()
    val storageCap      by vm.storageCap.collectAsState()
    val unsubBehavior   by vm.unsubBehavior.collectAsState()

    var pendingOpml by remember { mutableStateOf<String?>(null) }

    val createFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/xml")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val content = pendingOpml ?: return@rememberLauncherForActivityResult
        pendingOpml = null
        context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                SettingsDropdownRow(
                    title   = "Refresh interval",
                    options = listOf(1L to "1 hour", 3L to "3 hours", 6L to "6 hours", 12L to "12 hours", 24L to "24 hours"),
                    current = refreshInterval,
                    onChange = { vm.setRefreshInterval(it) },
                )
            }
            item {
                SettingsDropdownRow(
                    title   = "Default playback speed",
                    options = listOf(0.75f to "0.75×", 1.0f to "1.0×", 1.25f to "1.25×", 1.5f to "1.5×", 1.75f to "1.75×", 2.0f to "2.0×"),
                    current = defaultSpeed,
                    onChange = { vm.setDefaultSpeed(it) },
                )
            }
            item {
                ListItem(
                    headlineContent  = { Text("Download over Wi-Fi only") },
                    trailingContent  = { Switch(checked = wifiOnly, onCheckedChange = { vm.setWifiOnly(it) }) },
                )
            }
            item {
                SettingsDropdownRow(
                    title   = "Storage cap",
                    options = listOf(1024L to "1 GB", 2048L to "2 GB", 5120L to "5 GB", 10240L to "10 GB", 0L to "Unlimited"),
                    current = storageCap,
                    onChange = { vm.setStorageCap(it) },
                )
            }
            item {
                SettingsDropdownRow(
                    title   = "On unsubscribe",
                    options = listOf("ask" to "Ask", "keep" to "Keep downloads", "delete" to "Delete downloads"),
                    current = unsubBehavior,
                    onChange = { vm.setUnsubBehavior(it) },
                )
            }
            item { HorizontalDivider() }
            item {
                ListItem(
                    headlineContent  = { Text("Export subscriptions (OPML)") },
                    trailingContent  = {
                        IconButton(onClick = {
                            scope.launch {
                                pendingOpml = vm.generateOpml()
                                createFileLauncher.launch("podder-subscriptions.opml")
                            }
                        }) {
                            Icon(Icons.Default.FileOpen, contentDescription = "Export OPML")
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun <T> SettingsDropdownRow(
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
