package app.anikuta.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.anikuta.core.preference.Preference
import app.anikuta.notification.NotificationPreferences
import kotlinx.coroutines.flow.collectLatest
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// =========================================================================
// Sub-screen 1: General (enable notifications + notify mode + check completed)
// =========================================================================

@Composable
fun NotificationGeneralScreen(onBack: () -> Unit) {
    val prefs: NotificationPreferences = remember { Injekt.get() }
    val context = LocalContext.current

    val enabled by prefs.globalNotifyEnabled().stateInAsState()
    val mode by prefs.notifyMode().stateInAsState()
    val checkCompleted by prefs.checkCompletedAnime().stateInAsState()

    var showEnableDialog by remember { mutableStateOf(false) }

    SettingsSubpageScaffold(title = "General", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SettingsGroupCard(title = "Notifications") {
                    SwitchSettingsRow(
                        icon = Icons.Default.Notifications,
                        title = "Enable notifications",
                        subtitle = "Get notified when new episodes are available",
                        checked = enabled,
                        onCheckedChange = { newValue ->
                            if (newValue) {
                                showEnableDialog = true
                            } else {
                                prefs.globalNotifyEnabled().set(false)
                            }
                        },
                    )
                }
            }
            item {
                SettingsGroupCard(title = "Notify mode") {
                    ModernNotifyModeSelector(currentMode = mode) { newMode ->
                        prefs.notifyMode().set(newMode)
                    }
                }
            }
            item {
                SettingsGroupCard(title = "Completed anime") {
                    SwitchSettingsRow(
                        icon = Icons.Default.Check,
                        title = "Check completed anime",
                        subtitle = "Continue checking anime marked as finished (useful for dub episodes that release later)",
                        checked = checkCompleted,
                        onCheckedChange = { prefs.checkCompletedAnime().set(it) },
                    )
                }
            }
        }
    }

    // Confirmation dialog when enabling notifications
    if (showEnableDialog) {
        AlertDialog(
            onDismissRequest = { showEnableDialog = false },
            icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
            title = { Text("Enable notifications?") },
            text = {
                Text("When enabled, you'll receive notifications when new episodes of your library anime are released. " +
                     "You can configure which anime to track and whether to notify on sub, dub, or both.")
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.globalNotifyEnabled().set(true)
                    showEnableDialog = false
                    Toast.makeText(context, "Notifications enabled", Toast.LENGTH_SHORT).show()
                }) { Text("Enable") }
            },
            dismissButton = {
                TextButton(onClick = { showEnableDialog = false }) { Text("Cancel") }
            },
        )
    }
}

// =========================================================================
// Sub-screen 2: Sub / Dub
// =========================================================================

@Composable
fun NotificationSubDubScreen(onBack: () -> Unit) {
    val prefs: NotificationPreferences = remember { Injekt.get() }
    val notifySub by prefs.globalNotifySub().stateInAsState()
    val notifyDub by prefs.globalNotifyDub().stateInAsState()

    SettingsSubpageScaffold(title = "Sub / Dub", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SettingsGroupCard(title = "Notification preferences") {
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
                        subtitle = "Get notified when a new dubbed episode is available. Dub episodes are often released later than sub.",
                        checked = notifyDub,
                        onCheckedChange = { prefs.globalNotifyDub().set(it) },
                    )
                }
            }
        }
    }
}

// =========================================================================
// Sub-screen 3: Auto Download (new releases + watch-flow)
// =========================================================================

@Composable
fun NotificationAutoDownloadScreen(onBack: () -> Unit) {
    val prefs: NotificationPreferences = remember { Injekt.get() }
    val context = LocalContext.current

    val autoDlEnabled by prefs.globalAutoDownloadEnabled().stateInAsState()
    val autoDlSub by prefs.globalAutoDownloadSub().stateInAsState()
    val autoDlDub by prefs.globalAutoDownloadDub().stateInAsState()
    val autoDlQuality by prefs.globalAutoDownloadQuality().stateInAsState()
    val autoDlAudio by prefs.globalAutoDownloadAudio().stateInAsState()

    val watchFlowEnabled by prefs.watchFlowAutoDownloadEnabled().stateInAsState()
    val watchFlowAudio by prefs.watchFlowAutoDownloadAudio().stateInAsState()

    var showAutoDlDialog by remember { mutableStateOf(false) }

    SettingsSubpageScaffold(title = "Auto Download", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ---- New releases ----
            item {
                SettingsGroupCard(title = "New episode releases") {
                    SwitchSettingsRow(
                        icon = Icons.Default.CloudDownload,
                        title = "Auto-download new episodes",
                        subtitle = "Automatically download new episodes when they're released",
                        checked = autoDlEnabled,
                        onCheckedChange = { newValue ->
                            if (newValue) {
                                showAutoDlDialog = true
                            } else {
                                prefs.globalAutoDownloadEnabled().set(false)
                            }
                        },
                    )
                    if (autoDlEnabled) {
                        HorizontalDivider()
                        SwitchSettingsRow(
                            icon = Icons.Default.Subtitles,
                            title = "Auto-download SUB",
                            subtitle = "Download the subbed version. If both are on, both versions download.",
                            checked = autoDlSub,
                            onCheckedChange = { prefs.globalAutoDownloadSub().set(it) },
                        )
                        HorizontalDivider()
                        SwitchSettingsRow(
                            icon = Icons.Default.GraphicEq,
                            title = "Auto-download DUB",
                            subtitle = "Download the dubbed version. Dub episodes are often released later.",
                            checked = autoDlDub,
                            onCheckedChange = { prefs.globalAutoDownloadDub().set(it) },
                        )
                        HorizontalDivider()
                        QualitySelectorRow(autoDlQuality) { prefs.globalAutoDownloadQuality().set(it) }
                        HorizontalDivider()
                        AudioSelectorRow(autoDlAudio) { prefs.globalAutoDownloadAudio().set(it) }
                    }
                }
            }

            // ---- Watch-flow ----
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
        }
    }

    // Confirmation dialog when enabling auto-download
    if (showAutoDlDialog) {
        AlertDialog(
            onDismissRequest = { showAutoDlDialog = false },
            icon = { Icon(Icons.Default.CloudDownload, contentDescription = null) },
            title = { Text("Enable auto-download?") },
            text = {
                Text("When enabled, all anime in your library that have new episodes released " +
                     "will be automatically downloaded in the background. " +
                     "You can configure which anime to auto-download and whether to prefer sub or dub.")
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.globalAutoDownloadEnabled().set(true)
                    showAutoDlDialog = false
                    Toast.makeText(context, "Auto-download enabled", Toast.LENGTH_SHORT).show()
                }) { Text("Enable") }
            },
            dismissButton = {
                TextButton(onClick = { showAutoDlDialog = false }) { Text("Cancel") }
            },
        )
    }
}

// =========================================================================
// Sub-screen 4: Quiet Hours
// =========================================================================

@Composable
fun NotificationQuietHoursScreen(onBack: () -> Unit) {
    val prefs: NotificationPreferences = remember { Injekt.get() }
    val enabled by prefs.quietHoursEnabled().stateInAsState()
    val start by prefs.quietHoursStart().stateInAsState()
    val end by prefs.quietHoursEnd().stateInAsState()

    SettingsSubpageScaffold(title = "Quiet Hours", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SettingsGroupCard(title = "Quiet hours") {
                    SwitchSettingsRow(
                        icon = Icons.Default.Bedtime,
                        title = "Enable quiet hours",
                        subtitle = "Silence notifications during set hours",
                        checked = enabled,
                        onCheckedChange = { prefs.quietHoursEnabled().set(it) },
                    )
                    if (enabled) {
                        HorizontalDivider()
                        TimeSelectorRow("Start", start) { prefs.quietHoursStart().set(it) }
                        HorizontalDivider()
                        TimeSelectorRow("End", end) { prefs.quietHoursEnd().set(it) }
                    }
                }
            }
        }
    }
}

// =========================================================================
// Sub-screen 5: Background reliability
// =========================================================================

@Composable
fun NotificationBackgroundScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    SettingsSubpageScaffold(title = "Background", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SettingsGroupCard(title = "Battery optimization") {
                    val powerManager = remember {
                        context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
                    }
                    var isIgnoringBattery by remember {
                        mutableStateOf(powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false)
                    }
                    ClickableSettingsRow(
                        icon = Icons.Default.BatteryFull,
                        title = "Disable battery optimization",
                        subtitle = if (isIgnoringBattery)
                            "✓ App is exempt — background tracking will work reliably"
                        else
                            "Required for reliable background episode tracking. Some OEMs kill background apps without this.",
                        onClick = {
                            try {
                                val intent = android.content.Intent(
                                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                                ).apply {
                                    data = android.net.Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    val intent = android.content.Intent(
                                        android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                                    )
                                    context.startActivity(intent)
                                } catch (e2: Exception) {
                                    Toast.makeText(context, "Could not open battery settings", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

// =========================================================================
// Modern notify mode selector (card-based, not ugly radio buttons)
// =========================================================================

@Composable
private fun ModernNotifyModeSelector(currentMode: String, onModeChange: (String) -> Unit) {
    val modes = listOf(
        Triple("extension", "When watchable", "Notify only when the episode is actually available on the extension"),
        Triple("anilist", "When aired", "Notify at the AniList airing time (may not be watchable yet)"),
        Triple("both", "Both", "Notify at airing time AND when watchable"),
    )
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        modes.forEach { (value, title, desc) ->
            val isSelected = currentMode == value
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onModeChange(value) },
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    RadioButton(
                        selected = isSelected,
                        onClick = { onModeChange(value) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = if (isSelected) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        }
    }
}

// =========================================================================
// Shared helper composables
// =========================================================================

@Composable
private fun QualitySelectorRow(current: String, onChange: (String) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("Preferred quality", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        val qualities = listOf("best" to "Best available", "1080" to "1080p", "720" to "720p", "360" to "360p")
        qualities.forEach { (q, label) ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onChange(q) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = current == q, onClick = { onChange(q) })
                Spacer(modifier = Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium)
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
                verticalAlignment = Alignment.CenterVertically,
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
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.weight(1f))
        Text(formatHour(hour), style = MaterialTheme.typography.bodyLarge)
    }
    if (showDialog) {
        var selectedHour by remember { mutableStateOf(hour) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
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
            confirmButton = { TextButton(onClick = { onHourChange(selectedHour); showDialog = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } },
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

// ---- State helper ----
@Composable
private fun <T> Preference<T>.stateInAsState(): State<T> {
    return produceState(initialValue = this.get(), this) {
        this@stateInAsState.changes().collectLatest { value = it }
    }
}
