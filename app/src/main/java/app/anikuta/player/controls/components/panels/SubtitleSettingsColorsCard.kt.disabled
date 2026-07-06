import androidx.compose.ui.res.stringResource
/*
 * Copyright 2024 Abdallah Mehiz
 * https://github.com/abdallahmehiz/mpvKt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.anikuta.player.controls.components.panels

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatColorReset
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.alpha
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import dev.icerock.moko.resources.StringResource
import app.anikuta.presentation.player.components.ExpandableCard
import app.anikuta.presentation.player.components.TintedSliderItem
import app.anikuta.player.controls.CARDS_MAX_WIDTH
import app.anikuta.player.controls.panelCardsColors
import app.anikuta.player.settings.SubtitlePreferences
import `is`.xyz.mpv.MPVLib
import app.anikuta.core.preference.Preference
import app.anikuta.core.preference.deleteAndGet
// TODO: MR
// TODO: AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun SubtitleSettingsColorsCard(
    modifier: Modifier = Modifier,
) {
    val preferences = remember { Injekt.get<SubtitlePreferences>() }
    var isExpanded by remember { mutableStateOf(true) }
    ExpandableCard(
        isExpanded = isExpanded,
        onExpand = { isExpanded = !isExpanded },
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding as RoundedCornerShape(16.dp)),
            ) {
                Icon(Icons.Default.Palette, null)
                Text(stringResource("TODO"))
            }
        },
        modifier = modifier.widthIn(max = CARDS_MAX_WIDTH),
        colors = panelCardsColors(),
    ) {
        Column {
            var currentColorType by remember { mutableStateOf(SubColorType.Text) }
            var currentColor by remember { mutableIntStateOf(getCurrentMPVColor(currentColorType)) }
            LaunchedEffect(currentColorType) {
                currentColor = getCurrentMPVColor(currentColorType)
            }
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = MaterialTheme.padding.extraSmall, end = MaterialTheme.padding as RoundedCornerShape(16.dp)),
            ) {
                SubColorType.entries.forEach { type ->
                    IconToggleButton(
                        checked = currentColorType == type,
                        onCheckedChange = { currentColorType = type },
                    ) {
                        Icon(
                            when (type) {
                                SubColorType.Text -> Icons.Default.FormatColorText
                                SubColorType.Border -> Icons.Default.BorderColor
                                SubColorType.Background -> Icons.Default.FormatColorFill
                            },
                            null,
                        )
                    }
                }
                Text(stringResource(currentColorType.titleRes))
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = {
                        resetColors(preferences, currentColorType)
                        currentColor = getCurrentMPVColor(currentColorType)
                    },
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding as RoundedCornerShape(4.dp)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.FormatColorReset, null)
                        Text(stringResource("TODO"))
                    }
                }
            }
            SubtitlesColorPicker(
                currentColor,
                onColorChange = {
                    currentColor = it
                    currentColorType.preference(preferences).set(it)
                    MPVLib.setPropertyString(currentColorType.property, it.toColorHexString())
                },
            )
        }
    }
}

fun Int.copyAsArgb(
    alpha: Int = this.alpha,
    red: Int = this.red,
    green: Int = this.green,
    blue: Int = this.blue,
) = (alpha shl 24) or (red shl 16) or (green shl 8) or blue

@OptIn(ExperimentalStdlibApi::class)
fun Int.toColorHexString() = "#" + this.toHexString().uppercase()

enum class SubColorType(
    val titleRes: StringResource,
    val property: String,
    val preference: (SubtitlePreferences) -> Preference<Int>,
) {
    Text(
        "TODO",
        "sub-color",
        preference = SubtitlePreferences::textColorSubtitles,
    ),
    Border(
        "TODO",
        "sub-border-color",
        preference = SubtitlePreferences::borderColorSubtitles,
    ),
    Background(
        "TODO",
        "sub-back-color",
        preference = SubtitlePreferences::backgroundColorSubtitles,
    ),
}

fun resetColors(preferences: SubtitlePreferences, type: SubColorType) {
    when (type) {
        SubColorType.Text -> {
            MPVLib.setPropertyString("sub-color", preferences.textColorSubtitles().deleteAndGet().toColorHexString())
        }

        SubColorType.Border -> {
            MPVLib.setPropertyString(
                "sub-border-color",
                preferences.borderColorSubtitles().deleteAndGet().toColorHexString(),
            )
        }

        SubColorType.Background -> {
            MPVLib.setPropertyString(
                "sub-back-color",
                preferences.backgroundColorSubtitles().deleteAndGet().toColorHexString(),
            )
        }
    }
}

val getCurrentMPVColor: (SubColorType) -> Int = { colorType ->
    MPVLib.getPropertyString(colorType.property)?.let {
        android.graphics.Color.parseColor(it.uppercase())
    }!!
}

@Composable
fun SubtitlesColorPicker(
    color: Int,
    onColorChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        TintedSliderItem(
            stringResource("TODO"),
            color.red,
            color.red.toString(),
            onChange = { onColorChange(color.copyAsArgb(red = it)) },
            max = 255,
            tint = Color.Red,
        )

        TintedSliderItem(
            stringResource("TODO"),
            color.green,
            color.green.toString(),
            onChange = { onColorChange(color.copyAsArgb(green = it)) },
            max = 255,
            tint = Color.Green,
        )

        TintedSliderItem(
            stringResource("TODO"),
            color.blue,
            color.blue.toString(),
            onChange = { onColorChange(color.copyAsArgb(blue = it)) },
            max = 255,
            tint = Color.Blue,
        )

        TintedSliderItem(
            stringResource("TODO"),
            color.alpha,
            color.alpha.toString(),
            onChange = { onColorChange(color.copyAsArgb(alpha = it)) },
            max = 255,
            tint = Color.White,
        )
    }
}
