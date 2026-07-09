package app.anikuta.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
 * Details → Episode layout subpage.
 *
 * All position and size settings for the episode list:
 *  - Title position (right / below)
 *  - Synopsis position (right / below)
 *  - Date & audio pills position (above / below / full)
 *  - Episode number position (overlay / badge)
 *  - Thumbnail position (left / right)
 *  - Anime info position (above episodes / below episodes)
 *  - Thumbnail size (small / medium / large)
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

    SettingsSubpageScaffold(title = "Episode layout", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ---- Positions ----
            item {
                SettingsGroupCard(title = "Positions") {
                    // Title position
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("Title position", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("Where the episode title appears relative to the thumbnail", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Text("Where the episode synopsis appears relative to the thumbnail", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    // Date & audio pills position
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("Date & audio pills position", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("Where the date and SUB/DUB pills appear", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Text("Overlay = on the thumbnail; Badge = next to the title", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Text("Which side the thumbnail appears on", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Text("Where to show anime info (genres, score, etc.) relative to the episode list. When above, the whole page scrolls as one long list.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

            // ---- Thumbnail size ----
            item {
                SettingsGroupCard(title = "Thumbnail size") {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("Thumbnail size", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("Small (100dp) / Medium (120dp) / Large (160dp)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
