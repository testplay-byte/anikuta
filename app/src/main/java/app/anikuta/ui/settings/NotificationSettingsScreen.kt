package app.anikuta.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bell
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QuietTime
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.anikuta.core.preference.Preference
import app.anikuta.notification.NotificationPreferences
import kotlinx.coroutines.flow.collectLatest
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Phase N-4 (Settings UI) — Global notification + auto-download settings.
 *
 * Sections:
 *  - General (enable, notify mode, check completed)
 *  - Sub/Dub (notify sub, notify dub)
 *  - Auto-download (new releases)
 *  - Watch-flow auto-download
 *  - Quiet hours
 *
 * Per-anime settings are edited from the detail page's three-dot menu
 * (AnimeSettingsSheet), not here.
 */
@Composable
fun NotificationSettingsScreen(onBack: () -> Unit) {
    val prefs: NotificationPreferences = remember {
        try { Injekt.get() } catch (e: Exception) { null }
    } ?: run {
        // DI not available — show error
        SettingsSubpageScaffold(title = "Notifications", onBack = onBack) {
            Text("Settings unavailable", modifier = Modifier.padding(16.dp))
        }
        return
    }

    // General
    val globalNotifyEnabled by prefs.globalNotifyEnabled().stateInAsState()
    val notifyMode by prefs.notifyMode().stateInAsState()
    val checkCompleted by prefs.checkCompletedAnime().stateInAsState()

    // Sub/Dub
    val notifySub by prefs.globalNotifySub().stateInAsState()
    val notifyDub by prefs.globalNotifyDub().stateInAsState()

    // Auto-download (new releases)
    val autoDlEnabled by prefs.globalAutoDownloadEnabled().stateInAsState()
    val autoDlSub by prefs.globalAutoDownloadSub().stateInAsState()
    val autoDlDub by prefs.globalAutoDownloadDub().stateInAsState()
    val autoDlQuality by prefs.globalAutoDownloadQuality().stateInAsState()
    val autoDlAudio by prefs.globalAutoDownloadAudio().stateInAsState()

    // Watch-flow auto-download
    val watchFlowEnabled by prefs.watchFlowAutoDownloadEnabled().stateInAsState()
    val watchFlowAudio by prefs.watchFlowAutoDownloadAudio().stateInAsState()

    // Quiet hours
    val quietEnabled by prefs.quietHoursEnabled().stateInAsState()
    val quietStart by prefs.quietHoursStart().stateInAsState()
    val quietEnd by prefs.quietHoursEnd().stateInAsState()

    SettingsSubpageScaffold(title = "Notifications", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ---- General ----
            item {
                SettingsGroupCard(title = "General") {
                    SwitchSettingsRow(
                        icon = Icons.Default.Bell,
                        title = "Enable notifications",
                        subtitle = "Get notified when new episodes are available",
                        checked = globalNotifyEnabled,
                        onCheckedChange = { prefs.globalNotifyEnabled().set(it) },
                    )
                    HorizontalDivider()
                    // Notify mode selector
                    NotifyModeRow(notifyMode) { prefs.notifyMode().set(it) }
                    HorizontalDivider()
                    SwitchSettingsRow(
                        icon = Icons.Default.Check,
                        title = "Check completed anime",
                        subtitle = "Continue checking anime that are marked as finished (for dub episodes)",
                        checked = checkCompleted,
                        onCheckedChange = { prefs.checkCompletedAnime().set(it) },
                    )
                }
            }

            // ---- Sub/Dub ----
            item {
                SettingsGroupCard(title = "Sub / Dub") {
                    SwitchSettingsRow(
                        icon = Icons.Default.Subtitles,
                        title = "Notify on new SUB episodes",
                        subtitle = "Get notified when a new subbed episode is available",
                        checked = notifySub,
                        onCheckedChange = { prefs.globalNotifySub().set(it) },
                    )
                    HorizontalDivider()
                    SwitchSettingsRow(
                        icon = Icons.Default.GraphicEq,
                        title = "Notify on new DUB episodes",
                        subtitle = "Get notified when a new dubbed episode is available",
                        checked = notifyDub,
                        onCheckedChange = { prefs.globalNotifyDub().set(it) },
                    )
                }
            }

            // ---- Auto-download (new releases) ----
            item {
                SettingsGroupCard(title = "Auto-download (new releases)") {
                    SwitchSettingsRow(
                        icon = Icons.Default.CloudDownload,
                        title = "Auto-download new episodes",
                        subtitle = "Automatically download new episodes when they're released",
                        checked = autoDlEnabled,
                        onCheckedChange = { prefs.globalAutoDownloadEnabled().set(it) },
                    )
                    if (autoDlEnabled) {
                        HorizontalDivider()
                        SwitchSettingsRow(
                            icon = Icons.Default.Subtitles,
                            title = "Auto-download SUB",
                            subtitle = "Download new subbed episodes",
                            checked = autoDlSub,
                            onCheckedChange = { prefs.globalAutoDownloadSub().set(it) },
                        )
                        HorizontalDivider()
                        SwitchSettingsRow(
                            icon = Icons.Default.GraphicEq,
                            title = "Auto-download DUB",
                            subtitle = "Download new dubbed episodes",
                            checked = autoDlDub,
                            onCheckedChange = { prefs.globalAutoDownloadDub().set(it) },
                        )
                        HorizontalDivider()
                        // Quality selector
                        QualitySelectorRow(autoDlQuality) { prefs.globalAutoDownloadQuality().set(it) }
                        HorizontalDivider()
                        // Audio selector
                        AudioSelectorRow(autoDlAudio) { prefs.globalAutoDownloadAudio().set(it) }
                    }
                }
            }

            // ---- Watch-flow auto-download ----
            item {
                SettingsGroupCard(title = "Watch-flow auto-download") {
                    SwitchSettingsRow(
                        icon = Icons.Default.PlayArrow,
                        title = "Auto-download next episode",
                        subtitle = "While watching a downloaded episode, pre-download the next one",
                        checked = watchFlowEnabled,
                        onCheckedChange = { prefs.watchFlowAutoDownloadEnabled().set(it) },
                    )
                    if (watchFlowEnabled) {
                        HorizontalDivider()
                        AudioSelectorRow(watchFlowAudio) { prefs.watchFlowAutoDownloadAudio().set(it) }
                    }
                }
            }

            // ---- Quiet hours ----
            item {
                SettingsGroupCard(title = "Quiet hours") {
                    SwitchSettingsRow(
                        icon = Icons.Default.QuietTime,
                        title = "Enable quiet hours",
                        subtitle = "Silence notifications during set hours",
                        checked = quietEnabled,
                        onCheckedChange = { prefs.quietHoursEnabled().set(it) },
                    )
                    if (quietEnabled) {
                        HorizontalDivider()
                        TimeSelectorRow(
                            label = "Start",
                            hour = quietStart,
                            onHourChange = { prefs.quietHoursStart().set(it) },
                        )
                        HorizontalDivider()
                        TimeSelectorRow(
                            label = "End",
                            hour = quietEnd,
                            onHourChange = { prefs.quietHoursEnd().set(it) },
                        )
                    }
                }
            }
        }
    }
}

// ---- Helper composables ----

@Composable
private fun NotifyModeRow(currentMode: String, onModeChange: (String) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("Notify mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Text("When to send new-episode notifications", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        val modes = listOf(
            "extension" to "When watchable (extension-confirmed)",
            "anilist" to "When aired (AniList schedule)",
            "both" to "Both",
        )
        modes.forEach { (value, label) ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onModeChange(value) }.padding(vertical = 4.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                RadioButton(selected = currentMode == value, onClick = { onModeChange(value) })
                Spacer(modifier = Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun QualitySelectorRow(current: String, onChange: (String) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("Preferred quality", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        val qualities = listOf("best", "1080", "720", "360")
        qualities.forEach { q ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onChange(q) }.padding(vertical = 4.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                RadioButton(selected = current == q, onClick = { onChange(q) })
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (q == "best") "Best available" else "${q}p", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun AudioSelectorRow(current: String, onChange: (String) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("Preferred audio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        val audios = listOf("SUB" to "Sub", "DUB" to "Dub", "ANY" to "Any")
        audios.forEach { (value, label) ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onChange(value) }.padding(vertical = 4.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                RadioButton(selected = current == value, onClick = { onChange(value) })
                Spacer(modifier = Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun TimeSelectorRow(label: String, hour: Int, onHourChange: (Int) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable { showDialog = true }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.weight(1f))
        Text(formatHour(hour), style = MaterialTheme.typography.bodyLarge)
    }
    if (showDialog) {
        TimePickerDialog(
            initialHour = hour,
            onConfirm = { onHourChange(it); showDialog = false },
            onDismiss = { showDialog = false },
        )
    }
}

private fun formatHour(hour: Int): String {
    val amPm = if (hour < 12) "AM" else "PM"
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return "$displayHour:00 $amPm"
}

@Composable
private fun TimePickerDialog(initialHour: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var selectedHour by remember { mutableStateOf(initialHour) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select hour") },
        text = {
            Column {
                Text("Hour: $selectedHour (${formatHour(selectedHour)})")
                Slider(
                    value = selectedHour.toFloat(),
                    onValueChange = { selectedHour = it.toInt() },
                    valueRange = 0f..23f,
                    steps = 22,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selectedHour) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---- State helper: read a Preference as Compose state ----

@Composable
private fun <T> Preference<T>.stateInAsState(): State<T> {
    return produceState(initialValue = this.get(), this) {
        this@stateInAsState.changes().collectLatest { value = it }
    }
}
