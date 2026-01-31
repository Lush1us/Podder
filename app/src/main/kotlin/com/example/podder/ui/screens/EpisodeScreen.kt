package com.example.podder.ui.screens

import android.text.Html
import android.text.style.URLSpan
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.podder.player.PlayerUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeScreen(
    playerState: PlayerUiState,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Episode") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Episode Image at top
            AsyncImage(
                model = playerState.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(Color.Gray)
            )

            // Episode Info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Title
                Text(
                    text = playerState.currentTitle,
                    style = MaterialTheme.typography.headlineSmall
                )

                // Podcast name (artist)
                playerState.artist?.let { artist ->
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Duration info
                if (playerState.durationMillis > 0) {
                    val elapsedMin = (playerState.currentPositionMillis / 60000).toInt()
                    val totalMin = (playerState.durationMillis / 60000).toInt()
                    Text(
                        text = "Progress: ${elapsedMin}m / ${totalMin}m",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Description
                playerState.description?.let { description ->
                    val uriHandler = LocalUriHandler.current
                    val linkColor = MaterialTheme.colorScheme.primary
                    val textColor = MaterialTheme.colorScheme.onSurface
                    val annotatedText = remember(description) {
                        htmlToAnnotatedString(description, linkColor)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    ClickableText(
                        text = annotatedText,
                        style = MaterialTheme.typography.bodyMedium.copy(color = textColor),
                        onClick = { offset ->
                            annotatedText.getStringAnnotations("URL", offset, offset)
                                .firstOrNull()?.let { uriHandler.openUri(it.item) }
                        }
                    )
                }
            }
        }
    }
}

private fun htmlToAnnotatedString(html: String, linkColor: Color): AnnotatedString {
    val spanned = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
    return buildAnnotatedString {
        append(spanned.toString())
        spanned.getSpans(0, spanned.length, URLSpan::class.java).forEach { urlSpan ->
            val start = spanned.getSpanStart(urlSpan)
            val end = spanned.getSpanEnd(urlSpan)
            addStyle(
                SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                start,
                end
            )
            addStringAnnotation("URL", urlSpan.url, start, end)
        }
    }
}
