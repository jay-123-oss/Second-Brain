package com.example.brain.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.brain.data.entity.FolderEntity
import com.example.brain.data.entity.SavedItemEntity
import com.example.brain.data.model.ContentType
import com.example.brain.ui.components.EmptyStateView
import com.example.brain.ui.components.TypeBadge
import com.example.brain.ui.theme.AmberWarning
import com.example.brain.ui.theme.EmeraldSuccess
import com.example.brain.ui.theme.IndigoPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    viewModel: InboxViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var showFolderPickerItem by remember { mutableStateOf<SavedItemEntity?>(null) }
    var showBulkFolderPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (uiState.isSelectionMode) {
                TopAppBar(
                    title = { Text("${uiState.selectedItemIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showBulkFolderPicker = true }) {
                            Icon(Icons.Outlined.Folder, contentDescription = "Assign Project")
                        }
                        IconButton(onClick = { viewModel.bulkArchive() }) {
                            Icon(Icons.Outlined.Archive, contentDescription = "Archive")
                        }
                        IconButton(onClick = { viewModel.bulkDelete() }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text("Inbox", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = "${uiState.inboxItems.size} unorganized item${if (uiState.inboxItems.size != 1) "s" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = IndigoPrimary)
            }
        } else if (uiState.inboxItems.isEmpty()) {
            EmptyStateView(
                icon = Icons.Outlined.CheckCircle,
                title = "Inbox Zero",
                description = "All captured knowledge is organized into projects and tagged. Great job!",
                actionLabel = "Back to Home",
                onAction = onNavigateBack,
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.inboxItems, key = { it.id }) { item ->
                    val isSelected = uiState.selectedItemIds.contains(item.id)
                    val suggestion = uiState.suggestionsMap[item.id]

                    InboxItemCard(
                        item = item,
                        suggestion = suggestion,
                        isSelected = isSelected,
                        onItemClick = {
                            if (uiState.isSelectionMode) {
                                viewModel.toggleSelection(item.id)
                            } else {
                                onNavigateToDetail(item.id)
                            }
                        },
                        onLongClick = { viewModel.toggleSelection(item.id) },
                        onApplySuggestion = { viewModel.applySuggestion(item) },
                        onAssignFolder = { showFolderPickerItem = item },
                        onArchive = { viewModel.archiveItem(item) },
                        onDelete = { viewModel.deleteItem(item) }
                    )
                }
            }
        }
    }

    // Single item folder picker dialog
    showFolderPickerItem?.let { targetItem ->
        FolderPickerDialog(
            folders = uiState.availableFolders,
            onDismiss = { showFolderPickerItem = null },
            onFolderSelected = { folderId ->
                viewModel.assignFolder(targetItem, folderId)
                showFolderPickerItem = null
            }
        )
    }

    // Bulk folder picker dialog
    if (showBulkFolderPicker) {
        FolderPickerDialog(
            folders = uiState.availableFolders,
            onDismiss = { showBulkFolderPicker = false },
            onFolderSelected = { folderId ->
                viewModel.bulkAssignFolder(folderId)
                showBulkFolderPicker = false
            }
        )
    }
}

@Composable
private fun InboxItemCard(
    item: SavedItemEntity,
    suggestion: OrganizationSuggestion?,
    isSelected: Boolean,
    onItemClick: () -> Unit,
    onLongClick: () -> Unit,
    onApplySuggestion: () -> Unit,
    onAssignFolder: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) IndigoPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = if (isSelected) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(IndigoPrimary)) else CardDefaults.outlinedCardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onItemClick() }
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val contentType = try { ContentType.valueOf(item.type) } catch (_: Exception) { ContentType.NOTE }
                TypeBadge(type = contentType)
                Text(

                    text = item.notes.ifBlank { item.description ?: "Captured resource" }.take(32),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = item.title.ifBlank { "Untitled Item" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Smart One-Tap Organization Suggestion
            if (suggestion != null && (suggestion.suggestedFolderName != null || suggestion.suggestedTags.isNotEmpty())) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = EmeraldSuccess.copy(alpha = 0.12f),
                    modifier = Modifier.clickable { onApplySuggestion() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(14.dp))
                        val text = buildString {
                            append("Organize: ")
                            if (suggestion.suggestedFolderName != null) append("Move to ${suggestion.suggestedFolderName}")
                            if (suggestion.suggestedTags.isNotEmpty()) append(" + ${suggestion.suggestedTags.joinToString(", ") { "#$it" }}")
                        }
                        Text(text, style = MaterialTheme.typography.labelSmall, color = EmeraldSuccess, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Quick Actions Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onAssignFolder,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Project", fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = onArchive,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(Icons.Outlined.Archive, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Archive", fontSize = 11.sp)
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun FolderPickerDialog(
    folders: List<FolderEntity>,
    onDismiss: () -> Unit,
    onFolderSelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign to Project") },
        text = {
            if (folders.isEmpty()) {
                Text("No projects available. Create a project in Home first.", style = MaterialTheme.typography.bodySmall)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    folders.forEach { folder ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onFolderSelected(folder.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Outlined.Folder, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(18.dp))
                                Text(folder.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
