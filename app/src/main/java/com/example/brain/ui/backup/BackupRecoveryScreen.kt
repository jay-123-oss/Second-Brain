package com.example.brain.ui.backup

import android.net.Uri
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.brain.ui.home.SectionHeader
import com.example.brain.ui.theme.EmeraldSuccess
import com.example.brain.ui.theme.IndigoPrimary
import com.example.brain.ui.theme.PurpleVault
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRecoveryScreen(
    viewModel: BackupRecoveryViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    var showVaultBackupPassDialog by remember { mutableStateOf(false) }
    var pendingVaultPassphrase by remember { mutableStateOf("") }
    var showVaultRestorePassDialog by remember { mutableStateOf(false) }
    var pendingVaultRestoreUri by remember { mutableStateOf<Uri?>(null) }

    // SAF Launchers
    val standardBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.createStandardBackup(it) }
    }

    val vaultBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let {
            if (pendingVaultPassphrase.isNotBlank()) {
                viewModel.createEncryptedVaultBackup(it, pendingVaultPassphrase)
                pendingVaultPassphrase = ""
            }
        }
    }

    val standardRestoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.selectBackupForRestore(it) }
    }

    val vaultRestoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            pendingVaultRestoreUri = it
            showVaultRestorePassDialog = true
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importKnowledge(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Backup & Recovery",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Operation / Feedback Banner
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
                                IconButton(onClick = { viewModel.clearMessage() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Dismiss")
                                }
                            }
                        }
                    }
                }

                // Section 1: Backup
                item {
                    SectionHeader(title = "Create Backups", icon = Icons.Outlined.CloudUpload)
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column {
                            BackupActionRow(
                                title = "Create Standard Backup",
                                subtitle = "Full JSON export with SHA-256 verification (Vault strictly excluded)",
                                icon = Icons.Outlined.Archive,
                                onClick = {
                                    standardBackupLauncher.launch("second_brain_backup_${System.currentTimeMillis()}.secondbrain")
                                }
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                            BackupActionRow(
                                title = "Back Up Private Vault",
                                subtitle = "AES-256 GCM encrypted archive protected by a custom passphrase",
                                icon = Icons.Outlined.Shield,
                                iconTint = PurpleVault,
                                onClick = { showVaultBackupPassDialog = true }
                            )
                        }
                    }
                }

                // Section 2: Restore
                item {
                    SectionHeader(title = "Restore & Portability", icon = Icons.Outlined.SettingsBackupRestore)
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column {
                            BackupActionRow(
                                title = "Restore from Standard Backup",
                                subtitle = "Verify manifest & restore knowledge with automatic safety rollback",
                                icon = Icons.Outlined.Restore,
                                onClick = { standardRestoreLauncher.launch(arrayOf("*/*")) }
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                            BackupActionRow(
                                title = "Restore Encrypted Vault Backup",
                                subtitle = "Decrypt and restore secret records (Vault remains locked)",
                                icon = Icons.Outlined.LockReset,
                                iconTint = PurpleVault,
                                onClick = { vaultRestoreLauncher.launch(arrayOf("*/*")) }
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                            BackupActionRow(
                                title = "Import Knowledge (Merge)",
                                subtitle = "Import items from another backup without overwriting current data",
                                icon = Icons.Outlined.FileDownload,
                                onClick = { importLauncher.launch(arrayOf("*/*")) }
                            )
                        }
                    }
                }

                // Section 3: Diagnostic & Integrity Scan
                item {
                    SectionHeader(title = "Data Integrity & Health", icon = Icons.Outlined.HealthAndSafety)
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Integrity Diagnostic",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Scan database relationships, media presence, and Vault boundaries",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = { viewModel.runIntegrityScan() },
                                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                                ) {
                                    Text("Run Audit")
                                }
                            }

                            uiState.integrityReport?.let { report ->
                                Spacer(modifier = Modifier.height(14.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (report.isHealthy) Icons.Default.CheckCircle else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = if (report.isHealthy) EmeraldSuccess else MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = if (report.isHealthy) "All Subsystems Healthy" else "${report.issues.size} Issues Detected",
                                        fontWeight = FontWeight.Bold,
                                        color = if (report.isHealthy) EmeraldSuccess else MaterialTheme.colorScheme.error
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Database: ${report.databaseStatus}", style = MaterialTheme.typography.bodySmall)
                                Text("Media Storage: ${report.mediaStatus}", style = MaterialTheme.typography.bodySmall)
                                Text("Search Index: ${report.searchIndexStatus}", style = MaterialTheme.typography.bodySmall)
                                Text("Vault Boundary: ${report.vaultIsolationStatus}", style = MaterialTheme.typography.bodySmall)

                                if (!report.isHealthy) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = { viewModel.repairIssues() },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Text("Repair Relational Issues")
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 4: Physical Storage Usage
                item {
                    SectionHeader(title = "Storage Breakdown", icon = Icons.Outlined.PieChart)
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val storage = uiState.storageBreakdown
                            if (storage != null) {
                                StorageMetricRow("Database (SQLite Room)", formatBytes(storage.databaseSizeBytes))
                                StorageMetricRow("Media Attachments", formatBytes(storage.mediaSizeBytes))
                                StorageMetricRow("Encrypted Vault Media", formatBytes(storage.vaultSizeBytes))
                                StorageMetricRow("Temporary Cache", formatBytes(storage.cacheSizeBytes))
                                StorageMetricRow("Free Disk Space", formatBytes(storage.freeDiskSpaceBytes), isHighlight = true)
                            } else {
                                Text("Calculating storage metrics...", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            // Busy Overlay
            if (uiState.isBusy) {
                Surface(
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = IndigoPrimary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = uiState.operationStep ?: "Processing...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    // Vault Backup Passphrase Dialog
    if (showVaultBackupPassDialog) {
        var pass by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showVaultBackupPassDialog = false },
            title = { Text("Encrypted Vault Passphrase") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Set a passphrase to encrypt your Vault backup with AES-256 GCM. You will need this passphrase to restore your private knowledge.")
                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it },
                        label = { Text("Passphrase (min 6 chars)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingVaultPassphrase = pass
                        showVaultBackupPassDialog = false
                        vaultBackupLauncher.launch("second_brain_vault_${System.currentTimeMillis()}.secondbrainvault")
                    },
                    enabled = pass.length >= 6,
                    colors = ButtonDefaults.buttonColors(containerColor = PurpleVault)
                ) {
                    Text("Proceed to Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showVaultBackupPassDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Vault Restore Passphrase Dialog
    if (showVaultRestorePassDialog && pendingVaultRestoreUri != null) {
        var pass by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = {
                showVaultRestorePassDialog = false
                pendingVaultRestoreUri = null
            },
            title = { Text("Enter Vault Passphrase") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter the passphrase used when creating this encrypted Vault backup.")
                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it },
                        label = { Text("Passphrase") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = pendingVaultRestoreUri
                        showVaultRestorePassDialog = false
                        pendingVaultRestoreUri = null
                        if (uri != null) {
                            viewModel.restoreVaultBackup(uri, pass)
                        }
                    },
                    enabled = pass.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = PurpleVault)
                ) {
                    Text("Decrypt & Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showVaultRestorePassDialog = false
                    pendingVaultRestoreUri = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Restore Preview Dialog
    uiState.restorePreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissRestorePreview() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = if (preview.isValid) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (preview.isValid) EmeraldSuccess else MaterialTheme.colorScheme.error
                    )
                    Text("Restore Preview")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (preview.isValid) {
                        Text("Backup integrity verified via SHA-256.", color = EmeraldSuccess, fontWeight = FontWeight.SemiBold)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text("Knowledge Items: ${preview.manifest.itemCount}")
                        Text("Projects: ${preview.manifest.projectCount}")
                        Text("Tags: ${preview.manifest.tagCount}")
                        Text("Collections: ${preview.manifest.collectionCount}")
                        Text("Relations: ${preview.manifest.relationCount}")
                        Text("Created: ${SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault()).format(Date(preview.manifest.createdAt))}")
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Note: A safety snapshot of your current knowledge will be saved first before restoring.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = preview.validationError ?: "Corrupt backup file.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                if (preview.isValid && uiState.pendingRestoreUri != null) {
                    Button(
                        onClick = { viewModel.executeRestore(uiState.pendingRestoreUri!!) },
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                    ) {
                        Text("Restore Data")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissRestorePreview() }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun BackupActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color = IndigoPrimary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = iconTint)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun StorageMetricRow(
    label: String,
    value: String,
    isHighlight: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isHighlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (isHighlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
