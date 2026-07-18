package app.anikuta.ui.settings.restore

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.anikuta.backup.AutoBackupPreferences
import app.anikuta.backup.BackupAutoJob
import app.anikuta.core.util.system.logcat
import logcat.LogPriority
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Auto-backup settings section (shown inside BackupSettingsScreen).
 *
 * Lets the user configure:
 *  - **Frequency**: 0 (disabled) / 6h / 12h / 24h / 48h.
 *  - **Max backups**: 2 / 4 / 6 / 8 (older ones auto-deleted).
 *  - **Backup directory**: SAF folder picker.
 *  - **Format**: AniKuta (.anikuta) or Aniyomi (.tachibk).
 *  - **Last backup**: read-only timestamp display.
 *
 * When the user changes the frequency, [BackupAutoJob.schedule] is called to
 * update the WorkManager periodic work.
 *
 * Per user decision #4 (2026-07-18).
 */
@Composable
fun AutoBackupSettingsSection() {
    val context = LocalContext.current
    val autoPrefs: AutoBackupPreferences = remember { Injekt.get() }

    var frequencyHours by remember { mutableStateOf(autoPrefs.frequencyHours().get()) }
    var maxBackups by remember { mutableStateOf(autoPrefs.maxBackups().get()) }
    var format by remember { mutableStateOf(autoPrefs.format().get()) }
    var directoryUri by remember { mutableStateOf(autoPrefs.directoryUri().get()) }
    var lastBackup by remember { mutableStateOf(autoPrefs.lastBackupTimestamp().get()) }
    var showFrequencyDialog by remember { mutableStateOf(false) }
    var showMaxDialog by remember { mutableStateOf(false) }

    val dirLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            // Persist the URI permission so we can write to it later
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (e: SecurityException) {
                logcat(LogPriority.WARN, e) { "Could not persist URI permission" }
            }
            directoryUri = uri.toString()
            autoPrefs.directoryUri().set(uri.toString())
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("AUTO BACKUP", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

            // Frequency
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Frequency", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { showFrequencyDialog = true }) {
                    Text(if (frequencyHours <= 0) "Disabled" else "Every $frequencyHours hours")
                }
            }

            // Max backups (only relevant if enabled)
            if (frequencyHours > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Keep last N backups", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { showMaxDialog = true }) { Text("$maxBackups") }
                }

                // Format
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Format", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = {
                        format = if (format == "anikuta") "tachibk" else "anikuta"
                        autoPrefs.format().set(format)
                    }) { Text(if (format == "anikuta") "AniKuta (.anikuta)" else "Aniyomi (.tachibk)") }
                }

                // Directory
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Backup folder", style = MaterialTheme.typography.bodyMedium)
                        if (directoryUri.isEmpty()) {
                            Text("Not set — tap to choose", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("Set", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    TextButton(onClick = { dirLauncher.launch(null) }) { Text("Choose") }
                }

                // Last backup timestamp
                if (lastBackup > 0) {
                    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(lastBackup))
                    Text("Last backup: $dateStr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    // Frequency dialog
    if (showFrequencyDialog) {
        AlertDialog(
            onDismissRequest = { showFrequencyDialog = false },
            title = { Text("Backup frequency") },
            text = {
                Column {
                    listOf(0 to "Disabled", 6 to "Every 6 hours", 12 to "Every 12 hours", 24 to "Every 24 hours", 48 to "Every 48 hours").forEach { (hours, label) ->
                        TextButton(
                            onClick = {
                                frequencyHours = hours
                                autoPrefs.frequencyHours().set(hours)
                                BackupAutoJob.schedule(context, hours)
                                if (hours > 0 && directoryUri.isEmpty()) {
                                    Toast.makeText(context, "Choose a backup folder", Toast.LENGTH_SHORT).show()
                                }
                                showFrequencyDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(label) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFrequencyDialog = false }) { Text("Cancel") } },
        )
    }

    // Max backups dialog
    if (showMaxDialog) {
        AlertDialog(
            onDismissRequest = { showMaxDialog = false },
            title = { Text("Keep last N backups") },
            text = {
                Column {
                    listOf(2, 4, 6, 8, 10).forEach { n ->
                        TextButton(
                            onClick = {
                                maxBackups = n
                                autoPrefs.maxBackups().set(n)
                                showMaxDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("$n backups") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showMaxDialog = false }) { Text("Cancel") } },
        )
    }
}
