package app.anikuta.ui.settings

import android.graphics.drawable.Drawable
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import app.anikuta.extension.anime.model.AnimeExtension
import coil3.compose.AsyncImage

/**
 * Phase 6 tasks 6.1-6.6 — Extensions settings subpage.
 *
 * Lists installed + available anime extensions, with install / uninstall
 * actions backed by [ExtensionsViewModel]. Install downloads the APK from the
 * repo and hands it to the system package installer via FileProvider; the
 * `REQUEST_INSTALL_PACKAGES` permission is declared in the manifest.
 *
 * Pull-to-refresh re-fetches the available list and re-scans installed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsSettingsScreen(onBack: () -> Unit) {
    val viewModel: ExtensionsViewModel = viewModel()
    val installed by viewModel.installed.collectAsState()
    val available by viewModel.available.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val downloading by viewModel.downloading.collectAsState()

    SettingsSubpageScaffold(title = "Extensions", onBack = onBack) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ----- Installed section -----
                item(key = "installed_section") {
                    SettingsGroupCard(title = "Installed · ${installed.size}") {
                        if (installed.isEmpty()) {
                            EmptySectionBody(
                                text = "No extensions installed yet. Pick one from Available below to add a streaming source.",
                            )
                        } else {
                            Column {
                                installed.forEachIndexed { idx, ext ->
                                    InstalledExtensionRow(
                                        ext = ext,
                                        onUninstall = { viewModel.uninstallExtension(ext) },
                                    )
                                    if (idx < installed.size - 1) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                }
                            }
                        }
                    }
                }

                // ----- Available section -----
                item(key = "available_section") {
                    SettingsGroupCard(title = "Available · ${available.size}") {
                        when {
                            isLoading && available.isEmpty() -> LoadingBody()
                            available.isEmpty() -> EmptySectionBody(
                                text = "No extensions found in the repo. Pull down to refresh.",
                            )
                            else -> Column {
                                available.forEachIndexed { idx, ext ->
                                    val isInstalled = installed.any { it.pkgName == ext.pkgName }
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

/* ------------------------------------------------------------------ */
/* Rows                                                                */
/* ------------------------------------------------------------------ */

@Composable
private fun InstalledExtensionRow(
    ext: AnimeExtension.Installed,
    onUninstall: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExtensionIconSlot(
            drawable = ext.icon,
            iconUrl = null,
            contentDescription = ext.name,
        )
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
        IconButton(onClick = onUninstall) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Uninstall ${ext.name}",
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
        ExtensionIconSlot(
            drawable = null,
            iconUrl = ext.iconUrl,
            contentDescription = ext.name,
        )
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

/**
 * Tiny language-code → display label helper. We don't ship a full locale
 * table; this expands the most common aniyomi extension langs and falls back
 * to uppercased code for the rest.
 */
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
