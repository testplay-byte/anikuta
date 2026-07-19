package app.anikuta.ui.settings.restore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PublishedWithChanges
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.anikuta.backup.RestoreProgress
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The live-progress restore screen (Step 3 of the restore flow).
 *
 * Redesigned to match the app's M3 Expressive design language:
 *  - Large animated header with status icon + percentage.
 *  - Real progress bar that updates as anime are processed.
 *  - Scrollable live log with per-entry status icons (linked/fetched/saved/unlinked).
 *  - Auto-scrolls to the latest entry.
 *  - Color-coded status icons (green=linked, blue=fuzzy, orange=unlinked, red=error).
 *  - Spring animations for new entries.
 *
 * @param events the list of [RestoreProgress] events emitted so far.
 * @param isComplete `true` when restore is finished (shows Done button).
 * @param onDone called when the user taps Done.
 * @param onCancel called when the user taps Cancel.
 */
@Composable
fun RestoreProgressScreen(
    events: List<RestoreProgress>,
    isComplete: Boolean,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val lastRestoring = events.lastOrNull { it is RestoreProgress.Restoring } as? RestoreProgress.Restoring
    val isCompleteState = events.lastOrNull() is RestoreProgress.Complete
    val isError = events.lastOrNull() is RestoreProgress.Error
    val fraction = lastRestoring?.fraction ?: 0f
    val animatedFraction by animateFloatAsState(targetValue = fraction, label = "progress_bar")

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Auto-scroll to the latest event
    LaunchedEffect(events.size) {
        if (events.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(events.size - 1) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ---- Header ----
        HeaderCard(
            isComplete = isCompleteState,
            isError = isError,
            fraction = animatedFraction,
            currentMessage = lastRestoring?.message,
            completeSummary = (events.lastOrNull() as? RestoreProgress.Complete)?.summary,
        )

        // ---- Progress bar ----
        if (!isCompleteState && !isError) {
            LinearProgressIndicator(
                progress = { animatedFraction },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            )
            Text(
                "${(animatedFraction * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        // ---- Live log ----
        Text("LIVE LOG", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(events, key = { events.indexOf(it) }) { event ->
                LogEntryCard(event)
            }
        }

        // ---- Buttons ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!isComplete && !isError) {
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
            } else {
                Button(onClick = onDone) { Text("Done") }
            }
        }
    }
}

@Composable
private fun HeaderCard(
    isComplete: Boolean,
    isError: Boolean,
    fraction: Float,
    currentMessage: String?,
    completeSummary: String?,
) {
    val (icon, color, title) = when {
        isError -> Triple(Icons.Default.Error, MaterialTheme.colorScheme.error, "Restore failed")
        isComplete -> Triple(Icons.Default.CheckCircle, MaterialTheme.colorScheme.primary, "Restore complete")
        else -> Triple(Icons.Default.PublishedWithChanges, MaterialTheme.colorScheme.primary, "Restoring...")
    }

    // Pulsing animation for the icon while restoring
    val infiniteTransition = rememberInfiniteTransition(label = "header_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (!isComplete && !isError) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(32.dp).then(
                        if (!isComplete && !isError) Modifier.scale(scaleX = pulseScale, scaleY = pulseScale) else Modifier,
                    ),
                )
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            // Fixed-height message area to prevent the header from jumping when
            // the message text changes length. 2 lines max, ellipsized.
            Text(
                text = when {
                    isComplete && completeSummary != null -> completeSummary
                    !isComplete && !isError && currentMessage != null -> currentMessage
                    else -> ""
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.height(40.dp), // fixed height prevents jumping
            )
        }
    }
}

@Composable
private fun LogEntryCard(event: RestoreProgress) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val (icon, color, text) = when (event) {
        is RestoreProgress.Decoding -> Triple(Icons.Default.Search, MaterialTheme.colorScheme.onSurfaceVariant, event.message)
        is RestoreProgress.Restoring -> parseRestoringMessage(event.message)
        is RestoreProgress.Complete -> Triple(Icons.Default.CheckCircle, MaterialTheme.colorScheme.primary, event.summary)
        is RestoreProgress.Error -> Triple(Icons.Default.Error, MaterialTheme.colorScheme.error, event.message)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                timeFormat.format(Date()),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(64.dp),
            )
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp).padding(top = 1.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        }
    }
}

/** Parse a Restoring message to determine the status icon + color. */
@Composable
private fun parseRestoringMessage(message: String): Triple<ImageVector, Color, String> {
    return when {
        message.contains("LINKED_TRACKER") || message.contains("Tier 1") ->
            Triple(Icons.Default.Link, Color(0xFF4CAF50), message) // green
        message.contains("LINKED_CACHE") || message.contains("Tier 2") ->
            Triple(Icons.Default.Link, Color(0xFF2196F3), message) // blue
        message.contains("LINKED_FUZZY") || message.contains("Tier 3") || message.contains("Fuzzy") ->
            Triple(Icons.Default.Search, Color(0xFFFF9800), message) // orange
        message.contains("UNLINKED") ->
            Triple(Icons.Default.CloudOff, Color(0xFFFF5722), message) // red-orange
        message.contains("SAVED") ->
            Triple(Icons.Default.CheckCircle, Color(0xFF4CAF50), message) // green
        message.contains("FETCHING") ->
            Triple(Icons.Default.Visibility, MaterialTheme.colorScheme.primary, message)
        message.contains("SKIPPED") || message.contains("⚠") ->
            Triple(Icons.Default.Error, MaterialTheme.colorScheme.error, message)
        else ->
            Triple(Icons.Default.PublishedWithChanges, MaterialTheme.colorScheme.onSurfaceVariant, message)
    }
}
