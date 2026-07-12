package app.anikuta.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Dedicated subtitle settings subpage — accessible from Player settings.
 *
 * This is a full-screen version of the in-player SubtitleSettingsPanel, so
 * users can configure subtitle appearance without entering the player.
 *
 * NOTE: SubtitleSettingsPanel already has its own internal verticalScroll.
 * We must NOT wrap it in another scrollable container (Column.verticalScroll
 * or LazyColumn) — that causes "Vertically scrollable component was measured
 * with an infinity maximum height constraints" crash. A plain Column with
 * fillMaxSize is sufficient; the panel handles its own scrolling.
 */
@Composable
fun SubtitleSettingsScreen(
    onBack: () -> Unit,
) {
    SettingsSubpageScaffold(title = "Subtitle Settings", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            app.anikuta.player.controls.SubtitleSettingsPanel(
                onSettingsChanged = {},
            )
        }
    }
}
