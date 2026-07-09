package app.anikuta.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.anikuta.player.PlayerPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Details → Metadata fetching subpage.
 *
 * Controls the in-app episode metadata fetcher that enriches episodes with
 * thumbnails, titles, and descriptions from external sources (MAL/Jikan,
 * AniList, Kitsu) when the installed extension doesn't provide them.
 */
@Composable
fun MetadataSettingsScreen(onBack: () -> Unit) {
    val prefs: PlayerPreferences = remember { Injekt.get() }
    val scope = rememberCoroutineScope()
    val enableMetadataFetch by prefs.enableInAppMetadataFetch().stateIn(scope).collectAsState()

    SettingsSubpageScaffold(title = "Metadata fetching", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ---- Toggle ----
            item {
                SettingsGroupCard(title = "In-app metadata fetching") {
                    SwitchSettingsRow(
                        icon = Icons.Default.AutoAwesome,
                        title = "Fetch episode metadata",
                        subtitle = "Enrich episodes with thumbnails, titles, and descriptions from external sources when the extension doesn't provide them",
                        checked = enableMetadataFetch,
                        onCheckedChange = { prefs.enableInAppMetadataFetch().set(it) },
                    )
                }
            }

            // ---- How it works ----
            item {
                SettingsGroupCard(title = "How it works") {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        InfoBullet(
                            icon = Icons.Default.Info,
                            text = "When you open an anime's detail page, the app checks if the extension provides episode thumbnails, titles, and descriptions.",
                        )
                        Spacer(Modifier.height(8.dp))
                        InfoBullet(
                            icon = Icons.Default.Info,
                            text = "If any are missing, the app fetches them in parallel from three sources: Anikage.cc (primary), Jikan (MAL API), and Kitsu.",
                        )
                        Spacer(Modifier.height(8.dp))
                        InfoBullet(
                            icon = Icons.Default.Info,
                            text = "The fetched metadata is merged into the episode list and persists across navigation and app restarts.",
                        )
                        Spacer(Modifier.height(8.dp))
                        InfoBullet(
                            icon = Icons.Default.Info,
                            text = "A 'Fetching metadata…' indicator appears while the enrichment is running. The episode list updates live as data arrives.",
                        )
                    }
                }
            }

            // ---- Data sources ----
            item {
                SettingsGroupCard(title = "Data sources (in priority order)") {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        DataSourceRow(
                            name = "Anikage.cc",
                            description = "Primary source — provides thumbnails, descriptions, titles, and air dates. Requires Cloudflare bypass.",
                        )
                        HorizontalDivider()
                        DataSourceRow(
                            name = "Jikan (MAL API)",
                            description = "Provides episode titles and air dates from MyAnimeList. No thumbnails or descriptions.",
                        )
                        HorizontalDivider()
                        DataSourceRow(
                            name = "Kitsu",
                            description = "Provides titles, thumbnails, descriptions, and air dates. Rich data for older anime.",
                        )
                        HorizontalDivider()
                        DataSourceRow(
                            name = "AniList streaming",
                            description = "Provides thumbnails from AniList streaming episodes (rare).",
                        )
                    }
                }
            }

            // ---- Note ----
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Text(
                        text = "Note: Metadata fetching is best-effort. If all sources fail or have no data for an anime, episodes will show only what the extension provides. This does not affect playback.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoBullet(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DataSourceRow(name: String, description: String) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
