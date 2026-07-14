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
import androidx.compose.foundation.layout.statusBarsPadding
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
import app.anikuta.download.DownloadPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * ViewModel for the download queue page.
 *
 * Derives status counts from [downloadStatusMap] (NOT the queue list) so that
 * counts update reactively when individual download statuses change (e.g.
 * after Pause All, the Resume All button appears immediately without needing
 * to re-enter the page).
 */
class DownloadQueueViewModel : ViewModel() {
    private val manager: DownloadManager? = try { Injekt.get() } catch (e: Exception) { null }

    val queue: StateFlow<List<Download>> = manager?.queue ?: MutableStateFlow(emptyList())

    /**
     * Reactive status map — re-emits whenever any download's status changes.
     * Used to compute [statusCounts] so the action bar updates live.
     */
    val statusMap: StateFlow<Map<String, Download.State>> =
        manager?.downloadStatusMap ?: MutableStateFlow(emptyMap())

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
 * Design:
 *  - Clean top bar (back + title + settings) with status bar insets
 *  - Action bar (bulk operations) below the top bar
 *  - Each anime = ONE section card containing the header + all episode rows
 *  - Episode rows: name + info pills (right) + taller progress bar + action panel (right)
 *  - No alternating colors (uniform surfaceContainerLow)
 *  - No primaryContainer (deep dark blue) — uses secondaryContainer / surfaceVariant
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadQueueScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel: DownloadQueueViewModel = viewModel()
    val queue by viewModel.queue.collectAsState()
    val statusMap by viewModel.statusMap.collectAsState()

    // Read showDownloadSize preference reactively (same pattern as details page)
    val dlPrefs = remember { try { Injekt.get<DownloadPreferences>() } catch (e: Exception) { null } }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val showDownloadSize by (dlPrefs?.showDownloadSize()?.stateIn(scope) ?: MutableStateFlow(true)).collectAsState()

    // Derive counts from statusMap (reactive — updates immediately when statuses change)
    val downloading = statusMap.values.count { it == Download.State.DOWNLOADING }
    val queued = statusMap.values.count { it == Download.State.QUEUE }
    val completed = statusMap.values.count { it == Download.State.DOWNLOADED }
    val failed = statusMap.values.count { it == Download.State.ERROR }
    val paused = statusMap.values.count { it == Download.State.PAUSED }
    val hasActive = downloading > 0 || queued > 0

    // Group downloads by animeTitle
    val groupedByAnime = remember(queue) {
        queue.groupBy { it.animeTitle }.toList()
    }

    // Use statusBarsPadding on the root (matching SettingsSubpageScaffold pattern)
    // instead of Scaffold's padding (which was creating double insets).
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // Top bar
            DownloadTopBar(onBack = onBack, onOpenSettings = onOpenSettings)

            // Action bar
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
                    if (downloading > 0) StatChip("$downloading", "downloading", MaterialTheme.colorScheme.tertiary)
                    if (queued > 0) StatChip("$queued", "queued", MaterialTheme.colorScheme.onSurfaceVariant)
                    if (paused > 0) StatChip("$paused", "paused", MaterialTheme.colorScheme.tertiary)
                    if (completed > 0) StatChip("$completed", "done", MaterialTheme.colorScheme.tertiary)
                    if (failed > 0) StatChip("$failed", "failed", MaterialTheme.colorScheme.error)
                }
            }

            if (queue.isEmpty()) {
                DownloadEmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    groupedByAnime.forEach { (animeTitle, downloads) ->
                        item(key = "section_$animeTitle") {
                            AnimeSectionCard(
                                animeTitle = animeTitle,
                                downloads = downloads,
                                showDownloadSize = showDownloadSize,
                                onPause = { viewModel.pauseDownload(it) },
                                onResume = { viewModel.resumeDownload(it) },
                                onCancel = { viewModel.cancelDownload(it) },
                                onRetry = { viewModel.retryDownload(it) },
                                onRemove = { viewModel.removeDownload(it) },
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
                                onClick = { viewModel.clearCompleted() },
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

@Composable
private fun DownloadTopBar(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    // Matches SettingsSubpageScaffold pattern: no Surface wrapper, just a Row
    // with minimal padding. The Scaffold's padding already handles status bar.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
            // Back button
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
            // Settings button
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
// ACTION BAR
// ============================================================

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
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.size(96.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
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
// ANIME SECTION CARD (one card per anime, containing all episodes)
// ============================================================

/**
 * One section card per anime. Contains:
 *  - Header: accent bar + anime name + episode count badge
 *  - All episode rows for this anime (inside the same card)
 */
@Composable
private fun AnimeSectionCard(
    animeTitle: String,
    downloads: List<Download>,
    showDownloadSize: Boolean,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRetry: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
        // Header card (separate from episode cards)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.width(3.dp).height(20.dp),
                    shape = RoundedCornerShape(2.dp),
                    color = MaterialTheme.colorScheme.tertiary,
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
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        "${downloads.size}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }

        // Episode cards — each episode is its OWN distinct card with spacing
        downloads.forEachIndexed { index, download ->
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp,
            ) {
                EpisodeRow(
                    download = download,
                    showDownloadSize = showDownloadSize,
                    onPause = { onPause(download.id) },
                    onResume = { onResume(download.id) },
                    onCancel = { onCancel(download.id) },
                    onRetry = { onRetry(download.id) },
                    onRemove = { onRemove(download.id) },
                )
            }
        }
    }
}

// ============================================================
// EPISODE ROW (inside the anime section card)
// ============================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeRow(
    download: Download,
    showDownloadSize: Boolean,
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

    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top,
    ) {
        // ---- Left: Episode info ----
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 10.dp),
        ) {
            // Episode name (own row)
            Text(
                text = download.episodeName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Info pills — on the NEXT row below the episode name
            if (download.serverName.isNotBlank() || download.audioVersion.isNotBlank() || download.qualityLabel.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (download.serverName.isNotBlank()) {
                        InfoPill(text = download.serverName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (download.audioVersion.isNotBlank()) {
                        InfoPill(text = download.audioVersion.uppercase(), color = MaterialTheme.colorScheme.tertiary)
                    }
                    if (download.qualityLabel.isNotBlank()) {
                        InfoPill(text = download.qualityLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Progress bar — percentage ABOVE the bar (top-right, bold, with bg pill)
            if (status == Download.State.DOWNLOADING || status == Download.State.PAUSED ||
                status == Download.State.RESOLVING || status == Download.State.MUXING
            ) {
                Spacer(Modifier.height(8.dp))
                // Row: percentage pill (right) + size/speed (left) — ABOVE the bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Left: size text (if enabled)
                    if (showDownloadSize && status == Download.State.DOWNLOADING) {
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
                    } else {
                        Box(Modifier)
                    }
                    // Right: percentage in a background pill (bold)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (speed > 0 && status == Download.State.DOWNLOADING) {
                            Text(
                                formatSpeed(speed),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (status == Download.State.DOWNLOADING || status == Download.State.PAUSED) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                            ) {
                                Text(
                                    "$progress%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
                // Progress bar (taller, below the percentage row)
                Spacer(Modifier.height(4.dp))
                if (status == Download.State.DOWNLOADING || status == Download.State.PAUSED) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = MaterialTheme.colorScheme.tertiary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                } else {
                    // RESOLVING / MUXING — indeterminate
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = MaterialTheme.colorScheme.tertiary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }

            // Status text (for non-progress states)
            if (status == Download.State.QUEUE) {
                Spacer(Modifier.height(4.dp))
                Text("Queued", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (status == Download.State.DOWNLOADED) {
                Spacer(Modifier.height(4.dp))
                Text("Done", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
            } else if (status == Download.State.ERROR) {
                Spacer(Modifier.height(4.dp))
                Text("Failed", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                if (download.error != null) {
                    Text(
                        download.error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else if (status == Download.State.RECONNECTING) {
                Spacer(Modifier.height(4.dp))
                val transition = rememberInfiniteTransition(label = "reconnect_status")
                val reconnectColor by transition.animateColor(
                    initialValue = MaterialTheme.colorScheme.error,
                    targetValue = Color(0xFFFFA000),
                    animationSpec = infiniteRepeatable(animation = tween(500), repeatMode = RepeatMode.Reverse),
                    label = "reconnect_status_color",
                )
                Text("Reconnecting...", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = reconnectColor)
            }

            // Auto-remove countdown
            if (status == Download.State.DOWNLOADED && autoRemoveProgress >= 0f) {
                Spacer(Modifier.height(4.dp))
                val secondsLeft = (autoRemoveProgress * 20f).toInt()
                Text(
                    "Removing in ${secondsLeft}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                LinearProgressIndicator(
                    progress = { autoRemoveProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.tertiary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }

        // ---- Right: Action panel ----
        EpisodeActionPanel(
            status = status,
            onPause = onPause,
            onResume = onResume,
            onCancel = onCancel,
            onRetry = onRetry,
            onRemove = onRemove,
        )
    }
}

// ============================================================
// INFO PILL
// ============================================================

@Composable
private fun InfoPill(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

// ============================================================
// EPISODE ACTION PANEL (right side, with separation between buttons)
// ============================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeActionPanel(
    status: Download.State,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
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
            ActionBtn(Icons.Default.PlayArrow, "Resume", MaterialTheme.colorScheme.tertiary, onResume),
            ActionBtn(Icons.Default.Close, "Cancel", MaterialTheme.colorScheme.error, onCancel),
        )
        Download.State.ERROR -> listOf(
            ActionBtn(Icons.Default.Refresh, "Retry", MaterialTheme.colorScheme.tertiary, onRetry),
            ActionBtn(Icons.Default.Close, "Cancel", MaterialTheme.colorScheme.error, onCancel),
        )
        Download.State.DOWNLOADED -> listOf(
            ActionBtn(Icons.Default.Close, "Remove", MaterialTheme.colorScheme.onSurfaceVariant, onRemove),
            ActionBtn(Icons.Default.Delete, "Delete file", MaterialTheme.colorScheme.error, onCancel),
        )
        else -> emptyList()
    }

    if (buttons.isEmpty()) return

    // Action panel — each button in its own Surface with depth + borders
    Column(
        modifier = Modifier.fillMaxHeight().padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        buttons.forEach { btn ->
            // Each button is its own Surface with rounded corners + tonal elevation + border
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 1.dp,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier.combinedClickable(onClick = btn.onClick),
            ) {
                Box(
                    modifier = Modifier.size(36.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        btn.icon,
                        contentDescription = btn.label,
                        tint = btn.tint,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
