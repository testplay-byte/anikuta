package app.anikuta.player.controls.sheets

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.anikuta.player.PlayerViewModel
import app.anikuta.player.VideoTrack
import app.anikuta.source.api.model.Video

// ═══════════════════════════════════════════════════════════════════
// Phase 3.1 — Quality Sheet
// ═══════════════════════════════════════════════════════════════════

/**
 * Quality selection bottom sheet.
 * Lists available video qualities for the current episode.
 * Tap to switch — the Activity reloads the video at the new quality.
 */
@Composable
fun QualitySheet(
    videos: List<Video>,
    currentVideoUrl: String,
    onSelect: (Video) -> Unit,
    onDismiss: () -> Unit,
) {
    // FIX (Part 2): Parse each video with VideoTitleParser to show clean
    // quality labels (e.g. "1080p") instead of raw titles like
    // "VidPlay-1 - SUB - 1080p". Only show videos matching the current
    // server + audio version (since quality switching should stay within
    // the same server/audio).
    PlayerSheet(title = "Quality", onDismiss = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            items(videos, key = { it.videoUrl }) { video ->
                val parsed = app.anikuta.ui.detail.VideoTitleParser.parse(video)
                val qualityLabel = parsed.quality?.let { "${it}p" } ?: "Unknown"
                val subtitle = buildString {
                    append(parsed.server)
                    append(" • ")
                    append(parsed.audio.label)
                }
                SheetOption(
                    title = qualityLabel,
                    subtitle = subtitle,
                    selected = video.videoUrl == currentVideoUrl,
                    onClick = { onSelect(video); onDismiss() },
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Phase 3.2 — Subtitle Tracks Sheet
// ═══════════════════════════════════════════════════════════════════

/**
 * Subtitle track selection bottom sheet.
 * Lists available subtitle tracks from MPV's track-list.
 * Includes an "Off" option (id = -1).
 *
 * The "Subtitle settings" row opens a SEPARATE sheet that is height-constrained
 * (not full screen) so the video player remains visible behind it. The settings
 * panel scrolls internally if needed.
 */
@Composable
fun SubtitleTracksSheet(
    viewModel: PlayerViewModel,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    onApplySettings: () -> Unit = {},
) {
    val tracks by viewModel.subtitleTracks.collectAsState()
    val currentId by viewModel.currentSubtitleId.collectAsState()
    var showSettings by remember { mutableStateOf(false) }

    if (showSettings) {
        // Subtitle settings panel — height-constrained sheet (not full screen)
        // Uses a custom ModalBottomSheet with a max height so the video player
        // remains visible behind the sheet.
        SubtitleSettingsSheet(
            onDismiss = { showSettings = false },
            onApplySettings = onApplySettings,
        )
        return
    }

    PlayerSheet(title = "Subtitles", onDismiss = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            // "Subtitle settings" button at top
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSettings = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = "Subtitle Settings",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            // Track list
            items(tracks, key = { it.id }) { track ->
                SheetOption(
                    title = track.name,
                    subtitle = track.language,
                    selected = track.id == currentId,
                    onClick = { onSelect(track.id); onDismiss() },
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Phase 3.3 — Audio Tracks Sheet
// ═══════════════════════════════════════════════════════════════════

/**
 * Audio track selection bottom sheet.
 * Lists available audio tracks from MPV's track-list.
 * Includes an "Off" option (id = -1).
 */
@Composable
fun AudioTracksSheet(
    viewModel: PlayerViewModel,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val tracks by viewModel.audioTracks.collectAsState()
    val currentId by viewModel.currentAudioId.collectAsState()

    PlayerSheet(title = "Audio", onDismiss = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            items(tracks, key = { it.id }) { track ->
                SheetOption(
                    title = track.name,
                    subtitle = track.language,
                    selected = track.id == currentId,
                    onClick = { onSelect(track.id); onDismiss() },
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Phase 3.4 — Server Sheet
// ═══════════════════════════════════════════════════════════════════

/**
 * Server selection bottom sheet.
 * Lists available servers for the current episode.
 */
@Composable
fun ServerSheet(
    servers: List<String>,
    currentServer: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    PlayerSheet(title = "Server", onDismiss = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            items(servers, key = { it }) { server ->
                SheetOption(
                    title = server,
                    selected = server == currentServer,
                    onClick = { onSelect(server); onDismiss() },
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Phase 3.5 — Speed Sheet
// ═══════════════════════════════════════════════════════════════════

/**
 * Playback speed bottom sheet.
 * Slider from 0.25x to 4.0x + preset buttons.
 */
@Composable
fun SpeedSheet(
    currentSpeed: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var speed by remember { mutableFloatStateOf(currentSpeed) }

    PlayerSheet(title = "Playback Speed", onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "%.2fx".format(speed),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Slider(
                value = speed,
                onValueChange = { speed = it },
                onValueChangeFinished = { onSelect(speed) },
                valueRange = 0.25f..4.0f,
                steps = 14, // 0.25 increments
            )
            // Presets
            val presets = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
            ) {
                presets.forEach { preset ->
                    androidx.compose.material3.TextButton(
                        onClick = {
                            speed = preset
                            onSelect(preset)
                        },
                    ) {
                        Text(
                            "${preset}x",
                            color = if (preset == speed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (preset == speed) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Phase 3.6 — More Options Sheet
// ═══════════════════════════════════════════════════════════════════

/**
 * More options bottom sheet (⋯ button).
 * Advanced settings: subtitle delay, audio delay, screenshot, sleep timer.
 */
@Composable
fun MoreOptionsSheet(
    onSubtitleDelay: () -> Unit,
    onAudioDelay: () -> Unit,
    onScreenshot: () -> Unit,
    onSleepTimer: () -> Unit,
    onDismiss: () -> Unit,
) {
    PlayerSheet(title = "More Options", onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            MoreOptionRow(title = "Subtitle delay", subtitle = "Adjust subtitle timing", onClick = { onSubtitleDelay(); onDismiss() })
            MoreOptionRow(title = "Audio delay", subtitle = "Adjust audio timing", onClick = { onAudioDelay(); onDismiss() })
            MoreOptionRow(title = "Screenshot", subtitle = "Capture current frame", onClick = { onScreenshot(); onDismiss() })
            MoreOptionRow(title = "Sleep timer", subtitle = "Stop playback after N minutes", onClick = { onSleepTimer(); onDismiss() })
        }
    }
}

@Composable
private fun MoreOptionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Subtitle Settings Sheet — height-constrained (not full screen)
// ═══════════════════════════════════════════════════════════════════

/**
 * Height-constrained bottom sheet for subtitle settings.
 *
 * Unlike the full PlayerSheet (which uses skipPartiallyExpanded = true and
 * takes most of the screen), this sheet uses a max height of ~60% of the
 * screen so the video player remains visible behind it. The settings panel
 * scrolls internally if the content exceeds the sheet height.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleSettingsSheet(
    onDismiss: () -> Unit,
    onApplySettings: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Constrain height to ~60% of screen so video player stays visible
                .heightIn(max = 420.dp)
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(
                text = "Subtitle Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            // The settings panel scrolls internally
            app.anikuta.player.controls.SubtitleSettingsPanel(
                onSettingsChanged = {
                    onApplySettings()
                    Log.d("PlayerSheets", "Subtitle settings changed — applying live")
                },
            )
        }
    }
}
