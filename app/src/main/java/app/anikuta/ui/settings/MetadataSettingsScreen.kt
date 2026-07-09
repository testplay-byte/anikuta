package app.anikuta.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.anikuta.player.PlayerPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Details → Metadata fetching subpage.
 *
 * Minimal layout: master toggle + per-field fetch toggles.
 * No data sources listed, no detailed explanations — just clean controls.
 */
@Composable
fun MetadataSettingsScreen(onBack: () -> Unit) {
    val prefs: PlayerPreferences = remember { Injekt.get() }
    val scope = rememberCoroutineScope()
    val enableMetadataFetch by prefs.enableInAppMetadataFetch().stateIn(scope).collectAsState()
    val fetchThumbnails by prefs.fetchMetadataThumbnails().stateIn(scope).collectAsState()
    val fetchTitles by prefs.fetchMetadataTitles().stateIn(scope).collectAsState()
    val fetchSummaries by prefs.fetchMetadataSummaries().stateIn(scope).collectAsState()

    SettingsSubpageScaffold(title = "Metadata fetching", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- Master toggle ----
            item {
                SettingsGroupCard(title = "Fetching") {
                    SwitchSettingsRow(
                        icon = Icons.Default.AutoAwesome,
                        title = "Fetch episode metadata",
                        subtitle = "Automatically fetch missing episode info from external sources",
                        checked = enableMetadataFetch,
                        onCheckedChange = { prefs.enableInAppMetadataFetch().set(it) },
                    )
                }
            }

            // ---- What to fetch ----
            item {
                SettingsGroupCard(title = "What to fetch") {
                    SwitchSettingsRow(
                        icon = Icons.Default.Image,
                        title = "Thumbnails",
                        subtitle = "Fetch episode preview images",
                        checked = fetchThumbnails,
                        onCheckedChange = { prefs.fetchMetadataThumbnails().set(it) },
                    )
                    HorizontalDivider()
                    SwitchSettingsRow(
                        icon = Icons.Default.Title,
                        title = "Titles",
                        subtitle = "Fetch episode titles",
                        checked = fetchTitles,
                        onCheckedChange = { prefs.fetchMetadataTitles().set(it) },
                    )
                    HorizontalDivider()
                    SwitchSettingsRow(
                        icon = Icons.Default.Subtitles,
                        title = "Summaries",
                        subtitle = "Fetch episode descriptions",
                        checked = fetchSummaries,
                        onCheckedChange = { prefs.fetchMetadataSummaries().set(it) },
                    )
                }
            }

            // ---- Minimal note ----
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Text(
                        text = "Metadata is fetched when you open an anime's detail page. Only fields missing from the extension are enriched.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}
