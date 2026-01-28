package com.example.podder.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun PlayerControls(
    modifier: Modifier = Modifier,
    title: String,
    description: String?,
    imageUrl: String?,
    progress: Float = 0f, // 0.0 to 1.0
    isPlaying: Boolean,
    onPlayPause: () -> Unit
) {
    // Calculate 2.5% of screen width for consistent margins
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val margin: Dp = screenWidth * 0.025f

    // Outer container for alignment at bottom center
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = margin),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Column to stack Progress Bar on top of Player Card
        Column(
            modifier = Modifier.fillMaxWidth(0.95f), // 95vw width
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Floating Progress Bar (90vw = 90/95 of parent width)
            ProgressBar(progress = progress, modifier = Modifier.fillMaxWidth(90f / 95f))

            // 2px Gap
            Spacer(modifier = Modifier.height(2.dp))

            // 2. Player Card
            Surface(
                modifier = Modifier
                    .height(64.dp) // Fixed height for consistent image size
                    .fillMaxWidth()
                    .clickable { onPlayPause() },
                shape = RoundedCornerShape(8.dp), // Lowered corner radius
                color = MaterialTheme.colorScheme.surfaceContainer, // Distinct from background
                tonalElevation = 6.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Full Height Image
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f) // Make it square
                            .background(Color.Gray) // Placeholder
                    )

                    // Text Info
                    Column(
                        modifier = Modifier
                            .weight(1f)
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
                        description?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Play/Pause Button
                    IconButton(
                        onClick = onPlayPause,
                        modifier = Modifier.padding(end = 8.dp)
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

@Composable
private fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val barColor = Color(0xFFFF9800) // Orange for elapsed time
    val trackColor = Color.White

    Canvas(
        modifier = modifier.height(6.dp)
    ) {
        val strokeWidth = 3.dp.toPx() // Increased thickness
        val yPos = size.height / 2

        // Draw Track
        drawLine(
            color = trackColor,
            start = Offset(0f, yPos),
            end = Offset(size.width, yPos),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // Draw Progress
        drawLine(
            color = barColor,
            start = Offset(0f, yPos),
            end = Offset(size.width * progress, yPos),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // Draw Indicator Circle
        drawCircle(
            color = Color.Gray,
            radius = 3.dp.toPx(),
            center = Offset(size.width * progress, yPos)
        )
    }
}
