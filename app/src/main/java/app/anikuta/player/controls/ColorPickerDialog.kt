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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * An improved color picker dialog for subtitle colors. Combines:
 *  - A row of preset swatches (one-tap quick select)
 *  - RGB sliders for custom color selection
 *  - A live preview + hex display
 *
 * This replaces the old DropdownMenu-with-fixed-prescents which didn't allow
 * custom colors.
 */
@Composable
fun ColorPickerDialog(
    title: String,
    initialColor: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var a by remember { mutableStateOf((initialColor shr 24) and 0xFF) }
    var r by remember { mutableStateOf((initialColor shr 16) and 0xFF) }
    var g by remember { mutableStateOf((initialColor shr 8) and 0xFF) }
    var b by remember { mutableStateOf(initialColor and 0xFF) }

    val currentColor = Color(a.toInt(), r.toInt(), g.toInt(), b.toInt())
    val hex = String.format("#%02X%02X%02X%02X", a, r, g, b)

    val presets = listOf(
        "White" to 0xFFFFFFFF.toInt(),
        "Black" to 0xFF000000.toInt(),
        "Yellow" to 0xFFFFFF00.toInt(),
        "Cyan" to 0xFF00FFFF.toInt(),
        "Red" to 0xFFFF0000.toInt(),
        "Green" to 0xFF00FF00.toInt(),
        "Blue" to 0xFF0000FF.toInt(),
        "Transparent" to 0x00000000,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val colorInt = (a.toInt() shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
                onConfirm(colorInt)
            }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // ---- Live preview ----
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(currentColor)
                            .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
                    )
                    Column {
                        Text("Preview", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(hex, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                // ---- Preset swatches ----
                Text("Presets", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    presets.forEach { (_, colorValue) ->
                        val c = Color(colorValue)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable {
                                    a = (colorValue shr 24) and 0xFF
                                    r = (colorValue shr 16) and 0xFF
                                    g = (colorValue shr 8) and 0xFF
                                    b = colorValue and 0xFF
                                },
                        )
                    }
                }

                // ---- Custom RGB+A sliders ----
                Text("Custom", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                ColorSliderRow("Red", r, 0xFF) { r = it }
                ColorSliderRow("Green", g, 0xFF) { g = it }
                ColorSliderRow("Blue", b, 0xFF) { b = it }
                ColorSliderRow("Alpha", a, 0xFF) { a = it }
            }
        },
    )
}

@Composable
private fun ColorSliderRow(label: String, value: Int, max: Int, onChange: (Int) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            Text("$value", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = 0f..max.toFloat(),
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        )
    }
}
