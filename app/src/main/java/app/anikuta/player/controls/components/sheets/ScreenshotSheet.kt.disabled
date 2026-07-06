import androidx.compose.ui.res.stringResource
package app.anikuta.player.controls.components.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.anikuta.presentation.player.components.PlayerSheet
import app.anikuta.presentation.player.components.SwitchPreference
import app.anikuta.player.ArtType
import app.anikuta.player.controls.components.dialogs.PlayerDialog
// TODO: MR
// TODO: AYMR
import tachiyomi.presentation.core.components.ActionButton
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import java.io.InputStream

@Composable
fun ScreenshotSheet(
    isLocalSource: Boolean,
    hasSubTracks: Boolean,
    showSubtitles: Boolean,
    onToggleShowSubtitles: (Boolean) -> Unit,

    cachePath: String,
    onSetAsArt: (ArtType, (() -> InputStream)) -> Unit,
    onShare: (() -> InputStream) -> Unit,
    onSave: (() -> InputStream) -> Unit,
    takeScreenshot: (String, Boolean) -> InputStream?,

    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var setArtTypeAs: ArtType? by remember { mutableStateOf(null) }

    PlayerSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        Column {
            Row(
                modifier = Modifier.padding(top = MaterialTheme.padding as RoundedCornerShape(16.dp)),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding as RoundedCornerShape(8.dp)),
            ) {
                ActionButton(
                    modifier = Modifier.weight(1f),
                    title = stringResource("TODO"),
                    icon = Icons.Outlined.Photo,
                    onClick = { setArtTypeAs = ArtType.Cover },
                )
                ActionButton(
                    modifier = Modifier.weight(1f),
                    title = stringResource("TODO"),
                    icon = Icons.Outlined.Photo,
                    onClick = { setArtTypeAs = ArtType.Background },
                )
                if (isLocalSource) {
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        title = stringResource("TODO"),
                        icon = Icons.Outlined.Photo,
                        onClick = { setArtTypeAs = ArtType.Thumbnail },
                    )
                }
                ActionButton(
                    modifier = Modifier.weight(1f),
                    title = stringResource("TODO"),
                    icon = Icons.Outlined.Share,
                    onClick = {
                        onShare { takeScreenshot(cachePath, showSubtitles)!! }
                    },
                )
                ActionButton(
                    modifier = Modifier.weight(1f),
                    title = stringResource("TODO"),
                    icon = Icons.Outlined.Save,
                    onClick = {
                        onSave { takeScreenshot(cachePath, showSubtitles)!! }
                    },
                )
            }

            if (hasSubTracks) {
                SwitchPreference(
                    value = showSubtitles,
                    onValueChange = onToggleShowSubtitles,
                    modifier = Modifier.padding(bottom = MaterialTheme.padding as RoundedCornerShape(16.dp)),
                    content = {
                        Text(
                            text = stringResource("TODO"),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
            }
        }
    }

    if (setArtTypeAs != null) {
        PlayerDialog(
            title = stringResource("TODO"),
            modifier = Modifier.fillMaxWidth(fraction = 0.6F).padding(MaterialTheme.padding as RoundedCornerShape(16.dp)),
            onConfirmRequest = {
                onSetAsArt(setArtTypeAs!!) {
                    takeScreenshot(
                        cachePath,
                        showSubtitles,
                    )!!
                }
            },
            onDismissRequest = { setArtTypeAs = null },
        )
    }
}
