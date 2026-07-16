package app.anikuta.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.anikuta.notification.NotificationPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Notification Settings HUB — the entry point.
 *
 * Instead of cramming everything into one screen, this is a clean hub with
 * category cards. Each card navigates to a dedicated sub-screen.
 *
 * Categories:
 *  1. General → enable notifications, notify mode, check completed
 *  2. Sub/Dub → notify on sub, notify on dub
 *  3. Auto-download → new releases + watch-flow
 *  4. Quiet hours
 *  5. Background reliability
 *  6. Tracked anime → per-anime config (full screen)
 */
@Composable
fun NotificationSettingsScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val prefs: NotificationPreferences? = remember {
        try { Injekt.get() } catch (e: Exception) { null }
    }

    SettingsSubpageScaffold(title = "Notifications", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val categories = listOf(
                NotifCategory("settings/notifications/general", "General", "Enable, notify mode", Icons.Default.Notifications, "emerald"),
                NotifCategory("settings/notifications/subdub", "Sub / Dub", "Notify on new sub or dub episodes", Icons.Default.GraphicEq, "amber"),
                NotifCategory("settings/notifications/autodownload", "Auto Download", "New releases + watch-flow auto-download", Icons.Default.CloudDownload, "sky"),
                NotifCategory("settings/notifications/quiethours", "Quiet Hours", "Silence notifications during set hours", Icons.Default.Bedtime, "violet"),
                NotifCategory("settings/notifications/background", "Background", "Battery optimization for reliable tracking", Icons.Default.BatteryFull, "rose"),
                NotifCategory("settings/notifications/tracked", "Tracked Anime", "Per-anime notification + download settings", Icons.Default.VideoLibrary, "teal"),
            )
            items(categories.size) { index ->
                val cat = categories[index]
                NotifCategoryCard(category = cat, onClick = { onNavigate(cat.route) })
            }
        }
    }
}

private data class NotifCategory(
    val route: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val colorKey: String,
)

@Composable
private fun NotifCategoryCard(category: NotifCategory, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onClick() },
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    category.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    category.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    category.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
