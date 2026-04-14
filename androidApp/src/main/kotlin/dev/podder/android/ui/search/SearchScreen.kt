package com.lush1us.podder.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.podder.data.search.SearchResult
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onBack: () -> Unit) {
    val vm: SearchViewModel = koinViewModel()
    val query by vm.query.collectAsState()
    val uiState by vm.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }
    var pendingSubscribe by remember { mutableStateOf<SearchResult?>(null) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value         = query,
                        onValueChange = { vm.onQueryChange(it) },
                        placeholder   = { Text("Search podcasts...") },
                        singleLine    = true,
                        modifier      = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        colors        = TextFieldDefaults.colors(
                            focusedContainerColor   = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedIndicatorColor   = MaterialTheme.colorScheme.surface,
                            unfocusedIndicatorColor = MaterialTheme.colorScheme.surface,
                        ),
                        trailingIcon  = if (query.isNotEmpty()) {
                            {
                                IconButton(onClick = { vm.onQueryChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        } else null,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { vm.search() }),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is SearchUiState.Idle -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Search for a podcast", style = MaterialTheme.typography.bodyLarge)
                }
            }
            is SearchUiState.Loading -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            is SearchUiState.Empty -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No results for \u00ab${state.query}\u00bb",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            is SearchUiState.Error -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.message, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { vm.search() }) { Text("Retry") }
                    }
                }
            }
            is SearchUiState.Success -> {
                val listState = rememberLazyListState()
                val lastVisible = remember {
                    derivedStateOf {
                        val info = listState.layoutInfo
                        info.visibleItemsInfo.lastOrNull()?.index ?: 0
                    }
                }

                LaunchedEffect(lastVisible.value) {
                    if (lastVisible.value >= state.results.size - 5) {
                        vm.loadNextPage()
                    }
                }

                LazyColumn(
                    state    = listState,
                    modifier = Modifier.fillMaxSize().padding(padding),
                ) {
                    items(state.results, key = { it.id }) { result ->
                        SearchResultRow(result = result, onClick = { pendingSubscribe = result })
                        HorizontalDivider()
                    }
                    if (state.isPaginating) {
                        item {
                            Box(
                                Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    pendingSubscribe?.let { result ->
        SubscribeDialog(
            result    = result,
            onSubscribe = {
                vm.subscribe(result.rssUrl)
                pendingSubscribe = null
            },
            onDismiss = { pendingSubscribe = null },
        )
    }
}

@Composable
private fun SearchResultRow(result: SearchResult, onClick: () -> Unit) {
    ListItem(
        modifier      = Modifier.clickable(onClick = onClick),
        leadingContent = {
            AsyncImage(
                model              = result.artworkUrl,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        },
        headlineContent   = { Text(result.title, maxLines = 1) },
        supportingContent = {
            Text(
                "${result.author} · ${result.episodeCount} episodes",
                maxLines = 1,
                style    = MaterialTheme.typography.bodySmall,
            )
        },
    )
}

@Composable
private fun SubscribeDialog(
    result: SearchResult,
    onSubscribe: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(result.title) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AsyncImage(
                    model              = result.artworkUrl,
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .align(Alignment.CenterHorizontally),
                )
                if (result.author.isNotBlank()) {
                    Text(result.author, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSubscribe) { Text("Subscribe") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
