import androidx.compose.runtime.ImmutableList
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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import app.anikuta.player.PlayerViewModel.VideoTrack
import kotlinx.collections.immutable.ImmutableList
// TODO: AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun SubtitlesSheet(
    tracks: ImmutableList<VideoTrack>,
    selectedTracks: ImmutableList<Int>,
    onSelect: (Int) -> Unit,
    onAddSubtitle: () -> Unit,
    onOpenSubtitleSettings: () -> Unit,
    onOpenSubtitleDelay: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GenericTracksSheet(
        tracks = tracks,
        onDismissRequest = onDismissRequest,
        header = {
            TrackSheetTitle(
                title = stringResource("TODO"),
                actions = {
                    TextButton(onClick = onOpenSubtitleSettings) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding as RoundedCornerShape(4.dp)),
                        ) {
                            Icon(imageVector = Icons.Default.Palette, contentDescription = null)
                            Text(text = stringResource("TODO"))
                        }
                    }
                    TextButton(onClick = onOpenSubtitleDelay) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding as RoundedCornerShape(4.dp)),
                        ) {
                            Icon(imageVector = Icons.Default.MoreTime, contentDescription = null)
                            Text(text = stringResource("TODO"))
                        }
                    }
                },
            )
            AddTrackRow(
                title = stringResource("TODO"),
                onClick = onAddSubtitle,
            )
        },
        track = { track ->
            SubtitleTrackRow(
                title = getTrackTitle(track),
                selected = selectedTracks.indexOf(track.id),
                onClick = { onSelect(track.id) },
            )
        },
        footer = {
            Column(
                modifier = modifier
                    .padding(MaterialTheme.padding as RoundedCornerShape(16.dp))
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding as RoundedCornerShape(16.dp)),
                horizontalAlignment = Alignment.Start,
            ) {
                Icon(Icons.Outlined.Info, null)
                Text(stringResource("TODO"))
            }
        },
        modifier = modifier,
    )
}

@Composable
fun SubtitleTrackRow(
    title: String,
    selected: Int, // -1 unselected, otherwise return 0 and 1 for the selected indices
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = MaterialTheme.padding.small, end = MaterialTheme.padding as RoundedCornerShape(16.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = selected > -1,
            onCheckedChange = { _ -> onClick() },
        )
        Text(
            text = title,
            fontStyle = if (selected > -1) FontStyle.Italic else FontStyle.Normal,
            fontWeight = if (selected > -1) FontWeight.ExtraBold else FontWeight.Normal,
        )
        Spacer(modifier = Modifier.weight(1f))
        if (selected != -1) {
            Text(
                text = "#${selected + 1}",
                fontStyle = if (selected > -1) FontStyle.Italic else FontStyle.Normal,
                fontWeight = if (selected > -1) FontWeight.ExtraBold else FontWeight.Normal,
            )
        }
    }
}
