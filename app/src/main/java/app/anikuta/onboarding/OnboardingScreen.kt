package app.anikuta.onboarding

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The onboarding wizard — 7 steps.
 * Required steps (3=Storage, 4=Extension, 6=Design) can't be skipped.
 */
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
) {
    var state by remember { mutableStateOf(OnboardingState()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()  // Fix: respect status bar (notification bar)
            .navigationBarsPadding()  // Respect nav bar too
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Progress indicator
        LinearProgressIndicator(
            progress = { (state.currentStep + 1).toFloat() / state.totalSteps },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        )
        Text(
            text = "Step ${state.currentStep + 1} of ${state.totalSteps}: ${state.stepNames[state.currentStep]}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                0 -> WelcomeStep()
                1 -> PermissionsStep()
                2 -> StorageStep(
                    onFolderSelected = { uri -> state = state.copy(storageFolderUri = uri) },
                    selectedUri = state.storageFolderUri,
                )
                3 -> ExtensionStep(
                    onPrimarySelected = { pkg -> state = state.copy(primaryExtensionPkg = pkg) },
                    onSecondarySelected = { pkg -> state = state.copy(secondaryExtensionPkg = pkg) },
                    primaryPkg = state.primaryExtensionPkg,
                    secondaryPkg = state.secondaryExtensionPkg,
                )
                4 -> BackupRestoreStep(
                    onBackupSelected = { uri -> state = state.copy(backupFileUri = uri) },
                    onSkip = { state = state.copy(backupFileUri = null) },
                    selectedUri = state.backupFileUri,
                )
                5 -> DesignStep(
                    onDesignSelected = { design -> state = state.copy(selectedDesign = design) },
                    selectedDesign = state.selectedDesign,
                )
                6 -> AllSetStep()
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Navigation buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (state.currentStep > 0) {
                TextButton(onClick = { state = state.previous() }) {
                    Text("Back")
                }
            } else {
                Spacer(modifier = Modifier.width(64.dp))
            }

            Button(
                onClick = {
                    if (state.currentStep == state.totalSteps - 1) {
                        onComplete()
                    } else {
                        state = state.next()
                    }
                },
                enabled = state.canProceed(),
            ) {
                Text(if (state.currentStep == state.totalSteps - 1) "Start Watching" else "Next")
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("ANI-KUTA", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Your anime, your way.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(24.dp))
        Text("Let's get you set up. This takes about a minute.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PermissionsStep() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Permissions", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text("ANI-KUTA needs a few permissions to work properly:", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        PermissionItem("Notifications", "For download + schedule updates")
        PermissionItem("Storage", "For downloaded episodes")
        Spacer(modifier = Modifier.height(16.dp))
        Text("You can grant these later in Settings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PermissionItem(name: String, desc: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(12.dp))
        Column { Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium); Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun StorageStep(onFolderSelected: (String) -> Unit, selectedUri: String?) {
    // Auto-select default folder on first display
    LaunchedEffect(Unit) {
        if (selectedUri == null) {
            onFolderSelected("default://Android/data/app.anikuta/files/")
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Storage Folder", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Pick where ANI-KUTA stores downloads and cache.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = {
            // TODO (later): use SAF document picker for custom folder
            onFolderSelected("default://Android/data/app.anikuta/files/")
        }) {
            Text(if (selectedUri != null) "Folder Selected ✓" else "Select Folder")
        }
        if (selectedUri != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Default: Android/data/app.anikuta/files/", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ExtensionStep(onPrimarySelected: (String) -> Unit, onSecondarySelected: (String) -> Unit, primaryPkg: String?, secondaryPkg: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Select Extension", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Extensions are how ANI-KUTA finds anime streams. Pick one to get started.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(24.dp))
        // Recommended extension
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), onClick = { onPrimarySelected("eu.kanade.tachiyomi.animeextension.en.anikoto180") }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("AniKoto 180 (Recommended)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Default anime source", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (primaryPkg == "eu.kanade.tachiyomi.animeextension.en.anikoto180") {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("✓ Selected as primary", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("You can add more extensions later in Settings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BackupRestoreStep(
    onBackupSelected: (String) -> Unit,
    onSkip: () -> Unit,
    selectedUri: String?,
) {
    val context = LocalContext.current

    // SAF file picker for backup files
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            onBackupSelected(uri.toString())
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Backup Restore", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Have a backup file from a previous install?", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = {
                // Open the system file picker for backup files
                filePicker.launch(arrayOf("*/*"))
            }) {
                Text("Select Backup")
            }
            TextButton(onClick = { onSkip() }) {
                Text("Skip — fresh start")
            }
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
private fun DesignStep(onDesignSelected: (String) -> Unit, selectedDesign: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Pick a Design", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Choose how ANI-KUTA looks. You can change this later.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(24.dp))
        DesignCard("Material 3", "Clean, standard, Material You", "material3", selectedDesign, onDesignSelected)
        DesignCard("Dark Neon", "Coming soon", "neon", selectedDesign, onDesignSelected, enabled = false)
        DesignCard("Neobrutalism", "Coming soon", "neobrutalism", selectedDesign, onDesignSelected, enabled = false)
        DesignCard("Coffee Notebook", "Coming soon", "coffee", selectedDesign, onDesignSelected, enabled = false)
    }
}

@Composable
private fun DesignCard(name: String, desc: String, id: String, selected: String, onSelect: (String) -> Unit, enabled: Boolean = true) {
    val isSelected = selected == id
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        onClick = if (enabled) ({ onSelect(id) }) else ({ }),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
        enabled = enabled,
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isSelected) Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun AllSetStep() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text("You're all set!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Quick tips:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        Text("• Browse anime on the home page\n• Search by name or genre\n• Settings in the More tab", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}
