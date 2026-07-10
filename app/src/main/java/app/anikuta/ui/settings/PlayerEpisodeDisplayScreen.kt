package app.anikuta.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.sp
import app.anikuta.player.PlayerEpisodePreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Player → Episode list display settings subpage.
 *
 * Same structure as the detail page's DisplaySettingsScreen + LayoutSettingsScreen:
 *  - Sticky live preview at top
 *  - Show/hide toggles below
 *  - Layout positions section
 *
 * Uses PlayerEpisodePreferences (separate from detail page preferences).
 */
@Composable
fun PlayerEpisodeDisplayScreen(onBack: () -> Unit) {
    val prefs: PlayerEpisodePreferences = remember { Injekt.get() }
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

    SettingsSubpageScaffold(title = "Episode list", onBack = onBack) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ---- Sticky live preview ----
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            ) {
                Text(
                    "LIVE PREVIEW",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
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

            // ---- Scrollable settings ----
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        SettingsGroupCard(title = "Show or hide elements") {
                            SwitchSettingsRow(
                                icon = Icons.Default.Numbers,
                                title = "Episode number",
                                subtitle = "Show the episode number on each card",
                                checked = showEpisodeNumber,
                                onCheckedChange = { prefs.showEpisodeNumber().set(it) },
                            )
                            HorizontalDivider()
                            SwitchSettingsRow(
                                icon = Icons.Default.Title,
                                title = "Episode titles",
                                subtitle = "Show the parsed episode title",
                                checked = showTitles,
                                onCheckedChange = { prefs.showEpisodeTitles().set(it) },
                            )
                            HorizontalDivider()
                            SwitchSettingsRow(
                                icon = Icons.Default.Subtitles,
                                title = "Episode summaries",
                                subtitle = "Show the episode description",
                                checked = showSummaries,
                                onCheckedChange = { prefs.showEpisodeSummaries().set(it) },
                            )
                            HorizontalDivider()
                            SwitchSettingsRow(
                                icon = Icons.Default.Image,
                                title = "Episode thumbnails",
                                subtitle = "Show the preview image for each episode",
                                checked = showThumbnails,
                                onCheckedChange = { prefs.showEpisodeThumbnails().set(it) },
                            )
                            HorizontalDivider()
                            SwitchSettingsRow(
                                icon = Icons.Default.CalendarMonth,
                                title = "Episode dates",
                                subtitle = "Show the air date",
                                checked = showDates,
                                onCheckedChange = { prefs.showEpisodeDates().set(it) },
                            )
                            HorizontalDivider()
                            SwitchSettingsRow(
                                icon = Icons.Default.RecordVoiceOver,
                                title = "Audio pills",
                                subtitle = "Show SUB / DUB / HSUB tags",
                                checked = showAudioPills,
                                onCheckedChange = { prefs.showAudioPills().set(it) },
                            )
                        }
                    }
                }

                // Layout positions
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        SettingsGroupCard(title = "Positions") {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text("Title", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text("Where the episode title appears", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(10.dp))
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
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text("Synopsis", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text("Where the episode description appears", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(10.dp))
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
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text("Date & audio pills", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text("Where the date and SUB/DUB tags appear", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(10.dp))
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
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text("Episode number position", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text("Overlay on thumbnail or badge next to title", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(10.dp))
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
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text("Thumbnail side", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text("Left or right side of the episode card", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(10.dp))
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
                        }
                    }
                }

                // Thumbnail size
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        SettingsGroupCard(title = "Thumbnail size") {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text("Size", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text("Small, medium, or large", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(10.dp))
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
    }
}
