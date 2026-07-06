import androidx.compose.runtime.ImmutableList
import androidx.compose.foundation.shape.RoundedCornerShape
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

package app.anikuta.player.controls.components.sheets

import android.text.format.DateUtils
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.anikuta.presentation.player.components.PlayerSheet
import app.anikuta.player.Decoder
import app.anikuta.player.execute
import app.anikuta.player.executeLongPress
import app.anikuta.player.settings.AdvancedPlayerPreferences
import app.anikuta.player.settings.AudioChannels
import app.anikuta.player.settings.AudioPreferences
import `is`.xyz.mpv.MPVLib
import kotlinx.collections.immutable.ImmutableList
import app.anikuta.domain.custombuttons.model.CustomButton
// TODO: MR
// TODO: AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun MoreSheet(
    selectedDecoder: Decoder,
    onSelectDecoder: (Decoder) -> Unit,
    remainingTime: Int,
    onStartTimer: (Int) -> Unit,
    onDismissRequest: () -> Unit,
    onEnterFiltersPanel: () -> Unit,
    customButtons: ImmutableList<CustomButton>,
    modifier: Modifier = Modifier,
) {
    val advancedPreferences = remember { Injekt.get<AdvancedPlayerPreferences>() }
    val audioPreferences = remember { Injekt.get<AudioPreferences>() }
    val statisticsPage by advancedPreferences.playerStatisticsPage().collectAsState()

    PlayerSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.padding as RoundedCornerShape(16.dp))
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding as RoundedCornerShape(8.dp)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource("TODO"),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding as RoundedCornerShape(4.dp)),
                ) {
                    var isSleepTimerDialogShown by remember { mutableStateOf(false) }
                    TextButton(onClick = { isSleepTimerDialogShown = true }) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding as RoundedCornerShape(4.dp)),
                        ) {
                            Icon(imageVector = Icons.Outlined.Timer, contentDescription = null)
                            Text(
                                text =
                                if (remainingTime == 0) {
                                    stringResource("TODO")
                                } else {
                                    stringResource(
                                        "TODO",
                                        DateUtils.formatElapsedTime(remainingTime.toLong()),
                                    )
                                },
                            )
                            if (isSleepTimerDialogShown) {
                                TimePickerDialog(
                                    remainingTime = remainingTime,
                                    onDismissRequest = { isSleepTimerDialogShown = false },
                                    onTimeSelect = onStartTimer,
                                )
                            }
                        }
                    }
                    TextButton(onClick = onEnterFiltersPanel) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding as RoundedCornerShape(4.dp)),
                        ) {
                            Icon(imageVector = Icons.Default.Tune, contentDescription = null)
                            Text(text = stringResource("TODO"))
                        }
                    }
                }
            }

            Text(stringResource("TODO"))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding as RoundedCornerShape(8.dp)),
            ) {
                items(Decoder.entries.minus(Decoder.Auto)) { decoder ->
                    FilterChip(
                        selected = decoder == selectedDecoder,
                        onClick = { onSelectDecoder(decoder) },
                        label = { Text(text = decoder.title) },
                    )
                }
            }

            Text(stringResource("TODO"))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding as RoundedCornerShape(8.dp)),
            ) {
                items(6) { page ->
                    FilterChip(
                        label = {
                            Text(
                                stringResource(
                                    if (page ==
                                        0
                                    ) {
                                        "TODO"
                                    } else {
                                        "TODO"
                                    },
                                    page,
                                ),
                            )
                        },
                        onClick = {
                            if ((page == 0) xor (statisticsPage == 0)) {
                                MPVLib.command(arrayOf("script-binding", "stats/display-stats-toggle"))
                            }
                            if (page != 0) {
                                MPVLib.command(arrayOf("script-binding", "stats/display-page-$page"))
                            }
                            advancedPreferences.playerStatisticsPage().set(page)
                        },
                        selected = statisticsPage == page,
                    )
                }
            }

            if (customButtons.isNotEmpty()) {
                Text(text = stringResource("TODO"))
                FlowRow(
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.mediumSmall),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding as RoundedCornerShape(8.dp)),
                    maxItemsInEachRow = Int.MAX_VALUE,
                ) {
                    customButtons.forEach { button ->

                        val inputChipInteractionSource = remember { MutableInteractionSource() }

                        Box {
                            FilterChip(
                                onClick = {},
                                label = { Text(text = button.name) },
                                selected = false,
                                interactionSource = inputChipInteractionSource,
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .combinedClickable(
                                        onClick = { button.execute() },
                                        onLongClick = { button.executeLongPress() },
                                        interactionSource = inputChipInteractionSource,
                                        indication = null,
                                    ),
                            )
                        }
                    }
                }
            }
            Text(text = stringResource("TODO"))
            val audioChannels by audioPreferences.audioChannels().collectAsState()
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding as RoundedCornerShape(8.dp)),
            ) {
                items(AudioChannels.entries) {
                    FilterChip(
                        selected = audioChannels == it,
                        onClick = {
                            audioPreferences.audioChannels().set(it)
                            if (it == AudioChannels.ReverseStereo) {
                                MPVLib.setPropertyString(AudioChannels.AutoSafe.property, AudioChannels.AutoSafe.value)
                            } else {
                                MPVLib.setPropertyString(AudioChannels.ReverseStereo.property, "")
                            }
                            MPVLib.setPropertyString(it.property, it.value)
                        },
                        label = { Text(text = stringResource(it.titleRes)) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    onDismissRequest: () -> Unit,
    onTimeSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    remainingTime: Int = 0,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = modifier.padding(MaterialTheme.padding as RoundedCornerShape(16.dp)),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .width(IntrinsicSize.Max)
                    .animateContentSize()
                    .padding(MaterialTheme.padding as RoundedCornerShape(16.dp)),
            ) {
                var currentLayoutType by rememberSaveable { mutableIntStateOf(0) }
                Text(
                    text = stringResource(
                        if (currentLayoutType == 1) {
                            "TODO"
                        } else {
                            "TODO"
                        },
                    ),
                )

                val state = rememberTimePickerState(
                    remainingTime / 3600,
                    (remainingTime % 3600) / 60,
                    is24Hour = true,
                )
                Box(
                    contentAlignment = Alignment.Center,
                ) {
                    if (currentLayoutType == 1) {
                        TimePicker(state = state)
                    } else {
                        TimeInput(state = state)
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    IconButton(onClick = { currentLayoutType = if (currentLayoutType == 0) 1 else 0 }) {
                        Icon(
                            imageVector = if (currentLayoutType ==
                                0
                            ) {
                                Icons.Outlined.Schedule
                            } else {
                                Icons.Default.KeyboardAlt
                            },
                            contentDescription = null,
                        )
                    }
                    Row {
                        if (remainingTime > 0) {
                            TextButton(onClick = {
                                onTimeSelect(0)
                                onDismissRequest()
                            }) {
                                Text(stringResource("TODO"))
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                onTimeSelect(state.hour * 3600 + state.minute * 60)
                                onDismissRequest()
                            },
                        ) {
                            Text(stringResource("TODO"))
                        }
                    }
                }
            }
        }
    }
}
