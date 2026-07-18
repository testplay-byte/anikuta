package app.anikuta.ui.settings.restore

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.anikuta.backup.format.anikuta.RestoreOptions
import app.anikuta.backup.model.BackupFormat
import app.anikuta.backup.model.BackupSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The restore preview dialog (Step 2 of the restore flow).
 *
 * Shown after the user picks a backup file and [BackupValidator] decodes it.
 * Displays:
 *  - Format, created date, app version, schema version.
 *  - Per-section content counts (anime, history, categories, tracking, etc.).
 *  - Warnings (missing sources, manga data, unlinked anime estimate).
 *  - Restore options (checkboxes for each section).
 *  - [Cancel] / [Restore] buttons.
 *
 * The user reviews this, toggles options, and taps Restore to proceed to
 * Step 3 (the live-progress restore screen).
 *
 * @param summary the [BackupSummary] from [BackupValidator.peekBackup].
 * @param onRestore called with the user's chosen [RestoreOptions] when they
 *   tap Restore.
 * @param onCancel called when they tap Cancel or dismiss the dialog.
 */
@Composable
fun RestorePreviewDialog(
    summary: BackupSummary,
    onRestore: (RestoreOptions) -> Unit,
    onCancel: () -> Unit,
) {
    // Local state for the restore-option checkboxes.
    var library by remember { mutableStateOf(true) }
    var history by remember { mutableStateOf(true) }
    var searches by remember { mutableStateOf(true) }
    var categories by remember { mutableStateOf(true) }
    var tracking by remember { mutableStateOf(true) }
    var settings by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Backup preview") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Format + metadata
                Text(
                    "Format: ${summary.format.displayName}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                if (summary.createdAt > 0) {
                    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        .format(Date(summary.createdAt))
                    Text("Created: $dateStr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (summary.appVersion.isNotEmpty()) {
                    Text("App version: ${summary.appVersion}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (summary.schemaVersion > 0) {
                    Text("Schema: v${summary.schemaVersion}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                HorizontalDivider()

                // Content counts
                Text("Contains:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                if (summary.libraryCount > 0) Text("• ${summary.libraryCount} anime in library", style = MaterialTheme.typography.bodySmall)
                if (summary.historyCount > 0) Text("• ${summary.historyCount} history entries", style = MaterialTheme.typography.bodySmall)
                if (summary.categoryCount > 0) Text("• ${summary.categoryCount} categories", style = MaterialTheme.typography.bodySmall)
                if (summary.trackingCount > 0) Text("• ${summary.trackingCount} tracking entries", style = MaterialTheme.typography.bodySmall)
                if (summary.subDubCount > 0) Text("• ${summary.subDubCount} sub/dub entries", style = MaterialTheme.typography.bodySmall)
                if (summary.extensionLinkCount > 0) Text("• ${summary.extensionLinkCount} extension links", style = MaterialTheme.typography.bodySmall)
                if (summary.playbackStateCount > 0) Text("• ${summary.playbackStateCount} playback states", style = MaterialTheme.typography.bodySmall)
                if (summary.searchCount > 0) Text("• ${summary.searchCount} recent searches", style = MaterialTheme.typography.bodySmall)
                if (summary.preferenceCount > 0) Text("• ${summary.preferenceCount} preferences", style = MaterialTheme.typography.bodySmall)
                if (summary.mangaCount > 0) {
                    Text("• ${summary.mangaCount} manga entries (will be skipped)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }

                // Warnings
                if (summary.warnings.isNotEmpty() || summary.estimatedUnlinkedCount > 0 || summary.missingSources.isNotEmpty()) {
                    HorizontalDivider()
                    Text("⚠ Warnings:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                    summary.warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (summary.estimatedUnlinkedCount > 0) {
                        Text("• ${summary.estimatedUnlinkedCount} anime may need manual AniList linking after restore.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (summary.missingSources.isNotEmpty()) {
                        Text("• Sources not installed: ${summary.missingSources.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                HorizontalDivider()

                // Restore options
                Text("Restore options:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                RestoreOptionCheckbox("Library (${summary.libraryCount})", library, summary.libraryCount > 0) { library = it }
                RestoreOptionCheckbox("History (${summary.historyCount})", history, summary.historyCount > 0) { history = it }
                RestoreOptionCheckbox("Recent searches (${summary.searchCount})", searches, summary.searchCount > 0) { searches = it }
                RestoreOptionCheckbox("Categories (${summary.categoryCount})", categories, summary.categoryCount > 0) { categories = it }
                RestoreOptionCheckbox("Notifications & tracking (${summary.trackingCount})", tracking, summary.trackingCount > 0) { tracking = it }
                RestoreOptionCheckbox("Settings / preferences (${summary.preferenceCount})", settings, summary.preferenceCount > 0) { settings = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onRestore(RestoreOptions(library, history, searches, categories, tracking, settings))
                },
            ) { Text("Restore") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        },
    )
}

@Composable
private fun RestoreOptionCheckbox(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange, enabled = enabled)
        Text(label, style = MaterialTheme.typography.bodySmall, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Human-readable name for [BackupFormat]. */
private val BackupFormat.displayName: String
    get() = when (this) {
        BackupFormat.ANIKUTA -> "AniKuta (.anikuta)"
        BackupFormat.ANIYOMI -> "Aniyomi (.tachibk)"
        BackupFormat.UNKNOWN -> "Unknown"
    }
