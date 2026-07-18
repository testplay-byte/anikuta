package app.anikuta.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.anikuta.backup.BackupManager
import app.anikuta.backup.RestoreProgress
import app.anikuta.backup.format.anikuta.RestoreOptions
import app.anikuta.backup.model.BackupSummary
import app.anikuta.backup.validator.BackupValidator
import app.anikuta.ui.settings.restore.RestorePreviewDialog
import app.anikuta.ui.settings.restore.RestoreProgressScreen
import android.net.Uri
import app.anikuta.core.util.system.logcat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Backup & Restore settings screen.
 *
 * Two export formats:
 *  - AniKuta format (.anikuta) — our own JSON format, complete data
 *  - Aniyomi format (.tachibk) — protobuf+gzip, aniyomi-compatible
 *
 * Restore uses the 4-step flow (Phase 3):
 *  1. User picks a file → BackupValidator.peekBackup (decode only)
 *  2. RestorePreviewDialog shows summary + options → user confirms
 *  3. RestoreProgressScreen shows live progress
 *  4. (Phase 6) Review screen for unlinked anime
 */
@Composable
fun BackupSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backupManager: BackupManager = remember { Injekt.get() }
    val validator: BackupValidator = remember { BackupValidator(context) }

    var isBackingUp by remember { mutableStateOf(false) }
    var showFormatDialog by remember { mutableStateOf(false) }

    // Restore flow state
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var isPeeking by remember { mutableStateOf(false) }
    var previewSummary by remember { mutableStateOf<BackupSummary?>(null) }
    var peekError by remember { mutableStateOf<String?>(null) }

    var isRestoring by remember { mutableStateOf(false) }
    var progressEvents by remember { mutableStateOf<List<RestoreProgress>>(emptyList()) }
    var restoreResult by remember { mutableStateOf<BackupManager.RestoreResult?>(null) }
    var showReviewScreen by remember { mutableStateOf(false) }
    var pendingCountState by remember { mutableStateOf(0) }

    val pendingLinkStore: app.anikuta.data.cache.PendingLinkStore = remember { Injekt.get() }
    // Refresh pending count when the screen is shown or after restore completes
    LaunchedEffect(isRestoring) {
        pendingCountState = pendingLinkStore.pendingCount()
    }

    // File launchers
    val createAnikutaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isBackingUp = true
                val success = withContext(Dispatchers.IO) {
                    backupManager.createAnikutaBackup(uri)
                }
                isBackingUp = false
                Toast.makeText(context,
                    if (success) "Backup created" else "Backup failed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val createAniyomiLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gzip")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isBackingUp = true
                val success = withContext(Dispatchers.IO) {
                    backupManager.createAniyomiBackup(uri)
                }
                isBackingUp = false
                Toast.makeText(context,
                    if (success) "Aniyomi backup created" else "Backup failed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            // Step 1: peek the backup (decode without restoring)
            pendingUri = uri
            isPeeking = true
            peekError = null
            scope.launch {
                val summary = withContext(Dispatchers.IO) {
                    validator.peekBackup(uri)
                }
                isPeeking = false
                if (summary.isParseable) {
                    previewSummary = summary
                } else {
                    peekError = summary.parseError ?: "Could not parse backup"
                }
            }
        }
    }

    SettingsSubpageScaffold(title = "Backup & Restore", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ---- Create backup ----
            item {
                SettingsGroupCard(title = "Create backup") {
                    ClickableSettingsRow(
                        icon = Icons.Default.Backup,
                        title = "Create backup",
                        subtitle = "Export your library, history, searches, and settings",
                        onClick = { showFormatDialog = true },
                    )
                }
            }

            // ---- Restore backup ----
            item {
                SettingsGroupCard(title = "Restore backup") {
                    ClickableSettingsRow(
                        icon = Icons.Default.Restore,
                        title = "Restore from backup",
                        subtitle = "Import a backup file (auto-detects format)",
                        onClick = { restoreLauncher.launch(arrayOf("*/*")) },
                    )
                    if (pendingCountState > 0) {
                        ClickableSettingsRow(
                            icon = Icons.Default.Link,
                            title = "Review unlinked anime ($pendingCountState)",
                            subtitle = "Anime from an aniyomi backup that need manual AniList linking",
                            onClick = { showReviewScreen = true },
                        )
                    }
                }
            }

            // ---- Auto backup ----
            item {
                app.anikuta.ui.settings.restore.AutoBackupSettingsSection()
            }

            // ---- Info ----
            item {
                SettingsGroupCard(title = "About backups") {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Two backup formats are supported:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("• AniKuta format (.anikuta) — our own format with all data", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("• Aniyomi format (.tachibk) — compatible with aniyomi", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Restore shows a preview before proceeding, so you can see what's in the backup.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    // Loading overlay (for backup creation + peek)
    if (isBackingUp || isPeeking) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Card {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(if (isBackingUp) "Creating backup..." else "Reading backup...")
                }
            }
        }
    }

    // Step 3: Restore progress screen (full-screen overlay)
    if (isRestoring) {
        Surface(modifier = Modifier.fillMaxSize()) {
            RestoreProgressScreen(
                events = progressEvents,
                isComplete = progressEvents.lastOrNull() is RestoreProgress.Complete ||
                             progressEvents.lastOrNull() is RestoreProgress.Error,
                onDone = {
                    isRestoring = false
                    // The restoreResult is already set; the result dialog below will show
                    pendingUri = null
                    progressEvents = emptyList()
                },
                onCancel = {
                    // Phase 6 will wire graceful cancellation
                    isRestoring = false
                    pendingUri = null
                    progressEvents = emptyList()
                },
            )
        }
    }

    // Format selection dialog
    if (showFormatDialog) {
        AlertDialog(
            onDismissRequest = { showFormatDialog = false },
            title = { Text("Backup format") },
            text = { Text("Which format do you want to use?") },
            confirmButton = {
                TextButton(onClick = {
                    showFormatDialog = false
                    createAnikutaLauncher.launch("anikuta_backup_${System.currentTimeMillis()}.anikuta")
                }) { Text("AniKuta (.anikuta)") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showFormatDialog = false
                    createAniyomiLauncher.launch("anikuta_backup_${System.currentTimeMillis()}.tachibk")
                }) { Text("Aniyomi (.tachibk)") }
            },
        )
    }

    // Step 2: Restore preview dialog
    previewSummary?.let { summary ->
        val uri = pendingUri
        if (uri != null) {
            RestorePreviewDialog(
                summary = summary,
                onRestore = { options ->
                    previewSummary = null
                    // Step 3: start the restore with live progress
                    isRestoring = true
                    progressEvents = emptyList()
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            backupManager.restoreBackupWithOptions(uri, options) { event ->
                                progressEvents = progressEvents + event
                            }
                        }
                        restoreResult = result
                    }
                },
                onCancel = {
                    previewSummary = null
                    pendingUri = null
                },
            )
        }
    }

    // Peek error dialog
    peekError?.let { error ->
        AlertDialog(
            onDismissRequest = {
                peekError = null
                pendingUri = null
            },
            title = { Text("Could not read backup") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = {
                    peekError = null
                    pendingUri = null
                }) { Text("OK") }
            },
        )
    }

    // Restore result dialog (shown after Step 3 "Done")
    restoreResult?.let { result ->
        when (result) {
            is BackupManager.RestoreResult.Success -> {
                AlertDialog(
                    onDismissRequest = { restoreResult = null },
                    title = { Text("Restore complete") },
                    text = {
                        buildString {
                            append("✓ Library: ${result.libraryCount} anime\n")
                            append("✓ History: ${result.historyCount} entries\n")
                            append("✓ Searches: ${result.searchCount} terms\n")
                            append("✓ Categories: ${result.categoryCount}")
                            result.note?.let { append("\n\n⚠ $it") }
                        }.let { Text(it) }
                    },
                    confirmButton = { TextButton(onClick = { restoreResult = null }) { Text("OK") } },
                )
            }
            is BackupManager.RestoreResult.Error -> {
                AlertDialog(
                    onDismissRequest = { restoreResult = null },
                    title = { Text("Restore failed") },
                    text = { Text(result.message) },
                    confirmButton = { TextButton(onClick = { restoreResult = null }) { Text("OK") } },
                )
            }
        }
    }

    // Step 4: Unlinked-anime review screen (full-screen overlay)
    if (showReviewScreen) {
        Surface(modifier = Modifier.fillMaxSize()) {
            app.anikuta.ui.settings.restore.UnlinkedAnimeReviewScreen(
                onDone = {
                    showReviewScreen = false
                    pendingCountState = pendingLinkStore.pendingCount()
                },
            )
        }
    }
}
