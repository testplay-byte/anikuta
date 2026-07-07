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
 * Phase 6 task 6.21 — Downloads settings subpage.
 * Placeholder — filled in by Section 5 (Video downloads, tasks 6.12-6.16).
 */
@Composable
fun DownloadsSettingsScreen(onBack: () -> Unit) {
    SettingsSubpageScaffold(title = "Downloads", onBack = onBack) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Download management coming in Section 5",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
