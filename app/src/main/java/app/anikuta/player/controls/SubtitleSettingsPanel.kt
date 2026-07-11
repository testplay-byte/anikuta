package app.anikuta.player.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.anikuta.player.PlayerPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Subtitle settings panel — compact, scrollable, no live preview.
 *
 * Three sections:
 * 1. Typography: font size, scale, bold, italic, border size
 * 2. Position & Misc: position, shadow, ASS override, delay
 *
 * Design:
 *  - No live preview (the video player itself shows the subtitles)
 *  - Scrollable (verticalScroll) so it works in a constrained-height sheet
 *  - Compact slider rows with value displayed on the right
 *  - Themed with MaterialTheme.colorScheme for dynamic theming support
 *
 * Hosted in a PlayerSheet that doesn't take the full screen — the sheet
 * height is constrained so the video player remains visible behind it.
 */
@Composable
fun SubtitleSettingsPanel(
    onSettingsChanged: () -> Unit = {},
) {
    val prefs: PlayerPreferences = remember { Injekt.get() }
    val scope = rememberCoroutineScope()

    val fontSize by prefs.subtitleFontSize().stateIn(scope).collectAsState()
    val fontScale by prefs.subtitleFontScale().stateIn(scope).collectAsState()
    val borderSize by prefs.subtitleBorderSize().stateIn(scope).collectAsState()
    val bold by prefs.boldSubtitles().stateIn(scope).collectAsState()
    val italic by prefs.italicSubtitles().stateIn(scope).collectAsState()
    val position by prefs.subtitlePosition().stateIn(scope).collectAsState()
    val shadowOffset by prefs.subtitleShadowOffset().stateIn(scope).collectAsState()
    val overrideASS by prefs.overrideSubsASS().stateIn(scope).collectAsState()
    val delay by prefs.subtitlesDelay().stateIn(scope).collectAsState()

    // Scrollable column — allows the settings to scroll if they exceed the sheet height
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        // ---- Typography ----
        SectionHeader("Typography")
        CompactSliderRow(
            label = "Font size",
            valueText = fontSize.toString(),
            value = fontSize.toFloat(),
            range = 20f..100f,
            onChange = { prefs.subtitleFontSize().set(it.toInt()); onSettingsChanged() },
        )
        CompactSliderRow(
            label = "Scale",
            valueText = "%.1fx".format(fontScale),
            value = fontScale,
            range = 0.5f..3f,
            onChange = { prefs.subtitleFontScale().set(it); onSettingsChanged() },
        )
        CompactSliderRow(
            label = "Border size",
            valueText = borderSize.toString(),
            value = borderSize.toFloat(),
            range = 0f..10f,
            onChange = { prefs.subtitleBorderSize().set(it.toInt()); onSettingsChanged() },
        )
        CompactSwitchRow(label = "Bold", checked = bold, onChange = { prefs.boldSubtitles().set(it); onSettingsChanged() })
        CompactSwitchRow(label = "Italic", checked = italic, onChange = { prefs.italicSubtitles().set(it); onSettingsChanged() })

        Spacer(modifier = Modifier.height(16.dp))

        // ---- Position & Misc ----
        SectionHeader("Position & Misc")
        CompactSliderRow(
            label = "Position",
            valueText = "$position%",
            value = position.toFloat(),
            range = 0f..100f,
            onChange = { prefs.subtitlePosition().set(it.toInt()); onSettingsChanged() },
        )
        CompactSliderRow(
            label = "Shadow offset",
            valueText = shadowOffset.toString(),
            value = shadowOffset.toFloat(),
            range = 0f..10f,
            onChange = { prefs.subtitleShadowOffset().set(it.toInt()); onSettingsChanged() },
        )
        CompactSwitchRow(label = "Override ASS styling", checked = overrideASS, onChange = { prefs.overrideSubsASS().set(it); onSettingsChanged() })
        CompactSliderRow(
            label = "Delay",
            valueText = "${delay}ms",
            value = delay.toFloat(),
            range = -5000f..5000f,
            onChange = { prefs.subtitlesDelay().set(it.toInt()); onSettingsChanged() },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp, top = 4.dp),
    )
}

/**
 * Compact slider row: label on the left, value on the right, slider below.
 * Uses a thinner track and smaller thumb for a cleaner look.
 */
@Composable
private fun CompactSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        )
    }
}

@Composable
private fun CompactSwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
