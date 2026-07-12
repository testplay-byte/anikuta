package app.anikuta.player.controls

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.anikuta.player.PlayerPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * A bottom-sheet value-entry for subtitle numeric settings. Shown from the
 * bottom of the screen (NOT a center popup) so the video player stays visible
 * behind it and the user can see subtitle changes in real time as they type.
 *
 * Uses a custom 4×3 numeric keypad (experimental) or falls back to the device
 * keyboard via an [OutlinedTextField], depending on [PlayerPreferences.useCustomKeypad].
 *
 * The input is shown LIVE on the slider value itself (via [onLiveChange]) so
 * the user sees the effect immediately — the keypad does NOT show the value
 * in its own display. The video + the original setting row (which reflects
 * the live value) act as the preview.
 *
 * @param title   What's being edited (e.g. "Font size").
 * @param initial The starting value.
 * @param suffix  Optional unit label shown after the value (e.g. "ms", "%").
 * @param min     Minimum allowed value (input clamped on confirm).
 * @param max     Maximum allowed value (input clamped on confirm).
 * @param onLiveChange Called on every keystroke with the current (unclamped) value so the slider updates live.
 * @param onConfirm Called with the user-entered value (clamped to range) when Done/OK is pressed.
 * @param onDismiss Close the sheet without applying (revert is the caller's responsibility if needed).
 */
@Composable
fun NumericEntrySheet(
    title: String,
    initial: Int,
    suffix: String = "",
    min: Int = Int.MIN_VALUE,
    max: Int = Int.MAX_VALUE,
    onLiveChange: (Int) -> Unit,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val prefs: PlayerPreferences = remember { Injekt.get() }
    val scope = rememberCoroutineScope()
    val useCustom by prefs.useCustomKeypad().stateIn(scope).collectAsState()

    if (useCustom) {
        CustomKeypadSheet(
            title = title,
            initial = initial,
            suffix = suffix,
            min = min,
            max = max,
            onLiveChange = onLiveChange,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
    } else {
        TextFieldEntrySheet(
            title = title,
            initial = initial,
            suffix = suffix,
            min = min,
            max = max,
            onLiveChange = onLiveChange,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun CustomKeypadSheet(
    title: String,
    initial: Int,
    suffix: String,
    min: Int,
    max: Int,
    onLiveChange: (Int) -> Unit,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var input by remember { mutableStateOf(initial.toString()) }

    // Push live updates whenever the input changes.
    val liveValue = input.toIntOrNull() ?: initial
    androidx.compose.runtime.LaunchedEffect(input) {
        onLiveChange(liveValue)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            // Title only — the value itself is shown on the slider/setting row
            // behind the sheet (the live video + the original UI is the preview).
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp),
            )

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
                                        onConfirm(v.coerceIn(min, max))
                                    }
                                    else -> {
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

            // Done button (same as OK but always visible)
            Button(
                onClick = {
                    val v = input.toIntOrNull() ?: initial
                    onConfirm(v.coerceIn(min, max))
                },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) { Text("Done") }
        }
    }
}

@Composable
private fun KeypadButton(
    key: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
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
            .aspectRatio(1.6f)
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
private fun TextFieldEntrySheet(
    title: String,
    initial: Int,
    suffix: String,
    min: Int,
    max: Int,
    onLiveChange: (Int) -> Unit,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by remember { mutableStateOf(initial.toString()) }
    androidx.compose.runtime.LaunchedEffect(text) {
        text.toIntOrNull()?.let { onLiveChange(it) }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() || c == '-' } },
                label = { Text("$title ($min–$max$suffix)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    val v = text.toIntOrNull() ?: initial
                    onConfirm(v.coerceIn(min, max))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Done") }
        }
    }
}
