package com.example.brain.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.brain.data.entity.FolderEntity
import com.example.brain.data.entity.SavedItemEntity
import com.example.brain.data.repository.AppRepository
import com.example.brain.util.DeterministicLocalAIEngine
import com.example.brain.util.KnowledgeOSEngine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class OrganizationSuggestion(
    val suggestedFolderId: String?,
    val suggestedFolderName: String?,
    val suggestedTags: List<String>
)

data class InboxUiState(
    val isLoading: Boolean = true,
    val inboxItems: List<SavedItemEntity> = emptyList(),
    val availableFolders: List<FolderEntity> = emptyList(),
    val suggestionsMap: Map<String, OrganizationSuggestion> = emptyMap(),
    val isSelectionMode: Boolean = false,
    val selectedItemIds: Set<String> = emptySet()
)

class InboxViewModel(private val repository: AppRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(InboxUiState())
    val uiState: StateFlow<InboxUiState> = _uiState.asStateFlow()

    init {
        loadInboxData()
    }

    private fun loadInboxData() {
        viewModelScope.launch {
            combine(
                repository.getInboxItems(),
                repository.getAllFolders()
            ) { items, folders ->
                val projects = folders.filter { !it.isSecret && it.id != "inbox_default_id" }
                val suggestions = mutableMapOf<String, OrganizationSuggestion>()

                for (item in items) {
                    val suggestedTags = DeterministicLocalAIEngine.extractSuggestedTags(item.title, item.notes)
                    val domain = KnowledgeOSEngine.extractDomain(item.url) ?: KnowledgeOSEngine.extractDomain(item.originalUrl)

                    // Match project based on domain or title keyword
                    var matchedFolder: FolderEntity? = null
                    if (domain != null) {
                        matchedFolder = projects.find { folder ->
                            folder.name.contains("Android", ignoreCase = true) && domain.contains("android") ||
                            folder.name.contains("Code", ignoreCase = true) && domain.contains("github") ||
                            folder.name.contains("Research", ignoreCase = true) && (domain.contains("arxiv") || domain.contains("nature"))
                        }
                    }
                    if (matchedFolder == null) {
                        val itemTokens = DeterministicLocalAIEngine.tokenize(item.title).toSet()
                        matchedFolder = projects.find { folder ->
                            val folderTokens = DeterministicLocalAIEngine.tokenize(folder.name).toSet()
                            itemTokens.intersect(folderTokens).isNotEmpty()
                        }
                    }

                    suggestions[item.id] = OrganizationSuggestion(
                        suggestedFolderId = matchedFolder?.id,
                        suggestedFolderName = matchedFolder?.name,
                        suggestedTags = suggestedTags.take(3)
                    )
                }

                InboxUiState(
                    isLoading = false,
                    inboxItems = items,
                    availableFolders = projects,
                    suggestionsMap = suggestions,
                    isSelectionMode = _uiState.value.isSelectionMode,
                    selectedItemIds = _uiState.value.selectedItemIds
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun applySuggestion(item: SavedItemEntity) {
        val suggestion = _uiState.value.suggestionsMap[item.id] ?: return
        viewModelScope.launch {
            if (suggestion.suggestedFolderId != null) {
                repository.moveItemToFolder(item.id, suggestion.suggestedFolderId)
            }
            for (tag in suggestion.suggestedTags) {
                repository.addTagToItem(item.id, tag)
            }
            repository.updateMaturity(item.id, "REVIEWED")
        }
    }

    fun assignFolder(item: SavedItemEntity, folderId: String) {
        viewModelScope.launch {
            repository.moveItemToFolder(item.id, folderId)
            repository.updateMaturity(item.id, "PROCESSING")
        }
    }


    fun archiveItem(item: SavedItemEntity) {
        viewModelScope.launch {
            repository.updateMaturity(item.id, "ARCHIVED")
        }
    }

    fun deleteItem(item: SavedItemEntity) {
        viewModelScope.launch {
            repository.deleteItem(item)
        }
    }

    fun toggleSelection(itemId: String) {
        val current = _uiState.value.selectedItemIds.toMutableSet()
        if (current.contains(itemId)) {
            current.remove(itemId)
        } else {
            current.add(itemId)
        }
        _uiState.update {
            it.copy(selectedItemIds = current, isSelectionMode = current.isNotEmpty())
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedItemIds = emptySet(), isSelectionMode = false) }
    }

    fun bulkAssignFolder(folderId: String) {
        val ids = _uiState.value.selectedItemIds.toList()
        viewModelScope.launch {
            repository.bulkUpdateFolder(ids, folderId)
            clearSelection()
        }
    }

    fun bulkArchive() {
        val ids = _uiState.value.selectedItemIds.toList()
        viewModelScope.launch {
            repository.bulkUpdateMaturity(ids, "ARCHIVED")
            clearSelection()
        }
    }

    fun bulkDelete() {
        val ids = _uiState.value.selectedItemIds.toList()
        viewModelScope.launch {
            repository.bulkDeleteByIds(ids)
            clearSelection()
        }
    }

}

class InboxViewModelFactory(private val repository: AppRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(InboxViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return InboxViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
