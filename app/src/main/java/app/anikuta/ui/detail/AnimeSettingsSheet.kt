package app.anikuta.ui.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.anikuta.data.cache.ReleaseTrackingStore
import app.anikuta.notification.NotificationPreferences
import kotlinx.coroutines.flow.collectLatest
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Phase N-4 (Settings UI) — Per-anime notification + auto-download settings.
 *
 * Shown as a ModalBottomSheet from the three-dot menu on the detail page.
 * Per-anime settings override global defaults (null = inherit global).
 *
 * [mode] determines which section is shown:
 *  - [SettingsMode.NOTIFICATIONS] → only the notification toggles
 *  - [SettingsMode.DOWNLOADS] → only the auto-download toggles
 *
 * UI fixes (2026-07-16, user feedback):
 *  - No drag handle (clean top edge)
 *  - No anime name at the top (just the section title)
 *  - Better explanation text on auto-download sub/dub
 *  - Dependency rule: if autoDownloadNew is OFF, sub/dub toggles disabled
 */
enum class SettingsMode { NOTIFICATIONS, DOWNLOADS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimeSettingsSheet(
    anilistId: Int,
    mode: SettingsMode,
    onDismiss: () -> Unit,
) {
    val trackingStore: ReleaseTrackingStore? = remember {
        try { Injekt.get() } catch (e: Exception) { null }
    }
    val prefs: NotificationPreferences? = remember {
        try { Injekt.get() } catch (e: Exception) { null }
    }

    val title = when (mode) {
        SettingsMode.NOTIFICATIONS -> "Notification Settings"
        SettingsMode.DOWNLOADS -> "Download Settings"
    }

    if (trackingStore == null || prefs == null) {
        // DI not available — no drag handle
        ModalBottomSheet(onDismissRequest = onDismiss, dragHandle = {}) {
            Text("Settings unavailable", modifier = Modifier.padding(16.dp))
        }
        return
    }

    // Get current per-anime state (or defaults from global)
    val tracked by produceState(initialValue = trackingStore.get(anilistId), anilistId) {
        trackingStore.changes.collectLatest { map ->
            value = map[anilistId.toString()]
        }
    }

    val globalNotifySub by prefs.globalNotifySub().stateInAsState()
    val globalNotifyDub by prefs.globalNotifyDub().stateInAsState()
    val globalAutoDlEnabled by prefs.globalAutoDownloadEnabled().stateInAsState()
    val globalAutoDlSub by prefs.globalAutoDownloadSub().stateInAsState()
    val globalAutoDlDub by prefs.globalAutoDownloadDub().stateInAsState()

    // Effective values (per-anime override or global default)
    val notifyOnNew = tracked?.notifyOnNew ?: true
    val notifySub = tracked?.notifySub ?: globalNotifySub
    val notifyDub = tracked?.notifyDub ?: globalNotifyDub
    val autoDlNew = tracked?.autoDownloadNew ?: globalAutoDlEnabled
    val autoDlSub = tracked?.autoDownloadSub ?: globalAutoDlSub
    val autoDlDub = tracked?.autoDownloadDub ?: globalAutoDlDub

    fun update(block: (ReleaseTrackingStore.TrackedAnime) -> ReleaseTrackingStore.TrackedAnime) {
        val current = tracked ?: return
        trackingStore.put(block(current))
    }

    // No drag handle — clean top edge with just the title
    ModalBottomSheet(onDismissRequest = onDismiss, dragHandle = {}) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Title only — no anime name (user feedback)
            item {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            when (mode) {
                SettingsMode.NOTIFICATIONS -> {
                    item {
                        PerAnimeSwitchRow(
                            icon = Icons.Default.Notifications,
                            title = "Notify on new episodes",
                            subtitle = "Get a notification when a new episode is available" +
                                if (tracked?.notifyOnNew == null) " (global default: ON)" else "",
                            checked = notifyOnNew,
                            onCheckedChange = { newValue -> update { it.copy(notifyOnNew = newValue) } },
                        )
                    }
                    item {
                        PerAnimeSwitchRow(
                            icon = Icons.Default.Subtitles,
                            title = "Notify on new SUB episodes",
                            subtitle = "Get notified when a new subbed episode is available" +
                                if (tracked?.notifySub == null) " (global default: ${if (globalNotifySub) "ON" else "OFF"})" else "",
                            checked = notifySub,
                            enabled = notifyOnNew,
                            onCheckedChange = { newValue -> update { it.copy(notifySub = newValue) } },
                        )
                    }
                    item {
                        PerAnimeSwitchRow(
                            icon = Icons.Default.GraphicEq,
                            title = "Notify on new DUB episodes",
                            subtitle = "Get notified when a new dubbed episode is available. " +
                                "Dub episodes are often released later than sub." +
                                if (tracked?.notifyDub == null) " (global default: ${if (globalNotifyDub) "ON" else "OFF"})" else "",
                            checked = notifyDub,
                            enabled = notifyOnNew,
                            onCheckedChange = { newValue -> update { it.copy(notifyDub = newValue) } },
                        )
                    }
                }
                SettingsMode.DOWNLOADS -> {
                    item {
                        PerAnimeSwitchRow(
                            icon = Icons.Default.CloudDownload,
                            title = "Auto-download new episodes",
                            subtitle = "When a new episode is released, automatically download it in the background" +
                                if (tracked?.autoDownloadNew == null) " (global default: ${if (globalAutoDlEnabled) "ON" else "OFF"})" else "",
                            checked = autoDlNew,
                            onCheckedChange = { newValue -> update { it.copy(autoDownloadNew = newValue) } },
                        )
                    }
                    item {
                        PerAnimeSwitchRow(
                            icon = Icons.Default.Subtitles,
                            title = "Auto-download SUB",
                            subtitle = "When a new episode is released, download the subbed version. " +
                                "If both SUB and DUB are on, both versions will be downloaded." +
                                if (tracked?.autoDownloadSub == null) " (global default: ${if (globalAutoDlSub) "ON" else "OFF"})" else "",
                            checked = autoDlSub,
                            enabled = autoDlNew,
                            onCheckedChange = { newValue -> update { it.copy(autoDownloadSub = newValue) } },
                        )
                    }
                    item {
                        PerAnimeSwitchRow(
                            icon = Icons.Default.GraphicEq,
                            title = "Auto-download DUB",
                            subtitle = "When a new dubbed episode is released, download the dub version. " +
                                "Dub episodes are often released later than sub." +
                                if (tracked?.autoDownloadDub == null) " (global default: ${if (globalAutoDlDub) "ON" else "OFF"})" else "",
                            checked = autoDlDub,
                            enabled = autoDlNew,
                            onCheckedChange = { newValue -> update { it.copy(autoDownloadDub = newValue) } },
                        )
                    }
                }
            }

            // ---- Reset to defaults ----
            item {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        update { it.copy(
                            notifyOnNew = null, notifySub = null, notifyDub = null,
                            autoDownloadNew = null, autoDownloadSub = null, autoDownloadDub = null,
                            autoDownloadQuality = null, autoDownloadAudio = null,
                        ) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Reset to global defaults")
                }
            }
        }
    }
}

@Composable
private fun PerAnimeSwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

// ---- State helper ----
@Composable
private fun <T> app.anikuta.core.preference.Preference<T>.stateInAsState(): State<T> {
    return produceState(initialValue = this.get(), this) {
        this@stateInAsState.changes().collectLatest { value = it }
    }
}
