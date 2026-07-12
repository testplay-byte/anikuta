package app.anikuta.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Dedicated subtitle settings subpage — accessible from Player settings.
 *
 * This is a full-screen version of the in-player SubtitleSettingsPanel, so
 * users can configure subtitle appearance without entering the player. Changes
 * are saved to preferences and applied the next time the player starts (or
 * live if the player is already open).
 *
 * Uses a plain Column with verticalScroll (NOT LazyColumn) because
 * SubtitleSettingsPanel internally uses Modifier.verticalScroll — nesting
 * two scrollable containers (LazyColumn + verticalScroll Column) causes a
 * crash ("Vertically scrollable component was measured with an infinity
 * maximum height constraints").
 */
@Composable
fun SubtitleSettingsScreen(
    onBack: () -> Unit,
) {
    SettingsSubpageScaffold(title = "Subtitle Settings", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            app.anikuta.player.controls.SubtitleSettingsPanel(
                onSettingsChanged = {},
            )
        }
    }
}
