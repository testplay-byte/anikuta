package app.anikuta.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.anikuta.BuildConfig
import app.anikuta.data.cache.LocalCache
import app.anikuta.player.PlayerPreferences
import app.anikuta.ui.theme.AnikutaSprings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

private const val GITHUB_URL = "https://github.com/testplay-byte/anikuta"

/**
 * M3 Expressive Settings screen (Phase 5 task 5.14).
 *
 * Replaces the previous 14-line placeholder with a grouped settings list:
 *  1. General  → Clear cache
 *  2. Player defaults → speed slider + HW decoding switch + audio languages
 *  3. About → app/version/build info + GitHub link
 *     · Long-pressing the version text opens the hidden Debug screen.
 *  4. Coming soon → disabled AniList login / Design / Extension rows
 *
 * Top bar mirrors HomeScreen's FloatingTopBar (statusBarsPadding +
 * surfaceContainerHigh container). Each group is a RoundedCornerShape(16.dp)
 * Surface in surfaceContainerLow. List rows use a leading icon in a tinted
 * secondaryContainer circle, title, subtitle and a trailing widget.
 */
@Composable
fun MoreScreen(onOpenDebug: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Pulled via Injekt — same pattern as AnikutaMPVView.
    val playerPreferences: PlayerPreferences = remember { Injekt.get() }
    val localCache: LocalCache = remember { Injekt.get() }

    // Preference flows (collectAsState — NOT collectAsStateWithLifecycle per
    // project rule).
    val speedFlow = remember { playerPreferences.playerSpeed().stateIn(scope) }
    val speed by speedFlow.collectAsState()

    val hwFlow = remember { playerPreferences.tryHWDecoding().stateIn(scope) }
    val hwDecoding by hwFlow.collectAsState()

    val audioFlow = remember { playerPreferences.preferredAudioLanguages().stateIn(scope) }
    val audioLangs by audioFlow.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Floating top bar — same containment treatment as HomeScreen.
        item(key = "topbar") {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 3.dp,
                shadowElevation = 6.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // Group 1 — General
        item(key = "general") {
            SettingsGroupCard(title = "General") {
                ClickableSettingsRow(
                    icon = Icons.Default.CleaningServices,
                    title = "Clear cache",
                    subtitle = "Free up space used by cached anime data",
                    onClick = {
                        scope.launch {
                            runCatching { localCache.clear() }
                            Toast.makeText(
                                context,
                                "Cache cleared",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                )
            }
        }

        // Group 2 — Player defaults
        item(key = "player_defaults") {
            SettingsGroupCard(title = "Player defaults") {
                // Speed slider — header row + slider below
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LeadingIcon(Icons.Default.Speed)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Player speed",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                "Default playback speed for new videos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "%.2fx".format(speed),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = speed,
                        onValueChange = { playerPreferences.playerSpeed().set(it) },
                        valueRange = 0.25f..2.0f,
                        steps = 6, // 0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0
                    )
                }

                SettingsDivider()

                // HW decoding switch
                SwitchSettingsRow(
                    icon = Icons.Default.Memory,
                    title = "Hardware decoding",
                    subtitle = "Use HW decoder when available (recommended)",
                    checked = hwDecoding,
                    onCheckedChange = { playerPreferences.tryHWDecoding().set(it) },
                )

                SettingsDivider()

                // Preferred audio languages
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LeadingIcon(Icons.Default.RecordVoiceOver)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Preferred audio languages",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                "Comma-separated language codes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = audioLangs,
                        onValueChange = { playerPreferences.preferredAudioLanguages().set(it) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("jpn,eng") },
                        keyboardOptions = KeyboardOptions.Default,
                    )
                }
            }
        }

        // Group 3 — About
        item(key = "about") {
            SettingsGroupCard(title = "About") {
                // App identity + version (long-press on version → debug)
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LeadingIcon(Icons.Default.Info)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "ANI-KUTA",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        // Long-press the version text → hidden Debug screen.
                        // combinedClickable is the alternative to a 5-second
                        // press timer mentioned in the task spec.
                        VersionText(
                            text = "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                            onLongPress = onOpenDebug,
                        )
                        Text(
                            "arm64-v8a · debug",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }

                SettingsDivider()

                ClickableSettingsRow(
                    icon = Icons.Default.Code,
                    title = "GitHub",
                    subtitle = "github.com/testplay-byte/anikuta",
                    trailing = { ExternalLinkIcon() },
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    },
                )
            }
        }

        // Group 4 — Coming soon (disabled)
        item(key = "coming_soon") {
            SettingsGroupCard(title = "Coming soon") {
                ComingSoonRow(
                    icon = Icons.Default.Login,
                    title = "AniList login",
                )
                SettingsDivider()
                ComingSoonRow(
                    icon = Icons.Default.Palette,
                    title = "Design & theme",
                )
                SettingsDivider()
                ComingSoonRow(
                    icon = Icons.Default.Extension,
                    title = "Extension management",
                )
            }
        }
    }
}

// ---------------------------------------------------------------------
// Building blocks
// ---------------------------------------------------------------------

@Composable
private fun SettingsGroupCard(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Column {
            // Section header inside the card — small tonal accent + label
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier
                        .width(3.dp)
                        .height(16.dp),
                    shape = RoundedCornerShape(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                ) {}
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    title.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp,
                )
            }
            content()
        }
    }
}

/** A tinted circle holding the row's leading icon — M3 Expressive list style. */
@Composable
private fun LeadingIcon(icon: ImageVector) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/**
 * Clickable settings row with spring-based press feedback (AnikutaSprings.press).
 */
@Composable
private fun ClickableSettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = AnikutaSprings.press,
        label = "settings_row_scale",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
            ) { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LeadingIcon(icon)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
private fun SwitchSettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LeadingIcon(icon)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ComingSoonRow(icon: ImageVector, title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LeadingIcon(icon)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        // "Coming soon" badge — muted tonal chip
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                "Coming soon",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * Version text with a hidden long-press to enter the Debug screen.
 * Uses combinedClickable so we get a long-press gesture without a 5-second
 * delay timer. experimentalFoundationApi is required.
 *
 * Tapping once reveals a small "keep holding" hint that auto-dismisses after
 * 1.5 s — a tiny breadcrumb so the hidden route isn't totally invisible.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VersionText(text: String, onLongPress: () -> Unit) {
    var showHint by remember { mutableStateOf(false) }
    Column {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.combinedClickable(
                onClick = { showHint = true },
                onLongClick = onLongPress,
            ),
        )
        if (showHint) {
            LaunchedEffect(showHint) {
                delay(1500)
                showHint = false
            }
            Text(
                "Psst — keep holding for dev tools",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun ExternalLinkIcon() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
        contentDescription = "Open in browser",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(18.dp),
    )
}
