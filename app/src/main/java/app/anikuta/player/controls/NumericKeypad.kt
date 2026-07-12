package app.anikuta.player.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.anikuta.player.PlayerPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * A value-entry dialog for subtitle numeric settings. Uses a custom 4×3
 * numeric keypad (experimental) or falls back to the device keyboard via an
 * [OutlinedTextField], depending on [PlayerPreferences.useCustomKeypad].
 *
 * @param title   What's being edited (e.g. "Font size").
 * @param initial The starting value.
 * @param suffix  Optional unit label shown after the value (e.g. "ms", "%").
 * @param min     Minimum allowed value (input clamped on confirm).
 * @param max     Maximum allowed value (input clamped on confirm).
 * @param onConfirm Called with the user-entered value (clamped to range).
 * @param onDismiss Close the dialog without applying.
 */
@Composable
fun NumericEntryDialog(
    title: String,
    initial: Int,
    suffix: String = "",
    min: Int = Int.MIN_VALUE,
    max: Int = Int.MAX_VALUE,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val prefs: PlayerPreferences = remember { Injekt.get() }
    val scope = rememberCoroutineScope()
    val useCustom by prefs.useCustomKeypad().stateIn(scope).collectAsState()

    if (useCustom) {
        CustomKeypadDialog(
            title = title,
            initial = initial,
            suffix = suffix,
            min = min,
            max = max,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
    } else {
        TextFieldEntryDialog(
            title = title,
            initial = initial,
            suffix = suffix,
            min = min,
            max = max,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun CustomKeypadDialog(
    title: String,
    initial: Int,
    suffix: String,
    min: Int,
    max: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    // The raw input string — allows free typing, clamped on confirm.
    var input by remember { mutableStateOf(initial.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {},
        title = {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // ---- Value display ----
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                ) {
                    Text(
                        text = if (input.isEmpty()) "—" else "$input$suffix",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    )
                }

                // ---- 4×3 keypad grid ----
                // Layout: 4 columns × 3 rows = 12 buttons.
                // Left 3×3 = numbers 1-9.
                // Right column (top→bottom) = Delete, 0, Confirm.
                val keys = listOf(
                    listOf("1", "2", "3", "DEL"),
                    listOf("4", "5", "6", "0"),
                    listOf("7", "8", "9", "OK"),
                )
                keys.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { key ->
                            KeypadButton(
                                key = key,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    when (key) {
                                        "DEL" -> {
                                            if (input.isNotEmpty()) {
                                                input = input.dropLast(1)
                                            }
                                        }
                                        "OK" -> {
                                            val v = input.toIntOrNull() ?: initial
                                            val clamped = v.coerceIn(min, max)
                                            onConfirm(clamped)
                                        }
                                        else -> {
                                            // Prevent leading zeros for multi-digit input
                                            if (input == "0") input = key
                                            else if (input.length < 8) input += key
                                        }
                                    }
                                },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Cancel button
                TextButton(onClick = onDismiss, modifier = Modifier.padding(top = 4.dp)) {
                    Text("Cancel")
                }
            }
        },
    )
}

@Composable
private fun KeypadButton(
    key: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val isAction = key == "DEL" || key == "OK"
    val containerColor = when {
        key == "OK" -> MaterialTheme.colorScheme.primary
        key == "DEL" -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val contentColor = when {
        key == "OK" -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        modifier = modifier
            .aspectRatio(1.3f)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            when (key) {
                "DEL" -> Icon(Icons.Default.Backspace, contentDescription = "Delete", tint = contentColor, modifier = Modifier.size(22.dp))
                "OK" -> Icon(Icons.Default.Check, contentDescription = "Confirm", tint = contentColor, modifier = Modifier.size(24.dp))
                else -> Text(key, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = contentColor)
            }
        }
    }
}

@Composable
private fun TextFieldEntryDialog(
    title: String,
    initial: Int,
    suffix: String,
    min: Int,
    max: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val v = text.toIntOrNull() ?: initial
                onConfirm(v.coerceIn(min, max))
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() || c == '-' } },
                label = { Text("$title ($min–$max$suffix)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}
