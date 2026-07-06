package app.anikuta.onboarding

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.anikuta.ui.theme.AnikutaSprings

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
) {
    var state by remember { mutableStateOf(OnboardingState()) }

    // M3 Expressive: full-screen gradient background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.surface,
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Expressive progress indicator — rounded, spring-animated
            ExpressiveProgressBar(
                current = state.currentStep,
                total = state.totalSteps,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Step ${state.currentStep + 1} of ${state.totalSteps}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Step content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center,
            ) {
                when (state.currentStep) {
                    0 -> ExpressiveWelcomeStep()
                    1 -> ExpressivePermissionsStep()
                    2 -> ExpressiveStorageStep(
                        onFolderSelected = { uri -> state = state.copy(storageFolderUri = uri) },
                        selectedUri = state.storageFolderUri,
                    )
                    3 -> ExpressiveExtensionStep(
                        onPrimarySelected = { pkg -> state = state.copy(primaryExtensionPkg = pkg) },
                        primaryPkg = state.primaryExtensionPkg,
                    )
                    4 -> ExpressiveBackupStep(
                        onBackupSelected = { uri -> state = state.copy(backupFileUri = uri) },
                        onSkip = { state = state.copy(backupFileUri = null) },
                        selectedUri = state.backupFileUri,
                    )
                    5 -> ExpressiveDesignStep(
                        onDesignSelected = { design -> state = state.copy(selectedDesign = design) },
                        selectedDesign = state.selectedDesign,
                    )
                    6 -> ExpressiveAllSetStep()
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Expressive navigation buttons — spring press feedback
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (state.currentStep > 0) {
                    ExpressiveTextButton(
                        text = "Back",
                        onClick = { state = state.previous() },
                    )
                } else {
                    Spacer(modifier = Modifier.width(64.dp))
                }

                ExpressiveButton(
                    text = if (state.currentStep == state.totalSteps - 1) "Start Watching" else "Next",
                    onClick = {
                        if (state.currentStep == state.totalSteps - 1) onComplete()
                        else state = state.next()
                    },
                    enabled = state.canProceed(),
                )
            }
        }
    }
}

@Composable
private fun ExpressiveProgressBar(current: Int, total: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(total) { index ->
            val isActive = index <= current
            val animatedWeight by animateFloatAsState(
                targetValue = if (index == current) 2f else 1f,
                animationSpec = AnikutaSprings.effects,
                label = "progress_$index",
            )
            Surface(
                modifier = Modifier.weight(animatedWeight).height(6.dp),
                shape = RoundedCornerShape(3.dp),
                color = if (isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {}
        }
    }
}

@Composable
private fun ExpressiveButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = AnikutaSprings.press,
        label = "btn_scale",
    )

    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = Modifier.scale(scale),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ExpressiveTextButton(text: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = AnikutaSprings.press,
        label = "text_btn_scale",
    )
    TextButton(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier.scale(scale),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ExpressiveWelcomeStep() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Expressive icon in a large tonal container
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 2.dp,
            modifier = Modifier.size(96.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "ANI-KUTA",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Your anime, your way.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Let's get you set up. This takes about a minute.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ExpressivePermissionsStep() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Permissions", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "ANI-KUTA needs a few permissions to work properly:",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(20.dp))
        // Expressive permission cards — contained surfaces
        PermissionCard("Notifications", "For download + schedule updates", Icons.Default.Notifications)
        Spacer(modifier = Modifier.height(8.dp))
        PermissionCard("Storage", "For downloaded episodes", Icons.Default.Storage)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "You can grant these later in Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PermissionCard(name: String, desc: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ExpressiveStorageStep(onFolderSelected: (String) -> Unit, selectedUri: String?) {
    LaunchedEffect(Unit) {
        if (selectedUri == null) {
            onFolderSelected("default://Android/data/app.anikuta/files/")
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Storage Folder", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Pick where ANI-KUTA stores downloads and cache.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        // Expressive folder card
        Surface(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            color = if (selectedUri != null) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 1.dp,
            onClick = { onFolderSelected("default://Android/data/app.anikuta/files/") },
        ) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = if (selectedUri != null) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        if (selectedUri != null) "Folder Selected" else "Select Folder",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Default: Android/data/app.anikuta/files/",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (selectedUri != null) {
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun ExpressiveExtensionStep(onPrimarySelected: (String) -> Unit, primaryPkg: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Select Extension", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Extensions are how ANI-KUTA finds anime streams.\nPick one to get started.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        val isSelected = primaryPkg == "eu.kanade.tachiyomi.animeextension.en.anikoto180"
        Surface(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 1.dp,
            onClick = { onPrimarySelected("eu.kanade.tachiyomi.animeextension.en.anikoto180") },
        ) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Extension, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("AniKoto 180", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Recommended · Default anime source", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (isSelected) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "You can add more extensions later in Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ExpressiveBackupStep(
    onBackupSelected: (String) -> Unit,
    onSkip: () -> Unit,
    selectedUri: String?,
) {
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) onBackupSelected(uri.toString())
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Backup Restore", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Have a backup file from a previous install?", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { filePicker.launch(arrayOf("*/*")) },
                shape = RoundedCornerShape(16.dp),
            ) { Text("Select Backup") }
            TextButton(onClick = { onSkip() }) { Text("Skip — fresh start") }
        }
        if (selectedUri != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Backup selected: $selectedUri", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        } else {
            Spacer(modifier = Modifier.height(8.dp))
            Text("No backup selected — fresh start", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ExpressiveDesignStep(onDesignSelected: (String) -> Unit, selectedDesign: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Pick a Design", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Choose how ANI-KUTA looks. You can change this later.", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(24.dp))
        // Expressive design cards — large, contained, with icons
        ExpressiveDesignCard("Material 3", "Clean, standard, Material You", "material3", selectedDesign, onDesignSelected, Icons.Default.Palette)
        ExpressiveDesignCard("Dark Neon", "Coming soon", "neon", selectedDesign, onDesignSelected, Icons.Default.Bolt, enabled = false)
        ExpressiveDesignCard("Neobrutalism", "Coming soon", "neobrutalism", selectedDesign, onDesignSelected, Icons.Default.Brush, enabled = false)
        ExpressiveDesignCard("Coffee Notebook", "Coming soon", "coffee", selectedDesign, onDesignSelected, Icons.Default.Coffee, enabled = false)
    }
}

@Composable
private fun ExpressiveDesignCard(
    name: String,
    desc: String,
    id: String,
    selected: String,
    onSelect: (String) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true,
) {
    val isSelected = selected == id
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = if (isSelected) 2.dp else 1.dp,
        onClick = if (enabled) ({ onSelect(id) }) else ({}),
        enabled = enabled,
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isSelected) Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ExpressiveAllSetStep() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Expressive success icon — large, tonal container
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 2.dp,
            modifier = Modifier.size(96.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("You're all set!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        // Expressive tip cards
        TipCard("Browse anime on the home page", Icons.Default.Home)
        Spacer(modifier = Modifier.height(8.dp))
        TipCard("Search by name or genre", Icons.Default.Search)
        Spacer(modifier = Modifier.height(8.dp))
        TipCard("Settings in the More tab", Icons.Default.Settings)
    }
}

@Composable
private fun TipCard(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
