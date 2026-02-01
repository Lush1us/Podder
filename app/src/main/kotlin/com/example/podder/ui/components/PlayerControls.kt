package com.example.podder.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun PlayerControls(
    modifier: Modifier = Modifier,
    title: String,
    description: String?,
    imageUrl: String?,
    progress: Float = 0f,
    elapsedMillis: Long = 0L,
    durationMillis: Long = 0L,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onPlayerClick: () -> Unit = {},
    onSeekTo: (Long) -> Unit = {}
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val margin: Dp = screenWidth * 0.025f

    // Hoisted drag state for real-time updates across progress bar AND timer
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    // Use drag values while dragging, otherwise use player state
    val displayProgress = if (isDragging) dragProgress else progress
    val displayElapsedMillis = if (isDragging) {
        (dragProgress * durationMillis).toLong()
    } else {
        elapsedMillis
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(margin),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Progress Bar (90vw = 90/95 of parent since parent has 2.5vw margin each side)
            ProgressBar(
                progress = displayProgress,
                durationMillis = durationMillis,
                onSeekTo = onSeekTo,
                onDragStart = { newProgress ->
                    isDragging = true
                    dragProgress = newProgress
                },
                onDrag = { newProgress ->
                    dragProgress = newProgress
                },
                onDragEnd = {
                    isDragging = false
                },
                modifier = Modifier.fillMaxWidth(90f / 95f)
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Player Card
            Surface(
                modifier = Modifier
                    .height(64.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 6.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Full Height Image - tappable for episode page
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f)
                            .background(Color.Gray)
                            .clickable { onPlayerClick() }
                    )

                    // Text Info - tappable for episode page
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onPlayerClick() }
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // Duration display: "11/14m" - uses displayElapsedMillis for real-time drag updates
                        val durationText = formatElapsedDuration(displayElapsedMillis, durationMillis)
                        if (durationText.isNotEmpty()) {
                            Text(
                                text = durationText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    // Play/Pause Button - square area mirrored from image
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f)
                            .clickable { onPlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play"
                        )
                    }
                }
            }
        }
    }
}

private fun formatElapsedDuration(elapsedMillis: Long, durationMillis: Long): String {
    if (durationMillis <= 0) return ""
    return "${formatTime(elapsedMillis, durationMillis)} / ${formatTime(durationMillis, durationMillis)}"
}

private fun formatTime(millis: Long, durationMillis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val durationHours = durationMillis / 1000 / 3600
    return if (durationHours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

@Composable
private fun ProgressBar(
    progress: Float,
    durationMillis: Long,
    onSeekTo: (Long) -> Unit,
    onDragStart: (Float) -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val barColor = Color(0xFFFF9800)
    val trackColor = Color.White
    var barWidth = 0f

    // Track drag position locally to avoid stale closure issues
    var currentDragProgress by remember { mutableFloatStateOf(0f) }

    Canvas(
        modifier = modifier
            .height(24.dp) // Increased height for easier tapping
            .pointerInput(durationMillis) {
                detectTapGestures { offset ->
                    if (durationMillis > 0 && barWidth > 0) {
                        val newProgress = (offset.x / barWidth).coerceIn(0f, 1f)
                        val newPositionMillis = (newProgress * durationMillis).toLong()
                        onSeekTo(newPositionMillis)
                    }
                }
            }
            .pointerInput(durationMillis) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        if (durationMillis > 0 && barWidth > 0) {
                            val newProgress = (offset.x / barWidth).coerceIn(0f, 1f)
                            currentDragProgress = newProgress
                            onDragStart(newProgress)
                        }
                    },
                    onDragEnd = {
                        // Use locally tracked drag position instead of stale progress param
                        val newPositionMillis = (currentDragProgress * durationMillis).toLong()
                        onSeekTo(newPositionMillis)
                        onDragEnd()
                    },
                    onDragCancel = {
                        onDragEnd()
                    },
                    onHorizontalDrag = { change, _ ->
                        if (durationMillis > 0 && barWidth > 0) {
                            val newProgress = (change.position.x / barWidth).coerceIn(0f, 1f)
                            currentDragProgress = newProgress
                            onDrag(newProgress)
                        }
                    }
                )
            }
    ) {
        barWidth = size.width
        val trackStrokeWidth = 3.dp.toPx()
        val progressStrokeWidth = 4.dp.toPx() // 1px wider than track
        val yPos = size.height / 2

        // Draw Track
        drawLine(
            color = trackColor,
            start = Offset(0f, yPos),
            end = Offset(size.width, yPos),
            strokeWidth = trackStrokeWidth,
            cap = StrokeCap.Round
        )

        // Draw Progress (1px wider)
        drawLine(
            color = barColor,
            start = Offset(0f, yPos),
            end = Offset(size.width * progress, yPos),
            strokeWidth = progressStrokeWidth,
            cap = StrokeCap.Round
        )

        // Draw Indicator Circle (1px bigger)
        drawCircle(
            color = Color.Gray,
            radius = 4.dp.toPx(),
            center = Offset(size.width * progress, yPos)
        )
    }
}
