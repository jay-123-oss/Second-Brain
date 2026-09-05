package com.example.brain.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.brain.data.entity.FolderEntity
import com.example.brain.data.entity.SavedItemEntity
import com.example.brain.data.model.ContentType
import com.example.brain.ui.components.TagPill
import com.example.brain.ui.components.TypeBadge
import com.example.brain.ui.home.SectionHeader
import com.example.brain.ui.theme.AmberWarning
import com.example.brain.ui.theme.EmeraldSuccess
import com.example.brain.ui.theme.IndigoPrimary
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    viewModel: ItemDetailViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToRelated: (String) -> Unit,
    onNavigateToAsk: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var isEditingNotes by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }
    var showAddTagDialog by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) {
            onNavigateBack()
        }
    }

    LaunchedEffect(uiState.item) {
        uiState.item?.let {
            noteText = it.notes
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    uiState.item?.let { item ->
                        IconButton(onClick = { viewModel.toggleReadLater() }) {
                            Icon(
                                imageVector = if (item.isReadLater) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = "Read Later",
                                tint = if (item.isReadLater) EmeraldSuccess else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(onClick = { viewModel.toggleFavorite() }) {
                            Icon(
                                imageVector = if (item.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (item.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, item.title)
                                putExtra(Intent.EXTRA_TEXT, item.url ?: item.notes)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Knowledge"))
                        }) {
                            Icon(Icons.Outlined.Share, contentDescription = "Share")
                        }

                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
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
        val item = uiState.item
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = IndigoPrimary)
            }
        } else if (item == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Knowledge item no longer exists.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Button(
                        onClick = onNavigateBack,
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                    ) {
                        Text("Return to Brain")
                    }
                }
            }
        } else {
            val contentType = try { ContentType.valueOf(item.type) } catch (_: Exception) { ContentType.NOTE }
            val formattedDate = SimpleDateFormat("MMMM d, yyyy • h:mm a", Locale.getDefault()).format(Date(item.createdAt))

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Hero Image / Thumbnail if available
                val thumbModel = item.thumbnailPath
                    ?: (if (item.type == "IMAGE") item.filePath else null)
                    ?: item.imageUrl

                if (!thumbModel.isNullOrBlank()) {
                    item {
                        AsyncImage(
                            model = thumbModel,
                            contentDescription = "Cover Image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }
                }

                // 2. Metadata Header: Type + Date + Source + Processing State
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TypeBadge(type = contentType)
                            val sourceLabel = item.sourceApp ?: item.siteName
                            if (!sourceLabel.isNullOrBlank()) {
                                Text(
                                    text = "• $sourceLabel",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = "• $formattedDate",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Processing Status Badge + Retry
                        if (item.processingStatus == "PROCESSING") {
                            Surface(
                                color = IndigoPrimary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 1.5.dp,
                                        color = IndigoPrimary
                                    )
                                    Text("Enriching…", style = MaterialTheme.typography.labelSmall, color = IndigoPrimary)
                                }
                            }
                        } else if (item.processingStatus == "FAILED" || item.processingStatus == "PARTIAL") {
                            OutlinedButton(
                                onClick = { viewModel.retryEnrichment(context) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Retry", fontSize = 11.sp)
                            }
                        }
                    }
                }

                // 3. Knowledge Title
                item {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // 3a. Knowledge Context & Maturity (Prompt 10)
                item {
                    KnowledgeContextCard(
                        item = item,
                        folder = uiState.folder,
                        onUpdateMaturity = { viewModel.updateMaturity(it) },
                        onScheduleReview = { viewModel.scheduleReview(it) },
                        onMarkReviewed = { viewModel.markReviewed() }
                    )
                }

                // 3b. Media Intelligence Specs Card (Dimensions, Duration, File Size)
                val hasMediaSpecs = item.mediaWidth != null || item.mediaDurationMs != null || item.mediaFileSize != null
                if (hasMediaSpecs) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            border = CardDefaults.outlinedCardBorder(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (item.mediaWidth != null && item.mediaHeight != null) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Resolution", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${item.mediaWidth}x${item.mediaHeight}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (item.mediaDurationMs != null && item.mediaDurationMs > 0) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Duration", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        val sec = (item.mediaDurationMs / 1000) % 60
                                        val min = (item.mediaDurationMs / 1000) / 60
                                        Text("%d:%02d".format(min, sec), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (item.mediaFileSize != null && item.mediaFileSize > 0) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("File Size", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${item.mediaFileSize / 1024} KB", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // 3c. Extracted Text Deep Search Card (Prompt 6 FTS body text)
                if (!item.extractedText.isNullOrBlank()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            border = CardDefaults.outlinedCardBorder(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Search,
                                        contentDescription = null,
                                        tint = IndigoPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Indexed in Deep Search",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = IndigoPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = item.extractedText.take(300) + if (item.extractedText.length > 300) "…" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Local AI Summary Section
                if (!item.isSecret) {
                    item {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.Summarize, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(18.dp))
                                        Text("Local Summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = IndigoPrimary.copy(alpha = 0.12f)
                                    ) {
                                        Text("On-Device AI", style = MaterialTheme.typography.labelSmall, color = IndigoPrimary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }

                                if (!item.summary.isNullOrBlank()) {
                                    Text(
                                        text = item.summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        lineHeight = 20.sp
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(onClick = { viewModel.generateSummary() }) {
                                            Text("Regenerate", style = MaterialTheme.typography.labelSmall)
                                        }
                                        TextButton(onClick = { viewModel.deleteSummary() }) {
                                            Text("Remove", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                } else {
                                    Text(
                                        text = "Generate an on-device extractive summary of this knowledge item without sending data to the cloud.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Button(
                                        onClick = { viewModel.generateSummary() },
                                        enabled = !uiState.isSummarizing,
                                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        if (uiState.isSummarizing) {
                                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Summarizing...")
                                        } else {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Generate Local Summary")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Mentioned Concepts & Entities
                if (uiState.concepts.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Mentioned Concepts", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                uiState.concepts.take(4).forEach { concept ->
                                    AssistChip(
                                        onClick = { onNavigateToAsk("What did I save about $concept?") },
                                        label = { Text(concept, style = MaterialTheme.typography.labelSmall) },
                                        leadingIcon = { Icon(Icons.Default.Tag, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Ask About This Action Button
                if (!item.isSecret) {
                    item {
                        OutlinedButton(
                            onClick = { onNavigateToAsk("What are the key points and takeaways from \"${item.title}\"?") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.Psychology, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ask Your Brain About This", color = IndigoPrimary, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // 4. Source URL / Open in Browser Action
                if (!item.url.isNullOrBlank()) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.url))
                                    context.startActivity(intent)
                                },
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = IndigoPrimary)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.siteName ?: "Open Source Link",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = IndigoPrimary
                                    )
                                    Text(
                                        text = item.url,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // 4b. Local File / Media Open Action with secure FileProvider
                if (!item.filePath.isNullOrBlank()) {
                    item {
                        val file = java.io.File(item.filePath)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    if (file.exists()) {
                                        try {
                                            val contentUri = androidx.core.content.FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                file
                                            )
                                            val mime = when (contentType) {
                                                ContentType.PDF -> "application/pdf"
                                                ContentType.IMAGE -> "image/*"
                                                ContentType.VIDEO -> "video/*"
                                                ContentType.AUDIO -> "audio/*"
                                                else -> "*/*"
                                            }
                                            val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(contentUri, mime)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(openIntent)
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(context, "No app found to open this file", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        android.widget.Toast.makeText(context, "Original file unavailable", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                },
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    if (file.exists()) Icons.Default.Attachment else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (file.exists()) IndigoPrimary else MaterialTheme.colorScheme.error
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (file.exists()) "Open Local File (${file.name})" else "Original file unavailable",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (file.exists()) IndigoPrimary else MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = if (file.exists()) "${file.length() / 1024} KB • Tap to view" else "The file was moved or deleted",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // 5. Notes & Thoughts Section
                item {
                    SectionHeader(title = "Notes & Insights", icon = Icons.Outlined.EditNote)
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            if (isEditingNotes) {
                                OutlinedTextField(
                                    value = noteText,
                                    onValueChange = { noteText = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 4
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        viewModel.updateNotes(noteText)
                                        isEditingNotes = false
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Save Notes")
                                }
                            } else {
                                Text(
                                    text = if (item.notes.isNotBlank()) item.notes else "No notes added yet. Tap to write insights...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (item.notes.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isEditingNotes = true }
                                )
                            }
                        }
                    }
                }

                // 6. Tags Section
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader(title = "Tags", icon = Icons.Outlined.Tag)
                        IconButton(onClick = { showAddTagDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Tag", tint = IndigoPrimary)
                        }
                    }
                }

                item {
                    if (uiState.tags.isEmpty()) {
                        Text(
                            text = "No tags yet. Add tags to easily discover this later.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(uiState.tags, key = { it.name }) { tag ->
                                TagPill(
                                    name = tag.name,
                                    onClick = {},
                                    onDelete = { viewModel.removeTag(tag.name) }
                                )
                            }
                        }
                    }
                }

                // 7. Project Location & Move Picker
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader(title = "Project", icon = Icons.Outlined.Folder)
                    }
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(uiState.availableFolders, key = { it.id }) { folder ->
                            val isCurrent = item.folderId == folder.id
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.moveToFolder(folder.id) },
                                color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                border = CardDefaults.outlinedCardBorder()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(folder.icon, fontSize = 14.sp)
                                    Text(
                                        text = folder.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // 8. Knowledge Connections & Graph Relations
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader(title = "Connected Knowledge", icon = Icons.Outlined.Hub)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Toggle between Visual Graph and List
                            IconButton(onClick = { viewModel.toggleGraphViewMode() }) {
                                Icon(
                                    imageVector = if (uiState.isGraphViewMode) Icons.Outlined.List else Icons.Outlined.AccountTree,
                                    contentDescription = if (uiState.isGraphViewMode) "List View" else "Graph View",
                                    tint = IndigoPrimary
                                )
                            }
                            IconButton(onClick = { showLinkDialog = true }) {
                                Icon(Icons.Default.AddLink, contentDescription = "Connect Knowledge", tint = IndigoPrimary)
                            }
                        }
                    }
                }

                // Interactive Visual Graph View
                if (uiState.isGraphViewMode && uiState.graphData != null) {
                    item {
                        ItemCenteredGraphView(
                            graphData = uiState.graphData!!,
                            onNodeClick = { nodeId ->
                                if (nodeId != item.id && !nodeId.startsWith("proj_") && !nodeId.startsWith("tag_")) {
                                    onNavigateToRelated(nodeId)
                                }
                            }
                        )
                    }
                } else {
                    // Grouped Connections List View (Accessible)
                    if (uiState.relations.isEmpty()) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "No connections yet. Tap the connect button to link related knowledge.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                    } else {
                        // Group relations by directional type
                        val groupedRelations = uiState.relations.groupBy { rel ->
                            val (_, label) = com.example.brain.util.KnowledgeIntelligenceEngine.getDisplayRelationType(rel, item.id)
                            label
                        }

                        groupedRelations.forEach { (groupLabel, relList) ->
                            item {
                                Text(
                                    text = groupLabel.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = IndigoPrimary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }

                            items(relList, key = { "${it.sourceItemId}_${it.targetItemId}_${it.relationType}" }) { rel ->
                                val targetId = if (rel.sourceItemId == item.id) rel.targetItemId else rel.sourceItemId
                                val targetItem = uiState.allItemsForLinking.firstOrNull { it.id == targetId }
                                val displayTitle = targetItem?.title ?: "Connected Item (${targetId.take(8)}...)"

                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(10.dp),
                                    border = CardDefaults.outlinedCardBorder(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onNavigateToRelated(targetId) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Link,
                                                contentDescription = null,
                                                tint = IndigoPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = displayTitle,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (!rel.explanation.isNullOrBlank()) {
                                                    Text(
                                                        text = rel.explanation,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }

                                        IconButton(
                                            onClick = { viewModel.removeRelation(rel) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Disconnect",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Deterministic System Suggestions with Real Explanations
                if (uiState.suggestedCandidates.isNotEmpty()) {
                    item {
                        Text(
                            text = "SUGGESTED RELATIONSHIPS (LOCAL HEURISTICS)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }

                    items(uiState.suggestedCandidates, key = { it.item.id }) { candidate ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            border = CardDefaults.outlinedCardBorder(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = candidate.item.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { onNavigateToRelated(candidate.item.id) },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Surface(
                                        color = IndigoPrimary.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "${(candidate.confidence * 100).toInt()}% match",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = IndigoPrimary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Real Explainable Reasons
                                Text(
                                    text = "Why: ${candidate.explanation}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = { viewModel.dismissSuggestion(candidate) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("Dismiss", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Button(
                                        onClick = { viewModel.acceptSuggestion(candidate) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Connect (${candidate.suggestedType.replace("_", " ")})", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialogs
    if (showAddTagDialog) {
        var newTagName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddTagDialog = false },
            title = { Text("Add Tag") },
            text = {
                OutlinedTextField(
                    value = newTagName,
                    onValueChange = { newTagName = it },
                    label = { Text("Tag Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTagName.isNotBlank()) {
                            viewModel.addTag(newTagName)
                            showAddTagDialog = false
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTagDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Knowledge?") },
            text = { Text("This will permanently remove this item from your Second Brain.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteItem()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showLinkDialog) {
        ConnectKnowledgeDialog(
            items = uiState.allItemsForLinking,
            onDismiss = { showLinkDialog = false },
            onConnect = { targetId, relType ->
                viewModel.addRelation(targetId, relType)
                showLinkDialog = false
            }
        )
    }
}

@Composable
fun ConnectKnowledgeDialog(
    items: List<SavedItemEntity>,
    onDismiss: () -> Unit,
    onConnect: (targetId: String, relationType: String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf("ALL") }
    var selectedRelType by remember { mutableStateOf("RELATED_TO") }
    var selectedTargetId by remember { mutableStateOf("") }

    val filteredItems = remember(items, searchQuery, selectedTypeFilter) {
        items.filter { item ->
            val matchesQuery = searchQuery.isBlank() ||
                    item.title.contains(searchQuery, ignoreCase = true) ||
                    (item.notes.contains(searchQuery, ignoreCase = true))
            val matchesType = selectedTypeFilter == "ALL" || item.type.equals(selectedTypeFilter, ignoreCase = true)
            matchesQuery && matchesType
        }
    }

    LaunchedEffect(filteredItems) {
        if (selectedTargetId.isBlank() || filteredItems.none { it.id == selectedTargetId }) {
            selectedTargetId = filteredItems.firstOrNull()?.id ?: ""
        }
    }

    val relationTypes = listOf(
        "RELATED_TO" to "Related To",
        "REFERENCES" to "References",
        "INSPIRED_BY" to "Inspired By",
        "PART_OF" to "Part Of",
        "DERIVED_FROM" to "Derived From",
        "SUPPORTS" to "Supports",
        "CONTRADICTS" to "Contradicts",
        "DUPLICATE_OF" to "Duplicate Of"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Connect Knowledge", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Search Input
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search knowledge to connect…", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                // Content Type Filter Chips
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val filters = listOf("ALL" to "All", "LINK" to "Links", "NOTE" to "Notes", "PDF" to "PDFs", "IMAGE" to "Images")
                    items(filters) { (typeKey, label) ->
                        val isSel = selectedTypeFilter == typeKey
                        FilterChip(
                            selected = isSel,
                            onClick = { selectedTypeFilter = typeKey },
                            label = { Text(label, fontSize = 11.sp) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                Text("Relationship Type", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(relationTypes) { (key, label) ->
                        val isSel = selectedRelType == key
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedRelType = key },
                            color = if (isSel) IndigoPrimary else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = label,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Text("Select Target Item", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                if (filteredItems.isEmpty()) {
                    Text(
                        text = "No matching items found.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                    ) {
                        filteredItems.take(15).forEach { targetItem ->
                            val isSelected = selectedTargetId == targetItem.id
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedTargetId = targetItem.id }
                                    .padding(vertical = 2.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val cType = try { ContentType.valueOf(targetItem.type) } catch (_: Exception) { ContentType.NOTE }
                                    TypeBadge(type = cType)
                                    Text(
                                        text = targetItem.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConnect(selectedTargetId, selectedRelType) },
                enabled = selectedTargetId.isNotBlank(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Item-Centered Interactive Knowledge Graph.
 * Visualizes the active item at the center with connected knowledge items, project, and tags.
 */
@Composable
fun ItemCenteredGraphView(
    graphData: com.example.brain.util.ItemGraphData,
    onNodeClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder(),
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            val centerX = maxWidth / 2
            val centerY = maxHeight / 2
            val radius = (minOf(maxWidth, maxHeight) / 2) - 45.dp

            val nodes = graphData.connectedNodes
            val angleStep = if (nodes.isNotEmpty()) (2 * Math.PI / nodes.size).toFloat() else 0f

            // 1. Draw connection lines
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val cX = size.width / 2
                val cY = size.height / 2
                val rPx = radius.toPx()

                for (i in nodes.indices) {
                    val angle = i * angleStep - (Math.PI / 2).toFloat()
                    val targetX = cX + (rPx * kotlin.math.cos(angle))
                    val targetY = cY + (rPx * kotlin.math.sin(angle))

                    drawLine(
                        color = IndigoPrimary.copy(alpha = 0.4f),
                        start = androidx.compose.ui.geometry.Offset(cX, cY),
                        end = androidx.compose.ui.geometry.Offset(targetX, targetY),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }

            // 2. Peripheral Nodes
            nodes.forEachIndexed { index, node ->
                val angle = index * angleStep - (Math.PI / 2).toFloat()
                val offsetX = radius * kotlin.math.cos(angle)
                val offsetY = radius * kotlin.math.sin(angle)

                val nodeBg = when (node.type) {
                    "PROJECT" -> MaterialTheme.colorScheme.tertiaryContainer
                    "TAG" -> MaterialTheme.colorScheme.secondaryContainer
                    else -> IndigoPrimary.copy(alpha = 0.15f)
                }
                val nodeFg = when (node.type) {
                    "PROJECT" -> MaterialTheme.colorScheme.onTertiaryContainer
                    "TAG" -> MaterialTheme.colorScheme.onSecondaryContainer
                    else -> IndigoPrimary
                }

                Surface(
                    color = nodeBg,
                    shape = RoundedCornerShape(8.dp),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier
                        .offset(x = offsetX, y = offsetY)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onNodeClick(node.id) }
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = node.label.take(16) + if (node.label.length > 16) "…" else "",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = nodeFg,
                            maxLines = 1
                        )
                        if (node.subtitle != null) {
                            Text(
                                text = node.subtitle,
                                fontSize = 8.sp,
                                color = nodeFg.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // 3. Center Node
            Surface(
                color = IndigoPrimary,
                shape = CircleShape,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lightbulb,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = graphData.centerNode.label.take(18),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun KnowledgeContextCard(
    item: SavedItemEntity,
    folder: FolderEntity?,
    onUpdateMaturity: (String) -> Unit,
    onScheduleReview: (Long) -> Unit,
    onMarkReviewed: () -> Unit
) {
    var showMaturityMenu by remember { mutableStateOf(false) }
    var showReviewMenu by remember { mutableStateOf(false) }

    val daysAgo = ((System.currentTimeMillis() - item.createdAt) / (24 * 3600 * 1000L)).coerceAtLeast(0)
    val maturityOptions = listOf("CAPTURED", "PROCESSING", "REVIEWED", "UNDERSTOOD", "IMPORTANT", "ARCHIVED")

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Row 1: Belongs to & Maturity Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = null,
                        tint = IndigoPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = folder?.name ?: "📥 Inbox",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Maturity Chip
                Box {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when (item.maturity) {
                            "IMPORTANT" -> AmberWarning.copy(alpha = 0.2f)
                            "UNDERSTOOD", "REVIEWED" -> EmeraldSuccess.copy(alpha = 0.15f)
                            else -> IndigoPrimary.copy(alpha = 0.12f)
                        },
                        modifier = Modifier.clickable { showMaturityMenu = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = item.maturity,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = when (item.maturity) {
                                    "IMPORTANT" -> AmberWarning
                                    "UNDERSTOOD", "REVIEWED" -> EmeraldSuccess
                                    else -> IndigoPrimary
                                }
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showMaturityMenu,
                        onDismissRequest = { showMaturityMenu = false }
                    ) {
                        maturityOptions.forEach { mat ->
                            DropdownMenuItem(
                                text = { Text(mat, fontWeight = if (mat == item.maturity) FontWeight.Bold else FontWeight.Normal) },
                                onClick = {
                                    onUpdateMaturity(mat)
                                    showMaturityMenu = false
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // Row 2: Engagement Activity
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Saved ${daysAgo}d ago • Opened ${item.openCount} times",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Review scheduling button
                Box {
                    if (item.reviewAt != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val due = item.reviewAt <= System.currentTimeMillis()
                            Text(
                                text = if (due) "Review Due" else "Review set",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (due) AmberWarning else IndigoPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(
                                onClick = onMarkReviewed,
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Mark Done", fontSize = 11.sp)
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { showReviewMenu = true },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(Icons.Outlined.Alarm, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Review Later", fontSize = 11.sp)
                        }
                    }

                    DropdownMenu(
                        expanded = showReviewMenu,
                        onDismissRequest = { showReviewMenu = false }
                    ) {
                        val now = System.currentTimeMillis()
                        val day = 24 * 3600 * 1000L
                        DropdownMenuItem(
                            text = { Text("Tomorrow (+1 day)") },
                            onClick = {
                                onScheduleReview(now + day)
                                showReviewMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("In 3 Days") },
                            onClick = {
                                onScheduleReview(now + (3 * day))
                                showReviewMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Next Week (+7 days)") },
                            onClick = {
                                onScheduleReview(now + (7 * day))
                                showReviewMenu = false
                            }
                        )
                    }
                }
            }
        }
    }
}


