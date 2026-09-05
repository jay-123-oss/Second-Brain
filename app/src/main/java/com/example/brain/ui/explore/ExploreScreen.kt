package com.example.brain.ui.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.brain.data.entity.CollectionEntity
import com.example.brain.data.entity.SavedItemEntity
import com.example.brain.ui.components.EmptyStateView
import com.example.brain.ui.components.KnowledgeCard
import com.example.brain.ui.home.SectionHeader
import com.example.brain.ui.theme.IndigoPrimary
import com.example.brain.util.DiscoveredTopic
import com.example.brain.util.SmartCollectionRule
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    viewModel: ExploreViewModel,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToSearch: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCreateCollectionDialog by remember { mutableStateOf(false) }

    val topBarTitle = when {
        uiState.selectedTopic != null -> uiState.selectedTopic!!.name
        uiState.selectedCollection != null -> uiState.selectedCollection!!.name
        uiState.selectedTag != null -> "#${uiState.selectedTag}"
        else -> "Explore & Intelligence"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = topBarTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    if (uiState.selectedTopic != null) {
                        IconButton(onClick = { viewModel.selectTopic(null) }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back to Topics")
                        }
                    } else if (uiState.selectedCollection != null) {
                        IconButton(onClick = { viewModel.selectCollection(null) }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back to Collections")
                        }
                    } else if (uiState.selectedTag != null) {
                        IconButton(onClick = { viewModel.selectTag(null) }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back to Explore")
                        }
                    }
                },
                actions = {
                    if (uiState.activeTab == ExploreTab.COLLECTIONS && uiState.selectedCollection == null) {
                        IconButton(onClick = { showCreateCollectionDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "New Collection", tint = IndigoPrimary)
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
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = IndigoPrimary)
            }
        } else if (uiState.selectedTopic != null) {
            // Topic Detail View
            TopicDetailView(
                topic = uiState.selectedTopic!!,
                items = uiState.selectedTopicItems,
                onNavigateToDetail = onNavigateToDetail,
                onToggleFavorite = { viewModel.toggleFavorite(it) },
                onToggleReadLater = { viewModel.toggleReadLater(it) },
                modifier = Modifier.padding(innerPadding)
            )
        } else if (uiState.selectedCollection != null) {
            // Collection Detail View
            CollectionDetailView(
                collection = uiState.selectedCollection!!,
                items = uiState.selectedCollectionItems,
                onNavigateToDetail = onNavigateToDetail,
                onDeleteCollection = { viewModel.deleteCollection(uiState.selectedCollection!!) },
                onToggleFavorite = { viewModel.toggleFavorite(it) },
                onToggleReadLater = { viewModel.toggleReadLater(it) },
                modifier = Modifier.padding(innerPadding)
            )
        } else if (uiState.selectedTag != null) {
            // Tag items view
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "${uiState.selectedTagItems.size} items tagged with #${uiState.selectedTag}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(uiState.selectedTagItems, key = { it.id }) { item ->
                    KnowledgeCard(
                        item = item,
                        onClick = { onNavigateToDetail(item.id) },
                        onToggleFavorite = { viewModel.toggleFavorite(item) },
                        onToggleReadLater = { viewModel.toggleReadLater(item) }
                    )
                }
            }
        } else {
            // Main Hub with Tabs
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Secondary Tab Row
                TabRow(
                    selectedTabIndex = uiState.activeTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = IndigoPrimary
                ) {
                    Tab(
                        selected = uiState.activeTab == ExploreTab.DISCOVER,
                        onClick = { viewModel.setTab(ExploreTab.DISCOVER) },
                        text = { Text("Discover") },
                        icon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = uiState.activeTab == ExploreTab.TOPICS,
                        onClick = { viewModel.setTab(ExploreTab.TOPICS) },
                        text = { Text("Topics") },
                        icon = { Icon(Icons.Outlined.Tag, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = uiState.activeTab == ExploreTab.COLLECTIONS,
                        onClick = { viewModel.setTab(ExploreTab.COLLECTIONS) },
                        text = { Text("Collections") },
                        icon = { Icon(Icons.Outlined.Bookmarks, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = uiState.activeTab == ExploreTab.TIMELINE,
                        onClick = { viewModel.setTab(ExploreTab.TIMELINE) },
                        text = { Text("Timeline") },
                        icon = { Icon(Icons.Outlined.Timeline, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }

                when (uiState.activeTab) {
                    ExploreTab.DISCOVER -> DiscoverTabView(
                        uiState = uiState,
                        onNavigateToDetail = onNavigateToDetail,
                        onKeepForgotten = { viewModel.keepForgottenItem(it) },
                        onSelectTopic = { viewModel.selectTopic(it) },
                        onNavigateToSearch = onNavigateToSearch
                    )

                    ExploreTab.TOPICS -> TopicsTabView(
                        topics = uiState.discoveredTopics,
                        tags = uiState.tagsWithCount,
                        onSelectTopic = { viewModel.selectTopic(it) },
                        onSelectTag = { viewModel.selectTag(it) }
                    )
                    ExploreTab.COLLECTIONS -> CollectionsTabView(
                        smartCollections = uiState.smartCollections,
                        manualCollections = uiState.manualCollections,
                        onSelectCollection = { viewModel.selectCollection(it) },
                        onCreateCollection = { showCreateCollectionDialog = true }
                    )
                    ExploreTab.TIMELINE -> TimelineTabView(
                        items = uiState.filteredTimelineItems,
                        projects = uiState.projects,
                        selectedType = uiState.timelineTypeFilter,
                        selectedProject = uiState.timelineProjectFilter,
                        onFilterChanged = { t, p -> viewModel.filterTimeline(t, p) },
                        onNavigateToDetail = onNavigateToDetail
                    )
                }
            }
        }
    }

    if (showCreateCollectionDialog) {
        CreateCollectionDialog(
            projects = uiState.projects,
            tags = uiState.tagsWithCount.map { it.name },
            onDismiss = { showCreateCollectionDialog = false },
            onCreate = { name, desc, icon, color, isSmart, rule ->
                viewModel.createCollection(name, desc, icon, color, isSmart, rule)
                showCreateCollectionDialog = false
            }
        )
    }
}

/**
 * Tab 1: Discover Hub
 */
@Composable
fun DiscoverTabView(
    uiState: ExploreUiState,
    onNavigateToDetail: (String) -> Unit,
    onKeepForgotten: (SavedItemEntity) -> Unit,
    onSelectTopic: (DiscoveredTopic) -> Unit,
    onNavigateToSearch: () -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. Trending Collection Focus Banner ("You Keep Collecting")
        if (uiState.trendingTopics.isNotEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(14.dp),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(IndigoPrimary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.TrendingUp, contentDescription = null, tint = IndigoPrimary)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "You Keep Collecting",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Growing focus on ${uiState.trendingTopics.take(3).joinToString(", ") { "#$it" }} over recent weeks.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }
        }

        // 2. Continue Exploring (Related to recently added/viewed items)
        if (uiState.continueExploring.isNotEmpty()) {
            item {
                SectionHeader(title = "Continue Exploring", icon = Icons.Outlined.Explore)
            }
            items(uiState.continueExploring, key = { it.item.id }) { candidate ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToDetail(candidate.item.id) }
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
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Badge(containerColor = IndigoPrimary.copy(alpha = 0.15f)) {
                                Text(
                                    text = "${(candidate.confidence * 100).toInt()}% match",
                                    color = IndigoPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Related because: ${candidate.explanation}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 2b. Domain & Source Intelligence (Prompt 10)
        if (uiState.domainSources.isNotEmpty()) {
            item {
                SectionHeader(title = "Source Intelligence", icon = Icons.Outlined.Language)
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.domainSources, key = { it.domain }) { source ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = CardDefaults.outlinedCardBorder(),
                            modifier = Modifier.clickable { onNavigateToSearch() }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = source.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${source.itemCount} items • ${source.category}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Forgotten Knowledge (>60 days inactive)
        if (uiState.forgottenItems.isNotEmpty()) {
            item {
                SectionHeader(title = "Forgotten Knowledge", icon = Icons.Outlined.History)
            }
            item {
                Text(
                    text = "Items saved over 60 days ago that you haven't opened or revisited:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(uiState.forgottenItems.take(4), key = { it.id }) { item ->
                val daysAgo = ((System.currentTimeMillis() - item.createdAt) / (1000 * 60 * 60 * 24)).coerceAtLeast(1)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Saved $daysAgo days ago • Untouched since",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { onKeepForgotten(item) }) {
                                Text("Keep", fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = { onNavigateToDetail(item.id) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                            ) {
                                Text("Revisit", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // 4. Unexplored Inbox Knowledge
        if (uiState.unexploredItems.isNotEmpty()) {
            item {
                SectionHeader(title = "Unexplored Knowledge", icon = Icons.Outlined.Inbox)
            }
            items(uiState.unexploredItems.take(3), key = { it.id }) { item ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToDetail(item.id) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Outlined.Markunread, contentDescription = null, tint = IndigoPrimary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Awaiting notes and organization",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Empty state when knowledge base is brand new
        if (uiState.continueExploring.isEmpty() && uiState.forgottenItems.isEmpty() && uiState.unexploredItems.isEmpty()) {
            item {
                EmptyStateView(
                    icon = Icons.Outlined.Lightbulb,
                    title = "Your Knowledge Map Will Grow",
                    description = "Save a few items and Second Brain will begin discovering connections, forgotten knowledge, and thematic clusters."
                )
            }
        }
    }
}

/**
 * Tab 2: Discovered Topics View
 */
@Composable
fun TopicsTabView(
    topics: List<DiscoveredTopic>,
    tags: List<com.example.brain.data.dao.TagWithCount>,
    onSelectTopic: (DiscoveredTopic) -> Unit,
    onSelectTag: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (topics.isNotEmpty()) {
            item {
                SectionHeader(title = "Thematic Topics", icon = Icons.Outlined.Hub)
            }
            items(topics, key = { it.name }) { topic ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectTopic(topic) }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = topic.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Badge(containerColor = IndigoPrimary.copy(alpha = 0.15f)) {
                                Text(
                                    text = "${topic.itemCount} items",
                                    color = IndigoPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (topic.relatedProjects.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Projects: ${topic.relatedProjects.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (topic.commonTags.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(topic.commonTags) { tag ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = tag,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Lightweight Tags List
        if (tags.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader(title = "All Tags", icon = Icons.Outlined.Label)
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tags, key = { it.name }) { tagCount ->
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelectTag(tagCount.name) },
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("#${tagCount.name}", style = MaterialTheme.typography.labelSmall, color = IndigoPrimary, fontWeight = FontWeight.SemiBold)
                                Text("${tagCount.count}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        if (topics.isEmpty() && tags.isEmpty()) {
            item {
                EmptyStateView(
                    icon = Icons.Outlined.Tag,
                    title = "No Topics Discovered Yet",
                    description = "Save and tag knowledge items to let Second Brain automatically identify thematic topics."
                )
            }
        }
    }
}

/**
 * Topic Detail Screen
 */
@Composable
fun TopicDetailView(
    topic: DiscoveredTopic,
    items: List<SavedItemEntity>,
    onNavigateToDetail: (String) -> Unit,
    onToggleFavorite: (SavedItemEntity) -> Unit,
    onToggleReadLater: (SavedItemEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Topic Overview Card
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = topic.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${topic.itemCount} items collected • ${topic.relatedProjects.size} related projects",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Historical Growth (Evolution)
                    if (topic.growthHistory.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "TOPIC EVOLUTION (MONTHLY)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = IndigoPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(topic.growthHistory.entries.toList()) { (month, count) ->
                                Surface(
                                    color = IndigoPrimary.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(month, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("$count items", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IndigoPrimary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Knowledge Items in Topic (${items.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        items(items, key = { it.id }) { item ->
            KnowledgeCard(
                item = item,
                onClick = { onNavigateToDetail(item.id) },
                onToggleFavorite = { onToggleFavorite(item) },
                onToggleReadLater = { onToggleReadLater(item) }
            )
        }
    }
}

/**
 * Tab 3: Curated & Smart Collections View
 */
@Composable
fun CollectionsTabView(
    smartCollections: List<CollectionEntity>,
    manualCollections: List<CollectionEntity>,
    onSelectCollection: (CollectionEntity) -> Unit,
    onCreateCollection: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Smart Collections Section
        item {
            SectionHeader(title = "Smart Collections (Rule-Based)", icon = Icons.Outlined.AutoFixHigh)
        }

        if (smartCollections.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No smart collections created yet. Smart collections automatically aggregate knowledge matching specific rules.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        } else {
            items(smartCollections, key = { it.id }) { col ->
                CollectionCard(collection = col, onClick = { onSelectCollection(col) })
            }
        }

        // Curated Collections Section
        item {
            Spacer(modifier = Modifier.height(8.dp))
            SectionHeader(title = "Curated Collections", icon = Icons.Outlined.CollectionsBookmark)
        }

        if (manualCollections.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No curated collections. Tap the + icon to create your first curated reading queue or paper set.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        } else {
            items(manualCollections, key = { it.id }) { col ->
                CollectionCard(collection = col, onClick = { onSelectCollection(col) })
            }
        }
    }
}

@Composable
fun CollectionCard(
    collection: CollectionEntity,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(collection.icon, fontSize = 26.sp)
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = collection.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (collection.isSmart) {
                        Surface(
                            color = IndigoPrimary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "SMART",
                                color = IndigoPrimary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                if (collection.description.isNotBlank()) {
                    Text(
                        text = collection.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Collection Detail View
 */
@Composable
fun CollectionDetailView(
    collection: CollectionEntity,
    items: List<SavedItemEntity>,
    onNavigateToDetail: (String) -> Unit,
    onDeleteCollection: () -> Unit,
    onToggleFavorite: (SavedItemEntity) -> Unit,
    onToggleReadLater: (SavedItemEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(collection.icon, fontSize = 28.sp)
                            Column {
                                Text(
                                    text = collection.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (collection.isSmart) "Smart Collection • Rule-Based" else "Curated Collection",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = IndigoPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete Collection", tint = MaterialTheme.colorScheme.error)
                        }
                    }

                    if (collection.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = collection.description, style = MaterialTheme.typography.bodyMedium)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Deleting this collection will NOT delete the underlying saved items.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Text(
                text = "Items in Collection (${items.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        if (items.isEmpty()) {
            item {
                Text(
                    text = "No items in this collection currently match the criteria.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(items, key = { it.id }) { item ->
                KnowledgeCard(
                    item = item,
                    onClick = { onNavigateToDetail(item.id) },
                    onToggleFavorite = { onToggleFavorite(item) },
                    onToggleReadLater = { onToggleReadLater(item) }
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Collection?") },
            text = {
                Text("Are you sure you want to delete '${collection.name}'? Your actual saved knowledge items will remain safe and untouched.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteCollection()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Collection")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

/**
 * Tab 4: Filterable Timeline View
 */
@Composable
fun TimelineTabView(
    items: List<SavedItemEntity>,
    projects: List<com.example.brain.data.entity.FolderEntity>,
    selectedType: String,
    selectedProject: String,
    onFilterChanged: (type: String, project: String) -> Unit,
    onNavigateToDetail: (String) -> Unit
) {
    val typeFilters = listOf("ALL" to "All Types", "LINK" to "Links", "NOTE" to "Notes", "PDF" to "PDFs", "IMAGE" to "Images")

    Column(modifier = Modifier.fillMaxSize()) {
        // Filter Chips Row
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(typeFilters) { (typeKey, label) ->
                FilterChip(
                    selected = selectedType == typeKey,
                    onClick = { onFilterChanged(typeKey, selectedProject) },
                    label = { Text(label, fontSize = 11.sp) },
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        if (items.isEmpty()) {
            EmptyStateView(
                icon = Icons.Outlined.Timeline,
                title = "No Items in Timeline",
                description = "Knowledge will be charted chronologically as you capture."
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    TimelineRow(item = item, onClick = { onNavigateToDetail(item.id) })
                }
            }
        }
    }
}

@Composable
fun TimelineRow(
    item: SavedItemEntity,
    onClick: () -> Unit
) {
    val dateStr = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()).format(Date(item.createdAt))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(IndigoPrimary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AddCircleOutline,
                contentDescription = null,
                tint = IndigoPrimary,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Captured $dateStr",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun CreateCollectionDialog(
    projects: List<com.example.brain.data.entity.FolderEntity>,
    tags: List<String>,
    onDismiss: () -> Unit,
    onCreate: (name: String, desc: String, icon: String, color: String, isSmart: Boolean, rule: SmartCollectionRule?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("📚") }
    var isSmart by remember { mutableStateOf(false) }

    // Smart collection rule criteria
    var ruleTag by remember { mutableStateOf("") }
    var ruleType by remember { mutableStateOf("") }
    var ruleFavoriteOnly by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Collection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Collection Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Smart Collection (Rule-Based)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Switch(checked = isSmart, onCheckedChange = { isSmart = it })
                }

                if (isSmart) {
                    Text("Auto-aggregate items matching rules:", style = MaterialTheme.typography.labelSmall, color = IndigoPrimary)

                    // Tag picker
                    if (tags.isNotEmpty()) {
                        Text("Match Tag:", style = MaterialTheme.typography.labelSmall)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(tags.take(6)) { t ->
                                val isSel = ruleTag.equals(t, ignoreCase = true)
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { ruleTag = if (isSel) "" else t },
                                    color = if (isSel) IndigoPrimary else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "#$t",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    // Type picker
                    Text("Match Content Type:", style = MaterialTheme.typography.labelSmall)
                    val types = listOf("LINK", "NOTE", "PDF", "IMAGE")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(types) { t ->
                            val isSel = ruleType == t
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { ruleType = if (isSel) "" else t },
                                color = if (isSel) IndigoPrimary else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = t,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Only Favorites", style = MaterialTheme.typography.bodySmall)
                        Checkbox(checked = ruleFavoriteOnly, onCheckedChange = { ruleFavoriteOnly = it })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rule = if (isSmart) {
                        SmartCollectionRule(
                            tag = if (ruleTag.isNotBlank()) ruleTag else null,
                            contentType = if (ruleType.isNotBlank()) ruleType else null,
                            isFavorite = if (ruleFavoriteOnly) true else null
                        )
                    } else null
                    onCreate(name, description, icon, "#6366F1", isSmart, rule)
                },
                enabled = name.isNotBlank(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
