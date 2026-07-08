package app.anikuta.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.animesource.model.Video
import app.anikuta.source.api.model.SEpisode

/**
 * Phase 7 — Redesigned video picker bottom sheet.
 *
 * Key improvements over the old picker:
 *  1. **Scrollable** — uses `LazyColumn` (fixes the "can't scroll to bottom" bug).
 *  2. **Collapsible servers** — tap a server header to expand/collapse its videos.
 *  3. **Audio-version grouping** — dedicated sections for SUB / DUB / HSUB.
 *  4. **Quality descending sort** — 1080p at top, 360p at bottom within each server.
 *  5. **Cached state** — shows instantly with a "Refreshing…" badge on cache hit.
 *
 * Data structure: [AudioSection] → [ServerSection] (collapsible) → [Video] (sorted desc).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPickerSheet(
    state: VideoPickerState,
    expandedServers: Set<String>,
    onToggleServer: (String) -> Unit,
    onPickVideo: (Video, SEpisode) -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        is VideoPickerState.Resolving -> {
            // Full-screen resolving overlay (no bottom sheet yet)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Resolving video…", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Fetching servers from the extension",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        is VideoPickerState.Cached -> {
            VideoPickerBottomSheet(
                episode = state.episode,
                audioSections = state.audioSections,
                isRefreshing = state.isRefreshing,
                expandedServers = expandedServers,
                onToggleServer = onToggleServer,
                onPickVideo = { video -> onPickVideo(video, state.episode) },
                onDismiss = onDismiss,
            )
        }
        is VideoPickerState.Show -> {
            VideoPickerBottomSheet(
                episode = state.episode,
                audioSections = state.audioSections,
                isRefreshing = false,
                expandedServers = expandedServers,
                onToggleServer = onToggleServer,
                onPickVideo = { video -> onPickVideo(video, state.episode) },
                onDismiss = onDismiss,
            )
        }
        VideoPickerState.Hidden -> { /* nothing to show */ }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoPickerBottomSheet(
    episode: SEpisode,
    audioSections: List<AudioSection>,
    isRefreshing: Boolean,
    expandedServers: Set<String>,
    onToggleServer: (String) -> Unit,
    onPickVideo: (Video) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            // Header row: title + refreshing badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Select quality",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (isRefreshing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Refreshing…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Scrollable list — LazyColumn fixes the "can't scroll to bottom" bug
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp),
            ) {
                audioSections.forEach { section ->
                    // Audio version header (SUB / DUB / HSUB)
                    item(key = "audio_${section.audio.name}") {
                        AudioHeader(section.audio, section.servers.size)
                    }
                    // Server sections within this audio version
                    section.servers.forEach { serverSection ->
                        val expandKey = "${section.audio.name}_${serverSection.serverName}"
                        val isExpanded = expandKey !in expandedServers  // default expanded

                        item(key = "server_$expandKey") {
                            ServerHeader(
                                serverName = serverSection.serverName,
                                videoCount = serverSection.videos.size,
                                isExpanded = isExpanded,
                                onClick = { onToggleServer(expandKey) },
                            )
                        }
                        // Videos (quality sorted descending) — only show when expanded
                        if (isExpanded) {
                            items(serverSection.videos, key = { v -> v.videoUrl + v.videoTitle }) { video ->
                                VideoRow(
                                    video = video,
                                    onClick = { onPickVideo(video) },
                                )
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioHeader(audio: AudioVersion, serverCount: Int) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = audio.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$serverCount server${if (serverCount != 1) "s" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun ServerHeader(
    serverName: String,
    videoCount: Int,
    isExpanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = serverName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$videoCount",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VideoRow(
    video: Video,
    onClick: () -> Unit,
) {
    val parsed = VideoTitleParser.parse(video)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 48.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Quality chip (e.g. "1080p")
        val qualityLabel = parsed.quality?.let { "${it}p" } ?: "Unknown"
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text(
                text = qualityLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = video.videoTitle.ifBlank { qualityLabel },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "Play",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
    }
}
