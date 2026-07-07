package app.anikuta.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Phase 6 task 6.22 — Tracking settings subpage.
 * Placeholder — filled in by Section 4 (AniList tracking, tasks 6.7-6.11).
 */
@Composable
fun TrackingSettingsScreen(onBack: () -> Unit) {
    SettingsSubpageScaffold(title = "Tracking", onBack = onBack) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "AniList tracking coming in Section 4",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
