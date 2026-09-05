package com.example.brain.ui.vault

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.brain.data.entity.SavedItemEntity
import com.example.brain.ui.components.EmptyStateView
import com.example.brain.ui.theme.PurpleVault
import com.example.brain.util.BiometricAuthManager
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    viewModel: VaultViewModel,
    biometricAuthManager: BiometricAuthManager,
    onNavigateToDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showAddNoteDialog by remember { mutableStateOf(false) }
    var selectedDetailItem by remember { mutableStateOf<SavedItemEntity?>(null) }

    // Media import launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val originalName = "vault_import_${System.currentTimeMillis()}"
            viewModel.importPrivateMedia(context, uri, originalName)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = PurpleVault
                        )
                        Text(
                            text = "Private Vault",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                actions = {
                    if (uiState.isUnlocked) {
                        IconButton(onClick = { viewModel.lockVault() }) {
                            Icon(Icons.Default.LockOpen, contentDescription = "Lock Vault", tint = PurpleVault)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (uiState.isUnlocked && !uiState.isDecoyMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FloatingActionButton(
                        onClick = { filePickerLauncher.launch("*/*") },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Encrypt File")
                    }

                    FloatingActionButton(
                        onClick = { showAddNoteDialog = true },
                        containerColor = PurpleVault,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Encrypted Note")
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        if (uiState.isSettingUpPin) {
            SetupPinView(
                onSavePin = { pin, duress -> viewModel.setMasterPin(pin, duress) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else if (!uiState.isUnlocked) {
            VaultKeypadView(
                pinLength = uiState.pinInput.length,
                errorMessage = uiState.errorMessage,
                lockoutSeconds = uiState.lockoutSeconds,
                isBiometricEnabled = uiState.isBiometricEnabled && biometricAuthManager.isBiometricAvailable(),
                onDigitClick = { viewModel.onPinDigitEntered(it) },
                onBackspace = { viewModel.onPinBackspace() },
                onBiometricClick = {
                    val activity = context as? FragmentActivity ?: return@VaultKeypadView
                    biometricAuthManager.showBiometricPrompt(
                        activity = activity,
                        title = "Unlock Private Vault",
                        subtitle = "Authenticate to access your encrypted records",
                        onSuccess = { viewModel.unlockWithBiometric() },
                        onError = { _, _ -> },
                        onFailed = { }
                    )
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            // Unlocked Vault View
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Security status badge
                Surface(
                    color = PurpleVault.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = PurpleVault)
                        Text(
                            text = "AES-256 GCM Authenticated • Isolated from Normal Search & Export",
                            style = MaterialTheme.typography.bodySmall,
                            color = PurpleVault,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Dedicated In-Vault Search Bar
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    placeholder = { Text("Search private vault...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = PurpleVault) },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotBlank()) {
                            IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )

                if (uiState.filteredItems.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Outlined.Shield,
                        title = if (uiState.isDecoyMode) "Vault is empty" else "No private items found",
                        description = if (uiState.searchQuery.isNotBlank()) "No vault items match '${uiState.searchQuery}'." else "Tap + to add an encrypted note or import a file safely.",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.filteredItems, key = { it.id }) { item ->
                            val bitmap = uiState.decryptedBitmaps[item.id]
                            VaultItemCard(
                                item = item,
                                decryptedBitmap = bitmap,
                                onClick = { selectedDetailItem = item },
                                onDelete = { viewModel.deleteSecretItem(item) },
                                onCopy = {
                                    val clipManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Vault Note", item.note)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        clip.description.extras = PersistableBundle().apply {
                                            putBoolean("android.content.extra.IS_SENSITIVE", true)
                                        }
                                    }
                                    clipManager.setPrimaryClip(clip)
                                    Toast.makeText(context, "Note copied securely", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Add Private Note Dialog
    if (showAddNoteDialog) {
        var noteTitle by remember { mutableStateOf("") }
        var noteContent by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddNoteDialog = false },
            title = { Text("Add Encrypted Note") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = noteTitle,
                        onValueChange = { noteTitle = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = noteContent,
                        onValueChange = { noteContent = it },
                        label = { Text("Private Note / Password") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createPrivateNote(noteTitle, noteContent)
                        showAddNoteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PurpleVault)
                ) {
                    Text("Save to Vault")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddNoteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Secure Vault Item Detail Dialog
    if (selectedDetailItem != null) {
        val item = selectedDetailItem!!
        val bitmap = uiState.decryptedBitmaps[item.id]

        AlertDialog(
            onDismissRequest = { selectedDetailItem = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = PurpleVault)
                    Text(item.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Decrypted Media",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }

                    if (item.note.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = item.note,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Text(
                        text = "Added: ${SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault()).format(Date(item.createdAt))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedDetailItem = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun VaultItemCard(
    item: SavedItemEntity,
    decryptedBitmap: android.graphics.Bitmap?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = PurpleVault, modifier = Modifier.size(18.dp))
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row {
                    if (item.note.isNotBlank()) {
                        IconButton(onClick = onCopy) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Note", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (decryptedBitmap != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Image(
                    bitmap = decryptedBitmap.asImageBitmap(),
                    contentDescription = "Decrypted Media",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            }

            if (item.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = item.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun VaultKeypadView(
    pinLength: Int,
    errorMessage: String?,
    lockoutSeconds: Long,
    isBiometricEnabled: Boolean,
    onDigitClick: (String) -> Unit,
    onBackspace: () -> Unit,
    onBiometricClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(PurpleVault.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = PurpleVault, modifier = Modifier.size(36.dp))
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Enter Vault PIN",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (lockoutSeconds > 0) "Locked out for $lockoutSeconds seconds" else "Keystore-backed AES-256 authentication",
            style = MaterialTheme.typography.bodyMedium,
            color = if (lockoutSeconds > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // PIN dot indicators
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            for (i in 0 until 4) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            if (i < pinLength) PurpleVault else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(36.dp))

        // Numeric Keypad Grid
        val buttons = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf(if (isBiometricEnabled) "BIO" else "", "0", "DEL")
        )

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            for (row in buttons) {
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    for (btn in row) {
                        when (btn) {
                            "" -> Spacer(modifier = Modifier.size(68.dp))
                            "BIO" -> {
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(PurpleVault.copy(alpha = 0.15f))
                                        .clickable { onBiometricClick() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Fingerprint, contentDescription = "Biometrics", tint = PurpleVault, modifier = Modifier.size(32.dp))
                                }
                            }
                            "DEL" -> {
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .clickable { onBackspace() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Backspace, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            else -> {
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { onDigitClick(btn) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = btn,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SetupPinView(
    onSavePin: (pin: String, duress: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var pin by remember { mutableStateOf("") }
    var duressPin by remember { mutableStateOf("") }

    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(PurpleVault.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Shield, contentDescription = null, tint = PurpleVault, modifier = Modifier.size(32.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Create Vault Master PIN",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Set a 4-8 digit numeric PIN to protect your private encrypted knowledge.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) pin = it },
            label = { Text("Master PIN (4-8 digits)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = duressPin,
            onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) duressPin = it },
            label = { Text("Duress Decoy PIN (optional)") },
            placeholder = { Text("Shows decoy empty vault under coercion") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { onSavePin(pin, duressPin.takeIf { it.isNotBlank() }) },
            enabled = pin.length >= 4,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PurpleVault),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Set Vault PIN & Unlock", fontWeight = FontWeight.SemiBold)
        }
    }
}
