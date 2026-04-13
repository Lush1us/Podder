package com.lush1us.podder.ui.discover

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import coil.compose.AsyncImage
import dev.podder.data.discovery.DiscoveryCategory
import dev.podder.data.search.SearchResult
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(onBack: () -> Unit) {
    val vm: DiscoverViewModel = koinViewModel()
    val query by vm.query.collectAsState()
    val uiState by vm.uiState.collectAsState()
    val categoryDetail by vm.categoryDetail.collectAsState()
    val focusRequester = remember { FocusRequester() }
    var pendingSubscribe by remember { mutableStateOf<SearchResult?>(null) }

    BackHandler(enabled = categoryDetail != null) { vm.clearCategory() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value         = query,
                        onValueChange = { vm.onQueryChange(it) },
                        placeholder   = { Text(if (categoryDetail != null) "Search podcasts..." else "Search or discover...") },
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
                        trailingIcon = if (query.isNotEmpty()) {
                            { IconButton(onClick = { vm.onQueryChange("") }) { Icon(Icons.Default.Close, "Clear") } }
                        } else null,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { vm.search() }),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = if (categoryDetail != null) vm::clearCategory else onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        val detail = categoryDetail
        if (detail != null) {
            CategoryDetailContent(
                detail     = detail,
                modifier   = Modifier.padding(padding),
                onSubscribe = { pendingSubscribe = it },
            )
        } else {
            when (val state = uiState) {
                is DiscoverUiState.Loading -> {
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is DiscoverUiState.Discovery -> {
                    DiscoveryContent(
                        state      = state,
                        modifier   = Modifier.padding(padding),
                        onPodcastClick  = { pendingSubscribe = it },
                        onCategoryClick = { vm.loadCategory(it) },
                    )
                }
                is DiscoverUiState.Searching -> {
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is DiscoverUiState.SearchResults -> {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                        items(state.results, key = { it.id }) { result ->
                            SearchResultRow(result = result, onClick = { pendingSubscribe = result })
                            HorizontalDivider()
                        }
                    }
                }
                is DiscoverUiState.SearchEmpty -> {
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        Text("No results for \u00ab${state.query}\u00bb", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                is DiscoverUiState.Error -> {
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.message, style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { vm.retry() }) { Text("Retry") }
                        }
                    }
                }
            }
        }
    }

    pendingSubscribe?.let { result ->
        SubscribeDialog(
            result      = result,
            onSubscribe = { vm.subscribe(result.rssUrl); pendingSubscribe = null },
            onDismiss   = { pendingSubscribe = null },
        )
    }
}

@Composable
private fun DiscoveryContent(
    state: DiscoverUiState.Discovery,
    modifier: Modifier = Modifier,
    onPodcastClick: (SearchResult) -> Unit,
    onCategoryClick: (DiscoveryCategory) -> Unit,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        if (state.trending.isNotEmpty()) {
            item {
                Text(
                    "Trending",
                    style    = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            item {
                LazyRow(
                    contentPadding      = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.trending, key = { it.id }) { podcast ->
                        TrendingCard(podcast = podcast, onClick = { onPodcastClick(podcast) })
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (state.categories.isNotEmpty()) {
            item {
                Text(
                    "Browse by Category",
                    style    = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            items(state.categories, key = { it.id }) { category ->
                ListItem(
                    headlineContent = { Text(category.name) },
                    modifier        = Modifier.clickable { onCategoryClick(category) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun TrendingCard(podcast: SearchResult, onClick: () -> Unit) {
    Column(
        modifier            = Modifier
            .width(120.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model              = podcast.artworkUrl,
            contentDescription = null,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text     = podcast.title,
            style    = MaterialTheme.typography.labelSmall,
            maxLines = 2,
        )
    }
}

@Composable
private fun CategoryDetailContent(
    detail: DiscoverViewModel.CategoryDetail,
    modifier: Modifier = Modifier,
    onSubscribe: (SearchResult) -> Unit,
) {
    if (detail.isLoading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (detail.podcasts.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No podcasts found", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(detail.podcasts, key = { it.id }) { result ->
            SearchResultRow(result = result, onClick = { onSubscribe(result) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun SearchResultRow(result: SearchResult, onClick: () -> Unit) {
    ListItem(
        modifier       = Modifier.clickable(onClick = onClick),
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
        confirmButton = { TextButton(onClick = onSubscribe) { Text("Subscribe") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
