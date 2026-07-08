package app.anikuta.ui.settings

import android.graphics.drawable.Drawable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import app.anikuta.extension.anime.TrustResult
import app.anikuta.extension.anime.model.AnimeExtension
import coil3.compose.AsyncImage

/**
 * Phase 7 — Extensions settings screen with 3 sections:
 *
 *  1. **Sources** — trusted installed extensions (max 2). These are the only
 *     extensions used for search/resolve/play. Tap to open extension details.
 *  2. **Installed** — installed-but-untrusted extensions. Trust → moves to
 *     Sources. Delete → uninstalls.
 *  3. **Available** — extensions from repos. Install → downloads APK, installs
 *     as untrusted (appears in Installed).
 *
 * Also has a "Manage repositories" button to add/remove extension repos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsSettingsScreen(
    onBack: () -> Unit,
    onManageRepos: () -> Unit,
    onOpenExtensionDetails: (String) -> Unit = {},
) {
    val viewModel: ExtensionsViewModel = viewModel()
    val sources by viewModel.sources.collectAsState()
    val installed by viewModel.installed.collectAsState()
    val available by viewModel.available.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val downloading by viewModel.downloading.collectAsState()
    val trustResult by viewModel.trustResult.collectAsState()

    // Max-2-trusted popup
    trustResult?.let { result ->
        if (result is TrustResult.LimitExceeded) {
            MaxTrustedSourcesDialog(
                currentTrusted = result.currentTrusted,
                onDismiss = { viewModel.dismissTrustResult() },
                onRevokeAndTrust = { pkgToRevoke ->
                    viewModel.revokeTrust(
                        sources.find { it.pkgName == pkgToRevoke } ?: return@MaxTrustedSourcesDialog
                    )
                    // After revoking, the user can try trusting the new one again
                    viewModel.dismissTrustResult()
                },
            )
        }
    }

    SettingsSubpageScaffold(
        title = "Extensions",
        onBack = onBack,
        actions = {
            IconButton(onClick = onManageRepos) {
                Icon(
                    imageVector = Icons.Outlined.Public,
                    contentDescription = "Manage repositories",
                )
            }
        },
    ) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ----- Sources section (trusted, max 2) -----
                item(key = "sources_section") {
                    SettingsGroupCard(title = "Sources · ${sources.size}/2") {
                        if (sources.isEmpty()) {
                            EmptySectionBody(
                                text = "No trusted sources. Install an extension, then tap Trust to add it here.",
                            )
                        } else {
                            Column {
                                sources.forEachIndexed { idx, ext ->
                                    SourceExtensionRow(
                                        ext = ext,
                                        onClick = { onOpenExtensionDetails(ext.pkgName) },
                                        onUntrust = { viewModel.revokeTrust(ext) },
                                    )
                                    if (idx < sources.size - 1) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                }
                            }
                        }
                    }
                }

                // ----- Installed section (untrusted) -----
                item(key = "installed_section") {
                    SettingsGroupCard(title = "Installed · ${installed.size}") {
                        if (installed.isEmpty()) {
                            EmptySectionBody(
                                text = "No untrusted extensions installed.",
                            )
                        } else {
                            Column {
                                installed.forEachIndexed { idx, ext ->
                                    UntrustedExtensionRow(
                                        ext = ext,
                                        onTrust = { viewModel.trustExtension(ext) },
                                        onDelete = { viewModel.uninstallExtension(ext.pkgName) },
                                    )
                                    if (idx < installed.size - 1) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                }
                            }
                        }
                    }
                }

                // ----- Available section (from repos) -----
                item(key = "available_section") {
                    SettingsGroupCard(title = "Available · ${available.size}") {
                        when {
                            isLoading && available.isEmpty() -> LoadingBody()
                            available.isEmpty() -> EmptySectionBody(
                                text = "No extensions found. Tap the globe icon above to add a repository.",
                            )
                            else -> {
                                Column {
                                    available.forEachIndexed { idx, ext ->
                                        val isInstalled = sources.any { it.pkgName == ext.pkgName } ||
                                            installed.any { it.pkgName == ext.pkgName }
                                        AvailableExtensionRow(
                                            ext = ext,
                                            isInstalled = isInstalled,
                                            isDownloading = ext.pkgName in downloading,
                                            onInstall = { viewModel.installExtension(ext) },
                                        )
                                        if (idx < available.size - 1) {
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Max-2-trusted popup                                                 */
/* ------------------------------------------------------------------ */

@Composable
private fun MaxTrustedSourcesDialog(
    currentTrusted: Set<String>,
    onDismiss: () -> Unit,
    onRevokeAndTrust: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sources limit reached") },
        text = {
            Column {
                Text(
                    "You can only have 2 extensions in Sources. Remove one to continue:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                currentTrusted.forEach { pkgName ->
                    val displayName = pkgName.substringAfterLast('.')
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRevokeAndTrust(pkgName) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Extension,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onRevokeAndTrust(pkgName) }) {
                            Text("Remove")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/* ------------------------------------------------------------------ */
/* Rows                                                                */
/* ------------------------------------------------------------------ */

@Composable
private fun SourceExtensionRow(
    ext: AnimeExtension.Installed,
    onClick: () -> Unit,
    onUntrust: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExtensionIconSlot(drawable = ext.icon, iconUrl = null, contentDescription = ext.name)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ext.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ExtensionMetaRow(ext.versionName, ext.sources.size, ext.lang)
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onUntrust) {
            Icon(
                imageVector = Icons.Default.VerifiedUser,
                contentDescription = "Remove from Sources",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun UntrustedExtensionRow(
    ext: AnimeExtension.Untrusted,
    onTrust: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExtensionIconSlot(drawable = ext.icon, iconUrl = null, contentDescription = ext.name)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ext.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ExtensionMetaRow(ext.versionName, 0, ext.lang)
        }
        Spacer(Modifier.width(8.dp))
        // Compact Trust button (IconButton with shield icon)
        IconButton(onClick = onTrust) {
            Icon(
                imageVector = Icons.Default.VerifiedUser,
                contentDescription = "Trust ${ext.name}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete ${ext.name}",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun AvailableExtensionRow(
    ext: AnimeExtension.Available,
    isInstalled: Boolean,
    isDownloading: Boolean,
    onInstall: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExtensionIconSlot(drawable = null, iconUrl = ext.iconUrl, contentDescription = ext.name)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ext.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ExtensionMetaRow(ext.versionName, ext.sources.size, ext.lang)
        }
        Spacer(Modifier.width(8.dp))
        when {
            isInstalled -> InstalledBadge()
            isDownloading -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
            else -> OutlinedButton(onClick = onInstall) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("Install")
            }
        }
    }
}

@Composable
private fun ExtensionMetaRow(versionName: String, sourceCount: Int, lang: String?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "v$versionName",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (sourceCount > 0) {
            Text(
                text = "· $sourceCount source${if (sourceCount > 1) "s" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        lang?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = "· ${langLabel(it)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExtensionIconSlot(
    drawable: Drawable?,
    iconUrl: String?,
    contentDescription: String,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            val painter = drawable?.let { remember(it) { it.toPainterOrNull() } }
            when {
                painter != null -> androidx.compose.foundation.Image(
                    painter = painter,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(28.dp),
                )
                iconUrl != null -> AsyncImage(
                    model = iconUrl,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(28.dp),
                )
                else -> Icon(
                    imageVector = Icons.Default.Extension,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun InstalledBadge() {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            text = "Installed",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/* ------------------------------------------------------------------ */
/* Section bodies                                                      */
/* ------------------------------------------------------------------ */

@Composable
private fun EmptySectionBody(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Extension,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LoadingBody() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
            Text(
                text = "Loading extensions from repo…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* Helpers                                                             */
/* ------------------------------------------------------------------ */

private fun Drawable.toPainterOrNull(): Painter? = try {
    BitmapPainter(this.toBitmap().asImageBitmap())
} catch (e: Exception) {
    null
}

private fun langLabel(code: String): String = when (code.lowercase()) {
    "en" -> "EN"
    "ja" -> "JP"
    "zh" -> "ZH"
    "ko" -> "KO"
    "es" -> "ES"
    "fr" -> "FR"
    "de" -> "DE"
    "pt" -> "PT"
    "pt-br" -> "PT-BR"
    "it" -> "IT"
    "ru" -> "RU"
    "ar" -> "AR"
    "id" -> "ID"
    else -> code.uppercase()
}
