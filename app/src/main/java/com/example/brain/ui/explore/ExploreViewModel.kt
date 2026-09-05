package com.example.brain.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.brain.data.dao.TagWithCount
import com.example.brain.data.entity.CollectionEntity
import com.example.brain.data.entity.FolderEntity
import com.example.brain.data.entity.ItemRelationEntity
import com.example.brain.data.entity.SavedItemEntity
import com.example.brain.data.repository.AppRepository
import com.example.brain.util.DiscoveredTopic
import com.example.brain.util.KnowledgeIntelligenceEngine
import com.example.brain.util.RelationshipCandidate
import com.example.brain.util.SmartCollectionRule
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

enum class ExploreTab {
    DISCOVER,
    TOPICS,
    COLLECTIONS,
    TIMELINE
}

data class ExploreUiState(
    val isLoading: Boolean = true,
    val activeTab: ExploreTab = ExploreTab.DISCOVER,
    val tagsWithCount: List<TagWithCount> = emptyList(),
    val collections: List<CollectionEntity> = emptyList(),
    val smartCollections: List<CollectionEntity> = emptyList(),
    val manualCollections: List<CollectionEntity> = emptyList(),
    val projects: List<FolderEntity> = emptyList(),
    val inboxItems: List<SavedItemEntity> = emptyList(),
    val timelineItems: List<SavedItemEntity> = emptyList(),
    val filteredTimelineItems: List<SavedItemEntity> = emptyList(),
    val timelineTypeFilter: String = "ALL",
    val timelineProjectFilter: String = "ALL",

    // Discovered Topics
    val discoveredTopics: List<DiscoveredTopic> = emptyList(),
    val selectedTopic: DiscoveredTopic? = null,
    val selectedTopicItems: List<SavedItemEntity> = emptyList(),

    // Discovery Hub Signals
    val continueExploring: List<RelationshipCandidate> = emptyList(),
    val trendingTopics: List<String> = emptyList(),
    val unexploredItems: List<SavedItemEntity> = emptyList(),
    val forgottenItems: List<SavedItemEntity> = emptyList(),
    val connectedItems: List<SavedItemEntity> = emptyList(),
    val recentRelations: List<ItemRelationEntity> = emptyList(),

    // Domain & Source Intelligence (Prompt 10)
    val domainSources: List<com.example.brain.util.DomainSourceInfo> = emptyList(),

    // Collection Detail
    val selectedCollection: CollectionEntity? = null,
    val selectedCollectionItems: List<SavedItemEntity> = emptyList(),

    val selectedTagItems: List<SavedItemEntity> = emptyList(),
    val selectedTag: String? = null
)

class ExploreViewModel(private val repository: AppRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    private var allActiveItems: List<SavedItemEntity> = emptyList()
    private var allTagsMap: Map<String, List<String>> = emptyMap()

    init {
        loadExploreData()
    }

    private fun loadExploreData() {
        viewModelScope.launch {
            combine(
                repository.getTagsWithCount(),
                repository.getAllCollections(),
                repository.getAllFolders(),
                repository.getActiveItems(),
                repository.getRecentlyConnectedRelations(8)
            ) { tags, collections, folders, activeItems, recentRels ->
                allActiveItems = activeItems
                val nonSecretItems = activeItems.filter { !it.isSecret }
                val projects = folders.filter { it.id != "inbox_default_id" }
                val inbox = nonSecretItems.filter { it.folderId == "inbox_default_id" }

                // Build tags map for items
                val tagsMap = mutableMapOf<String, List<String>>()
                for (item in nonSecretItems) {
                    tagsMap[item.id] = repository.getTagsForItemSync(item.id).map { it.name }
                }
                allTagsMap = tagsMap

                // 1. Topics Detection
                val topics = KnowledgeIntelligenceEngine.detectTopics(
                    items = nonSecretItems,
                    tagsMap = tagsMap,
                    projects = projects
                )

                // 2. Forgotten Knowledge
                val forgotten = KnowledgeIntelligenceEngine.detectForgottenKnowledge(nonSecretItems, thresholdDays = 60)

                // 3. Unexplored Knowledge
                val unexplored = KnowledgeIntelligenceEngine.detectUnexploredKnowledge(nonSecretItems)

                // 4. Trending Topics (You keep collecting)
                val trending = KnowledgeIntelligenceEngine.detectTrendingTopics(nonSecretItems, tagsMap, days = 14)

                // 5. Continue Exploring (Related to most recent item)
                val mostRecent = nonSecretItems.maxByOrNull { it.createdAt }
                val continueExploringCandidates = if (mostRecent != null && nonSecretItems.size > 1) {
                    KnowledgeIntelligenceEngine.findRelatedCandidates(
                        target = mostRecent,
                        candidates = nonSecretItems,
                        targetTags = tagsMap[mostRecent.id] ?: emptyList(),
                        projectsMap = folders.associate { it.id to it.name },
                        limit = 3
                    )
                } else {
                    emptyList()
                }

                // 6. Connected Items (Top connected in recent relations)
                val connectedIds = recentRels.flatMap { listOf(it.sourceItemId, it.targetItemId) }.distinct()
                val connectedList = nonSecretItems.filter { it.id in connectedIds }

                val smartCols = collections.filter { it.isSmart }
                val manualCols = collections.filter { !it.isSmart }

                ExploreUiState(
                    isLoading = false,
                    activeTab = _uiState.value.activeTab,
                    tagsWithCount = tags,
                    collections = collections,
                    smartCollections = smartCols,
                    manualCollections = manualCols,
                    projects = projects,
                    inboxItems = inbox,
                    timelineItems = nonSecretItems.sortedByDescending { it.createdAt },
                    filteredTimelineItems = filterTimelineItems(
                        nonSecretItems.sortedByDescending { it.createdAt },
                        _uiState.value.timelineTypeFilter,
                        _uiState.value.timelineProjectFilter
                    ),
                    timelineTypeFilter = _uiState.value.timelineTypeFilter,
                    timelineProjectFilter = _uiState.value.timelineProjectFilter,
                    discoveredTopics = topics,
                    trendingTopics = trending,
                    forgottenItems = forgotten,
                    unexploredItems = unexplored,
                    continueExploring = continueExploringCandidates,
                    connectedItems = connectedList,
                    recentRelations = recentRels,
                    domainSources = com.example.brain.util.KnowledgeOSEngine.analyzeDomains(nonSecretItems),
                    selectedTopic = _uiState.value.selectedTopic,
                    selectedTopicItems = _uiState.value.selectedTopicItems,
                    selectedCollection = _uiState.value.selectedCollection,
                    selectedCollectionItems = _uiState.value.selectedCollectionItems,
                    selectedTag = _uiState.value.selectedTag,
                    selectedTagItems = _uiState.value.selectedTagItems
                )
            }.collect { state ->
                _uiState.update { current ->
                    state.copy(
                        activeTab = current.activeTab,
                        selectedTopic = current.selectedTopic,
                        selectedTopicItems = current.selectedTopicItems,
                        selectedCollection = current.selectedCollection,
                        selectedCollectionItems = current.selectedCollectionItems,
                        selectedTag = current.selectedTag,
                        selectedTagItems = current.selectedTagItems,
                        timelineTypeFilter = current.timelineTypeFilter,
                        timelineProjectFilter = current.timelineProjectFilter
                    )
                }
            }
        }
    }

    fun setTab(tab: ExploreTab) {
        _uiState.update { it.copy(activeTab = tab, selectedTopic = null, selectedCollection = null, selectedTag = null) }
    }

    fun selectTopic(topic: DiscoveredTopic?) {
        if (topic == null) {
            _uiState.update { it.copy(selectedTopic = null, selectedTopicItems = emptyList()) }
            return
        }
        val topicNameLower = topic.name.lowercase()
        val itemsInTopic = allActiveItems.filter { item ->
            val tags = allTagsMap[item.id] ?: emptyList()
            val hasTag = tags.any { it.equals(topicNameLower, ignoreCase = true) }
            val inTitle = item.title.contains(topicNameLower, ignoreCase = true)
            hasTag || inTitle
        }
        _uiState.update { it.copy(selectedTopic = topic, selectedTopicItems = itemsInTopic) }
    }

    fun selectCollection(collection: CollectionEntity?) {
        if (collection == null) {
            _uiState.update { it.copy(selectedCollection = null, selectedCollectionItems = emptyList()) }
            return
        }

        viewModelScope.launch {
            if (collection.isSmart && !collection.ruleJson.isNullOrBlank()) {
                val rule = SmartCollectionRule.fromJson(collection.ruleJson)
                val matching = allActiveItems.filter { item ->
                    val tags = allTagsMap[item.id] ?: emptyList()
                    rule.matches(item, tags)
                }
                _uiState.update { it.copy(selectedCollection = collection, selectedCollectionItems = matching) }
            } else {
                repository.getItemsInCollection(collection.id).collect { items ->
                    _uiState.update { it.copy(selectedCollection = collection, selectedCollectionItems = items) }
                }
            }
        }
    }

    fun createCollection(
        name: String,
        description: String,
        icon: String = "📚",
        colorHex: String = "#6366F1",
        isSmart: Boolean = false,
        rule: SmartCollectionRule? = null
    ) {
        viewModelScope.launch {
            val newCol = CollectionEntity(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                description = description.trim(),
                icon = icon,
                colorHex = colorHex,
                isSmart = isSmart,
                ruleJson = if (isSmart && rule != null) rule.toJson() else null,
                createdAt = System.currentTimeMillis()
            )
            repository.insertCollection(newCol)
        }
    }

    fun deleteCollection(collection: CollectionEntity) {
        viewModelScope.launch {
            repository.deleteCollection(collection)
            if (_uiState.value.selectedCollection?.id == collection.id) {
                _uiState.update { it.copy(selectedCollection = null, selectedCollectionItems = emptyList()) }
            }
        }
    }

    fun keepForgottenItem(item: SavedItemEntity) {
        viewModelScope.launch {
            // Touch updatedAt to now so it is kept active
            repository.updateNotes(item.id, item.notes)
            _uiState.update { current ->
                current.copy(forgottenItems = current.forgottenItems.filter { it.id != item.id })
            }
        }
    }

    fun selectTag(tagName: String?) {
        _uiState.update { it.copy(selectedTag = tagName) }
        if (tagName != null) {
            viewModelScope.launch {
                repository.getItemsForTag(tagName).collect { items ->
                    _uiState.update { it.copy(selectedTagItems = items) }
                }
            }
        } else {
            _uiState.update { it.copy(selectedTagItems = emptyList()) }
        }
    }

    fun filterTimeline(type: String, project: String) {
        _uiState.update { current ->
            current.copy(
                timelineTypeFilter = type,
                timelineProjectFilter = project,
                filteredTimelineItems = filterTimelineItems(current.timelineItems, type, project)
            )
        }
    }

    private fun filterTimelineItems(items: List<SavedItemEntity>, type: String, project: String): List<SavedItemEntity> {
        return items.filter { item ->
            val matchesType = type == "ALL" || item.type.equals(type, ignoreCase = true)
            val matchesProject = project == "ALL" || item.folderId == project
            matchesType && matchesProject
        }
    }

    fun toggleFavorite(item: SavedItemEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(item.id, !item.isFavorite)
        }
    }

    fun toggleReadLater(item: SavedItemEntity) {
        viewModelScope.launch {
            repository.toggleReadLater(item.id, !item.isReadLater)
        }
    }
}

class ExploreViewModelFactory(private val repository: AppRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ExploreViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ExploreViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

