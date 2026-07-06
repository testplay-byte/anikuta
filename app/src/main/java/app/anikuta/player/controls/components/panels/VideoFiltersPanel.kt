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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.constraintlayout.compose.ConstraintLayout
import app.anikuta.presentation.player.components.SliderItem
import app.anikuta.player.VideoFilters
import app.anikuta.player.controls.CARDS_MAX_WIDTH
import app.anikuta.player.controls.components.ControlsButton
import app.anikuta.player.controls.panelCardsColors
import app.anikuta.player.settings.DecoderPreferences
import `is`.xyz.mpv.MPVLib
import app.anikuta.core.preference.deleteAndGet
// TODO: MR
// TODO: AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun VideoFiltersPanel(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ConstraintLayout(
        modifier = modifier
            .fillMaxSize()
            .padding(MaterialTheme.padding as RoundedCornerShape(16.dp)),
    ) {
        val filtersCard = createRef()

        FiltersCard(
            Modifier.constrainAs(filtersCard) {
                linkTo(parent.top, parent.bottom, bias = 0.8f)
                end.linkTo(parent.end)
            },
            onClose = onDismissRequest,
        )
    }
}

@Composable
fun FiltersCard(
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
) {
    val decoderPreferences = remember { Injekt.get<DecoderPreferences>() }
    Card(
        colors = panelCardsColors(),
        modifier = modifier
            .widthIn(max = CARDS_MAX_WIDTH),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = MaterialTheme.padding as RoundedCornerShape(16.dp)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource("TODO"),
                style = MaterialTheme.typography.headlineMedium,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding as RoundedCornerShape(4.dp)),
            ) {
                TextButton(
                    onClick = {
                        VideoFilters.entries.forEach {
                            MPVLib.setPropertyInt(it.mpvProperty, it.preference(decoderPreferences).deleteAndGet())
                        }
                    },
                ) {
                    Text(text = stringResource("TODO"))
                }
                ControlsButton(Icons.Default.Close, onClose)
            }
        }
        LazyColumn {
            items(VideoFilters.entries) { filter ->
                val value by filter.preference(decoderPreferences).collectAsState()
                SliderItem(
                    label = stringResource(filter.titleRes),
                    value = value,
                    valueText = value.toString(),
                    onChange = {
                        filter.preference(decoderPreferences).set(it)
                        MPVLib.setPropertyInt(filter.mpvProperty, it)
                    },
                    max = 100,
                    min = -100,
                )
            }
            item {
                if (decoderPreferences.gpuNext().get()) return@item
                Column(
                    modifier = Modifier
                        .padding(MaterialTheme.padding as RoundedCornerShape(16.dp))
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding as RoundedCornerShape(16.dp)),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Icon(Icons.Outlined.Info, null)
                    Text(stringResource("TODO"))
                }
            }
        }
    }
}
