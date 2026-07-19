package app.anikuta.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
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
import app.anikuta.ui.settings.restore.RestorePreviewScreen
import app.anikuta.ui.settings.restore.RestoreProgressScreen
import app.anikuta.ui.settings.restore.RestoreCompleteScreen
import app.anikuta.ui.settings.restore.UnlinkedAnimeReviewScreen
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Backup & Restore settings screen.
 *
 * Restore flow (5 steps, all full-screen):
 *  1. User picks a file → BackupValidator.peekBackup (loading overlay)
 *  2. RestorePreviewScreen (full screen) — summary + options → user taps Restore
 *  3. RestoreProgressScreen (full screen) — live progress → auto-advances to 4
 *  4. RestoreCompleteScreen (full screen) — summary + Show Results + Review/Finish
 *  5. UnlinkedAnimeReviewScreen (full screen, if unlinked > 0) → returns to 4
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

    // Full-screen overlays
    var showPreviewScreen by remember { mutableStateOf(false) }
    var showCompleteScreen by remember { mutableStateOf(false) }
    var showReviewScreen by remember { mutableStateOf(false) }
    var pendingCountState by remember { mutableStateOf(0) }

    val pendingLinkStore: app.anikuta.data.cache.PendingLinkStore = remember { Injekt.get() }
    LaunchedEffect(isRestoring, showReviewScreen) {
        pendingCountState = pendingLinkStore.pendingCount()
    }

    // File launchers
    val createAnikutaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isBackingUp = true
                val success = withContext(Dispatchers.IO) { backupManager.createAnikutaBackup(uri) }
                isBackingUp = false
                Toast.makeText(context, if (success) "Backup created" else "Backup failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val createAniyomiLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gzip")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isBackingUp = true
                val success = withContext(Dispatchers.IO) { backupManager.createAniyomiBackup(uri) }
                isBackingUp = false
                Toast.makeText(context, if (success) "Aniyomi backup created" else "Backup failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingUri = uri
            isPeeking = true
            peekError = null
            scope.launch {
                val summary = withContext(Dispatchers.IO) { validator.peekBackup(uri) }
                isPeeking = false
                if (summary.isParseable) {
                    previewSummary = summary
                    showPreviewScreen = true
                } else {
                    peekError = summary.parseError ?: "Could not parse backup"
                }
            }
        }
    }

    // ---- The settings list (shown when no full-screen overlay is active) ----
    SettingsSubpageScaffold(title = "Backup & Restore", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
            item { app.anikuta.ui.settings.restore.AutoBackupSettingsSection() }
            item {
                SettingsGroupCard(title = "About backups") {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Two backup formats are supported:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("• AniKuta format (.anikuta) — our own format with all data", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("• Aniyomi format (.tachibk) — compatible with aniyomi", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    // Loading overlay (backup creation + peek)
    if (isBackingUp || isPeeking) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Card {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(if (isBackingUp) "Creating backup..." else "Reading backup...")
                }
            }
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

    // Peek error dialog
    peekError?.let { error ->
        AlertDialog(
            onDismissRequest = { peekError = null; pendingUri = null },
            title = { Text("Could not read backup") },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = { peekError = null; pendingUri = null }) { Text("OK") } },
        )
    }

    // ---- Step 2: Preview (full screen) ----
    if (showPreviewScreen) {
        val summary = previewSummary
        val uri = pendingUri
        if (summary != null && uri != null) {
            Surface(modifier = Modifier.fillMaxSize()) {
                RestorePreviewScreen(
                    summary = summary,
                    onRestore = { options ->
                        showPreviewScreen = false
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
                        showPreviewScreen = false
                        previewSummary = null
                        pendingUri = null
                    },
                )
            }
        }
    }

    // ---- Step 3: Progress (full screen) ----
    if (isRestoring) {
        Surface(modifier = Modifier.fillMaxSize()) {
            RestoreProgressScreen(
                events = progressEvents,
                isComplete = progressEvents.lastOrNull() is RestoreProgress.Complete ||
                             progressEvents.lastOrNull() is RestoreProgress.Error,
                onDone = {
                    isRestoring = false
                    showCompleteScreen = true
                    pendingUri = null
                    progressEvents = emptyList()
                },
                onCancel = {
                    isRestoring = false
                    pendingUri = null
                    progressEvents = emptyList()
                },
            )
        }
    }

    // ---- Step 4: Complete (full screen) ----
    if (showCompleteScreen) {
        val result = restoreResult
        Surface(modifier = Modifier.fillMaxSize()) {
            RestoreCompleteScreen(
                libraryCount = (result as? BackupManager.RestoreResult.Success)?.libraryCount ?: 0,
                historyCount = (result as? BackupManager.RestoreResult.Success)?.historyCount ?: 0,
                categoryCount = (result as? BackupManager.RestoreResult.Success)?.categoryCount ?: 0,
                preferenceCount = 0,
                unlinkedCount = (result as? BackupManager.RestoreResult.Success)?.unlinkedCount ?: 0,
                errors = if (result is BackupManager.RestoreResult.Error) listOf(result.message) else emptyList(),
                note = (result as? BackupManager.RestoreResult.Success)?.note,
                onReviewUnlinked = {
                    showCompleteScreen = false
                    showReviewScreen = true
                    pendingCountState = pendingLinkStore.pendingCount()
                },
                onDone = {
                    showCompleteScreen = false
                    restoreResult = null
                },
            )
        }
    }

    // ---- Step 5: Review unlinked (full screen) ----
    if (showReviewScreen) {
        Surface(modifier = Modifier.fillMaxSize()) {
            UnlinkedAnimeReviewScreen(
                onDone = {
                    showReviewScreen = false
                    pendingCountState = pendingLinkStore.pendingCount()
                    // If there are still unlinked anime, return to complete screen.
                    // Otherwise, we're done.
                    if (pendingLinkStore.pendingCount() > 0) {
                        showCompleteScreen = true
                    }
                },
            )
        }
    }
}
