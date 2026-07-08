package app.anikuta.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudDownload
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
 * Phase 7.5 — Details page settings with live preview.
 *
 * Layout order:
 *  1. Episode list display toggles
 *  2. Live preview (reflects toggle + position changes)
 *  3. Layout positions (title, synopsis, date, episode number, thumbnail)
 *  4. Thumbnail size
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
    val thumbSize by prefs.thumbnailSize().stateIn(scope).collectAsState()
    val titlePos by prefs.titlePosition().stateIn(scope).collectAsState()
    val epNumPos by prefs.episodeNumberPosition().stateIn(scope).collectAsState()
    val thumbPos by prefs.thumbnailPosition().stateIn(scope).collectAsState()
    val animeInfoPos by prefs.animeInfoPosition().stateIn(scope).collectAsState()
    val enableMetadataFetch by prefs.enableInAppMetadataFetch().stateIn(scope).collectAsState()

    SettingsSubpageScaffold(title = "Details", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ---- 1. Show/hide toggles ----
            item {
                SettingsGroupCard(title = "Episode list display") {
                    SwitchSettingsRow(
                        icon = Icons.Default.Numbers,
                        title = "Show episode number",
                        subtitle = "Display the episode number",
                        checked = showEpisodeNumber,
                        onCheckedChange = { prefs.showEpisodeNumber().set(it) },
                    )
                    HorizontalDivider()
                    SwitchSettingsRow(
                        icon = Icons.Default.Title,
                        title = "Show episode titles",
                        subtitle = "Display parsed episode titles",
                        checked = showTitles,
                        onCheckedChange = { prefs.showEpisodeTitles().set(it) },
                    )
                    HorizontalDivider()
                    SwitchSettingsRow(
                        icon = Icons.Default.Subtitles,
                        title = "Show episode summaries",
                        subtitle = "Display episode descriptions",
                        checked = showSummaries,
                        onCheckedChange = { prefs.showEpisodeSummaries().set(it) },
                    )
                    HorizontalDivider()
                    SwitchSettingsRow(
                        icon = Icons.Default.Image,
                        title = "Show episode thumbnails",
                        subtitle = "Display preview images",
                        checked = showThumbnails,
                        onCheckedChange = { prefs.showEpisodeThumbnails().set(it) },
                    )
                    HorizontalDivider()
                    SwitchSettingsRow(
                        icon = Icons.Default.CalendarMonth,
                        title = "Show episode dates",
                        subtitle = "Display air dates",
                        checked = showDates,
                        onCheckedChange = { prefs.showEpisodeDates().set(it) },
                    )
                    HorizontalDivider()
                    SwitchSettingsRow(
                        icon = Icons.Default.RecordVoiceOver,
                        title = "Show audio availability pills",
                        subtitle = "Display SUB/DUB/HSUB tags",
                        checked = showAudioPills,
                        onCheckedChange = { prefs.showAudioPills().set(it) },
                    )
                    HorizontalDivider()
                    SwitchSettingsRow(
                        icon = Icons.Default.CloudDownload,
                        title = "In-app metadata fetching",
                        subtitle = "Fetch episode thumbnails, titles, and descriptions from MAL + AniList for extensions that don't provide them",
                        checked = enableMetadataFetch,
                        onCheckedChange = { prefs.enableInAppMetadataFetch().set(it) },
                    )
                }
            }

            // ---- 2. Live preview (below toggles, above positions) ----
            item {
                SettingsGroupCard(title = "Live preview") {
                    Column(Modifier.padding(8.dp)) {
                        EpisodeRowPreview(
                            showThumbnails = showThumbnails,
                            showSummaries = showSummaries,
                            showTitles = showTitles,
                            showDates = showDates,
                            showEpisodeNumber = showEpisodeNumber,
                            showAudioPills = showAudioPills,
                            synopsisPosition = synopsisPos,
                            datePosition = datePos,
                            thumbnailSize = thumbSize,
                            titlePosition = titlePos,
                            episodeNumberPosition = epNumPos,
                            thumbnailPosition = thumbPos,
                        )
                    }
                }
            }

            // ---- 3. Layout positions ----
            item {
                SettingsGroupCard(title = "Layout positions") {
                    // Title position
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("Title position", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        SingleChoiceSegmentedButtonRow {
                            SegmentedButton(
                                selected = titlePos == "right",
                                onClick = { prefs.titlePosition().set("right") },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            ) { Text("Right") }
                            SegmentedButton(
                                selected = titlePos == "below",
                                onClick = { prefs.titlePosition().set("below") },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            ) { Text("Below") }
                        }
                    }
                    HorizontalDivider()
                    // Synopsis position
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("Synopsis position", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
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
                    // Date position — 3 options
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("Date & audio pills position", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        SingleChoiceSegmentedButtonRow {
                            SegmentedButton(
                                selected = datePos == "right_above_synopsis",
                                onClick = { prefs.datePosition().set("right_above_synopsis") },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                            ) { Text("Above") }
                            SegmentedButton(
                                selected = datePos == "right_below_synopsis",
                                onClick = { prefs.datePosition().set("right_below_synopsis") },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                            ) { Text("Below") }
                            SegmentedButton(
                                selected = datePos == "below",
                                onClick = { prefs.datePosition().set("below") },
                                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                            ) { Text("Full") }
                        }
                    }
                    HorizontalDivider()
                    // Episode number position
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("Episode number position", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        SingleChoiceSegmentedButtonRow {
                            SegmentedButton(
                                selected = epNumPos == "overlay",
                                onClick = { prefs.episodeNumberPosition().set("overlay") },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            ) { Text("Overlay") }
                            SegmentedButton(
                                selected = epNumPos == "badge",
                                onClick = { prefs.episodeNumberPosition().set("badge") },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            ) { Text("Badge") }
                        }
                    }
                    HorizontalDivider()
                    // Thumbnail position
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("Thumbnail position", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        SingleChoiceSegmentedButtonRow {
                            SegmentedButton(
                                selected = thumbPos == "left",
                                onClick = { prefs.thumbnailPosition().set("left") },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            ) { Text("Left") }
                            SegmentedButton(
                                selected = thumbPos == "right",
                                onClick = { prefs.thumbnailPosition().set("right") },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            ) { Text("Right") }
                        }
                    }
                    HorizontalDivider()
                    // Anime info position
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("Anime info position", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Text("Where to show anime info (genres, score, etc.) relative to episodes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        SingleChoiceSegmentedButtonRow {
                            SegmentedButton(
                                selected = animeInfoPos == "above",
                                onClick = { prefs.animeInfoPosition().set("above") },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            ) { Text("Above episodes") }
                            SegmentedButton(
                                selected = animeInfoPos == "below",
                                onClick = { prefs.animeInfoPosition().set("below") },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            ) { Text("Below episodes") }
                        }
                    }
                }
            }

            // ---- 4. Thumbnail size ----
            item {
                SettingsGroupCard(title = "Thumbnail size") {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("Thumbnail size", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        SingleChoiceSegmentedButtonRow {
                            SegmentedButton(
                                selected = thumbSize == "small",
                                onClick = { prefs.thumbnailSize().set("small") },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                            ) { Text("Small") }
                            SegmentedButton(
                                selected = thumbSize == "medium",
                                onClick = { prefs.thumbnailSize().set("medium") },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                            ) { Text("Medium") }
                            SegmentedButton(
                                selected = thumbSize == "large",
                                onClick = { prefs.thumbnailSize().set("large") },
                                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                            ) { Text("Large") }
                        }
                    }
                }
            }
        }
    }
}
