package com.example.brain.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.brain.data.model.ContentType
import com.example.brain.ui.components.BrainSearchBar
import com.example.brain.ui.components.EmptyStateView
import com.example.brain.ui.components.KnowledgeCard
import com.example.brain.ui.theme.EmeraldSuccess
import com.example.brain.ui.theme.IndigoPrimary

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var showFilterSheet by remember { mutableStateOf(false) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    var showBulkMoveDialog by remember { mutableStateOf(false) }
    var showSaveSearchDialog by remember { mutableStateOf(false) }
    var saveSearchName by remember { mutableStateOf("") }

    val activeFilterCount = listOfNotNull(
        uiState.selectedType,
        uiState.selectedFolderId,
        uiState.selectedTag,
        if (uiState.favoritesOnly) true else null,
        if (uiState.readLaterOnly) true else null,
        if (uiState.dateFilter != SearchDateFilter.ALL) true else null
    ).size

    Scaffold(
        topBar = {
            if (uiState.isSelectionMode) {
                // Bulk Selection Top Bar
                TopAppBar(
                    title = {
                        Text(
                            text = "${uiState.selectedItemIds.size} selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Exit selection mode")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.bulkFavorite(true) }) {
                            Icon(Icons.Outlined.Favorite, contentDescription = "Favorite selected")
                        }
                        IconButton(onClick = { viewModel.bulkReadLater(true) }) {
                            Icon(Icons.Outlined.Bookmark, contentDescription = "Read Later selected")
                        }
                        IconButton(onClick = { showBulkMoveDialog = true }) {
                            Icon(Icons.Outlined.DriveFileMove, contentDescription = "Move selected")
                        }
                        IconButton(onClick = { showBulkDeleteConfirm = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete selected", tint = MaterialTheme.colorScheme.error)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                )
            } else {
                TopAppBar(
                    title = {
                        BrainSearchBar(
                            query = uiState.query,
                            onQueryChange = { viewModel.onQueryChange(it) },
                            onSearchClick = { viewModel.executeSearch() },
                            placeholder = "Search your brain...",
                            readOnly = false
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showFilterSheet = true }) {
                            BadgedBox(
                                badge = {
                                    if (activeFilterCount > 0) {
                                        Badge { Text("$activeFilterCount") }
                                    }
                                }
                            ) {
                                Icon(Icons.Outlined.Tune, contentDescription = "Search Filters")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Quick Filter Chips Row
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Type Filter Chips
                listOf(
                    ContentType.LINK to "Links",
                    ContentType.NOTE to "Notes",
                    ContentType.PDF to "Documents",
                    ContentType.IMAGE to "Images",
                    ContentType.VIDEO to "Videos"
                ).forEach { (type, label) ->
                    item {
                        val isSelected = uiState.selectedType == type
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setTypeFilter(type) },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                item {
                    FilterChip(
                        selected = uiState.favoritesOnly,
                        onClick = { viewModel.toggleFavoritesFilter() },
                        label = { Text("Favorites", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(Icons.Filled.Favorite, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    )
                }

                item {
                    FilterChip(
                        selected = uiState.readLaterOnly,
                        onClick = { viewModel.toggleReadLaterFilter() },
                        label = { Text("Read Later", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(Icons.Filled.Bookmark, contentDescription = null, modifier = Modifier.size(14.dp), tint = EmeraldSuccess)
                        }
                    )
                }

                if (activeFilterCount > 0) {
                    item {
                        AssistChip(
                            onClick = { viewModel.clearAllFilters() },
                            label = { Text("Clear all", fontSize = 12.sp) },
                            trailingIcon = {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(12.dp))
                            }
                        )
                    }
                }
            }

            // Real-time suggestions (while typing)
            if (uiState.query.isNotBlank() && (uiState.suggestedTags.isNotEmpty() || uiState.suggestedFolders.isNotEmpty())) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Suggestions:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(uiState.suggestedTags) { tag ->
                                SuggestionChip(
                                    onClick = {
                                        viewModel.setTagFilter(tag)
                                        viewModel.onQueryChange("")
                                    },
                                    label = { Text("#$tag", fontSize = 11.sp) }
                                )
                            }
                            items(uiState.suggestedFolders) { folder ->
                                SuggestionChip(
                                    onClick = {
                                        viewModel.setFolderFilter(folder.id)
                                        viewModel.onQueryChange("")
                                    },
                                    label = { Text("${folder.icon} ${folder.name}", fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }
            }

            // Search States Content
            if (uiState.isSearching) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = IndigoPrimary)
                }
            } else if (uiState.query.isNotBlank() && uiState.results.isEmpty()) {
                // No Results State
                EmptyStateView(
                    icon = Icons.Outlined.SearchOff,
                    title = "Nothing found",
                    description = "No items matched \"${uiState.query}\". Try another keyword, tag, or clear active filters.",
                    actionLabel = if (activeFilterCount > 0) "Clear Filters" else null,
                    onAction = { viewModel.clearAllFilters() },
                    modifier = Modifier.fillMaxSize()
                )
            } else if (uiState.query.isBlank() && activeFilterCount == 0) {
                // Initial State: Recent searches & Popular Topics
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Saved Searches (Prompt 10)
                    if (uiState.savedSearches.isNotEmpty()) {
                        item {
                            Text(
                                text = "Saved Searches",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        items(uiState.savedSearches, key = { it.id }) { search ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.applySavedSearch(search) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Outlined.Bookmark, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(18.dp))
                                        Column {
                                            Text(search.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                            if (search.query.isNotBlank()) {
                                                Text(search.query, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                    IconButton(
                                        onClick = { viewModel.deleteSavedSearch(search.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Search Shortcuts & Commands
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Search Commands & Shortcuts",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tap any filter keyword to inject into your search query:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val commands = listOf("project:", "tag:", "type:pdf", "type:url", "source:github.com", "review:due", "favorite:true")
                                items(commands) { cmd ->
                                    AssistChip(
                                        onClick = {
                                            val current = uiState.query.trim()
                                            val newQ = if (current.isBlank()) cmd else "$current $cmd"
                                            viewModel.onQueryChange(newQ)
                                        },
                                        label = { Text(cmd, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace) }
                                    )
                                }
                            }
                        }
                    }

                    // Recent Searches
                    if (uiState.recentSearches.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Recent Searches",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                TextButton(onClick = { viewModel.clearAllRecentSearches() }) {
                                    Text("Clear", fontSize = 12.sp)
                                }
                            }
                        }

                        items(uiState.recentSearches) { recent ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        viewModel.onQueryChange(recent)
                                        viewModel.executeSearch(recent)
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.History,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = recent,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.removeRecentSearch(recent) },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Popular Tags
                    if (uiState.popularTags.isNotEmpty()) {
                        item {
                            Text(
                                text = "Explore Popular Topics",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(uiState.popularTags) { tag ->
                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { viewModel.setTagFilter(tag) },
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(8.dp),
                                        border = CardDefaults.outlinedCardBorder()
                                    ) {
                                        Text(
                                            text = "#$tag",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = IndigoPrimary,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Helper banner
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = IndigoPrimary)
                                Text(
                                    text = "Search across title, notes, URLs, and tags. Works 100% offline with SQLite FTS5.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                // Results State
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Found ${uiState.results.size} result${if (uiState.results.size != 1) "s" else ""}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = {
                                        saveSearchName = uiState.query
                                        showSaveSearchDialog = true
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Outlined.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Save Search", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    items(uiState.results, key = { it.id }) { item ->
                        val isSelected = uiState.selectedItemIds.contains(item.id)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (uiState.isSelectionMode) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { viewModel.toggleItemSelection(item.id) }
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .combinedClickable(
                                        onClick = {
                                            if (uiState.isSelectionMode) {
                                                viewModel.toggleItemSelection(item.id)
                                            } else {
                                                onNavigateToDetail(item.id)
                                            }
                                        },
                                        onLongClick = {
                                            viewModel.toggleItemSelection(item.id)
                                        }
                                    )
                            ) {
                                KnowledgeCard(
                                    item = item,
                                    onClick = {
                                        if (uiState.isSelectionMode) {
                                            viewModel.toggleItemSelection(item.id)
                                        } else {
                                            onNavigateToDetail(item.id)
                                        }
                                    },
                                    onToggleFavorite = { viewModel.toggleFavorite(item) },
                                    onToggleReadLater = { viewModel.toggleReadLater(item) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Comprehensive Filter ModalBottomSheet
    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Filter Knowledge",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = {
                        viewModel.clearAllFilters()
                        showFilterSheet = false
                    }) {
                        Text("Reset All")
                    }
                }

                // Project Selector
                Text("Project", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = uiState.selectedFolderId == null,
                            onClick = { viewModel.setFolderFilter(null) },
                            label = { Text("All Projects") }
                        )
                    }
                    items(uiState.availableFolders, key = { it.id }) { folder ->
                        FilterChip(
                            selected = uiState.selectedFolderId == folder.id,
                            onClick = { viewModel.setFolderFilter(folder.id) },
                            label = { Text("${folder.icon} ${folder.name}") }
                        )
                    }
                }

                // Date Filter
                Text("Date Added", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        SearchDateFilter.ALL to "All Time",
                        SearchDateFilter.TODAY to "Today",
                        SearchDateFilter.THIS_WEEK to "This Week",
                        SearchDateFilter.THIS_MONTH to "This Month"
                    ).forEach { (date, label) ->
                        FilterChip(
                            selected = uiState.dateFilter == date,
                            onClick = { viewModel.setDateFilter(date) },
                            label = { Text(label) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showFilterSheet = false },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Apply Filters")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Bulk Delete Confirmation Dialog
    if (showBulkDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirm = false },
            title = { Text("Delete ${uiState.selectedItemIds.size} Items?") },
            text = { Text("These items will be permanently removed from your Second Brain.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.bulkDelete()
                        showBulkDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    // Bulk Move Dialog
    if (showBulkMoveDialog) {
        AlertDialog(
            onDismissRequest = { showBulkMoveDialog = false },
            title = { Text("Move ${uiState.selectedItemIds.size} Items") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose Destination Project:", style = MaterialTheme.typography.labelMedium)
                    uiState.availableFolders.forEach { folder ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.bulkMove(folder.id)
                                    showBulkMoveDialog = false
                                }
                                .padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "${folder.icon} ${folder.name}",
                                modifier = Modifier.padding(10.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showBulkMoveDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Save Search Dialog (Prompt 10)
    if (showSaveSearchDialog) {
        AlertDialog(
            onDismissRequest = { showSaveSearchDialog = false },
            title = { Text("Save Current Search") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter a name to save this query and its active filters for quick access later:", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = saveSearchName,
                        onValueChange = { saveSearchName = it },
                        placeholder = { Text("Search name...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveCurrentSearch(saveSearchName)
                        showSaveSearchDialog = false
                        saveSearchName = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveSearchDialog = false }) { Text("Cancel") }
            }
        )
    }
}
