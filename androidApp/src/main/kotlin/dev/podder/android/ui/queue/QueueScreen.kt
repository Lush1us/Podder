package dev.podder.android.ui.queue

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import dev.podder.android.queue.QueueEntry
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(onBack: () -> Unit) {
    val vm: QueueViewModel = koinViewModel()
    val queue by vm.queue.collectAsState()

    // Drag state — shared across all items so the grabbed item can read it in graphicsLayer.
    // dragOffsetY is only read in the graphicsLayer lambda (draw phase), so it doesn't trigger
    // recomposition on every drag delta — only the grabbed item redraws.
    var grabbedId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var itemHeightPx by remember { mutableFloatStateOf(0f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Queue") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (queue.isNotEmpty() && grabbedId == null) {
                        TextButton(onClick = { vm.clear() }) { Text("Clear") }
                    }
                },
            )
        }
    ) { padding ->
        if (queue.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Queue is empty", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(queue, key = { it.episodeId }) { entry ->
                    val isGrabbed = entry.episodeId == grabbedId
                    val currentQueue = rememberUpdatedState(queue)

                    val hPadding by animateDpAsState(
                        targetValue = if (isGrabbed) 2.5.dp else 0.dp,
                        animationSpec = tween(durationMillis = 150),
                        label = "hPadding",
                    )
                    val elevation by animateDpAsState(
                        targetValue = if (isGrabbed) 6.dp else 0.dp,
                        animationSpec = tween(durationMillis = 150),
                        label = "elevation",
                    )
                    val shape = if (isGrabbed) RoundedCornerShape(8.dp) else RectangleShape

                    Column(
                        modifier = (if (isGrabbed) Modifier else Modifier.animateItem())
                            .fillMaxWidth()
                            .onSizeChanged { size ->
                                // All items are same height; capture once.
                                if (itemHeightPx == 0f) itemHeightPx = size.height.toFloat()
                            }
                            .zIndex(if (isGrabbed) 1f else 0f)
                            // graphicsLayer read is draw-phase only — no recomposition on drag.
                            .graphicsLayer { if (isGrabbed) translationY = dragOffsetY }
                            .padding(horizontal = hPadding)
                            .shadow(elevation = elevation, shape = shape, clip = isGrabbed)
                            .background(MaterialTheme.colorScheme.surface, shape)
                            .pointerInput(entry.episodeId) {
                                var workingIndex = -1
                                var maxIndex = -1
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        val q = currentQueue.value
                                        workingIndex = q.indexOfFirst { it.episodeId == entry.episodeId }
                                        maxIndex = q.size - 1
                                        grabbedId = entry.episodeId
                                        dragOffsetY = 0f
                                    },
                                    onDrag = { _, delta ->
                                        dragOffsetY += delta.y
                                        val h = itemHeightPx.takeIf { it > 0f } ?: return@detectDragGesturesAfterLongPress
                                        val threshold = h * 0.3f
                                        // Loop handles fast flings that cover multiple items in one frame.
                                        while (dragOffsetY > threshold && workingIndex < maxIndex) {
                                            vm.reorderQueue(workingIndex, workingIndex + 1)
                                            workingIndex++
                                            dragOffsetY -= h
                                        }
                                        while (dragOffsetY < -threshold && workingIndex > 0) {
                                            vm.reorderQueue(workingIndex, workingIndex - 1)
                                            workingIndex--
                                            dragOffsetY += h
                                        }
                                    },
                                    onDragEnd = { grabbedId = null; dragOffsetY = 0f },
                                    onDragCancel = { grabbedId = null; dragOffsetY = 0f },
                                )
                            },
                    ) {
                        QueueEntryRow(
                            entry = entry,
                            onRemove = if (!isGrabbed && grabbedId == null) {
                                { vm.removeFromQueue(entry.episodeId) }
                            } else null,
                        )
                        if (!isGrabbed) HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueEntryRow(entry: QueueEntry, onRemove: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model              = entry.artworkUrl,
            contentDescription = null,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text     = entry.title,
                style    = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text     = entry.podcastTitle,
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Remove from queue")
            }
        } else {
            // Keep row height stable when X is hidden during drag.
            Spacer(Modifier.size(48.dp))
        }
    }
}
