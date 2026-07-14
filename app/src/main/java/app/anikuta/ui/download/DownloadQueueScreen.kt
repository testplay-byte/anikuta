package app.anikuta.ui.download

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import app.anikuta.download.Download
import app.anikuta.download.DownloadManager
import app.anikuta.download.progress.formatBytes
import app.anikuta.download.progress.formatSpeed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
 * Download queue page — Material 3 Expressive design.
 *
 * Features:
 * - Downloads grouped by anime (collapsible headers)
 * - Summary bar with status counts
 * - Proper empty state
 * - Settings button with depth (rectangular, rounded, tonal elevation)
 * - Per-download: progress, size, speed, server/quality info
 * - Auto-remove countdown bar for completed downloads
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

    // Group downloads by animeTitle
    val groupedByAnime = remember(queue) {
        queue.groupBy { it.animeTitle }.toList()
    }

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
                    if (failed > 0) {
                        IconButton(onClick = { viewModel.retryAll() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Retry all")
                        }
                    }
                    if (hasActive) {
                        IconButton(onClick = { viewModel.pauseAll() }) {
                            Icon(Icons.Default.Pause, contentDescription = "Pause all")
                        }
                    }
                    if (paused > 0) {
                        IconButton(onClick = { viewModel.resumeAll() }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Resume all")
                        }
                    }
                    if (queue.isNotEmpty()) {
                        IconButton(onClick = { viewModel.cancelAll() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel all")
                        }
                    }
                    // Settings button — rectangular with rounded edges + depth
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
            // Summary bar
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

            if (queue.isEmpty()) {
                // Empty state
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
                // Grouped list
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    groupedByAnime.forEach { (animeTitle, downloads) ->
                        // Anime group header
                        item(key = "header_$animeTitle") {
                            Text(
                                animeTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                            )
                        }

                        // Downloads for this anime
                        items(downloads, key = { it.id }) { download ->
                            DownloadQueueItem(
                                download = download,
                                showAnimeTitle = false, // Already shown in header
                                onPause = { viewModel.pauseDownload(download.id) },
                                onResume = { viewModel.resumeDownload(download.id) },
                                onCancel = { viewModel.cancelDownload(download.id) },
                                onRetry = { viewModel.retryDownload(download.id) },
                                onRemove = { viewModel.removeDownload(download.id) },
                            )
                        }
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
            modifier = Modifier.padding(horizontal = 8.dp, vertical: 4.dp),
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
    showAnimeTitle: Boolean = true,
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
            // Top row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(download.episodeName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    if (showAnimeTitle) {
                        Text(download.animeTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    // Server/quality info
                    val infoText = buildString {
                        if (download.serverName.isNotBlank()) append(download.serverName)
                        if (download.audioVersion.isNotBlank()) { if (isNotEmpty()) append(" · "); append(download.audioVersion) }
                        if (download.qualityLabel.isNotBlank()) { if (isNotEmpty()) append(" · "); append(download.qualityLabel) }
                        if (download.actualResolution.isNotBlank()) { if (isNotEmpty()) append(" · "); append(download.actualResolution) }
                    }
                    if (infoText.isNotBlank()) {
                        Text(infoText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), maxLines = 1)
                    }
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
                        val reconnectColor = rememberInfiniteTransition(label = "reconnect").let { transition ->
                            val color by transition.animateColor(
                                initialValue = MaterialTheme.colorScheme.error,
                                targetValue = androidx.compose.ui.graphics.Color(0xFFFFA000),
                                animationSpec = infiniteRepeatable(animation = tween(500), repeatMode = RepeatMode.Reverse),
                                label = "reconnectColor",
                            )
                            color
                        }
                        Text("Reconnecting...", style = MaterialTheme.typography.labelSmall, color = reconnectColor, fontWeight = FontWeight.Bold)
                    }
                    else -> {}
                }
            }

            // Progress bars
            when (status) {
                Download.State.DOWNLOADING, Download.State.PAUSED -> {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                }
                Download.State.RESOLVING, Download.State.MUXING -> {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                else -> {}
            }

            // Auto-remove countdown
            if (status == Download.State.DOWNLOADED && autoRemoveProgress >= 0f) {
                Spacer(Modifier.height(6.dp))
                val secondsLeft = (autoRemoveProgress * 20f).toInt()
                Text("Removing from list in ${secondsLeft}s", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                LinearProgressIndicator(
                    progress = { autoRemoveProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }

            // Size + speed
            if (status == Download.State.DOWNLOADING) {
                Spacer(Modifier.height(4.dp))
                val sizeText = if (download.totalSize > 0) "${formatBytes(download.downloadedBytes)} / ${formatBytes(download.totalSize)}" else formatBytes(download.downloadedBytes)
                val speedText = if (speed > 0) " · ${formatSpeed(speed)}" else ""
                Text("$sizeText$speedText", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Error message
            if (status == Download.State.ERROR && download.error != null) {
                Spacer(Modifier.height(4.dp))
                Text(download.error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 2)
            }

            // Action buttons
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (status) {
                    Download.State.DOWNLOADING, Download.State.RESOLVING, Download.State.MUXING, Download.State.QUEUE -> {
                        TextButton(onClick = onPause) { Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Pause") }
                        TextButton(onClick = onCancel) { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Cancel") }
                    }
                    Download.State.RECONNECTING -> {
                        TextButton(onClick = onCancel) { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Cancel") }
                    }
                    Download.State.PAUSED -> {
                        TextButton(onClick = onResume) { Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Resume") }
                        TextButton(onClick = onCancel) { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Cancel") }
                    }
                    Download.State.ERROR -> {
                        TextButton(onClick = onRetry) { Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Retry") }
                        TextButton(onClick = onCancel) { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Cancel") }
                    }
                    Download.State.DOWNLOADED -> {
                        TextButton(onClick = onRemove) { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Remove") }
                        TextButton(onClick = onCancel) { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Delete file") }
                    }
                    else -> {}
                }
            }
        }
    }
}
