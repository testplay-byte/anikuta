package app.anikuta.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.anikuta.player.PlayerPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Player settings subpage.
 * Phase 1.7: Added default view, gestures, auto-hide, skip duration.
 * Original: Speed, hardware decoding, audio language.
 */
@Composable
fun PlayerSettingsScreen(
    onBack: () -> Unit,
    onOpenEpisodeDisplay: () -> Unit = {},
) {
    val prefs: PlayerPreferences = remember { Injekt.get() }
    val scope = rememberCoroutineScope()
    val speed by prefs.playerSpeed().stateIn(scope).collectAsState()
    val hwdec by prefs.tryHWDecoding().stateIn(scope).collectAsState()
    var audioLang by remember { mutableStateOf(prefs.preferredAudioLanguages().get()) }
    val defaultView by prefs.defaultPlayerView().stateIn(scope).collectAsState()
    val skipDuration by prefs.skipButtonDuration().stateIn(scope).collectAsState()
    val gesturesEnabled by prefs.playerGesturesEnabled().stateIn(scope).collectAsState()
    val autoHide by prefs.autoHideControls().stateIn(scope).collectAsState()

    SettingsSubpageScaffold(title = "Player", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Default view (Phase 1.7)
            item {
                SettingsGroupCard(title = "Default view") {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("Which mode to open the player in", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("Ask shows a prompt the first time", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                }
            }

            // Episode list display
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SettingsGroupCard(title = "Episode list") {
                        ClickableSettingsRow(
                            icon = Icons.Default.ViewAgenda,
                            title = "Episode display",
                            subtitle = "Customize how episodes appear in the player",
                            onClick = onOpenEpisodeDisplay,
                        )
                    }
                }
            }

            // Playback settings (original + new)
            item {
                SettingsGroupCard(title = "Playback") {
                    // Speed slider
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        LeadingIcon(Icons.Default.Speed)
                        Text("Playback speed: %.2fx".format(speed), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Slider(
                            value = speed,
                            onValueChange = { prefs.playerSpeed().set(it) },
                            valueRange = 0.25f..2.0f,
                            steps = 6,
                        )
                    }
                    HorizontalDivider()
                    // HW decoding
                    SwitchSettingsRow(
                        icon = Icons.Default.Memory,
                        title = "Hardware decoding",
                        subtitle = "Use GPU for video decoding (recommended)",
                        checked = hwdec,
                        onCheckedChange = { prefs.tryHWDecoding().set(it) },
                    )
                    HorizontalDivider()
                    // Audio language
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        LeadingIcon(Icons.Default.RecordVoiceOver)
                        Text("Preferred audio languages", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
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

            // Player behavior (Phase 1.7)
            item {
                SettingsGroupCard(title = "Player behavior") {
                    SwitchSettingsRow(
                        icon = Icons.Default.TouchApp,
                        title = "Gestures",
                        subtitle = "Swipe to seek, brightness, volume, double-tap",
                        checked = gesturesEnabled,
                        onCheckedChange = { prefs.playerGesturesEnabled().set(it) },
                    )
                    HorizontalDivider()
                    SwitchSettingsRow(
                        icon = Icons.Default.Visibility,
                        title = "Show top bar",
                        subtitle = "Show the floating navigation bar in minimized mode",
                        checked = prefs.showPlayerTopBar().get(),
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
                        icon = Icons.Default.SkipNext,
                        title = "Skip button",
                        subtitle = "Skip opening duration: ${skipDuration}s",
                        checked = skipDuration > 0,
                        onCheckedChange = {
                            prefs.skipButtonDuration().set(if (it) 85 else 0)
                        },
                    )
                }
            }
        }
    }
}
