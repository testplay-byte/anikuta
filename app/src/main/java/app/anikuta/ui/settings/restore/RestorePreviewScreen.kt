package app.anikuta.ui.settings.restore

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.anikuta.backup.format.anikuta.RestoreOptions
import app.anikuta.backup.model.BackupFormat
import app.anikuta.backup.model.BackupSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-screen backup preview (Step 2 of the restore flow).
 *
 * Replaces the old AlertDialog. Shows:
 *  - Format, date, version metadata in a header card.
 *  - Content counts as color-coded chips.
 *  - Warnings (manga skipped, missing sources, unlinked estimate).
 *  - Restore-option toggles.
 *  - Restore / Cancel buttons at the bottom.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun RestorePreviewScreen(
    summary: BackupSummary,
    onRestore: (RestoreOptions) -> Unit,
    onCancel: () -> Unit,
) {
    var library by remember { mutableStateOf(true) }
    var history by remember { mutableStateOf(true) }
    var searches by remember { mutableStateOf(true) }
    var categories by remember { mutableStateOf(true) }
    var tracking by remember { mutableStateOf(true) }
    var settings by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
    ) {
        // Scrollable content
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
        ) {
            // Header
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Backup Preview", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            }

            // Metadata card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(summary.format.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                        if (summary.createdAt > 0) {
                            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(summary.createdAt))
                            Text("Created: $dateStr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (summary.appVersion.isNotEmpty()) {
                            Text("App version: ${summary.appVersion}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (summary.schemaVersion > 0) {
                            Text("Schema: v${summary.schemaVersion}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Contents
            item {
                Text("CONTENTS", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (summary.libraryCount > 0) CountChip(Icons.Default.VideoLibrary, "Anime", summary.libraryCount)
                    if (summary.historyCount > 0) CountChip(Icons.Default.History, "History", summary.historyCount)
                    if (summary.categoryCount > 0) CountChip(Icons.Default.Category, "Categories", summary.categoryCount)
                    if (summary.trackingCount > 0) CountChip(Icons.Default.Notifications, "Tracking", summary.trackingCount)
                    if (summary.subDubCount > 0) CountChip(Icons.Default.CloudDownload, "Sub/Dub", summary.subDubCount)
                    if (summary.extensionLinkCount > 0) CountChip(Icons.Default.Bookmark, "Ext. links", summary.extensionLinkCount)
                    if (summary.playbackStateCount > 0) CountChip(Icons.Default.History, "Playback", summary.playbackStateCount)
                    if (summary.searchCount > 0) CountChip(Icons.Default.Search, "Searches", summary.searchCount)
                    if (summary.preferenceCount > 0) CountChip(Icons.Default.Settings, "Prefs", summary.preferenceCount)
                    if (summary.mangaCount > 0) CountChip(Icons.Default.Warning, "Manga (skip)", summary.mangaCount, isWarning = true)
                }
            }

            // Warnings
            if (summary.warnings.isNotEmpty() || summary.estimatedUnlinkedCount > 0 || summary.missingSources.isNotEmpty()) {
                item {
                    Text("WARNINGS", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            summary.warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer) }
                            if (summary.estimatedUnlinkedCount > 0) {
                                Text("• ${summary.estimatedUnlinkedCount} anime may need manual AniList linking after restore.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                            if (summary.missingSources.isNotEmpty()) {
                                Text("• Sources not installed: ${summary.missingSources.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
            }

            // Restore options
            item {
                Text("RESTORE OPTIONS", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    RestoreOptionRow(Icons.Default.VideoLibrary, "Library", "${summary.libraryCount} anime", library, summary.libraryCount > 0) { library = it }
                    RestoreOptionRow(Icons.Default.History, "History", "${summary.historyCount} entries", history, summary.historyCount > 0) { history = it }
                    RestoreOptionRow(Icons.Default.Search, "Recent searches", "${summary.searchCount} terms", searches, summary.searchCount > 0) { searches = it }
                    RestoreOptionRow(Icons.Default.Category, "Categories", "${summary.categoryCount} categories", categories, summary.categoryCount > 0) { categories = it }
                    RestoreOptionRow(Icons.Default.Notifications, "Tracking & notifications", "${summary.trackingCount} entries", tracking, summary.trackingCount > 0) { tracking = it }
                    RestoreOptionRow(Icons.Default.Settings, "Settings & preferences", "${summary.preferenceCount} prefs", settings, summary.preferenceCount > 0) { settings = it }
                }
            }
        }

        // Bottom buttons
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(
                onClick = { onRestore(RestoreOptions(library, history, searches, categories, tracking, settings)) },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Restore")
            }
        }
    }
}

@Composable
private fun CountChip(icon: ImageVector, label: String, count: Int, isWarning: Boolean = false) {
    val containerColor = if (isWarning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val contentColor = if (isWarning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(shape = RoundedCornerShape(12.dp), color = containerColor) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = contentColor)
            Text("$count", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = contentColor)
            Text(label, style = MaterialTheme.typography.labelSmall, color = contentColor)
        }
    }
}

@Composable
private fun RestoreOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val borderColor = if (checked && enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val backgroundColor = if (checked && enabled) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { if (enabled) onChange(!checked) },
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        border = androidx.compose.foundation.BorderStroke(if (checked && enabled) 2.dp else 1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Checkbox(checked = checked, onCheckedChange = { if (enabled) onChange(it) }, enabled = enabled, colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary))
        }
    }
}

private val BackupFormat.displayName: String
    get() = when (this) {
        BackupFormat.ANIKUTA -> "AniKuta (.anikuta)"
        BackupFormat.ANIYOMI -> "Aniyomi (.tachibk)"
        BackupFormat.UNKNOWN -> "Unknown"
    }
