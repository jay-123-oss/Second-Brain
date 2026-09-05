package com.example.brain.ui.search

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.brain.data.entity.FolderEntity
import com.example.brain.data.entity.SavedItemEntity
import com.example.brain.data.entity.TagEntity
import com.example.brain.data.model.ContentType
import com.example.brain.data.repository.AppRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

enum class SearchDateFilter {
    ALL, TODAY, THIS_WEEK, THIS_MONTH
}

data class SearchUiState(
    val query: String = "",
    val results: List<SavedItemEntity> = emptyList(),
    val isSearching: Boolean = false,
    val selectedType: ContentType? = null,
    val selectedFolderId: String? = null,
    val selectedTag: String? = null,
    val favoritesOnly: Boolean = false,
    val readLaterOnly: Boolean = false,
    val dateFilter: SearchDateFilter = SearchDateFilter.ALL,
    val recentSearches: List<String> = emptyList(),
    val availableFolders: List<FolderEntity> = emptyList(),
    val availableTags: List<TagEntity> = emptyList(),
    val suggestedTags: List<String> = emptyList(),
    val suggestedFolders: List<FolderEntity> = emptyList(),
    val isSelectionMode: Boolean = false,
    val selectedItemIds: Set<String> = emptySet(),
    val popularTags: List<String> = emptyList(),
    val savedSearches: List<com.example.brain.data.entity.SavedSearchEntity> = emptyList()
)

data class ParsedSearchCommand(
    val cleanQuery: String,
    val projectCmd: String? = null,
    val tagCmd: String? = null,
    val typeCmd: String? = null,
    val sourceCmd: String? = null,
    val reviewDueCmd: Boolean = false,
    val favoriteCmd: Boolean? = null,
    val maturityCmd: String? = null
)

fun parseSearchCommands(input: String): ParsedSearchCommand {
    val tokens = input.split(Regex("\\s+")).filter { it.isNotBlank() }
    val cleanTokens = mutableListOf<String>()
    var project: String? = null
    var tag: String? = null
    var type: String? = null
    var source: String? = null
    var reviewDue = false
    var favorite: Boolean? = null
    var maturity: String? = null

    for (token in tokens) {
        when {
            token.startsWith("project:", ignoreCase = true) -> {
                project = token.substringAfter(":").trim()
            }
            token.startsWith("tag:", ignoreCase = true) -> {
                tag = token.substringAfter(":").trim().removePrefix("#")
            }
            token.startsWith("type:", ignoreCase = true) -> {
                type = token.substringAfter(":").trim().uppercase()
            }
            token.startsWith("source:", ignoreCase = true) -> {
                source = token.substringAfter(":").trim().lowercase()
            }
            token.equals("review:due", ignoreCase = true) -> {
                reviewDue = true
            }
            token.equals("favorite:true", ignoreCase = true) -> {
                favorite = true
            }
            token.startsWith("maturity:", ignoreCase = true) -> {
                maturity = token.substringAfter(":").trim().uppercase()
            }
            else -> {
                cleanTokens.add(token)
            }
        }
    }

    return ParsedSearchCommand(
        cleanQuery = cleanTokens.joinToString(" ").trim(),
        projectCmd = project,
        tagCmd = tag,
        typeCmd = type,
        sourceCmd = source,
        reviewDueCmd = reviewDue,
        favoriteCmd = favorite,
        maturityCmd = maturity
    )
}

@OptIn(FlowPreview::class)
class SearchViewModel(
    application: Application,
    private val repository: AppRepository
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("second_brain_search_history", Context.MODE_PRIVATE)

    private val _queryFlow = MutableStateFlow("")
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        loadRecentSearches()
        loadFoldersAndTags()
        observeQueryAndFilters()
    }

    private fun loadRecentSearches() {
        val saved = prefs.getStringSet("recent_queries", emptySet()) ?: emptySet()
        _uiState.update { it.copy(recentSearches = saved.toList().take(10)) }
    }

    private fun saveRecentSearch(query: String) {
        val clean = query.trim()
        if (clean.isBlank()) return
        val current = _uiState.value.recentSearches.toMutableList()
        current.remove(clean)
        current.add(0, clean)
        val updated = current.take(10)
        prefs.edit().putStringSet("recent_queries", updated.toSet()).apply()
        _uiState.update { it.copy(recentSearches = updated) }
    }

    fun removeRecentSearch(query: String) {
        val current = _uiState.value.recentSearches.toMutableList()
        current.remove(query)
        prefs.edit().putStringSet("recent_queries", current.toSet()).apply()
        _uiState.update { it.copy(recentSearches = current) }
    }

    fun clearAllRecentSearches() {
        prefs.edit().remove("recent_queries").apply()
        _uiState.update { it.copy(recentSearches = emptyList()) }
    }

    private fun loadFoldersAndTags() {
        viewModelScope.launch {
            repository.getAllFolders().collect { folders ->
                _uiState.update { it.copy(availableFolders = folders) }
            }
        }
        viewModelScope.launch {
            repository.getAllTags().collect { tags ->
                _uiState.update {
                    it.copy(
                        availableTags = tags,
                        popularTags = tags.take(8).map { t -> t.name }
                    )
                }
            }
        }
        viewModelScope.launch {
            repository.getAllSavedSearches().collect { searches ->
                _uiState.update { it.copy(savedSearches = searches) }
            }
        }
    }

    private fun observeQueryAndFilters() {
        viewModelScope.launch {
            _queryFlow
                .debounce(300)
                .distinctUntilChanged()
                .collect { q ->
                    executeSearch(q)
                }
        }
    }

    fun onQueryChange(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
        _queryFlow.value = newQuery

        // Update suggestions
        val clean = newQuery.trim().lowercase()
        if (clean.isNotBlank()) {
            val matchingTags = _uiState.value.availableTags
                .filter { it.name.lowercase().contains(clean) }
                .map { it.name }
                .take(5)

            val matchingFolders = _uiState.value.availableFolders
                .filter { it.name.lowercase().contains(clean) }
                .take(3)

            _uiState.update {
                it.copy(suggestedTags = matchingTags, suggestedFolders = matchingFolders)
            }
        } else {
            _uiState.update {
                it.copy(suggestedTags = emptyList(), suggestedFolders = emptyList())
            }
        }
    }

    fun executeSearch(query: String = _uiState.value.query) {
        viewModelScope.launch {
            val state = _uiState.value
            val parsedCmd = parseSearchCommands(query)
            val effectiveQuery = parsedCmd.cleanQuery
            val hasCommand = parsedCmd.projectCmd != null || parsedCmd.tagCmd != null ||
                    parsedCmd.typeCmd != null || parsedCmd.sourceCmd != null ||
                    parsedCmd.reviewDueCmd || parsedCmd.favoriteCmd != null || parsedCmd.maturityCmd != null

            val isBlankQuery = effectiveQuery.isBlank()
            val hasActiveFilter = state.selectedType != null ||
                    state.selectedFolderId != null ||
                    state.selectedTag != null ||
                    state.favoritesOnly ||
                    state.readLaterOnly ||
                    state.dateFilter != SearchDateFilter.ALL ||
                    hasCommand

            if (isBlankQuery && !hasActiveFilter) {
                _uiState.update { it.copy(results = emptyList(), isSearching = false) }
                return@launch
            }

            _uiState.update { it.copy(isSearching = true) }

            // Retrieve base candidates: if query present, search FTS/LIKE; else get all active items
            val rawItems = if (!isBlankQuery) {
                saveRecentSearch(query)
                repository.searchItems(effectiveQuery)
            } else {
                repository.getActiveItems().first()
            }

            // Apply multi-dimensional filters & search commands
            var filtered = rawItems

            // 1. Type filter & command
            val activeType = state.selectedType?.name ?: parsedCmd.typeCmd
            if (activeType != null) {
                filtered = filtered.filter { it.type.equals(activeType, ignoreCase = true) }
            }

            // 2. Folder filter & command
            val activeFolderId = state.selectedFolderId
            if (activeFolderId != null) {
                filtered = filtered.filter { it.folderId == activeFolderId }
            } else if (parsedCmd.projectCmd != null) {
                val matchedFolder = state.availableFolders.find {
                    it.name.contains(parsedCmd.projectCmd, ignoreCase = true) || it.id.equals(parsedCmd.projectCmd, ignoreCase = true)
                }
                if (matchedFolder != null) {
                    filtered = filtered.filter { it.folderId == matchedFolder.id }
                }
            }

            // 3. Favorites filter & command
            if (state.favoritesOnly || parsedCmd.favoriteCmd == true) {
                filtered = filtered.filter { it.isFavorite }
            }

            // 4. Read Later filter
            if (state.readLaterOnly) {
                filtered = filtered.filter { it.isReadLater }
            }

            // 5. Review due command
            if (parsedCmd.reviewDueCmd) {
                val now = System.currentTimeMillis()
                filtered = filtered.filter { it.reviewAt != null && it.reviewAt <= now && it.reviewStatus != "REVIEWED" }
            }

            // 6. Maturity command
            if (parsedCmd.maturityCmd != null) {
                filtered = filtered.filter { it.maturity.equals(parsedCmd.maturityCmd, ignoreCase = true) }
            }

            // 7. Source command (domain)
            if (parsedCmd.sourceCmd != null) {
                filtered = filtered.filter {
                    val dom = com.example.brain.util.KnowledgeOSEngine.extractDomain(it.url) ?: com.example.brain.util.KnowledgeOSEngine.extractDomain(it.originalUrl)
                    dom?.contains(parsedCmd.sourceCmd) == true
                }
            }

            // 8. Tag filter & command
            val activeTag = state.selectedTag ?: parsedCmd.tagCmd
            if (activeTag != null) {
                val taggedItems = repository.getItemsForTag(activeTag).first().map { it.id }.toSet()
                filtered = filtered.filter { taggedItems.contains(it.id) }
            }

            // 5. Date filter
            if (state.dateFilter != SearchDateFilter.ALL) {
                val now = Calendar.getInstance()
                val cutoff = when (state.dateFilter) {
                    SearchDateFilter.TODAY -> {
                        now.set(Calendar.HOUR_OF_DAY, 0)
                        now.set(Calendar.MINUTE, 0)
                        now.timeInMillis
                    }
                    SearchDateFilter.THIS_WEEK -> {
                        now.add(Calendar.DAY_OF_YEAR, -7)
                        now.timeInMillis
                    }
                    SearchDateFilter.THIS_MONTH -> {
                        now.add(Calendar.DAY_OF_YEAR, -30)
                        now.timeInMillis
                    }
                    else -> 0L
                }
                filtered = filtered.filter { it.createdAt >= cutoff }
            }

            // 6. Tag filter if specified
            state.selectedTag?.let { tag ->
                val taggedItems = repository.getItemsForTag(tag).first().map { it.id }.toSet()
                filtered = filtered.filter { taggedItems.contains(it.id) }
            }

            _uiState.update {
                it.copy(
                    results = filtered,
                    isSearching = false
                )
            }
        }
    }

    fun setTypeFilter(type: ContentType?) {
        _uiState.update { it.copy(selectedType = if (it.selectedType == type) null else type) }
        executeSearch()
    }

    fun setFolderFilter(folderId: String?) {
        _uiState.update { it.copy(selectedFolderId = if (it.selectedFolderId == folderId) null else folderId) }
        executeSearch()
    }

    fun setTagFilter(tag: String?) {
        _uiState.update { it.copy(selectedTag = if (it.selectedTag == tag) null else tag) }
        executeSearch()
    }

    fun toggleFavoritesFilter() {
        _uiState.update { it.copy(favoritesOnly = !it.favoritesOnly) }
        executeSearch()
    }

    fun toggleReadLaterFilter() {
        _uiState.update { it.copy(readLaterOnly = !it.readLaterOnly) }
        executeSearch()
    }

    fun setDateFilter(filter: SearchDateFilter) {
        _uiState.update { it.copy(dateFilter = filter) }
        executeSearch()
    }

    fun clearAllFilters() {
        _uiState.update {
            it.copy(
                selectedType = null,
                selectedFolderId = null,
                selectedTag = null,
                favoritesOnly = false,
                readLaterOnly = false,
                dateFilter = SearchDateFilter.ALL
            )
        }
        executeSearch()
    }

    // -------------------------------------------------------------
    // Bulk Selection Actions
    // -------------------------------------------------------------

    fun toggleItemSelection(id: String) {
        val current = _uiState.value.selectedItemIds.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _uiState.update {
            it.copy(
                selectedItemIds = current,
                isSelectionMode = current.isNotEmpty()
            )
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedItemIds = emptySet(), isSelectionMode = false) }
    }

    fun bulkFavorite(isFavorite: Boolean) {
        val ids = _uiState.value.selectedItemIds.toList()
        viewModelScope.launch {
            repository.bulkFavorite(ids, isFavorite)
            clearSelection()
            executeSearch()
        }
    }

    fun bulkReadLater(isReadLater: Boolean) {
        val ids = _uiState.value.selectedItemIds.toList()
        viewModelScope.launch {
            repository.bulkReadLater(ids, isReadLater)
            clearSelection()
            executeSearch()
        }
    }

    fun bulkMove(folderId: String) {
        val ids = _uiState.value.selectedItemIds.toList()
        viewModelScope.launch {
            repository.bulkMove(ids, folderId)
            clearSelection()
            executeSearch()
        }
    }

    fun bulkDelete() {
        val selectedIds = _uiState.value.selectedItemIds
        val itemsToDelete = _uiState.value.results.filter { selectedIds.contains(it.id) }
        viewModelScope.launch {
            repository.bulkDelete(itemsToDelete)
            clearSelection()
            executeSearch()
        }
    }

    fun toggleFavorite(item: SavedItemEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(item.id, !item.isFavorite)
            executeSearch()
        }
    }

    fun toggleReadLater(item: SavedItemEntity) {
        viewModelScope.launch {
            repository.toggleReadLater(item.id, !item.isReadLater)
            executeSearch()
        }
    }

    // -------------------------------------------------------------
    // Saved Searches (Prompt 10)
    // -------------------------------------------------------------

    fun saveCurrentSearch(name: String) {
        viewModelScope.launch {
            val state = _uiState.value
            val entity = com.example.brain.data.entity.SavedSearchEntity(
                id = java.util.UUID.randomUUID().toString(),
                name = name.ifBlank { state.query.ifBlank { "Saved Search" } },
                query = state.query,
                filterType = state.selectedType?.name,
                filterFolderId = state.selectedFolderId,
                filterTag = state.selectedTag
            )
            repository.insertSavedSearch(entity)
        }
    }

    fun deleteSavedSearch(id: String) {
        viewModelScope.launch {
            repository.deleteSavedSearch(id)
        }
    }

    fun applySavedSearch(search: com.example.brain.data.entity.SavedSearchEntity) {
        _uiState.update {
            it.copy(
                query = search.query,
                selectedType = search.filterType?.let { t -> ContentType.valueOf(t) },
                selectedFolderId = search.filterFolderId,
                selectedTag = search.filterTag
            )
        }
        _queryFlow.value = search.query
        executeSearch(search.query)
    }
}

class SearchViewModelFactory(
    private val application: Application,
    private val repository: AppRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SearchViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
