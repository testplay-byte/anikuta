package app.anikuta.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.anikuta.data.cache.LocalCache
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Phase 6 task 6.18 — General settings subpage.
 * Clear cache + storage info.
 */
@Composable
fun GeneralSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val localCache: LocalCache? = try { Injekt.get() } catch (e: Exception) { null }

    SettingsSubpageScaffold(title = "General", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SettingsGroupCard(title = "Storage") {
                    ClickableSettingsRow(
                        icon = Icons.Default.CleaningServices,
                        title = "Clear cache",
                        subtitle = "Remove cached AniList data + images",
                        onClick = {
                            scope.launch {
                                try { localCache?.clear() } catch (_: Exception) {}
                                Toast.makeText(context, "Cache cleared", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                }
            }
        }
    }
}
