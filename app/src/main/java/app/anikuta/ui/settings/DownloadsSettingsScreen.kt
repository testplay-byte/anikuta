package app.anikuta.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.anikuta.download.AudioVersion
import app.anikuta.download.DownloadQuality
import app.anikuta.download.DownloadStatus

/**
 * Phase 6 task 6.21 — Downloads settings subpage.
 * Quality + audio version + server priority + WiFi-only + delete after watch.
 */
@Composable
fun DownloadsSettingsScreen(onBack: () -> Unit) {
    val viewModel: DownloadsViewModel = viewModel()
    val queue by viewModel.queue.collectAsState()

    val active = queue.filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }
    val completed = queue.filter { it.status == DownloadStatus.COMPLETED }
    val failed = queue.filter { it.status == DownloadStatus.FAILED }

    SettingsSubpageScaffold(title = "Downloads", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Download preferences
            item {
                var quality by remember { mutableStateOf(viewModel.preferredQuality()) }
                var audioVersion by remember { mutableStateOf(viewModel.preferredAudioVersion()) }
                var preferredServer by remember { mutableStateOf(viewModel.preferredServer()) }
                var wifiOnly by remember { mutableStateOf(viewModel.downloadOverWifiOnly()) }
                var deleteAfter by remember { mutableStateOf(viewModel.deleteAfterWatching()) }
                var qualityExpanded by remember { mutableStateOf(false) }
                var audioExpanded by remember { mutableStateOf(false) }

                SettingsGroupCard(title = "Download preferences") {
                    // Quality dropdown
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LeadingIcon(Icons.Default.HighQuality)
                            Text("Preferred quality", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.height(4.dp))
                        Box {
                            OutlinedButton(onClick = { qualityExpanded = true }) {
                                Text(DownloadQuality.fromValue(quality).label)
                            }
                            DropdownMenu(expanded = qualityExpanded, onDismissRequest = { qualityExpanded = false }) {
                                DownloadQuality.entries.forEach { q ->
                                    DropdownMenuItem(
                                        text = { Text(q.label) },
                                        onClick = { quality = q.value; viewModel.setPreferredQuality(q.value); qualityExpanded = false },
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                    // Audio version dropdown
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LeadingIcon(Icons.Default.RecordVoiceOver)
                            Text("Preferred audio version", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.height(4.dp))
                        Box {
                            OutlinedButton(onClick = { audioExpanded = true }) {
                                Text(AudioVersion.fromValue(audioVersion).label)
                            }
                            DropdownMenu(expanded = audioExpanded, onDismissRequest = { audioExpanded = false }) {
                                AudioVersion.entries.forEach { a ->
                                    DropdownMenuItem(
                                        text = { Text(a.label) },
                                        onClick = { audioVersion = a.value; viewModel.setPreferredAudioVersion(a.value); audioExpanded = false },
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                    // Preferred server (text field)
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LeadingIcon(Icons.Default.Storage)
                            Text("Preferred server", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = preferredServer,
                            onValueChange = { preferredServer = it; viewModel.setPreferredServer(it) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("Auto (first available)") },
                        )
                    }
                    HorizontalDivider()
                    SwitchSettingsRow(
                        icon = Icons.Default.Wifi,
                        title = "Download over WiFi only",
                        subtitle = "Save mobile data",
                        checked = wifiOnly,
                        onCheckedChange = { wifiOnly = it; viewModel.setDownloadOverWifiOnly(it) },
                    )
                    HorizontalDivider()
                    SwitchSettingsRow(
                        icon = Icons.Default.Delete,
                        title = "Delete after watching",
                        subtitle = "Auto-remove downloaded episodes",
                        checked = deleteAfter,
                        onCheckedChange = { deleteAfter = it; viewModel.setDeleteAfterWatching(it) },
                    )
                }
            }

            // Active downloads
            if (active.isNotEmpty()) {
                item {
                    SettingsGroupCard(title = "Downloading (${active.size})") {
                        active.forEach { entry ->
                            DownloadRow(
                                title = entry.title,
                                subtitle = "${entry.progress}%",
                                progress = entry.progress,
                                status = entry.status,
                                onCancel = { viewModel.cancelDownload(entry.id) },
                            )
                        }
                    }
                }
            }

            // Completed
            if (completed.isNotEmpty()) {
                item {
                    SettingsGroupCard(title = "Completed (${completed.size})") {
                        completed.forEach { entry ->
                            DownloadRow(
                                title = entry.title,
                                subtitle = "Downloaded",
                                progress = 100,
                                status = DownloadStatus.COMPLETED,
                                onRemove = { viewModel.removeDownload(entry.id) },
                            )
                        }
                        TextButton(onClick = { viewModel.clearCompleted() }, modifier = Modifier.padding(8.dp)) {
                            Text("Clear all completed")
                        }
                    }
                }
            }

            // Failed
            if (failed.isNotEmpty()) {
                item {
                    SettingsGroupCard(title = "Failed (${failed.size})") {
                        failed.forEach { entry ->
                            DownloadRow(
                                title = entry.title,
                                subtitle = entry.error ?: "Failed",
                                progress = entry.progress,
                                status = DownloadStatus.FAILED,
                                onRemove = { viewModel.removeDownload(entry.id) },
                            )
                        }
                    }
                }
            }

            // Empty state
            if (queue.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Text("No downloads yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Long-press an episode to download", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    title: String,
    subtitle: String,
    progress: Int,
    status: DownloadStatus,
    onCancel: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (status == DownloadStatus.DOWNLOADING && progress >= 0) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
        }
        if (onCancel != null || onRemove != null) {
            Row(Modifier.padding(top = 4.dp)) {
                if (onCancel != null) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
                if (onRemove != null) {
                    TextButton(onClick = onRemove) { Text("Remove") }
                }
            }
        }
    }
}
