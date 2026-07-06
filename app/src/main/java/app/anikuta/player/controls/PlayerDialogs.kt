package app.anikuta.player.controls

import androidx.compose.runtime.Composable
import app.anikuta.data.database.models.anime.Episode
import app.anikuta.player.Dialogs
import app.anikuta.player.controls.components.dialogs.EpisodeListDialog
import app.anikuta.player.controls.components.dialogs.IntegerPickerDialog
import java.time.format.DateTimeFormatter

@Composable
fun PlayerDialogs(
    dialogShown: Dialogs,

    // Episode list
    episodeDisplayMode: Long?,
    currentEpisodeIndex: Int,
    episodeList: List<Episode>,
    dateRelativeTime: Boolean,
    dateFormat: DateTimeFormatter,
    onBookmarkClicked: (Long?, Boolean) -> Unit,
    onFillermarkClicked: (Long?, Boolean) -> Unit,
    onEpisodeClicked: (Long?) -> Unit,

    onDismissRequest: () -> Unit,
) {
    when (dialogShown) {
        Dialogs.None -> {}
        Dialogs.EpisodeList -> {
            EpisodeListDialog(
                displayMode = episodeDisplayMode,
                currentEpisodeIndex = currentEpisodeIndex,
                episodeList = episodeList,
                dateRelativeTime = dateRelativeTime,
                dateFormat = dateFormat,
                onBookmarkClicked = onBookmarkClicked,
                onFillermarkClicked = onFillermarkClicked,
                onEpisodeClicked = onEpisodeClicked,
                onDismissRequest = onDismissRequest,
            )
        }
        is Dialogs.IntegerPicker -> {
            IntegerPickerDialog(
                defaultValue = dialogShown.defaultValue,
                minValue = dialogShown.minValue,
                maxValue = dialogShown.maxValue,
                step = dialogShown.step,
                nameFormat = dialogShown.nameFormat,
                title = dialogShown.title,
                onChange = dialogShown.onChange,
                onDismissRequest = dialogShown.onDismissRequest,
            )
        }
    }
}
