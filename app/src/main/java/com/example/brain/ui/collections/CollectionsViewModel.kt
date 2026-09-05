package com.example.brain.ui.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.brain.data.entity.CollectionEntity
import com.example.brain.data.entity.FolderEntity
import com.example.brain.data.repository.AppRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class CollectionsUiState(
    val selectedTab: Int = 0, // 0 = Collections, 1 = Projects
    val collections: List<CollectionEntity> = emptyList(),
    val projects: List<FolderEntity> = emptyList(),
    val isLoading: Boolean = true
)

class CollectionsViewModel(private val repository: AppRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionsUiState())
    val uiState: StateFlow<CollectionsUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            combine(
                repository.getAllCollections(),
                repository.getAllFolders()
            ) { cols, folders ->
                CollectionsUiState(
                    selectedTab = _uiState.value.selectedTab,
                    collections = cols,
                    projects = folders,
                    isLoading = false
                )
            }.collect { state ->
                _uiState.update { current ->
                    current.copy(
                        collections = state.collections,
                        projects = state.projects,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    fun createCollection(title: String, description: String, icon: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val col = CollectionEntity(
                id = UUID.randomUUID().toString(),
                name = title.trim(),
                description = description.trim(),
                icon = icon.ifBlank { "📚" },
                colorHex = "#6366F1",
                createdAt = System.currentTimeMillis()
            )
            repository.insertCollection(col)
        }
    }

    fun createProject(name: String, icon: String, parentId: String? = null) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val folder = FolderEntity(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                icon = icon.ifBlank { "📁" },
                colorHex = "#4F46E5",
                parentId = parentId,
                createdAt = System.currentTimeMillis()
            )
            repository.insertFolder(folder)
        }
    }
}

class CollectionsViewModelFactory(private val repository: AppRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CollectionsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CollectionsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
