package app.anikuta.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Dedicated subtitle settings subpage — accessible from Player settings.
 *
 * This is a full-screen version of the in-player SubtitleSettingsPanel, so
 * users can configure subtitle appearance without entering the player. Changes
 * are saved to preferences and applied the next time the player starts (or
 * live if the player is already open).
 *
 * The panel itself is the same [SubtitleSettingsPanel] used in the player's
 * bottom sheet, so the UX is consistent between the two entry points.
 */
@Composable
fun SubtitleSettingsScreen(
    onBack: () -> Unit,
) {
    SettingsSubpageScaffold(title = "Subtitle Settings", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                // The same panel used in the player's bottom sheet — no
                // onSettingsChanged callback needed here because the player
                // reads preferences on init. Changes will apply on next player
                // open (or live if the player is already running).
                app.anikuta.player.controls.SubtitleSettingsPanel(
                    onSettingsChanged = {},
                )
            }
        }
    }
}
