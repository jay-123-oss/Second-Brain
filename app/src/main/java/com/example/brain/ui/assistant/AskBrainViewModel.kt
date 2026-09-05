package com.example.brain.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.brain.data.entity.ConversationEntity
import com.example.brain.data.entity.SavedItemEntity
import com.example.brain.data.repository.AppRepository
import com.example.brain.util.AssistantMode
import com.example.brain.util.AssistantResponse
import com.example.brain.util.DeterministicLocalAIEngine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class AskBrainUiState(
    val query: String = "",
    val selectedMode: AssistantMode = AssistantMode.SEARCH,
    val isThinking: Boolean = false,
    val currentResponse: AssistantResponse? = null,
    val conversations: List<ConversationEntity> = emptyList(),
    val suggestedPrompts: List<String> = listOf(
        "What did I save about Android architecture?",
        "Summarize my saved research notes",
        "What do my notes say about security & encryption?",
        "Compare my notes on transformers and embeddings"
    )
)

class AskBrainViewModel(
    private val repository: AppRepository,
    initialQuestion: String? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AskBrainUiState(query = initialQuestion ?: "")
    )
    val uiState: StateFlow<AskBrainUiState> = _uiState.asStateFlow()

    init {
        loadHistory()
        if (!initialQuestion.isNullOrBlank()) {
            askQuestion(initialQuestion)
        }
    }

    private fun loadHistory() {
        viewModelScope.launch {
            repository.getAllConversations().collect { history ->
                _uiState.update { it.copy(conversations = history) }
            }
        }
    }

    fun onQueryChange(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
    }

    fun setMode(mode: AssistantMode) {
        _uiState.update { it.copy(selectedMode = mode) }
    }

    fun askQuestion(prompt: String = _uiState.value.query) {
        val clean = prompt.trim()
        if (clean.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(query = clean, isThinking = true) }

            // Strictly fetch active non-secret items
            val activeItems = repository.getActiveItems().first()
            val mode = _uiState.value.selectedMode

            val response = DeterministicLocalAIEngine.answerQuestion(
                question = clean,
                activeItems = activeItems,
                mode = mode
            )

            // Persist to local conversation history if grounded or user question
            val conversationId = UUID.randomUUID().toString()
            val sourceIds = response.sources.joinToString(",") { it.itemId }
            val conversation = ConversationEntity(
                id = conversationId,
                question = clean,
                answer = response.answer,
                sourceItemIdsJson = sourceIds,
                mode = mode.name,
                createdAt = System.currentTimeMillis()
            )
            repository.saveConversation(conversation)

            _uiState.update {
                it.copy(
                    isThinking = false,
                    currentResponse = response
                )
            }
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            repository.deleteConversation(id)
        }
    }

    fun clearAllConversations() {
        viewModelScope.launch {
            repository.clearConversations()
            _uiState.update { it.copy(conversations = emptyList()) }
        }
    }
}

class AskBrainViewModelFactory(
    private val repository: AppRepository,
    private val initialQuestion: String? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AskBrainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AskBrainViewModel(repository, initialQuestion) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
