package app.anikuta.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.HorizontalDivider
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
 * Details → Episode display subpage.
 *
 * All show/hide toggles for episode list elements:
 *  - Episode number
 *  - Episode titles
 *  - Episode summaries
 *  - Episode thumbnails
 *  - Episode dates
 *  - Audio availability pills (SUB/DUB/HSUB)
 */
@Composable
fun DisplaySettingsScreen(onBack: () -> Unit) {
    val prefs: PlayerPreferences = remember { Injekt.get() }
    val scope = rememberCoroutineScope()
    val showTitles by prefs.showEpisodeTitles().stateIn(scope).collectAsState()
    val showSummaries by prefs.showEpisodeSummaries().stateIn(scope).collectAsState()
    val showThumbnails by prefs.showEpisodeThumbnails().stateIn(scope).collectAsState()
    val showDates by prefs.showEpisodeDates().stateIn(scope).collectAsState()
    val showEpisodeNumber by prefs.showEpisodeNumber().stateIn(scope).collectAsState()
    val showAudioPills by prefs.showAudioPills().stateIn(scope).collectAsState()

    SettingsSubpageScaffold(title = "Episode display", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SettingsGroupCard(title = "Show or hide elements") {
                    SwitchSettingsRow(
                        icon = Icons.Default.Numbers,
                        title = "Episode number",
                        subtitle = "Display the episode number (overlay on thumbnail or badge)",
                        checked = showEpisodeNumber,
                        onCheckedChange = { prefs.showEpisodeNumber().set(it) },
                    )
                    HorizontalDivider()
                    SwitchSettingsRow(
                        icon = Icons.Default.Title,
                        title = "Episode titles",
                        subtitle = "Display parsed episode titles",
                        checked = showTitles,
                        onCheckedChange = { prefs.showEpisodeTitles().set(it) },
                    )
                    HorizontalDivider()
                    SwitchSettingsRow(
                        icon = Icons.Default.Subtitles,
                        title = "Episode summaries",
                        subtitle = "Display episode descriptions (synopsis)",
                        checked = showSummaries,
                        onCheckedChange = { prefs.showEpisodeSummaries().set(it) },
                    )
                    HorizontalDivider()
                    SwitchSettingsRow(
                        icon = Icons.Default.Image,
                        title = "Episode thumbnails",
                        subtitle = "Display preview images for each episode",
                        checked = showThumbnails,
                        onCheckedChange = { prefs.showEpisodeThumbnails().set(it) },
                    )
                    HorizontalDivider()
                    SwitchSettingsRow(
                        icon = Icons.Default.CalendarMonth,
                        title = "Episode dates",
                        subtitle = "Display air dates",
                        checked = showDates,
                        onCheckedChange = { prefs.showEpisodeDates().set(it) },
                    )
                    HorizontalDivider()
                    SwitchSettingsRow(
                        icon = Icons.Default.RecordVoiceOver,
                        title = "Audio availability pills",
                        subtitle = "Display SUB / DUB / HSUB tags (from extension scanlator field)",
                        checked = showAudioPills,
                        onCheckedChange = { prefs.showAudioPills().set(it) },
                    )
                }
            }
        }
    }
}
