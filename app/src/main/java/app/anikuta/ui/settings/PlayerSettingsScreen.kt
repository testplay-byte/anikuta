package app.anikuta.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Speed
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
        }
    }
}
