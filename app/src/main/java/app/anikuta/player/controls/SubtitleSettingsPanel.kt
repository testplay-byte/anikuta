package app.anikuta.player.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.anikuta.player.PlayerPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Phase 5.3-5.4 — Subtitle settings panel with live preview.
 *
 * Three sections:
 * 1. Typography: font size, scale, bold, italic, border size
 * 2. Colors: text, border, background
 * 3. Position & Misc: position, shadow, ASS override, delay
 *
 * Live preview at top shows sample subtitle text with current settings.
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
    val textColor by prefs.textColorSubtitles().stateIn(scope).collectAsState()
    val borderColor by prefs.borderColorSubtitles().stateIn(scope).collectAsState()
    val bgColor by prefs.backgroundColorSubtitles().stateIn(scope).collectAsState()
    val position by prefs.subtitlePosition().stateIn(scope).collectAsState()
    val shadowOffset by prefs.subtitleShadowOffset().stateIn(scope).collectAsState()
    val overrideASS by prefs.overrideSubsASS().stateIn(scope).collectAsState()
    val delay by prefs.subtitlesDelay().stateIn(scope).collectAsState()

    Column(modifier = Modifier.fillMaxWidth()) {
        // ---- Live Preview ----
        SubtitlePreview(
            text = "Sample subtitle text",
            fontSize = fontSize,
            fontScale = fontScale,
            bold = bold,
            italic = italic,
            textColor = Color(textColor),
            borderColor = Color(borderColor),
            bgColor = Color(bgColor),
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ---- Typography ----
        Text("Typography", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        SliderRow(label = "Font size", value = fontSize.toFloat(), range = 20f..100f, steps = 0, onChange = { prefs.subtitleFontSize().set(it.toInt()); onSettingsChanged() })
        SliderRow(label = "Scale", value = fontScale, range = 0.5f..3f, steps = 9, onChange = { prefs.subtitleFontScale().set(it); onSettingsChanged() })
        SliderRow(label = "Border size", value = borderSize.toFloat(), range = 0f..10f, steps = 0, onChange = { prefs.subtitleBorderSize().set(it.toInt()); onSettingsChanged() })
        SwitchRow(label = "Bold", checked = bold, onChange = { prefs.boldSubtitles().set(it); onSettingsChanged() })
        SwitchRow(label = "Italic", checked = italic, onChange = { prefs.italicSubtitles().set(it); onSettingsChanged() })

        Spacer(modifier = Modifier.height(16.dp))

        // ---- Position & Misc ----
        Text("Position & Misc", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        SliderRow(label = "Position (0=top, 100=bottom)", value = position.toFloat(), range = 0f..100f, steps = 0, onChange = { prefs.subtitlePosition().set(it.toInt()); onSettingsChanged() })
        SliderRow(label = "Shadow offset", value = shadowOffset.toFloat(), range = 0f..10f, steps = 0, onChange = { prefs.subtitleShadowOffset().set(it.toInt()); onSettingsChanged() })
        SwitchRow(label = "Override ASS styling", checked = overrideASS, onChange = { prefs.overrideSubsASS().set(it); onSettingsChanged() })
        SliderRow(label = "Delay (ms)", value = delay.toFloat(), range = -5000f..5000f, steps = 0, onChange = { prefs.subtitlesDelay().set(it.toInt()); onSettingsChanged() })
    }
}

@Composable
private fun SubtitlePreview(
    text: String,
    fontSize: Int,
    fontScale: Float,
    bold: Boolean,
    italic: Boolean,
    textColor: Color,
    borderColor: Color,
    bgColor: Color,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color.Black,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(bgColor.copy(alpha = 0.3f)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Text(
                text = text,
                color = textColor,
                fontSize = (fontSize * fontScale).sp,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text("$label: ${"%.1f".format(value)}", style = MaterialTheme.typography.bodySmall)
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
