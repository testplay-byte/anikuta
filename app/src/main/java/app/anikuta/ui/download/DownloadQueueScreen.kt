package app.anikuta.ui.download

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.anikuta.download.Download
import app.anikuta.download.DownloadManager
import app.anikuta.download.progress.formatBytes
import app.anikuta.download.progress.formatSpeed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * ViewModel for the download queue page.
 */
class DownloadQueueViewModel : ViewModel() {
    private val manager: DownloadManager? = try { Injekt.get() } catch (e: Exception) { null }

    val queue: StateFlow<List<Download>> = manager?.queue ?: MutableStateFlow(emptyList())

    fun pauseDownload(id: String) { manager?.pauseDownload(id) }
    fun resumeDownload(id: String) { manager?.resumeDownload(id) }
    fun cancelDownload(id: String) { manager?.cancelDownload(id) }
    fun retryDownload(id: String) { manager?.retryDownload(id) }
    fun removeDownload(id: String) { manager?.removeDownload(id) }
    fun clearCompleted() { manager?.clearCompleted() }
    fun pauseAll() { manager?.pauseAll() }
    fun resumeAll() { manager?.resumeAll() }
    fun cancelAll() { manager?.cancelAll() }
    fun retryAll() { manager?.retryAll() }
}

/**
 * Download queue page — shows all downloads with progress + controls.
 *
 * UI states per download:
 * - QUEUE: "Queued" + Cancel button
 * - RESOLVING: "Checking..." + indeterminate spinner + Cancel button
 * - DOWNLOADING: "XX%" + progress bar + Pause + Cancel buttons
 * - MUXING: "Finalizing..." + indeterminate spinner + Cancel button
 * - DOWNLOADED: "Done" + Remove button
 * - ERROR: "Failed" + error message + Retry + Cancel buttons
 * - PAUSED: "Paused" + last progress + Resume + Cancel buttons
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadQueueScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel: DownloadQueueViewModel = viewModel()
    val queue by viewModel.queue.collectAsState()

    val downloading = queue.count { it.status == Download.State.DOWNLOADING }
    val queued = queue.count { it.status == Download.State.QUEUE }
    val completed = queue.count { it.status == Download.State.DOWNLOADED }
    val failed = queue.count { it.status == Download.State.ERROR }
    val paused = queue.count { it.status == Download.State.PAUSED }
    val hasActive = downloading > 0 || queued > 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Retry all failed downloads (only when there are failures)
                    if (failed > 0) {
                        IconButton(onClick = { viewModel.retryAll() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Retry all failed")
                        }
                    }
                    // Pause all (only when there are active downloads)
                    if (hasActive) {
                        IconButton(onClick = { viewModel.pauseAll() }) {
                            Icon(Icons.Default.Pause, contentDescription = "Pause all")
                        }
                    }
                    // Resume all (only when there are paused downloads)
                    if (paused > 0) {
                        IconButton(onClick = { viewModel.resumeAll() }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Resume all")
                        }
                    }
                    // Cancel all (always available when there are any downloads)
                    if (queue.isNotEmpty()) {
                        IconButton(onClick = { viewModel.cancelAll() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel all")
                        }
                    }
                    // Settings button — rectangular with rounded edges + depth (per user request)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 2.dp,
                        shadowElevation = 1.dp,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        TextButton(
                            onClick = onOpenSettings,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Settings")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // ---- Summary bar ----
            if (queue.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (downloading > 0) StatChip("$downloading", "downloading", MaterialTheme.colorScheme.primary)
                    if (queued > 0) StatChip("$queued", "queued", MaterialTheme.colorScheme.onSurfaceVariant)
                    if (paused > 0) StatChip("$paused", "paused", MaterialTheme.colorScheme.tertiary)
                    if (completed > 0) StatChip("$completed", "done", MaterialTheme.colorScheme.tertiary)
                    if (failed > 0) StatChip("$failed", "failed", MaterialTheme.colorScheme.error)
                }
                HorizontalDivider()
            }

            // ---- Queue list ----
            if (queue.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.size(96.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                                )
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Text("No downloads yet", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(4.dp))
                        Text("Download episodes from the anime detail page", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(queue, key = { it.id }) { download ->
                        DownloadQueueItem(
                            download = download,
                            onPause = { viewModel.pauseDownload(download.id) },
                            onResume = { viewModel.resumeDownload(download.id) },
                            onCancel = { viewModel.cancelDownload(download.id) },
                            onRetry = { viewModel.retryDownload(download.id) },
                            onRemove = { viewModel.removeDownload(download.id) },
                        )
                    }
                    if (completed > 0) {
                        item {
                            TextButton(onClick = { viewModel.clearCompleted() }) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Clear completed")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(count: String, label: String, color: androidx.compose.ui.graphics.Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(count, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun DownloadQueueItem(
    download: Download,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    val status by download.statusFlow.collectAsState()
    val progress by download.progressFlow.collectAsState()
    val speed by download.speedFlow.collectAsState()
    val autoRemoveProgress by download.autoRemoveCountdown.collectAsState()

    val isActive = status == Download.State.DOWNLOADING ||
        status == Download.State.QUEUE ||
        status == Download.State.RESOLVING ||
        status == Download.State.MUXING

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (status) {
                Download.State.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                Download.State.DOWNLOADED -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                Download.State.PAUSED -> MaterialTheme.colorScheme.surfaceContainerLow
                else -> MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top row: episode name + status text
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(download.episodeName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(download.animeTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                when (status) {
                    Download.State.QUEUE -> Text("Queued", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Download.State.RESOLVING -> Text("Checking...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Download.State.DOWNLOADING -> Text("$progress%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Download.State.MUXING -> Text("Finalizing...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Download.State.DOWNLOADED -> Text("Done", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Download.State.ERROR -> Text("Failed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    Download.State.PAUSED -> Text("Paused ($progress%)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    Download.State.RECONNECTING -> {
                        // Issue 6: Pulsing red/yellow "Reconnecting..." text
                        val reconnectColor = rememberInfiniteTransition(label = "reconnect").let { transition ->
                            val color by transition.animateColor(
                                initialValue = MaterialTheme.colorScheme.error,
                                targetValue = androidx.compose.ui.graphics.Color(0xFFFFA000), // amber/yellow
                                animationSpec = infiniteRepeatable(
                                    animation = tween(500),
                                    repeatMode = RepeatMode.Reverse,
                                ),
                                label = "reconnectColor",
                            )
                            color
                        }
                        Text("Reconnecting...", style = MaterialTheme.typography.labelSmall, color = reconnectColor, fontWeight = FontWeight.Bold)
                    }
                    else -> {}
                }
            }

            // Progress bar (determinate for DOWNLOADING/PAUSED, indeterminate for RESOLVING/MUXING)
            when (status) {
                Download.State.DOWNLOADING, Download.State.PAUSED -> {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Download.State.RESOLVING, Download.State.MUXING -> {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) // indeterminate
                }
                else -> {}
            }

            // Issue 4: Auto-remove countdown bar for completed downloads
            if (status == Download.State.DOWNLOADED && autoRemoveProgress >= 0f) {
                Spacer(Modifier.height(6.dp))
                val secondsLeft = (autoRemoveProgress * 20f).toInt()
                Text(
                    "Removing from list in ${secondsLeft}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                LinearProgressIndicator(
                    progress = { autoRemoveProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }

            // Size + speed info (only during DOWNLOADING)
            if (status == Download.State.DOWNLOADING) {
                Spacer(Modifier.height(4.dp))
                val sizeText = if (download.totalSize > 0) {
                    "${formatBytes(download.downloadedBytes)} / ${formatBytes(download.totalSize)}"
                } else {
                    formatBytes(download.downloadedBytes)
                }
                val speedText = if (speed > 0) " · ${formatSpeed(speed)}" else ""
                Text("$sizeText$speedText", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Error message
            if (status == Download.State.ERROR && download.error != null) {
                Spacer(Modifier.height(4.dp))
                Text(download.error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 2)
            }

            // Action buttons — separate Pause/Resume and Cancel (fixes C4)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (status) {
                    Download.State.DOWNLOADING, Download.State.RESOLVING, Download.State.MUXING -> {
                        TextButton(onClick = onPause) {
                            Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Pause")
                        }
                        TextButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Cancel")
                        }
                    }
                    Download.State.RECONNECTING -> {
                        // Issue 6: Only Cancel available during reconnection
                        TextButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Cancel")
                        }
                    }
                    Download.State.QUEUE -> {
                        // FIX (D3): Show Pause + Cancel for QUEUE state (same as DOWNLOADING).
                        // Previously only Cancel was shown, which was confusing after resuming
                        // a paused download — the user couldn't re-pause while it was queued.
                        TextButton(onClick = onPause) {
                            Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Pause")
                        }
                        TextButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Cancel")
                        }
                    }
                    Download.State.PAUSED -> {
                        TextButton(onClick = onResume) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Resume")
                        }
                        TextButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Cancel")
                        }
                    }
                    Download.State.ERROR -> {
                        TextButton(onClick = onRetry) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Retry")
                        }
                        TextButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Cancel")
                        }
                    }
                    Download.State.DOWNLOADED -> {
                        // "Remove" = remove from queue, keep the file (for completed downloads)
                        TextButton(onClick = onRemove) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Remove")
                        }
                        // "Delete" = delete the file too (via cancelDownload which deletes everything)
                        TextButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Delete file")
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}
