package com.example.podder.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
    onPlayerClick: () -> Unit = {}
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val margin: Dp = screenWidth * 0.025f

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
                progress = progress,
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
                        // Duration display: "11/14m"
                        val durationText = formatElapsedDuration(elapsedMillis, durationMillis)
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
    val elapsedMinutes = (elapsedMillis / 60000).toInt()
    val totalMinutes = (durationMillis / 60000).toInt()
    return "$elapsedMinutes/${totalMinutes}m"
}

@Composable
private fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val barColor = Color(0xFFFF9800)
    val trackColor = Color.White

    Canvas(
        modifier = modifier.height(8.dp)
    ) {
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
