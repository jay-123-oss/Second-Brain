package com.example.brain.ui.home

import androidx.compose.foundation.Image
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.brain.R
import com.example.brain.data.entity.FolderEntity
import com.example.brain.data.entity.SavedItemEntity
import com.example.brain.ui.components.BrainSearchBar
import com.example.brain.ui.components.EmptyStateView
import com.example.brain.ui.components.KnowledgeCard
import com.example.brain.ui.theme.AmberWarning
import com.example.brain.ui.theme.EmeraldSuccess
import com.example.brain.ui.theme.IndigoPrimary
import com.example.brain.util.KnowledgeGap
import com.example.brain.util.KnowledgePulse

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToSearch: () -> Unit,
    onNavigateToCapture: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToAskBrain: () -> Unit = {},
    onNavigateToInbox: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White,
                            shadowElevation = 1.dp,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_app_logo),
                                contentDescription = "Second Brain Logo",
                                modifier = Modifier
                                    .padding(3.dp)
                                    .fillMaxSize()
                            )
                        }
                        Column {
                            Text(
                                text = uiState.greeting,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Your Second Brain",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                actions = {
                    if (uiState.inboxCount > 0) {
                        IconButton(
                            onClick = onNavigateToInbox,
                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            BadgedBox(
                                badge = {
                                    Badge {
                                        Text("${uiState.inboxCount}")
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Inbox,
                                    contentDescription = "Inbox (${uiState.inboxCount} unorganized)",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onNavigateToAskBrain,
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Ask Your Brain",
                            tint = IndigoPrimary
                        )
                    }


                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
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
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = IndigoPrimary)
            }
        } else if (uiState.totalCount == 0) {
            EmptyStateView(
                icon = Icons.Outlined.Psychology,
                title = "Your Second Brain starts here",
                description = "Capture your first link, note, image, or document. It will be organized locally and ready to search or connect.",
                actionLabel = "Capture Something",
                onAction = onNavigateToCapture,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // 1. Search Bar Entry
                item {
                    BrainSearchBar(
                        query = "",
                        onQueryChange = {},
                        onSearchClick = { onNavigateToSearch() },
                        placeholder = "Search knowledge, tags, notes...",
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )
                }

                // 2. Quick Capture Action Bar
                item {
                    QuickCaptureBar(
                        onCaptureAll = onNavigateToCapture,
                        onCaptureLink = onNavigateToCapture,
                        onCaptureNote = onNavigateToCapture
                    )
                }

                // 3. Knowledge Pulse Stats (100% Real local data)
                uiState.pulse?.let { pulse ->
                    item {
                        KnowledgePulseRow(
                            pulse = pulse,
                            inboxCount = uiState.inboxCount,
                            onInboxClick = onNavigateToInbox
                        )
                    }
                }

                // 4. Knowledge Gaps (Actionable, Explainable Insights)
                if (uiState.knowledgeGaps.isNotEmpty()) {
                    item {
                        KnowledgeGapsSection(
                            gaps = uiState.knowledgeGaps,
                            onDismiss = { viewModel.dismissGap(it) },
                            onAction = { onNavigateToCapture() }
                        )
                    }
                }

                // 5. Continue Where You Left Off (Recently Opened)
                if (uiState.continueItems.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Continue Reading", icon = Icons.Outlined.History)
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(uiState.continueItems, key = { "continue_${it.id}" }) { item ->
                                ContinueItemCard(item = item, onClick = { onNavigateToDetail(item.id) })
                            }
                        }
                    }
                }

                // 6. Review Due / Revisit Reminder
                if (uiState.reviewDueItems.isNotEmpty()) {
                    item {
                        val reviewItem = uiState.reviewDueItems.first()
                        ReviewDueCard(
                            item = reviewItem,
                            onOpen = { onNavigateToDetail(reviewItem.id) },
                            onMarkReviewed = { viewModel.markItemReviewed(reviewItem) },
                            onSnooze = { viewModel.snoozeReview(reviewItem) }
                        )
                    }
                } else if (uiState.forgottenItems.isNotEmpty()) {
                    item {
                        val forgottenItem = uiState.forgottenItems.first()
                        ForgottenKnowledgeCard(
                            item = forgottenItem,
                            onOpen = { onNavigateToDetail(forgottenItem.id) },
                            onMarkReviewed = { viewModel.markItemReviewed(forgottenItem) }
                        )
                    }
                }

                // 7. Active Projects
                if (uiState.projects.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Projects", icon = Icons.Outlined.Folder)
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(uiState.projects, key = { it.id }) { project ->
                                ProjectChipCard(
                                    project = project,
                                    onClick = { onNavigateToSearch() }
                                )
                            }
                        }
                    }
                }

                // 8. Recently Added Knowledge
                if (uiState.recentItems.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Recently Added", icon = Icons.Outlined.Schedule)
                    }
                    items(uiState.recentItems, key = { it.id }) { item ->
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            KnowledgeCard(
                                item = item,
                                onClick = { onNavigateToDetail(item.id) },
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

// -------------------------------------------------------------
// Sub-components
// -------------------------------------------------------------

@Composable
private fun QuickCaptureBar(
    onCaptureAll: () -> Unit,
    onCaptureLink: () -> Unit,
    onCaptureNote: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Main capture button
        Button(
            onClick = onCaptureAll,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            modifier = Modifier.weight(1.3f)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Capture", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }

        // Quick Note
        OutlinedButton(
            onClick = onCaptureNote,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Outlined.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Note", fontSize = 13.sp)
        }

        // Quick Link
        OutlinedButton(
            onClick = onCaptureLink,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Link", fontSize = 13.sp)
        }
    }
}

@Composable
private fun KnowledgePulseRow(
    pulse: KnowledgePulse,
    inboxCount: Int,
    onInboxClick: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "KNOWLEDGE PULSE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                letterSpacing = 1.sp
            )
            if (inboxCount > 0) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AmberWarning.copy(alpha = 0.15f),
                    modifier = Modifier.clickable { onInboxClick() }
                ) {
                    Text(
                        text = "📥 $inboxCount unorganized",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberWarning,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PulseStatCard(
                label = "Today",
                value = "+${pulse.capturedToday}",
                icon = Icons.Outlined.AddCircleOutline,
                modifier = Modifier.weight(1f)
            )
            PulseStatCard(
                label = "Connections",
                value = "${pulse.totalConnections}",
                icon = Icons.Outlined.Share,
                modifier = Modifier.weight(1f)
            )
            PulseStatCard(
                label = "Projects",
                value = "${pulse.activeProjectsCount}",
                icon = Icons.Outlined.Folder,
                modifier = Modifier.weight(1f)
            )
            PulseStatCard(
                label = "Due Review",
                value = "${pulse.reviewsPendingCount}",
                icon = Icons.Outlined.Alarm,
                tint = if (pulse.reviewsPendingCount > 0) AmberWarning else IndigoPrimary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PulseStatCard(
    label: String,
    value: String,
    icon: ImageVector,
    tint: Color = IndigoPrimary,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun KnowledgeGapsSection(
    gaps: List<KnowledgeGap>,
    onDismiss: (String) -> Unit,
    onAction: () -> Unit
) {
    val topGap = gaps.firstOrNull() ?: return

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = IndigoPrimary.copy(alpha = 0.08f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = IndigoPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "POTENTIAL KNOWLEDGE GAP",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = IndigoPrimary,
                        letterSpacing = 0.5.sp
                    )
                }
                IconButton(
                    onClick = { onDismiss(topGap.id) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Text(
                text = topGap.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = topGap.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(topGap.actionLabel, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ContinueItemCard(
    item: SavedItemEntity,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        modifier = Modifier
            .width(220.dp)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = IndigoPrimary.copy(alpha = 0.12f)
            ) {
                Text(
                    text = item.type,
                    style = MaterialTheme.typography.labelSmall,
                    color = IndigoPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Text(
                text = item.title.ifBlank { "Untitled" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.notes.ifBlank { item.description ?: "Saved knowledge item" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ReviewDueCard(
    item: SavedItemEntity,
    onOpen: () -> Unit,
    onMarkReviewed: () -> Unit,
    onSnooze: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AmberWarning.copy(alpha = 0.10f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Outlined.Alarm, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(18.dp))
                Text("DUE FOR REVIEW", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = AmberWarning)
            }
            Text(item.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(
                text = "You scheduled this knowledge item to be revisited today.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onOpen,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Open & Review", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onMarkReviewed,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Mark Done", fontSize = 12.sp)
                }
                TextButton(
                    onClick = onSnooze,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text("Snooze", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ForgottenKnowledgeCard(
    item: SavedItemEntity,
    onOpen: () -> Unit,
    onMarkReviewed: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(18.dp))
                Text("WORTH REVISITING", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = IndigoPrimary)
            }
            Text(item.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(
                text = "Saved some time ago and hasn't been opened recently.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onOpen,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Revisit", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onMarkReviewed,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Mark Reviewed", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ProjectChipCard(
    project: FolderEntity,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Outlined.Folder, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(18.dp))
            Text(project.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
