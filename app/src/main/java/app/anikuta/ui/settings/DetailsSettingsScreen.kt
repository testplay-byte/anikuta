package app.anikuta.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.anikuta.player.PlayerPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Phase 7.5 — Details page settings.
 *
 * Controls how the anime detail page renders episode lists:
 *  - Show/hide: episode titles, summaries, thumbnails, dates, episode number, audio pills
 *  - Layout: synopsis position (right of thumbnail / below thumbnail)
 *  - Layout: date position (right of thumbnail / below thumbnail)
 */
@Composable
fun DetailsSettingsScreen(onBack: () -> Unit) {
    val prefs: PlayerPreferences = remember { Injekt.get() }
    val scope = rememberCoroutineScope()
    val showTitles by prefs.showEpisodeTitles().stateIn(scope).collectAsState()
    val showSummaries by prefs.showEpisodeSummaries().stateIn(scope).collectAsState()
    val showThumbnails by prefs.showEpisodeThumbnails().stateIn(scope).collectAsState()
    val showDates by prefs.showEpisodeDates().stateIn(scope).collectAsState()
    val showEpisodeNumber by prefs.showEpisodeNumber().stateIn(scope).collectAsState()
    val showAudioPills by prefs.showAudioPills().stateIn(scope).collectAsState()
    val synopsisPos by prefs.synopsisPosition().stateIn(scope).collectAsState()
    val datePos by prefs.datePosition().stateIn(scope).collectAsState()

    SettingsSubpageScaffold(title = "Details", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ---- Show/hide toggles ----
            item {
                SettingsGroupCard(title = "Episode list display") {
                    SwitchSettingsRow(
                        icon = Icons.Default.Numbers,
                        title = "Show episode number",
                        subtitle = "Display the episode number badge",
                        checked = showEpisodeNumber,
                        onCheckedChange = { prefs.showEpisodeNumber().set(it) },
                    )
                    HorizontalDivider()
                    SwitchSettingsRow(
                        icon = Icons.Default.Title,
                        title = "Show episode titles",
                        subtitle = "Display parsed episode titles (strips 'Episode N - ' prefix)",
                        checked = showTitles,
                        onCheckedChange = { prefs.showEpisodeTitles().set(it) },
                    )
                    HorizontalDivider()
                    SwitchSettingsRow(
                        icon = Icons.Default.Subtitles,
                        title = "Show episode summaries",
                        subtitle = "Display episode descriptions (when provided by the extension)",
                        checked = showSummaries,
                        onCheckedChange = { prefs.showEpisodeSummaries().set(it) },
                    )
                    HorizontalDivider()
                    SwitchSettingsRow(
                        icon = Icons.Default.Image,
                        title = "Show episode thumbnails",
                        subtitle = "Display preview images (when provided by the extension)",
                        checked = showThumbnails,
                        onCheckedChange = { prefs.showEpisodeThumbnails().set(it) },
                    )
                    HorizontalDivider()
                    SwitchSettingsRow(
                        icon = Icons.Default.CalendarMonth,
                        title = "Show episode dates",
                        subtitle = "Display air dates (when provided by the extension)",
                        checked = showDates,
                        onCheckedChange = { prefs.showEpisodeDates().set(it) },
                    )
                    HorizontalDivider()
                    SwitchSettingsRow(
                        icon = Icons.Default.RecordVoiceOver,
                        title = "Show audio availability pills",
                        subtitle = "Display SUB/DUB/HSUB tags (derived from episode name)",
                        checked = showAudioPills,
                        onCheckedChange = { prefs.showAudioPills().set(it) },
                    )
                }
            }

            // ---- Layout positions ----
            item {
                SettingsGroupCard(title = "Layout positions") {
                    // Synopsis position
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("Synopsis position", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Text("Where to show the episode synopsis relative to the thumbnail", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        SingleChoiceSegmentedButtonRow {
                            SegmentedButton(
                                selected = synopsisPos == "right",
                                onClick = { prefs.synopsisPosition().set("right") },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            ) { Text("Right") }
                            SegmentedButton(
                                selected = synopsisPos == "below",
                                onClick = { prefs.synopsisPosition().set("below") },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            ) { Text("Below") }
                        }
                    }
                    HorizontalDivider()
                    // Date position
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("Date & audio pills position", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Text("Where to show the date and audio pills relative to the thumbnail", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        SingleChoiceSegmentedButtonRow {
                            SegmentedButton(
                                selected = datePos == "right",
                                onClick = { prefs.datePosition().set("right") },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            ) { Text("Right") }
                            SegmentedButton(
                                selected = datePos == "below",
                                onClick = { prefs.datePosition().set("below") },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            ) { Text("Below") }
                        }
                    }
                }
            }
        }
    }
}
