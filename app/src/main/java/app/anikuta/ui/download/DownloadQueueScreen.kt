package app.anikuta.ui.download

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
 * Download queue page — Material 3 Expressive card-style design.
 *
 * Design language (matches episode list on the details page):
 *  - Cards: Surface(RoundedCornerShape(12.dp), surfaceContainerLow, tonalElevation 1dp)
 *  - Title surfaces: RoundedCornerShape(8.dp), surfaceContainer bg
 *  - Info pills: RoundedCornerShape(6.dp), outlineVariant bg
 *  - Action panel: tall button on right side, own background, 12dp rounded
 *  - Alternating card colors by index (even=Low, odd=High)
 *
 * Layout:
 *  - Top bar: back button (styled) + "Downloads" title + settings button
 *  - Action bar (below top bar): Pause All / Resume All / Retry All / Cancel All
 *  - Summary chips: status counts
 *  - Anime group headers: highlighted anime title
 *  - Download item cards: episode info (left) + action panel (right)
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
            DownloadTopBar(onBack = onBack, onOpenSettings = onOpenSettings)
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Action bar (dedicated section below top bar)
            if (queue.isNotEmpty()) {
                DownloadActionBar(
                    hasActive = hasActive,
                    hasPaused = paused > 0,
                    hasFailed = failed > 0,
                    hasAny = queue.isNotEmpty(),
                    onPauseAll = { viewModel.pauseAll() },
                    onResumeAll = { viewModel.resumeAll() },
                    onRetryAll = { viewModel.retryAll() },
                    onCancelAll = { viewModel.cancelAll() },
                )
            }

            // Summary chips
            if (queue.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (downloading > 0) StatChip("$downloading", "downloading", MaterialTheme.colorScheme.primary)
                    if (queued > 0) StatChip("$queued", "queued", MaterialTheme.colorScheme.onSurfaceVariant)
                    if (paused > 0) StatChip("$paused", "paused", MaterialTheme.colorScheme.tertiary)
                    if (completed > 0) StatChip("$completed", "done", MaterialTheme.colorScheme.primary)
                    if (failed > 0) StatChip("$failed", "failed", MaterialTheme.colorScheme.error)
                }
            }

            if (queue.isEmpty()) {
                DownloadEmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    groupedByAnime.forEach { (animeTitle, downloads) ->
                        // Anime group header
                        item(key = "header_$animeTitle") {
                            AnimeGroupHeader(animeTitle = animeTitle, count = downloads.size)
                        }

                        // Downloads for this anime
                        items(downloads, key = { it.id }) { download ->
                            val index = downloads.indexOf(download)
                            DownloadItemCard(
                                download = download,
                                index = index,
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
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                tonalElevation = 1.dp,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Clear completed", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// TOP BAR
// ============================================================

/**
 * Clean top bar — back button (styled) + "Downloads" title + settings button.
 * No action icons cluttering the bar; bulk actions live in [DownloadActionBar].
 */
@Composable
private fun DownloadTopBar(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Back button — styled Surface with rounded corners + tonal elevation
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 1.dp,
                onClick = onBack,
            ) {
                Box(
                    modifier = Modifier.size(44.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                "Downloads",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            // Settings button — rectangular with rounded edges + depth
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 1.dp,
                onClick = onOpenSettings,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Text("Settings", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ============================================================
// ACTION BAR (bulk operations)
// ============================================================

/**
 * Dedicated section for bulk actions (Pause All / Resume All / Retry All / Cancel All).
 * Lives below the top bar, NOT inside it — keeping the top bar clean.
 */
@Composable
private fun DownloadActionBar(
    hasActive: Boolean,
    hasPaused: Boolean,
    hasFailed: Boolean,
    hasAny: Boolean,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onRetryAll: () -> Unit,
    onCancelAll: () -> Unit,
) {
    val actions = mutableListOf<Pair<ImageVector, () -> Unit>>()
    if (hasActive) actions.add(Icons.Default.Pause to onPauseAll)
    if (hasPaused) actions.add(Icons.Default.PlayArrow to onResumeAll)
    if (hasFailed) actions.add(Icons.Default.Refresh to onRetryAll)
    if (hasAny) actions.add(Icons.Default.Close to onCancelAll)

    if (actions.isEmpty()) return

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            actions.forEach { (icon, action) ->
                ActionButton(icon = icon, onClick = action, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp,
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        }
    }
}

// ============================================================
// SUMMARY CHIPS
// ============================================================

@Composable
private fun StatChip(count: String, label: String, color: Color) {
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

// ============================================================
// EMPTY STATE
// ============================================================

@Composable
private fun DownloadEmptyState() {
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
}

// ============================================================
// ANIME GROUP HEADER
// ============================================================

/**
 * Anime group header — highlighted anime title with a primary-colored accent bar
 * and episode count badge. Matches the [SettingsGroupCard] design language.
 */
@Composable
private fun AnimeGroupHeader(animeTitle: String, count: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Accent bar
            Surface(
                modifier = Modifier.width(3.dp).height(20.dp),
                shape = RoundedCornerShape(2.dp),
                color = MaterialTheme.colorScheme.primary,
            ) {}
            Spacer(Modifier.width(10.dp))
            Text(
                animeTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            // Episode count badge
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    "$count",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}

// ============================================================
// DOWNLOAD ITEM CARD
// ============================================================

/**
 * Download item card — card-style layout matching the episode list design.
 *
 * Structure:
 *  - Surface(RoundedCornerShape(12.dp), alternating color by index)
 *  - Row(IntrinsicSize.Min):
 *    - Left (weight 1f): Episode info column
 *      - Episode name in a title Surface (8dp rounded, surfaceContainer bg)
 *      - Info pills row: server, audio, quality, resolution (6dp rounded, outlineVariant)
 *      - Status pill
 *      - Progress surface (progress bar + size/speed)
 *      - Error message (if any)
 *    - Right (56dp): Action panel — tall Surface with vertical icon buttons
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownloadItemCard(
    download: Download,
    index: Int,
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

    // Alternating card colors (matching episode list)
    val cardColor = if (index % 2 == 0) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = cardColor,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            verticalAlignment = Alignment.Top,
        ) {
            // ---- Left: Episode info ----
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 10.dp),
            ) {
                // Episode name — title surface (matching episode list)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = download.episodeName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }

                // Info pills row — server, audio version, quality, resolution
                val pills = mutableListOf<Triple<String, Color, FontWeight>>()
                if (download.serverName.isNotBlank()) {
                    pills.add(Triple(download.serverName, MaterialTheme.colorScheme.onSurfaceVariant, FontWeight.SemiBold))
                }
                if (download.audioVersion.isNotBlank()) {
                    pills.add(Triple(download.audioVersion.uppercase(), MaterialTheme.colorScheme.primary, FontWeight.Bold))
                }
                if (download.qualityLabel.isNotBlank()) {
                    pills.add(Triple(download.qualityLabel, MaterialTheme.colorScheme.tertiary, FontWeight.SemiBold))
                }
                if (download.actualResolution.isNotBlank()) {
                    pills.add(Triple(download.actualResolution, MaterialTheme.colorScheme.onSurfaceVariant, FontWeight.SemiBold))
                }
                if (pills.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        pills.forEach { (label, color, weight) ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = weight,
                                    color = color,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }
                }

                // Status text
                Spacer(Modifier.height(6.dp))
                StatusText(status = status, progress = progress)

                // Progress bar + size/speed — in a dedicated surface
                if (status == Download.State.DOWNLOADING || status == Download.State.PAUSED ||
                    status == Download.State.RESOLVING || status == Download.State.MUXING
                ) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            // Progress bar
                            if (status == Download.State.DOWNLOADING || status == Download.State.PAUSED) {
                                LinearProgressIndicator(
                                    progress = { progress / 100f },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = if (status == Download.State.PAUSED) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                            } else {
                                // RESOLVING / MUXING — indeterminate
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                            }

                            // Size + speed
                            if (status == Download.State.DOWNLOADING) {
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val sizeText = if (download.totalSize > 0) {
                                        "${formatBytes(download.downloadedBytes)} / ${formatBytes(download.totalSize)}"
                                    } else {
                                        formatBytes(download.downloadedBytes)
                                    }
                                    Text(
                                        sizeText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (speed > 0) {
                                        Text(
                                            formatSpeed(speed),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Auto-remove countdown
                if (status == Download.State.DOWNLOADED && autoRemoveProgress >= 0f) {
                    Spacer(Modifier.height(4.dp))
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

                // Error message
                if (status == Download.State.ERROR && download.error != null) {
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            download.error!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            // ---- Right: Action panel ----
            DownloadActionPanel(
                status = status,
                onPause = onPause,
                onResume = onResume,
                onCancel = onCancel,
                onRetry = onRetry,
                onRemove = onRemove,
                index = index,
            )
        }
    }
}

// ============================================================
// STATUS TEXT
// ============================================================

@Composable
private fun StatusText(status: Download.State, progress: Int) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = when (status) {
            Download.State.DOWNLOADING -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            Download.State.DOWNLOADED -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            Download.State.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            Download.State.PAUSED -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
            Download.State.RECONNECTING -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            else -> MaterialTheme.colorScheme.outlineVariant
        },
    ) {
        val (text, color, weight) = when (status) {
            Download.State.QUEUE -> Triple("Queued", MaterialTheme.colorScheme.onSurfaceVariant, FontWeight.Medium)
            Download.State.RESOLVING -> Triple("Checking...", MaterialTheme.colorScheme.primary, FontWeight.Bold)
            Download.State.DOWNLOADING -> Triple("$progress%", MaterialTheme.colorScheme.primary, FontWeight.Bold)
            Download.State.MUXING -> Triple("Finalizing...", MaterialTheme.colorScheme.primary, FontWeight.Bold)
            Download.State.DOWNLOADED -> Triple("Done", MaterialTheme.colorScheme.primary, FontWeight.Bold)
            Download.State.ERROR -> Triple("Failed", MaterialTheme.colorScheme.error, FontWeight.Bold)
            Download.State.PAUSED -> Triple("Paused ($progress%)", MaterialTheme.colorScheme.tertiary, FontWeight.Bold)
            Download.State.RECONNECTING -> {
                val transition = rememberInfiniteTransition(label = "reconnect_status")
                val reconnectColor by transition.animateColor(
                    initialValue = MaterialTheme.colorScheme.error,
                    targetValue = Color(0xFFFFA000),
                    animationSpec = infiniteRepeatable(animation = tween(500), repeatMode = RepeatMode.Reverse),
                    label = "reconnect_status_color",
                )
                Triple("Reconnecting...", reconnectColor, FontWeight.Bold)
            }
            else -> Triple("", MaterialTheme.colorScheme.onSurfaceVariant, FontWeight.Medium)
        }
        if (text.isNotEmpty()) {
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = weight,
                color = color,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

// ============================================================
// ACTION PANEL (right side of download card)
// ============================================================

/**
 * Tall action panel on the right side of the download card.
 * Contains state-dependent icon buttons stacked vertically.
 * Has its own dedicated background (alternating, opposite of card).
 * Matches the [DownloadButtonTall] design from the details page.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownloadActionPanel(
    status: Download.State,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    index: Int,
) {
    // Alternating background — opposite of the card color
    val panelColor = if (index % 2 == 0) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    // Determine which action buttons to show based on status
    data class ActionBtn(val icon: ImageVector, val label: String, val tint: Color, val onClick: () -> Unit)
    val buttons = when (status) {
        Download.State.DOWNLOADING, Download.State.RESOLVING, Download.State.MUXING, Download.State.QUEUE -> listOf(
            ActionBtn(Icons.Default.Pause, "Pause", MaterialTheme.colorScheme.onSurfaceVariant, onPause),
            ActionBtn(Icons.Default.Close, "Cancel", MaterialTheme.colorScheme.error, onCancel),
        )
        Download.State.RECONNECTING -> listOf(
            ActionBtn(Icons.Default.Close, "Cancel", MaterialTheme.colorScheme.error, onCancel),
        )
        Download.State.PAUSED -> listOf(
            ActionBtn(Icons.Default.PlayArrow, "Resume", MaterialTheme.colorScheme.primary, onResume),
            ActionBtn(Icons.Default.Close, "Cancel", MaterialTheme.colorScheme.error, onCancel),
        )
        Download.State.ERROR -> listOf(
            ActionBtn(Icons.Default.Refresh, "Retry", MaterialTheme.colorScheme.primary, onRetry),
            ActionBtn(Icons.Default.Close, "Cancel", MaterialTheme.colorScheme.error, onCancel),
        )
        Download.State.DOWNLOADED -> listOf(
            ActionBtn(Icons.Default.Close, "Remove", MaterialTheme.colorScheme.onSurfaceVariant, onRemove),
            ActionBtn(Icons.Default.Delete, "Delete file", MaterialTheme.colorScheme.error, onCancel),
        )
        else -> emptyList()
    }

    if (buttons.isEmpty()) return

    Surface(
        modifier = Modifier.width(52.dp).fillMaxHeight(),
        shape = RoundedCornerShape(12.dp),
        color = panelColor,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxHeight().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            buttons.forEach { btn ->
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .combinedClickable(onClick = btn.onClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        btn.icon,
                        contentDescription = btn.label,
                        tint = btn.tint,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}
