package com.example.brain.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.brain.data.entity.FolderEntity
import com.example.brain.data.entity.ItemRelationEntity
import com.example.brain.data.entity.SavedItemEntity
import com.example.brain.data.repository.AppRepository
import com.example.brain.util.KnowledgeGap
import com.example.brain.util.KnowledgeOSEngine
import com.example.brain.util.KnowledgePulse
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

data class HomeUiState(
    val isLoading: Boolean = true,
    val greeting: String = "Good day",
    val pulse: KnowledgePulse? = null,
    val continueItems: List<SavedItemEntity> = emptyList(),
    val recentItems: List<SavedItemEntity> = emptyList(),
    val reviewDueItems: List<SavedItemEntity> = emptyList(),
    val forgottenItems: List<SavedItemEntity> = emptyList(),
    val knowledgeGaps: List<KnowledgeGap> = emptyList(),
    val readLaterItems: List<SavedItemEntity> = emptyList(),
    val favoriteItems: List<SavedItemEntity> = emptyList(),
    val projects: List<FolderEntity> = emptyList(),
    val inboxCount: Int = 0,
    val totalCount: Int = 0
)

class HomeViewModel(private val repository: AppRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val dismissedGapIds = MutableStateFlow<Set<String>>(emptySet())

    init {
        loadHomeData()
    }

    private fun computeTimeGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..22 -> "Good evening"
            else -> "Good night"
        }
    }

    private fun loadHomeData() {
        viewModelScope.launch {
            val thresholdForgotten = System.currentTimeMillis() - (30L * 24 * 3600 * 1000)

            combine(
                repository.getActiveItems(),
                repository.getAllFolders(),
                repository.getAllActiveRelations(),
                repository.getRecentlyOpenedItems(limit = 6),
                repository.getReviewDueItems(),
                repository.getForgottenItems(thresholdForgotten, limit = 5),
                repository.getInboxItems(),
                dismissedGapIds
            ) { args: Array<Any?> ->
                @Suppress("UNCHECKED_CAST")
                val activeItems = args[0] as List<SavedItemEntity>
                @Suppress("UNCHECKED_CAST")
                val folders = args[1] as List<FolderEntity>
                @Suppress("UNCHECKED_CAST")
                val relations = args[2] as List<ItemRelationEntity>
                @Suppress("UNCHECKED_CAST")
                val recentlyOpened = args[3] as List<SavedItemEntity>
                @Suppress("UNCHECKED_CAST")
                val reviewDue = args[4] as List<SavedItemEntity>
                @Suppress("UNCHECKED_CAST")
                val forgotten = args[5] as List<SavedItemEntity>
                @Suppress("UNCHECKED_CAST")
                val inboxItems = args[6] as List<SavedItemEntity>
                @Suppress("UNCHECKED_CAST")
                val dismissed = args[7] as Set<String>

                val pulse = KnowledgeOSEngine.computePulse(activeItems, relations, folders)
                val rawGaps = KnowledgeOSEngine.detectKnowledgeGaps(activeItems, folders, relations)
                val activeGaps = rawGaps.filter { !dismissed.contains(it.id) }

                HomeUiState(
                    isLoading = false,
                    greeting = computeTimeGreeting(),
                    pulse = pulse,
                    continueItems = recentlyOpened,
                    recentItems = activeItems.take(8),
                    reviewDueItems = reviewDue,
                    forgottenItems = forgotten,
                    knowledgeGaps = activeGaps,
                    readLaterItems = activeItems.filter { it.isReadLater }.take(8),
                    favoriteItems = activeItems.filter { it.isFavorite }.take(8),
                    projects = folders.filter { !it.isSecret && it.id != "inbox_default_id" },
                    inboxCount = inboxItems.size,
                    totalCount = activeItems.size
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun dismissGap(gapId: String) {
        dismissedGapIds.value = dismissedGapIds.value + gapId
    }

    fun markItemReviewed(item: SavedItemEntity) {
        viewModelScope.launch {
            repository.updateReviewState(
                id = item.id,
                reviewAt = null,
                reviewStatus = "REVIEWED",
                lastReviewedAt = System.currentTimeMillis(),
                reviewCount = item.reviewCount + 1
            )
        }
    }

    fun snoozeReview(item: SavedItemEntity, days: Int = 3) {
        viewModelScope.launch {
            val newReviewAt = System.currentTimeMillis() + (days.toLong() * 24 * 3600 * 1000)
            repository.updateReviewState(
                id = item.id,
                reviewAt = newReviewAt,
                reviewStatus = "SNOOZED",
                lastReviewedAt = item.lastReviewedAt,
                reviewCount = item.reviewCount
            )
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

class HomeViewModelFactory(private val repository: AppRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
