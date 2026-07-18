package app.anikuta.ui.settings.restore

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.anikuta.backup.RestoreProgress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The live-progress restore screen (Step 3 of the restore flow).
 *
 * Shown after the user confirms the restore in [RestorePreviewDialog]. Displays:
 *  - A progress bar (when in Restoring state).
 *  - A live log of [RestoreProgress] events (timestamped).
 *  - Live errors/issues shown as they occur (read-only — user can't fix during restore).
 *  - A Cancel button (aborts gracefully — Phase 6 will wire cancellation).
 *  - On completion: a "Done" button to proceed to Step 4 (review).
 *
 * @param events the list of [RestoreProgress] events emitted so far.
 * @param isComplete `true` when restore is finished (shows Done button).
 * @param onDone called when the user taps Done (proceed to Step 4 review).
 * @param onCancel called when the user taps Cancel (aborts).
 */
@Composable
fun RestoreProgressScreen(
    events: List<RestoreProgress>,
    isComplete: Boolean,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val lastEvent = events.lastOrNull()
    val progress = (lastEvent as? RestoreProgress.Restoring)?.fraction
    val isError = lastEvent is RestoreProgress.Error
    val isCompleteState = lastEvent is RestoreProgress.Complete

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Status header
        Text(
            text = when {
                isError -> "❌ Restore failed"
                isCompleteState -> "✅ Restore complete"
                progress != null -> "Restoring... ${(progress * 100).toInt()}%"
                else -> "Processing..."
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        // Progress bar
        if (progress != null && !isCompleteState && !isError) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (!isCompleteState && !isError) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        // Live log
        Text("Live log:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(events) { event ->
                RestoreProgressLogEntry(event)
            }
        }

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!isComplete && !isError) {
                TextButton(onClick = onCancel) { Text("Cancel") }
            } else {
                Button(onClick = onDone) { Text("Done") }
            }
        }
    }
}

/** A single timestamped log entry in the live-progress screen. */
@Composable
private fun RestoreProgressLogEntry(event: RestoreProgress) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val now = remember { System.currentTimeMillis() }

    val (icon, color, text) = when (event) {
        is RestoreProgress.Decoding -> Triple(null, MaterialTheme.colorScheme.onSurfaceVariant, event.message)
        is RestoreProgress.Restoring -> Triple(null, MaterialTheme.colorScheme.onSurface, event.message)
        is RestoreProgress.Complete -> Triple(Icons.Default.CheckCircle, MaterialTheme.colorScheme.primary, event.summary)
        is RestoreProgress.Error -> Triple(Icons.Default.Error, MaterialTheme.colorScheme.error, event.message)
    }

    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = timeFormat.format(Date(now)),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp).padding(top = 2.dp))
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            modifier = Modifier.weight(1f),
        )
    }
}
