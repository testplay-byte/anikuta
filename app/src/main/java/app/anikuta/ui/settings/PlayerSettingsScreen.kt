package app.anikuta.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.anikuta.player.PlayerPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Player settings main page — reorganized into clear, logical sections.
 *
 * Structure:
 *   1. General — default view, quality sheet display
 *   2. Playback — speed, hardware decoding, audio language
 *   3. Subtitles — default mode, preferred language, → subpage
 *   4. Display & Behavior — top bar, auto-hide, gestures, PiP, skip button
 *   5. Episode list — → subpage
 *   6. Storage — → subpage
 *
 * Selection options use SingleChoiceSegmentedButtonRow (same style as the
 * episode display settings page).
 */
@Composable
fun PlayerSettingsScreen(
    onBack: () -> Unit,
    onOpenEpisodeDisplay: () -> Unit = {},
    onOpenSubtitleSettings: () -> Unit = {},
    onOpenStorageSettings: () -> Unit = {},
) {
    val prefs: PlayerPreferences = remember { Injekt.get() }
    val scope = rememberCoroutineScope()
    val speed by prefs.playerSpeed().stateIn(scope).collectAsState()
    val hwdec by prefs.tryHWDecoding().stateIn(scope).collectAsState()
    var audioLang by remember { mutableStateOf(prefs.preferredAudioLanguages().get()) }
    val defaultView by prefs.defaultPlayerView().stateIn(scope).collectAsState()
    val subtitleMode by prefs.defaultSubtitleMode().stateIn(scope).collectAsState()
    var subtitleLang by remember { mutableStateOf(prefs.preferredSubtitleLanguage().get()) }
    val skipDuration by prefs.skipButtonDuration().stateIn(scope).collectAsState()
    val gesturesEnabled by prefs.playerGesturesEnabled().stateIn(scope).collectAsState()
    val autoHide by prefs.autoHideControls().stateIn(scope).collectAsState()
    val showTopBar by prefs.showPlayerTopBar().stateIn(scope).collectAsState()
    val qualityDisplayMode by prefs.qualitySheetDisplayMode().stateIn(scope).collectAsState()
    val pipOnExit by prefs.pipOnExit().stateIn(scope).collectAsState()

    SettingsSubpageScaffold(title = "Player", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ═══ 1. General ═══
            item {
                SettingsGroupCard(title = "General") {
                    // Default view
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("Default view", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("Which mode to open the player in", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        SingleChoiceSegmentedButtonRow {
                            SegmentedButton(
                                selected = defaultView == "minimized",
                                onClick = { prefs.defaultPlayerView().set("minimized") },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                            ) { Text("Minimized") }
                            SegmentedButton(
                                selected = defaultView == "fullscreen",
                                onClick = { prefs.defaultPlayerView().set("fullscreen") },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                            ) { Text("Fullscreen") }
                            SegmentedButton(
                                selected = defaultView == "ask",
                                onClick = { prefs.defaultPlayerView().set("ask") },
                                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                            ) { Text("Ask") }
                        }
                    }
                    HorizontalDivider()
                    // Quality sheet display
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("Quality sheet display", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("Choose what qualities to show in the quality picker", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        SingleChoiceSegmentedButtonRow {
                            SegmentedButton(
                                selected = qualityDisplayMode == "current",
                                onClick = { prefs.qualitySheetDisplayMode().set("current") },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            ) { Text("Current only") }
                            SegmentedButton(
                                selected = qualityDisplayMode == "all",
                                onClick = { prefs.qualitySheetDisplayMode().set("all") },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            ) { Text("Show all") }
                        }
                    }
                }
            }

            // ═══ 2. Playback ═══
            item {
                SettingsGroupCard(title = "Playback") {
                    // Speed slider
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("Playback speed: %.2fx".format(speed), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Slider(
                            value = speed,
                            onValueChange = { prefs.playerSpeed().set(it) },
                            valueRange = 0.25f..2.0f,
                            steps = 6,
                        )
                    }
                    HorizontalDivider()
                    SwitchSettingsRow(
                        icon = Icons.Default.Memory,
                        title = "Hardware decoding",
                        subtitle = "Use GPU for video decoding (recommended)",
                        checked = hwdec,
                        onCheckedChange = { prefs.tryHWDecoding().set(it) },
                    )
                    HorizontalDivider()
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("Preferred audio languages", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("Comma-separated, e.g. jpn,eng", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = audioLang,
                            onValueChange = { audioLang = it; prefs.preferredAudioLanguages().set(it) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("jpn,eng") },
                        )
                    }
                }
            }

            // ═══ 3. Subtitles ═══
            item {
                SettingsGroupCard(title = "Subtitles") {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("Default subtitle mode", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("What to do when an episode starts playing", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        SingleChoiceSegmentedButtonRow {
                            SegmentedButton(
                                selected = subtitleMode == "off",
                                onClick = { prefs.defaultSubtitleMode().set("off") },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                            ) { Text("Off") }
                            SegmentedButton(
                                selected = subtitleMode == "on",
                                onClick = { prefs.defaultSubtitleMode().set("on") },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                            ) { Text("On") }
                            SegmentedButton(
                                selected = subtitleMode == "auto",
                                onClick = { prefs.defaultSubtitleMode().set("auto") },
                                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                            ) { Text("Auto") }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Off = never show · On = always show first track · Auto = only if language matches",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider()
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("Preferred subtitle language", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("Used by Auto mode, and as a tiebreaker for On", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = subtitleLang,
                            onValueChange = { subtitleLang = it; prefs.preferredSubtitleLanguage().set(it) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("en,eng") },
                        )
                    }
                    HorizontalDivider()
                    ClickableSettingsRow(
                        icon = Icons.Default.Subtitles,
                        title = "Subtitle appearance",
                        subtitle = "Font, size, colors, position, delay",
                        onClick = onOpenSubtitleSettings,
                    )
                }
            }

            // ═══ 4. Display & Behavior ═══
            item {
                SettingsGroupCard(title = "Display & Behavior") {
                    SwitchSettingsRow(
                        icon = Icons.Default.Visibility,
                        title = "Show top bar",
                        subtitle = "Floating navigation bar in minimized mode",
                        checked = showTopBar,
                        onCheckedChange = { prefs.showPlayerTopBar().set(it) },
                    )
                    HorizontalDivider()
                    SwitchSettingsRow(
                        icon = Icons.Default.Visibility,
                        title = "Auto-hide controls",
                        subtitle = "Hide player controls after inactivity",
                        checked = autoHide,
                        onCheckedChange = { prefs.autoHideControls().set(it) },
                    )
                    HorizontalDivider()
                    SwitchSettingsRow(
                        icon = Icons.Default.TouchApp,
                        title = "Gestures",
                        subtitle = "Swipe to seek, brightness, volume, double-tap",
                        checked = gesturesEnabled,
                        onCheckedChange = { prefs.playerGesturesEnabled().set(it) },
                    )
                    HorizontalDivider()
                    SwitchSettingsRow(
                        icon = Icons.Default.PictureInPicture,
                        title = "Auto PiP on exit",
                        subtitle = "Enter picture-in-picture when pressing Home",
                        checked = pipOnExit,
                        onCheckedChange = { prefs.pipOnExit().set(it) },
                    )
                    HorizontalDivider()
                    SwitchSettingsRow(
                        icon = Icons.Default.SkipNext,
                        title = "Skip button",
                        subtitle = "Skip opening duration: ${skipDuration}s",
                        checked = skipDuration > 0,
                        onCheckedChange = { prefs.skipButtonDuration().set(if (it) 85 else 0) },
                    )
                }
            }

            // ═══ 5. Episode list ═══
            item {
                SettingsGroupCard(title = "Episode list") {
                    ClickableSettingsRow(
                        icon = Icons.Default.ViewAgenda,
                        title = "Episode display",
                        subtitle = "Customize how episodes appear in the player",
                        onClick = onOpenEpisodeDisplay,
                    )
                }
            }

            // ═══ 6. Storage ═══
            item {
                SettingsGroupCard(title = "Storage") {
                    ClickableSettingsRow(
                        icon = Icons.Default.Folder,
                        title = "Storage folder",
                        subtitle = "Where downloads, data, and backups are stored",
                        onClick = onOpenStorageSettings,
                    )
                }
            }
        }
    }
}
