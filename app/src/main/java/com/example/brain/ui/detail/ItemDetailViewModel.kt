package com.example.brain.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.brain.data.entity.FolderEntity
import com.example.brain.data.entity.ItemRelationEntity
import com.example.brain.data.entity.SavedItemEntity
import com.example.brain.data.entity.TagEntity
import com.example.brain.data.repository.AppRepository
import com.example.brain.util.DeterministicLocalAIEngine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ItemDetailUiState(
    val item: SavedItemEntity? = null,
    val folder: FolderEntity? = null,
    val availableFolders: List<FolderEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val relations: List<ItemRelationEntity> = emptyList(),
    val suggestedCandidates: List<com.example.brain.util.RelationshipCandidate> = emptyList(),
    val allItemsForLinking: List<SavedItemEntity> = emptyList(),
    val graphData: com.example.brain.util.ItemGraphData? = null,
    val isGraphViewMode: Boolean = false,
    val concepts: List<String> = emptyList(),
    val isSummarizing: Boolean = false,
    val isDeleted: Boolean = false,
    val isLoading: Boolean = true
)

class ItemDetailViewModel(
    private val itemId: String,
    private val repository: AppRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ItemDetailUiState())
    val uiState: StateFlow<ItemDetailUiState> = _uiState.asStateFlow()

    init {
        loadItemDetails()
        loadAvailableFolders()
    }

    private fun loadAvailableFolders() {
        viewModelScope.launch {
            repository.getAllFolders().collect { folders ->
                _uiState.update { it.copy(availableFolders = folders) }
            }
        }
    }

    private fun loadItemDetails() {
        viewModelScope.launch {
            repository.recordItemOpened(itemId)
            val item = repository.getItemById(itemId)
            if (item == null) {
                _uiState.update { it.copy(isLoading = false, isDeleted = true) }
                return@launch
            }

            val folder = repository.getFolderById(item.folderId)
            val tags = repository.getTagsForItemSync(item.id)
            val dismissedIds = repository.getDismissedRelationItemIds(item.id).toSet()

            combine(
                repository.getRelationsForItem(item.id),
                repository.getActiveItems(),
                repository.getAllFolders()
            ) { relations, allActive, folders ->
                val projectsMap = folders.associate { it.id to it.name }
                val activeItemsMap = allActive.associateBy { it.id }

                val candidates = if (item.isSecret) {
                    emptyList()
                } else {
                    com.example.brain.util.KnowledgeIntelligenceEngine.findRelatedCandidates(
                        target = item,
                        candidates = allActive,
                        targetTags = tags.map { it.name },
                        targetProjectName = folder?.name,
                        projectsMap = projectsMap,
                        dismissedIds = dismissedIds,
                        limit = 4
                    )
                }

                val graph = if (item.isSecret) {
                    null
                } else {
                    com.example.brain.util.KnowledgeIntelligenceEngine.buildItemGraph(
                        currentItem = item,
                        relations = relations,
                        allItemsMap = activeItemsMap,
                        projectName = folder?.name,
                        tags = tags.map { it.name }
                    )
                }

                val concepts = if (item.isSecret) {
                    emptyList()
                } else {
                    DeterministicLocalAIEngine.extractConcepts(item)
                }

                _uiState.value.copy(
                    item = item,
                    folder = folder,
                    tags = tags,
                    relations = relations,
                    suggestedCandidates = candidates,
                    allItemsForLinking = if (item.isSecret) emptyList() else allActive.filter { it.id != item.id },
                    graphData = graph,
                    concepts = concepts,
                    isLoading = false
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun toggleGraphViewMode() {
        _uiState.update { it.copy(isGraphViewMode = !it.isGraphViewMode) }
    }

    fun moveToFolder(newFolderId: String) {
        viewModelScope.launch {
            repository.moveItemToFolder(itemId, newFolderId)
            val updated = repository.getItemById(itemId)
            val folder = repository.getFolderById(newFolderId)
            _uiState.update { it.copy(item = updated, folder = folder) }
        }
    }

    fun updateNotes(newNotes: String) {
        viewModelScope.launch {
            repository.updateNotes(itemId, newNotes)
            val updated = repository.getItemById(itemId)
            _uiState.update { it.copy(item = updated) }
        }
    }

    fun toggleFavorite() {
        val current = _uiState.value.item ?: return
        viewModelScope.launch {
            repository.toggleFavorite(itemId, !current.isFavorite)
            val updated = repository.getItemById(itemId)
            _uiState.update { it.copy(item = updated) }
        }
    }

    fun toggleReadLater() {
        val current = _uiState.value.item ?: return
        viewModelScope.launch {
            repository.toggleReadLater(itemId, !current.isReadLater)
            val updated = repository.getItemById(itemId)
            _uiState.update { it.copy(item = updated) }
        }
    }

    fun addTag(tagName: String) {
        viewModelScope.launch {
            repository.addTagToItem(itemId, tagName)
            val updatedTags = repository.getTagsForItemSync(itemId)
            _uiState.update { it.copy(tags = updatedTags) }
        }
    }

    fun removeTag(tagName: String) {
        viewModelScope.launch {
            repository.removeTagFromItem(itemId, tagName)
            val updatedTags = repository.getTagsForItemSync(itemId)
            _uiState.update { it.copy(tags = updatedTags) }
        }
    }

    fun addRelation(targetId: String, relationType: String) {
        viewModelScope.launch {
            repository.addRelation(
                sourceId = itemId,
                targetId = targetId,
                relationType = relationType,
                origin = "USER_CREATED",
                explanation = "Manually connected by user",
                confidence = 1.0f
            )
        }
    }

    fun acceptSuggestion(candidate: com.example.brain.util.RelationshipCandidate) {
        viewModelScope.launch {
            repository.addRelation(
                sourceId = itemId,
                targetId = candidate.item.id,
                relationType = candidate.suggestedType,
                origin = "USER_CREATED",
                explanation = candidate.explanation,
                confidence = candidate.confidence
            )
        }
    }

    fun dismissSuggestion(candidate: com.example.brain.util.RelationshipCandidate) {
        viewModelScope.launch {
            repository.dismissSuggestion(
                sourceId = itemId,
                targetId = candidate.item.id,
                relationType = candidate.suggestedType
            )
            // Immediately filter out candidate from UI
            _uiState.update { current ->
                current.copy(
                    suggestedCandidates = current.suggestedCandidates.filter { it.item.id != candidate.item.id }
                )
            }
        }
    }

    fun removeRelation(relation: ItemRelationEntity) {
        viewModelScope.launch {
            repository.deleteRelation(relation.sourceItemId, relation.targetItemId, relation.relationType)
        }
    }

    fun retryEnrichment(context: android.content.Context) {
        val current = _uiState.value.item ?: return
        if (current.isSecret) return
        viewModelScope.launch {
            repository.updateProcessingStatus(itemId, "QUEUED")
            com.example.brain.worker.ContentEnrichmentWorker.schedule(
                context = context,
                itemId = itemId,
                requiresNetwork = (current.type == "LINK")
            )
            val updated = repository.getItemById(itemId)
            _uiState.update { it.copy(item = updated) }
        }
    }

    fun deleteItem() {
        val current = _uiState.value.item ?: return
        viewModelScope.launch {
            repository.deleteItem(current)
            _uiState.update { it.copy(isDeleted = true) }
        }
    }

    fun generateSummary() {
        val current = _uiState.value.item ?: return
        if (current.isSecret) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSummarizing = true) }
            val summaryText = DeterministicLocalAIEngine.summarize(current)
            repository.updateItemSummary(itemId, summaryText)
            val updated = repository.getItemById(itemId)
            _uiState.update { it.copy(item = updated, isSummarizing = false) }
        }
    }

    fun deleteSummary() {
        viewModelScope.launch {
            repository.updateItemSummary(itemId, null)
            val updated = repository.getItemById(itemId)
            _uiState.update { it.copy(item = updated) }
        }
    }

    fun scheduleReview(timestamp: Long) {
        val current = _uiState.value.item ?: return
        viewModelScope.launch {
            repository.updateReviewState(
                id = current.id,
                reviewAt = timestamp,
                reviewStatus = "PENDING",
                lastReviewedAt = current.lastReviewedAt,
                reviewCount = current.reviewCount
            )
            val updated = repository.getItemById(itemId)
            _uiState.update { it.copy(item = updated) }
        }
    }

    fun markReviewed() {
        val current = _uiState.value.item ?: return
        viewModelScope.launch {
            repository.updateReviewState(
                id = current.id,
                reviewAt = null,
                reviewStatus = "REVIEWED",
                lastReviewedAt = System.currentTimeMillis(),
                reviewCount = current.reviewCount + 1
            )
            val updated = repository.getItemById(itemId)
            _uiState.update { it.copy(item = updated) }
        }
    }

    fun updateMaturity(maturity: String) {
        viewModelScope.launch {
            repository.updateMaturity(itemId, maturity)
            val updated = repository.getItemById(itemId)
            _uiState.update { it.copy(item = updated) }
        }
    }
}

class ItemDetailViewModelFactory(
    private val itemId: String,
    private val repository: AppRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ItemDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ItemDetailViewModel(itemId, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
