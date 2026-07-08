package app.anikuta.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.anikuta.player.PlayerPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Phase 6 task 6.19 — Player settings subpage.
 * Speed, hardware decoding, audio language. Subtitle/gesture settings deferred.
 */
@Composable
fun PlayerSettingsScreen(onBack: () -> Unit) {
    val prefs: PlayerPreferences = remember { Injekt.get() }
    val scope = rememberCoroutineScope()
    val speed by prefs.playerSpeed().stateIn(scope).collectAsState()
    val hwdec by prefs.tryHWDecoding().stateIn(scope).collectAsState()
    var audioLang by remember { mutableStateOf(prefs.preferredAudioLanguages().get()) }
    val showTitles by prefs.showEpisodeTitles().stateIn(scope).collectAsState()
    val showSummaries by prefs.showEpisodeSummaries().stateIn(scope).collectAsState()
    val showThumbnails by prefs.showEpisodeThumbnails().stateIn(scope).collectAsState()
    val showDates by prefs.showEpisodeDates().stateIn(scope).collectAsState()

    SettingsSubpageScaffold(title = "Player", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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

            // ---- Episode list display settings (Phase 7.5) ----
            item {
                SettingsGroupCard(title = "Episode list") {
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
                }
            }
        }
    }
}
