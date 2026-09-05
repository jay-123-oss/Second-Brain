package com.example.brain.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.brain.ui.home.SectionHeader
import com.example.brain.ui.theme.EmeraldSuccess
import com.example.brain.ui.theme.IndigoPrimary
import com.example.brain.ui.theme.PurpleVault
import com.example.brain.util.SecurityManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToBackupRecovery: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    var showPinDialog by remember { mutableStateOf(false) }
    var showDuressDialog by remember { mutableStateOf(false) }
    var showExitDecoyDialog by remember { mutableStateOf(false) }
    var showTimeoutMenu by remember { mutableStateOf(false) }
    var showResetAIDialog by remember { mutableStateOf(false) }
    var showThemeMenu by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportBackup(it) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importBackup(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Operation feedback banner
            uiState.operationMessage?.let { msg ->
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.clearMessage() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // 1. General Settings
            // -------------------------------------------------------------
            item {
                SectionHeader(title = "General", icon = Icons.Default.Tune)
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        // Appearance / Theme
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showThemeMenu = true }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Appearance", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text("Theme: ${uiState.themeMode}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.Palette, contentDescription = "Appearance", tint = IndigoPrimary)
                        }

                        DropdownMenu(
                            expanded = showThemeMenu,
                            onDismissRequest = { showThemeMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("System Default") },
                                onClick = { viewModel.setThemeMode("SYSTEM"); showThemeMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Light Theme") },
                                onClick = { viewModel.setThemeMode("LIGHT"); showThemeMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Dark Theme") },
                                onClick = { viewModel.setThemeMode("DARK"); showThemeMenu = false }
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        // Haptic Feedback
                        SettingsSwitchRow(
                            title = "Haptic Feedback",
                            subtitle = "Vibrate on capture and key interactions",
                            icon = Icons.Default.Vibration,
                            iconTint = IndigoPrimary,
                            checked = uiState.hapticFeedbackEnabled,
                            onCheckedChange = { viewModel.toggleHapticFeedback(it) }
                        )
                    }
                }
            }

            // -------------------------------------------------------------
            // 2. Capture Settings
            // -------------------------------------------------------------
            item {
                SectionHeader(title = "Capture", icon = Icons.Default.AddCircleOutline)
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        SettingsSwitchRow(
                            title = "Background Enrichment",
                            subtitle = "Automatically extract web titles, descriptions, and text",
                            icon = Icons.Default.CloudSync,
                            iconTint = Color(0xFF0284C7),
                            checked = uiState.autoEnrichMetadata,
                            onCheckedChange = { viewModel.toggleAutoEnrichMetadata(it) }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsSwitchRow(
                            title = "Quick Note Auto-Save",
                            subtitle = "Save notes automatically when navigating away",
                            icon = Icons.Default.Save,
                            iconTint = Color(0xFF0284C7),
                            checked = uiState.autoSaveQuickNotes,
                            onCheckedChange = { viewModel.toggleAutoSaveQuickNotes(it) }
                        )
                    }
                }
            }

            // -------------------------------------------------------------
            // 3. Search Settings
            // -------------------------------------------------------------
            item {
                SectionHeader(title = "Search", icon = Icons.Default.Search)
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        SettingsSwitchRow(
                            title = "Search History",
                            subtitle = "Remember recent queries for instant suggestions",
                            icon = Icons.Default.History,
                            iconTint = Color(0xFF0D9488),
                            checked = uiState.searchHistoryEnabled,
                            onCheckedChange = { viewModel.toggleSearchHistory(it) }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsActionRow(
                            title = "Clear Search History",
                            subtitle = "Erase all saved recent search queries",
                            icon = Icons.Default.DeleteOutline,
                            iconTint = Color(0xFF0D9488),
                            onClick = { viewModel.clearSearchHistory() }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsActionRow(
                            title = "Rebuild Full-Text Index",
                            subtitle = if (uiState.isRebuildingIndex) "Indexing active items..." else "Re-index SQLite FTS5 for fast search",
                            icon = Icons.Default.Refresh,
                            iconTint = Color(0xFF0D9488),
                            enabled = !uiState.isRebuildingIndex,
                            onClick = { viewModel.rebuildSearchIndex() }
                        )
                    }
                }
            }

            // -------------------------------------------------------------
            // 4. Intelligence & Local AI
            // -------------------------------------------------------------
            item {
                SectionHeader(title = "Local Intelligence", icon = Icons.Default.Psychology)
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        // Privacy statement
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = IndigoPrimary.copy(alpha = 0.08f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = "Local intelligence processes supported content on this device. No knowledge leaves your phone.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }

                        SettingsSwitchRow(
                            title = "Local AI & Vector Embeddings",
                            subtitle = "Enable on-device semantic understanding (${uiState.embeddingsCount} vectors cached)",
                            icon = Icons.Default.Psychology,
                            iconTint = IndigoPrimary,
                            checked = uiState.isLocalAIEnabled,
                            onCheckedChange = { viewModel.toggleLocalAI(it) }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsSwitchRow(
                            title = "Semantic Search",
                            subtitle = "Search by meaning alongside exact keyword matching",
                            icon = Icons.Default.Search,
                            iconTint = IndigoPrimary,
                            checked = uiState.isSemanticSearchEnabled,
                            enabled = uiState.isLocalAIEnabled,
                            onCheckedChange = { viewModel.toggleSemanticSearch(it) }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsSwitchRow(
                            title = "Auto-Summarize Long Content",
                            subtitle = "Generate extractive summaries for articles and long notes",
                            icon = Icons.Default.Summarize,
                            iconTint = IndigoPrimary,
                            checked = uiState.isAutoSummarizeEnabled,
                            enabled = uiState.isLocalAIEnabled,
                            onCheckedChange = { viewModel.toggleAutoSummarize(it) }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsActionRow(
                            title = "Rebuild Semantic Index",
                            subtitle = if (uiState.isRebuildingEmbeddings) "Computing vectors..." else "Regenerate all embeddings with current model",
                            icon = Icons.Default.Sync,
                            iconTint = IndigoPrimary,
                            enabled = uiState.isLocalAIEnabled && !uiState.isRebuildingEmbeddings,
                            onClick = { viewModel.rebuildSemanticIndex() }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsActionRow(
                            title = "Reset Local Intelligence",
                            subtitle = "Clear summaries, vectors, and chat history (keeps notes & projects)",
                            icon = Icons.Default.CleaningServices,
                            iconTint = MaterialTheme.colorScheme.error,
                            onClick = { showResetAIDialog = true }
                        )
                    }
                }
            }

            // -------------------------------------------------------------
            // 5. Security & Vault
            // -------------------------------------------------------------
            item {
                SectionHeader(title = "Security & Private Vault", icon = Icons.Default.Security)
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        SettingsSwitchRow(
                            title = "App Lock",
                            subtitle = "Require authentication when opening Second Brain",
                            icon = Icons.Default.Lock,
                            iconTint = PurpleVault,
                            checked = uiState.isAppLockEnabled,
                            onCheckedChange = { viewModel.toggleAppLock(it) }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsSwitchRow(
                            title = "Biometric Authentication",
                            subtitle = "Use fingerprint or face recognition for quick unlock",
                            icon = Icons.Default.Fingerprint,
                            iconTint = PurpleVault,
                            checked = uiState.isBiometricEnabled,
                            enabled = uiState.isAppLockEnabled,
                            onCheckedChange = { viewModel.toggleBiometric(it) }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        // Auto-lock Timeout selector
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = uiState.isAppLockEnabled) { showTimeoutMenu = true }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-Lock Timeout", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                val timeoutLabel = when (uiState.autoLockTimeoutMs) {
                                    SecurityManager.TIMEOUT_IMMEDIATELY -> "Immediately on background"
                                    SecurityManager.TIMEOUT_30_SEC -> "30 seconds"
                                    SecurityManager.TIMEOUT_1_MIN -> "1 minute"
                                    SecurityManager.TIMEOUT_5_MIN -> "5 minutes"
                                    else -> "1 minute"
                                }
                                Text(timeoutLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.Timer, contentDescription = "Timeout", tint = PurpleVault)
                        }

                        DropdownMenu(
                            expanded = showTimeoutMenu,
                            onDismissRequest = { showTimeoutMenu = false }
                        ) {
                            DropdownMenuItem(text = { Text("Immediately") }, onClick = { viewModel.setAutoLockTimeout(SecurityManager.TIMEOUT_IMMEDIATELY); showTimeoutMenu = false })
                            DropdownMenuItem(text = { Text("30 seconds") }, onClick = { viewModel.setAutoLockTimeout(SecurityManager.TIMEOUT_30_SEC); showTimeoutMenu = false })
                            DropdownMenuItem(text = { Text("1 minute") }, onClick = { viewModel.setAutoLockTimeout(SecurityManager.TIMEOUT_1_MIN); showTimeoutMenu = false })
                            DropdownMenuItem(text = { Text("5 minutes") }, onClick = { viewModel.setAutoLockTimeout(SecurityManager.TIMEOUT_5_MIN); showTimeoutMenu = false })
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsActionRow(
                            title = if (uiState.isPinSet) "Change Master PIN" else "Set Master PIN",
                            subtitle = if (uiState.isPinSet) "Configured (Hardware encrypted)" else "Not configured",
                            icon = Icons.Default.Pin,
                            iconTint = PurpleVault,
                            onClick = { showPinDialog = true }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsActionRow(
                            title = if (uiState.hasDuressPin) "Duress Decoy PIN" else "Configure Duress PIN",
                            subtitle = if (uiState.hasDuressPin) "Active (Unlocks harmless empty decoy)" else "Not set",
                            icon = Icons.Default.Shield,
                            iconTint = PurpleVault,
                            onClick = { showDuressDialog = true }
                        )

                        if (uiState.isDecoyMode) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            SettingsActionRow(
                                title = "Exit Decoy Mode",
                                subtitle = "Restore hidden Vault using Master PIN",
                                icon = Icons.Default.Visibility,
                                iconTint = EmeraldSuccess,
                                onClick = { showExitDecoyDialog = true }
                            )
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // 6. Storage & Cleanup (Real values)
            // -------------------------------------------------------------
            item {
                SectionHeader(title = "Storage Breakdown", icon = Icons.Default.Storage)
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        StorageRow(label = "Knowledge Database", value = uiState.storageMetrics.databaseFormatted)
                        StorageRow(label = "Media & Attachments", value = uiState.storageMetrics.mediaFilesFormatted)
                        StorageRow(label = "Thumbnails & Web Cache", value = uiState.storageMetrics.cacheFormatted)
                        StorageRow(label = "Total Application Storage", value = uiState.storageMetrics.totalAppFormatted, isBold = true)

                        Spacer(modifier = Modifier.height(4.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.clearThumbnailCache() },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Clear Cache", style = MaterialTheme.typography.bodySmall)
                            }

                            OutlinedButton(
                                onClick = { viewModel.clearTemporaryFiles() },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Clean Temp Files", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // 7. Backup & Recovery
            // -------------------------------------------------------------
            item {
                SectionHeader(title = "Backup & Recovery", icon = Icons.Default.Backup)
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        SettingsActionRow(
                            title = "Full Backup Subsystem",
                            subtitle = "Encrypted archives, snapshots, and SHA-256 verification",
                            icon = Icons.Default.Backup,
                            iconTint = EmeraldSuccess,
                            onClick = onNavigateToBackupRecovery
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsActionRow(
                            title = "Quick Export (JSON)",
                            subtitle = "Export normal knowledge items to local file",
                            icon = Icons.Default.FileDownload,
                            iconTint = EmeraldSuccess,
                            onClick = { exportLauncher.launch("second_brain_export.json") }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsActionRow(
                            title = "Quick Import (JSON)",
                            subtitle = "Import items from a JSON backup file",
                            icon = Icons.Default.FileUpload,
                            iconTint = EmeraldSuccess,
                            onClick = { importLauncher.launch(arrayOf("application/json")) }
                        )
                    }
                }
            }

            // -------------------------------------------------------------
            // 8. About & Diagnostics
            // -------------------------------------------------------------
            item {
                SectionHeader(title = "About", icon = Icons.Default.Info)
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(shape = RoundedCornerShape(8.dp), color = IndigoPrimary, modifier = Modifier.size(36.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Psychology, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                                }
                            }
                            Column {
                                Text("Second Brain", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Version 1.0.0 (Production Hardened)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Architecture: 100% Local-First Native Android. Zero cloud AI APIs. Zero remote server dependencies. AES-256 Vault encryption. Offline SQLite FTS5 search.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        TextButton(
                            onClick = { viewModel.showIntroductionAgain() },
                            modifier = Modifier.align(Alignment.Start)
                        ) {
                            Text("Show Introduction Flow Again", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    // -------------------------------------------------------------
    // Dialogs
    // -------------------------------------------------------------

    if (showResetAIDialog) {
        AlertDialog(
            onDismissRequest = { showResetAIDialog = false },
            title = { Text("Reset Local Intelligence?") },
            text = { Text("This will delete all AI-generated summaries, semantic vectors, and Ask Your Brain conversation history. Your saved knowledge, notes, tags, projects, and Vault data will NOT be deleted.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetLocalIntelligence()
                        showResetAIDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reset Intelligence")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetAIDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showPinDialog) {
        PinInputDialog(
            title = if (uiState.isPinSet) "Change Master PIN" else "Set Master PIN",
            onConfirm = { pin ->
                viewModel.setMasterPin(pin)
                showPinDialog = false
            },
            onDismiss = { showPinDialog = false }
        )
    }

    if (showDuressDialog) {
        DuressPinDialog(
            hasDuressPin = uiState.hasDuressPin,
            onSetDuress = { pin ->
                viewModel.setDuressPin(pin)
                showDuressDialog = false
            },
            onRemoveDuress = {
                viewModel.removeDuressPin()
                showDuressDialog = false
            },
            onDismiss = { showDuressDialog = false }
        )
    }

    if (showExitDecoyDialog) {
        PinInputDialog(
            title = "Enter Master PIN to Exit Decoy",
            onConfirm = { pin ->
                val ok = viewModel.exitDecoyMode(pin)
                if (ok) showExitDecoyDialog = false
            },
            onDismiss = { showExitDecoyDialog = false }
        )
    }
}

@Composable
private fun StorageRow(label: String, value: String, isBold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isBold) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isBold) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
            color = if (isBold) IndigoPrimary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) iconTint else iconTint.copy(alpha = 0.4f),
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) iconTint else iconTint.copy(alpha = 0.4f),
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun PinInputDialog(
    title: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 8) pin = it },
                    label = { Text("Enter PIN (4-8 digits)") },
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 8) confirmPin = it },
                    label = { Text("Confirm PIN") },
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (pin.length < 4) {
                    error = "PIN must be at least 4 digits"
                } else if (pin != confirmPin) {
                    error = "PINs do not match"
                } else {
                    onConfirm(pin)
                }
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun DuressPinDialog(
    hasDuressPin: Boolean,
    onSetDuress: (String) -> Unit,
    onRemoveDuress: () -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Duress Decoy PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "When entering this PIN under duress, Second Brain will unlock into an empty decoy state with Vault completely hidden.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 8) pin = it },
                    label = { Text("Decoy PIN (different from Master)") },
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasDuressPin) {
                    OutlinedButton(onClick = onRemoveDuress) {
                        Text("Remove")
                    }
                }
                Button(onClick = {
                    if (pin.length < 4) {
                        error = "Decoy PIN must be at least 4 digits"
                    } else {
                        onSetDuress(pin)
                    }
                }) {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
