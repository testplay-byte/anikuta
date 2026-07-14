package app.anikuta.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.anikuta.download.AudioFallback
import app.anikuta.download.PriorityMode
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Phase 7 — Downloads settings screen with drag-and-drop priority lists.
 *
 * 3 reorderable lists (quality, audio, server) + quality-vs-audio priority
 * toggle + audio fallback radio + WiFi/delete toggles.
 */
@Composable
fun DownloadsSettingsScreen(onBack: () -> Unit) {
    val viewModel: DownloadsViewModel = viewModel()
    val qualityOrder by viewModel.qualityOrder.collectAsState()
    val audioOrder by viewModel.audioOrder.collectAsState()
    val serverOrder by viewModel.serverOrder.collectAsState()
    val priorityMode by viewModel.priorityMode.collectAsState()
    val audioFallback by viewModel.audioFallback.collectAsState()

    SettingsSubpageScaffold(title = "Downloads", onBack = onBack) {
        // Use a scrollable Column (NOT LazyColumn) so the inner ReorderableLazyColumn
        // doesn't get infinite-height constraints. Nested LazyColumns crash; a
        // scrollable Column wrapping a LazyColumn is fine because the Column
        // provides bounded height.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- Quality priority (drag-and-drop) ----
            SettingsGroupCard(title = "Preferred quality (drag to reorder)") {
                if (qualityOrder.isEmpty()) {
                    Text(
                        "No qualities set. Default: 1080p → 720p → 360p",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    ReorderableStringList(
                        items = qualityOrder,
                        onReorder = { from, to -> viewModel.reorderQuality(from, to) },
                    )
                }
            }

            // ---- Audio priority (drag-and-drop) ----
            SettingsGroupCard(title = "Preferred audio (drag to reorder)") {
                if (audioOrder.isEmpty()) {
                    Text(
                        "No audio versions set. Default: Sub → Dub",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    ReorderableStringList(
                        items = audioOrder,
                        onReorder = { from, to -> viewModel.reorderAudio(from, to) },
                    )
                }
            }

            // ---- Quality vs Audio priority ----
            SettingsGroupCard(title = "Priority mode") {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    PriorityRadioButton(
                        label = "Quality first",
                        subtitle = "Match quality, then audio version",
                        selected = priorityMode == PriorityMode.QUALITY_FIRST,
                        onClick = { viewModel.setPriorityMode(PriorityMode.QUALITY_FIRST) },
                    )
                    PriorityRadioButton(
                        label = "Audio first",
                        subtitle = "Match audio version, then quality",
                        selected = priorityMode == PriorityMode.AUDIO_FIRST,
                        onClick = { viewModel.setPriorityMode(PriorityMode.AUDIO_FIRST) },
                    )
                }
            }

            // ---- Audio fallback mode ----
            SettingsGroupCard(title = "Audio not available") {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    PriorityRadioButton(
                        label = "Download next available audio",
                        subtitle = "Fall back to the next audio version in your priority list",
                        selected = audioFallback == AudioFallback.NEXT,
                        onClick = { viewModel.setAudioFallback(AudioFallback.NEXT) },
                    )
                    PriorityRadioButton(
                        label = "Show error (don't download)",
                        subtitle = "Skip this episode if the preferred audio isn't available",
                        selected = audioFallback == AudioFallback.FAIL,
                        onClick = { viewModel.setAudioFallback(AudioFallback.FAIL) },
                    )
                }
            }

            // ---- Server priority (drag-and-drop) ----
            SettingsGroupCard(title = "Preferred servers (drag to reorder)") {
                if (serverOrder.isEmpty()) {
                    Text(
                        "No server priority set. Auto-picks the first available server.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    ReorderableStringList(
                        items = serverOrder,
                        onReorder = { from, to -> viewModel.reorderServer(from, to) },
                    )
                }
            }

            // ---- Download method ----
            SettingsGroupCard(title = "Download method") {
                Column {
                    val method by viewModel.downloadMethod.collectAsState()
                    DownloadMethodOption(
                        label = "Single-pass (recommended)",
                        description = "Aniyomi's approach. Correct size & duration. No resume after failure.",
                        selected = method == "single_pass",
                        onClick = { viewModel.setDownloadMethod("single_pass") },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DownloadMethodOption(
                        label = "HLS direct",
                        description = "Downloads .ts segments via HTTP. Resume + precise progress. May not work with all sources.",
                        selected = method == "hls_direct",
                        onClick = { viewModel.setDownloadMethod("hls_direct") },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DownloadMethodOption(
                        label = "Segment (legacy)",
                        description = "FFmpeg -ss segments. Resume + precise progress. Wrong size for short videos.",
                        selected = method == "segment",
                        onClick = { viewModel.setDownloadMethod("segment") },
                    )
                }
            }

            // ---- Toggles ----
            SettingsGroupCard(title = "General") {
                Column {
                    val wifiOnly by viewModel.downloadOverWifiOnly.collectAsState()
                    ToggleRow(
                        label = "Download over WiFi only",
                        checked = wifiOnly,
                        onCheckedChange = { viewModel.setDownloadOverWifiOnly(it) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val deleteAfter by viewModel.deleteAfterWatching.collectAsState()
                    ToggleRow(
                        label = "Delete after watching",
                        checked = deleteAfter,
                        onCheckedChange = { viewModel.setDeleteAfterWatching(it) },
                    )
                }
            }
        }
    }
}

/**
 * A drag-and-drop reorderable list of strings. Uses [sh.calvin.reorderable].
 *
 * Uses a LazyColumn with a calculated height (items × rowHeight) because a
 * LazyColumn inside a verticalScroll Column needs bounded height constraints.
 * The items are few (3-5), so the height is small.
 */
@Composable
private fun ReorderableStringList(
    items: List<String>,
    onReorder: (from: Int, to: Int) -> Unit,
) {
    val rowHeight = 48.dp
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onReorder(from.index, to.index)
    }

    LazyColumn(
        state = lazyListState,
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight * items.size),
    ) {
        items(items, key = { it }) { item ->
            ReorderableItem(reorderableState, key = item) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .draggableHandle()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Drag to reorder",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun PriorityRadioButton(
    label: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.size(8.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun DownloadMethodOption(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
