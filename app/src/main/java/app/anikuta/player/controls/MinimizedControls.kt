package app.anikuta.player.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.anikuta.player.PlayerLoadingState
import app.anikuta.player.PlayerViewModel

/**
 * Phase 1.4 — Minimized video player area with controls overlay.
 *
 * Layout:
 *  - Video surface (aspect-ratio adaptive, fills width)
 *  - Controls overlay (tap to show/hide, auto-hide):
 *    - Center: rewind 10s / play-pause / forward 10s
 *    - Bottom: seekbar + timestamps
 *    - Bottom-left: quality button + subtitle button
 *    - Bottom-right: maximize (fullscreen) button
 *
 * The video surface itself is rendered by PlayerActivity via AndroidView.
 * This composable just draws the controls ON TOP of the video.
 */
@Composable
fun MinimizedControls(
    viewModel: PlayerViewModel,
    onTogglePlay: () -> Unit,
    onSeekRelative: (Int) -> Unit,
    onMaximize: () -> Unit,
    onQualityClick: () -> Unit,
    onSubtitleClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val controlsVisible by viewModel.controlsVisible.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val position by viewModel.position.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val buffering by viewModel.buffering.collectAsState()
    val loadingState by viewModel.loadingState.collectAsState()
    val isSwitchingEpisode by viewModel.isSwitchingEpisode.collectAsState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { viewModel.toggleControls() },
    ) {
        // Gradient overlay for control readability (always present, subtle)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.3f),
                        0.3f to Color.Transparent,
                        0.7f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.5f),
                    ),
                ),
        )

        // Loading / switching episode indicator
        if (buffering || loadingState == PlayerLoadingState.LOADING || isSwitchingEpisode) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier.size(56.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }
        }

        // Controls (show/hide on tap)
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Center controls: rewind / play-pause / forward
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CenterIconButton(
                        icon = Icons.Default.Replay10,
                        contentDescription = "Rewind 10 seconds",
                        onClick = { onSeekRelative(-10) },
                    )
                    Box(contentAlignment = Alignment.Center) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                IconButton(onClick = onTogglePlay) {
                                    Icon(
                                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = Color.Black,
                                        modifier = Modifier.size(28.dp),
                                    )
                                }
                            }
                        }
                    }
                    CenterIconButton(
                        icon = Icons.Default.Forward10,
                        contentDescription = "Forward 10 seconds",
                        onClick = { onSeekRelative(10) },
                    )
                }

                // Bottom controls: seekbar + timestamps + quality/sub/maximize
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    // Seekbar
                    MinimizedSeekbar(
                        position = position,
                        duration = duration,
                        onSeekTo = { /* will be wired in Phase 2 */ },
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    // Bottom row: left (quality + subtitle) | right (maximize)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SmallIconButton(
                                icon = Icons.Default.HighQuality,
                                contentDescription = "Quality",
                                onClick = onQualityClick,
                            )
                            SmallIconButton(
                                icon = Icons.Default.Subtitles,
                                contentDescription = "Subtitles",
                                onClick = onSubtitleClick,
                            )
                        }
                        SmallIconButton(
                            icon = Icons.Default.Fullscreen,
                            contentDescription = "Fullscreen",
                            onClick = onMaximize,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.4f),
        modifier = Modifier.size(36.dp),
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SmallIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.15f),
        modifier = Modifier.size(32.dp),
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun MinimizedSeekbar(
    position: Int,
    duration: Int,
    onSeekTo: (Int) -> Unit,
) {
    val progress = if (duration > 0) position.toFloat() / duration else 0f
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
            )
        }
        Spacer(modifier = Modifier.size(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTime(position),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = formatTime(duration),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun formatTime(seconds: Int): String {
    if (seconds <= 0) return "0:00"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
}
