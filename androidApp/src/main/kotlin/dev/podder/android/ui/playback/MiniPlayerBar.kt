package dev.podder.android.ui.playback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.podder.domain.model.PlaybackState
import kotlin.math.pow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.androidx.compose.koinViewModel

/** Exponent for the scrub speed curve. 1.0 = linear, 2.0 = quadratic (slow near entry, fast at edges). */
private const val SCRUB_CURVE_EXPONENT = 2.0
/** Milliseconds of stillness before resuming audio mid-hold */
private const val STOP_RESUME_DELAY_MS = 200L

@Composable
fun MiniPlayerBar(
    onArtworkClick: (podcastId: String) -> Unit,
    onMiddleClick: (episodeId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: PlaybackViewModel = koinViewModel()
    val state by vm.state.collectAsState()
    val info by vm.nowPlayingInfo.collectAsState()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val playerHeightDp = (screenWidthDp * 0.20f).dp

    // Scroll mode state
    var scrollMode by remember { mutableStateOf(false) }
    var wasPlayingBeforeScroll by remember { mutableStateOf(false) }
    var scrollSeekPositionMs by remember { mutableStateOf(0L) }
    var baseSeekPositionMs by remember { mutableStateOf(0L) }

    // Non-observable Job holder — does NOT trigger recomposition
    val stopResumeJobRef = remember { object { var job: Job? = null } }

    // rememberUpdatedState captures latest lambdas without restarting the gesture coroutine
    val currentOnMiddleClick by rememberUpdatedState(onMiddleClick)
    val currentOnArtworkClick by rememberUpdatedState(onArtworkClick)

    val visible = state !is PlaybackState.Idle && state !is PlaybackState.Error

    AnimatedVisibility(
        visible = visible,
        enter   = slideInVertically(initialOffsetY = { it }),
        exit    = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier,
    ) {
        Surface(
            tonalElevation  = 4.dp,
            shadowElevation = 4.dp,
            modifier = Modifier
                .navigationBarsPadding()
                .pointerInput(Unit) {
                    val viewConfig = viewConfiguration
                    awaitEachGesture {
                        // Await the initial down event (awaitEachGesture guarantees we start
                        // after all pointers are up, so the first event is always a down).
                        val downEvent = awaitPointerEvent()
                        val down = downEvent.changes.first()
                        // Artwork zone width in px = playerHeightDp.toPx() (it's a square)
                        val artworkZonePx = with(density) { playerHeightDp.toPx() }

                        val longPressResult = withTimeoutOrNull(viewConfig.longPressTimeoutMillis) {
                            var outcome = "none"
                            while (true) {
                                val event  = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break

                                if (!change.pressed) {
                                    // Finger lifted before timeout → tap
                                    outcome = "tap"
                                    break
                                }

                                val dx = change.position.x - down.position.x
                                val dy = change.position.y - down.position.y

                                if (dy < -viewConfig.touchSlop && kotlin.math.abs(dy) > kotlin.math.abs(dx)) {
                                    // Upward swipe
                                    change.consume()
                                    outcome = "swipe_up"
                                    break
                                }

                                if (kotlin.math.abs(dx) > viewConfig.touchSlop ||
                                    kotlin.math.abs(dy) > viewConfig.touchSlop) {
                                    // Diagonal or horizontal movement before long press established — abort
                                    break
                                }
                            }
                            outcome
                        }

                        when {
                            longPressResult == "tap" -> {
                                // Only route to episode detail if tap was outside the artwork zone
                                if (down.position.x > artworkZonePx) {
                                    info?.episodeId?.let { currentOnMiddleClick(it) }
                                }
                                // If in artwork zone: artwork's own Modifier.clickable handles it
                            }

                            longPressResult == "swipe_up" -> {
                                info?.episodeId?.let { currentOnMiddleClick(it) }
                            }

                            longPressResult == null -> {
                                // Timeout elapsed = long press established → enter scrub mode
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                vm.setScrubbing(true)
                                scrollMode = true
                                wasPlayingBeforeScroll = state is PlaybackState.Playing

                                // Pause ONCE on scrub entry — not on every drag frame
                                if (wasPlayingBeforeScroll) vm.pause()

                                val currentPos = when (val s = state) {
                                    is PlaybackState.Playing -> s.positionMs
                                    is PlaybackState.Paused  -> s.positionMs
                                    else -> 0L
                                }
                                baseSeekPositionMs   = currentPos
                                scrollSeekPositionMs = currentPos

                                val tapXDp         = with(density) { down.position.x.toDp().value }
                                val maxLeftDragDp  = tapXDp.coerceAtLeast(1f)
                                val maxRightDragDp = (screenWidthDp.toFloat() - tapXDp).coerceAtLeast(1f)
                                val entryElapsedMs   = currentPos
                                val entryRemainingMs = ((info?.durationMs ?: 0L) - currentPos).coerceAtLeast(1L)
                                var cumulativeDragDp = 0f

                                // Track horizontal drag until finger lifts
                                while (true) {
                                    val event  = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    if (!change.pressed) break

                                    change.consume()

                                    val dpDelta = with(density) { (change.position - change.previousPosition).x.toDp().value }
                                    cumulativeDragDp += dpDelta
                                    scrollSeekPositionMs = if (cumulativeDragDp <= 0f) {
                                        val t = (-cumulativeDragDp / maxLeftDragDp).coerceIn(0.0, 1.0)
                                        baseSeekPositionMs - (entryElapsedMs * t.toDouble().pow(SCRUB_CURVE_EXPONENT)).toLong()
                                    } else {
                                        val t = (cumulativeDragDp / maxRightDragDp).coerceIn(0.0, 1.0)
                                        baseSeekPositionMs + (entryRemainingMs * t.toDouble().pow(SCRUB_CURVE_EXPONENT)).toLong()
                                    }
                                    vm.seekTo(scrollSeekPositionMs)

                                    // Restart the stop-resume timer on each movement
                                    stopResumeJobRef.job?.cancel()
                                    stopResumeJobRef.job = scope.launch {
                                        delay(STOP_RESUME_DELAY_MS)
                                        if (scrollMode) {
                                            vm.resume()
                                        }
                                    }
                                }

                                // Finger lifted — commit seek and restore playback
                                stopResumeJobRef.job?.cancel()
                                vm.seekTo(scrollSeekPositionMs)
                                if (wasPlayingBeforeScroll) vm.resume()
                                vm.setScrubbing(false)
                                scrollMode = false
                            }

                            // else: aborted (diagonal/horizontal movement before long press) — do nothing
                        }
                    }
                },
        ) {
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(playerHeightDp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // ── Artwork ──────────────────────────────────────────────
                    AsyncImage(
                        model              = info?.artworkUrl,
                        contentDescription = "Podcast artwork",
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f)
                            .clickable { info?.podcastId?.let(onArtworkClick) },
                    )

                    // ── Middle (title + elapsed) ──────────────────────────────
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        // Show scroll position hint in scroll mode
                        if (scrollMode) {
                            Text(
                                text  = "⟵  ${formatPlayerTime(scrollSeekPositionMs)}  ⟶",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Text(
                                text     = info?.title ?: "",
                                style    = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        val positionMs = if (scrollMode) scrollSeekPositionMs else when (val s = state) {
                            is PlaybackState.Playing -> s.positionMs
                            is PlaybackState.Paused  -> s.positionMs
                            else -> 0L
                        }
                        val durationMs = info?.durationMs ?: 0L
                        Text(
                            text  = "${formatPlayerTime(positionMs)} / ${formatPlayerTime(durationMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // ── Controls ─────────────────────────────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(64.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (state) {
                            is PlaybackState.Buffering -> {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            }
                            is PlaybackState.Playing -> {
                                IconButton(onClick = { vm.pause() }) {
                                    Icon(
                                        Icons.Default.Pause,
                                        contentDescription = "Pause",
                                        modifier = Modifier.size(32.dp),
                                    )
                                }
                            }
                            else -> {
                                IconButton(onClick = { vm.resume() }) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = "Play",
                                        modifier = Modifier.size(32.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Progress bar — top edge, 95% width, 2dp tall ──────────────
                val positionMs = if (scrollMode) scrollSeekPositionMs else when (val s = state) {
                    is PlaybackState.Playing -> s.positionMs
                    is PlaybackState.Paused  -> s.positionMs
                    else -> 0L
                }
                val durationMs = info?.durationMs ?: 0L
                val progress = if (durationMs > 0L) positionMs.toFloat() / durationMs.toFloat() else 0f

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .height(2.dp)
                        .align(Alignment.TopCenter),
                )

                // ── Scroll mode overlay indicator ─────────────────────────────
                if (scrollMode) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 4.dp),
                        color  = MaterialTheme.colorScheme.primaryContainer,
                        shape  = MaterialTheme.shapes.small,
                        tonalElevation = 2.dp,
                    ) {
                        Text(
                            text     = "SCRUB",
                            style    = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Format milliseconds as h:mm:ss or m:ss, stripping leading zeros. */
fun formatPlayerTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%d:%02d".format(m, s)
}
