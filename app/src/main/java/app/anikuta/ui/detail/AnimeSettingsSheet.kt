package app.anikuta.ui.detail

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bell
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
 * Dependency rule (§4.1): if autoDownloadNew is OFF, the sub/dub toggles
 * below it are disabled. Same for notifyOnNew → notifySub/notifyDub.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimeSettingsSheet(
    anilistId: Int,
    onDismiss: () -> Unit,
) {
    val trackingStore: ReleaseTrackingStore? = remember {
        try { Injekt.get() } catch (e: Exception) { null }
    }
    val prefs: NotificationPreferences? = remember {
        try { Injekt.get() } catch (e: Exception) { null }
    }

    if (trackingStore == null || prefs == null) {
        // DI not available
        ModalBottomSheet(onDismissRequest = onDismiss) {
            Text("Settings unavailable", modifier = Modifier.padding(16.dp))
        }
        return
    }

    // Get current per-anime state (or defaults from global)
    val tracked by produceState(initialValue = trackingStore.get(anilistId), anilistId) {
        trackingStore.changes().collectLatest { map ->
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

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "Notification & Download Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    tracked?.title ?: "Anime",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ---- Notifications ----
            item {
                Text("Notifications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
            }
            item {
                PerAnimeSwitchRow(
                    icon = Icons.Default.Bell,
                    title = "Notify on new episodes",
                    subtitle = if (tracked?.notifyOnNew == null) "Global default: ON" else null,
                    checked = notifyOnNew,
                    onCheckedChange = { update { it.copy(notifyOnNew = it) } },
                )
            }
            item {
                PerAnimeSwitchRow(
                    icon = Icons.Default.Subtitles,
                    title = "Notify on SUB",
                    subtitle = if (tracked?.notifySub == null) "Global default: ${if (globalNotifySub) "ON" else "OFF"}" else null,
                    checked = notifySub,
                    enabled = notifyOnNew,
                    onCheckedChange = { update { it.copy(notifySub = it) } },
                )
            }
            item {
                PerAnimeSwitchRow(
                    icon = Icons.Default.GraphicEq,
                    title = "Notify on DUB",
                    subtitle = if (tracked?.notifyDub == null) "Global default: ${if (globalNotifyDub) "ON" else "OFF"}" else null,
                    checked = notifyDub,
                    enabled = notifyOnNew,
                    onCheckedChange = { update { it.copy(notifyDub = it) } },
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // ---- Auto-download ----
            item {
                Text("Auto-download", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
            }
            item {
                PerAnimeSwitchRow(
                    icon = Icons.Default.CloudDownload,
                    title = "Auto-download new episodes",
                    subtitle = if (tracked?.autoDownloadNew == null) "Global default: ${if (globalAutoDlEnabled) "ON" else "OFF"}" else null,
                    checked = autoDlNew,
                    onCheckedChange = { update { it.copy(autoDownloadNew = it) } },
                )
            }
            item {
                PerAnimeSwitchRow(
                    icon = Icons.Default.Subtitles,
                    title = "Auto-download SUB",
                    subtitle = if (tracked?.autoDownloadSub == null) "Global default: ${if (globalAutoDlSub) "ON" else "OFF"}" else null,
                    checked = autoDlSub,
                    enabled = autoDlNew,  // Dependency: disabled if autoDlNew is off
                    onCheckedChange = { update { it.copy(autoDownloadSub = it) } },
                )
            }
            item {
                PerAnimeSwitchRow(
                    icon = Icons.Default.GraphicEq,
                    title = "Auto-download DUB",
                    subtitle = if (tracked?.autoDownloadDub == null) "Global default: ${if (globalAutoDlDub) "ON" else "OFF"}" else null,
                    checked = autoDlDub,
                    enabled = autoDlNew,  // Dependency: disabled if autoDlNew is off
                    onCheckedChange = { update { it.copy(autoDownloadDub = it) } },
                )
            }

            // ---- Reset to defaults ----
            item {
                Spacer(modifier = Modifier.height(16.dp))
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
    subtitle: String?,
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
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
