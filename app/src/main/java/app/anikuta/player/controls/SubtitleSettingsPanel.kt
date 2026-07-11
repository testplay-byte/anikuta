package app.anikuta.player.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

    val font by prefs.subtitleFont().stateIn(scope).collectAsState()
    val fontSize by prefs.subtitleFontSize().stateIn(scope).collectAsState()
    val fontScale by prefs.subtitleFontScale().stateIn(scope).collectAsState()
    val borderSize by prefs.subtitleBorderSize().stateIn(scope).collectAsState()
    val bold by prefs.boldSubtitles().stateIn(scope).collectAsState()
    val italic by prefs.italicSubtitles().stateIn(scope).collectAsState()
    val textColor by prefs.textColorSubtitles().stateIn(scope).collectAsState()
    val borderColor by prefs.borderColorSubtitles().stateIn(scope).collectAsState()
    val bgColor by prefs.backgroundColorSubtitles().stateIn(scope).collectAsState()
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
        // Font family selector
        CompactDropdownRow(
            label = "Font",
            value = font,
            options = listOf("Sans Serif", "Serif", "Monospace", "Roboto"),
            onChange = { prefs.subtitleFont().set(it); onSettingsChanged() },
        )
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

        // ---- Colors ----
        SectionHeader("Colors")
        ColorPickerRow(
            label = "Text color",
            color = textColor,
            onChange = { prefs.textColorSubtitles().set(it); onSettingsChanged() },
        )
        ColorPickerRow(
            label = "Border color",
            color = borderColor,
            onChange = { prefs.borderColorSubtitles().set(it); onSettingsChanged() },
        )
        ColorPickerRow(
            label = "Background color",
            color = bgColor,
            onChange = { prefs.backgroundColorSubtitles().set(it); onSettingsChanged() },
        )

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

/**
 * Compact dropdown row: label on the left, dropdown selector on the right.
 * Used for font family selection.
 */
@Composable
private fun CompactDropdownRow(
    label: String,
    value: String,
    options: List<String>,
    onChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onChange(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/**
 * Color picker row: label on the left, color swatch + hex value on the right.
 * Tapping opens a simple color palette popup with common subtitle colors.
 */
@Composable
private fun ColorPickerRow(
    label: String,
    color: Int,
    onChange: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val colorObj = Color(color)
    val hex = String.format("#%08X", color)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Color swatch
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(colorObj)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                )
                Text(
                    text = hex,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                // Common subtitle colors
                val presetColors = listOf(
                    "White" to 0xFFFFFFFF.toInt(),
                    "Black" to 0xFF000000.toInt(),
                    "Yellow" to 0xFFFFFF00.toInt(),
                    "Cyan" to 0xFF00FFFF.toInt(),
                    "Red" to 0xFFFF0000.toInt(),
                    "Green" to 0xFF00FF00.toInt(),
                    "Blue" to 0xFF0000FF.toInt(),
                    "Transparent" to 0x00000000,
                )
                presetColors.forEach { (name, colorValue) ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(Color(colorValue))
                                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(name)
                            }
                        },
                        onClick = {
                            onChange(colorValue)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
