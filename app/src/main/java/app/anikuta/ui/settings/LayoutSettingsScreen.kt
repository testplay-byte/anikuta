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
import androidx.compose.material.icons.filled.Palette
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
import app.anikuta.player.PlayerPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Details → Episode layout subpage.
 *
 * Layout: live preview at top + organized settings sections below.
 *
 * Sections:
 *  1. Text content — title and synopsis positions
 *  2. Badges & pills — date/audio pills position
 *  3. Episode number — overlay or badge
 *  4. Thumbnail — position and size
 *  5. Page layout — anime info position (above/below episodes)
 */
@Composable
fun LayoutSettingsScreen(onBack: () -> Unit) {
    val prefs: PlayerPreferences = remember { Injekt.get() }
    val scope = rememberCoroutineScope()
    val synopsisPos by prefs.synopsisPosition().stateIn(scope).collectAsState()
    val datePos by prefs.datePosition().stateIn(scope).collectAsState()
    val thumbSize by prefs.thumbnailSize().stateIn(scope).collectAsState()
    val titlePos by prefs.titlePosition().stateIn(scope).collectAsState()
    val epNumPos by prefs.episodeNumberPosition().stateIn(scope).collectAsState()
    val thumbPos by prefs.thumbnailPosition().stateIn(scope).collectAsState()
    val animeInfoPos by prefs.animeInfoPosition().stateIn(scope).collectAsState()
    // Read display prefs too so the preview reflects the full state
    val showTitles by prefs.showEpisodeTitles().stateIn(scope).collectAsState()
    val showSummaries by prefs.showEpisodeSummaries().stateIn(scope).collectAsState()
    val showThumbnails by prefs.showEpisodeThumbnails().stateIn(scope).collectAsState()
    val showDates by prefs.showEpisodeDates().stateIn(scope).collectAsState()
    val showEpisodeNumber by prefs.showEpisodeNumber().stateIn(scope).collectAsState()
    val showAudioPills by prefs.showAudioPills().stateIn(scope).collectAsState()

    SettingsSubpageScaffold(title = "Episode layout", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- Live preview at top ----
            item {
                Column {
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
            }

            // ---- Section 1: Text content ----
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SettingsGroupCard(title = "Text content") {
                        // Title position
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text("Title", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("Where the episode title appears", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            Text("Synopsis", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("Where the episode description appears", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    }
                }
            }

            // ---- Section 2: Badges & pills ----
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SettingsGroupCard(title = "Badges & pills") {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text("Date & audio pills", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("Where the date and SUB/DUB tags appear", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    }
                }
            }

            // ---- Section 3: Episode number ----
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SettingsGroupCard(title = "Episode number") {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text("Position", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("Overlay on thumbnail or badge next to title", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    }
                }
            }

            // ---- Section 4: Thumbnail ----
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SettingsGroupCard(title = "Thumbnail") {
                        // Position
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text("Side", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("Left or right side of the episode card", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        // Size
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text("Size", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("Small, medium, or large", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

            // ---- Section 5: Page layout ----
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SettingsGroupCard(title = "Page layout") {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text("Anime info", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("Show anime info above or below the episode list. Above = full-page scroll. Below = episodes in a scrollable section.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        HorizontalDivider()
                        // Dynamic theming toggle
                        val dynamicTheming by prefs.dynamicDetailTheming().stateIn(scope).collectAsState()
                        SwitchSettingsRow(
                            icon = Icons.Default.Palette,
                            title = "Dynamic theming",
                            subtitle = "Color the detail page based on the anime's cover image",
                            checked = dynamicTheming,
                            onCheckedChange = { prefs.dynamicDetailTheming().set(it) },
                        )
                    }
                }
            }
        }
    }
}
