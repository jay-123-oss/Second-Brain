package com.example.brain.ui.capture

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.brain.data.entity.FolderEntity
import com.example.brain.data.entity.SavedItemEntity
import com.example.brain.data.model.CapturedContent
import com.example.brain.data.model.ContentType
import com.example.brain.data.repository.AppRepository
import com.example.brain.util.DeterministicLocalAIEngine
import com.example.brain.util.MediaStorageManager
import com.example.brain.util.MetadataFetcher
import com.example.brain.util.UrlNormalizer
import com.example.brain.worker.PendingMetadataWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

enum class CaptureState {
    IDLE,
    RECEIVING,
    METADATA,
    SAVING,
    COMPLETE,
    FAILED
}

data class CaptureUiState(
    val state: CaptureState = CaptureState.IDLE,
    val title: String = "",
    val url: String = "",
    val originalUrl: String? = null,
    val sourceApp: String? = null,
    val notes: String = "",
    val type: ContentType = ContentType.LINK,
    val imageUrl: String? = null,
    val thumbnailPath: String? = null,
    val siteName: String? = null,
    val description: String? = null,
    val localFilePath: String? = null,
    val contentHash: String? = null,
    val mediaDurationMs: Long? = null,
    val mediaWidth: Int? = null,
    val mediaHeight: Int? = null,
    val mediaFileSize: Long? = null,
    val extractedText: String? = null,
    val isFetchingMetadata: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val errorMessage: String? = null,
    val isSecret: Boolean = false,
    val selectedFolderId: String = "inbox_default_id",
    val availableFolders: List<FolderEntity> = emptyList(),
    val currentTags: List<String> = emptyList(),
    val suggestedTags: List<String> = emptyList(),
    val duplicateItem: SavedItemEntity? = null,
    val pendingUris: List<Uri> = emptyList(),
    val isBatchCapture: Boolean = false
)

class CaptureViewModel(
    private val repository: AppRepository,
    private val vaultCryptoManager: com.example.brain.util.VaultCryptoManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    init {
        loadFolders()
    }

    private fun loadFolders() {
        viewModelScope.launch {
            repository.getAllFolders().collect { folders ->
                _uiState.update { it.copy(availableFolders = folders) }
            }
        }
    }

    fun initFromContent(content: CapturedContent?, context: Context? = null) {
        if (content == null) return

        val normalizedUrl = content.url?.let { UrlNormalizer.normalize(it) } ?: ""
        val detectedSource = content.sourceApp ?: (content.url?.let { UrlNormalizer.detectSourceApp(it) })

        _uiState.update {
            it.copy(
                title = content.title,
                url = normalizedUrl,
                originalUrl = content.originalUrl ?: content.url,
                sourceApp = detectedSource,
                notes = content.notes ?: "",
                type = content.type,
                pendingUris = content.uris ?: (content.uri?.let { u -> listOf(u) } ?: emptyList()),
                isBatchCapture = (content.uris != null && content.uris.size > 1)
            )
        }

        // Check for duplicates if URL is present
        if (normalizedUrl.isNotBlank()) {
            checkForDuplicateUrl(normalizedUrl)
            fetchUrlMetadata(normalizedUrl)
        }

        // If media URI is provided and context is available, store and analyze it locally
        if (content.uri != null && context != null && content.type != ContentType.LINK) {
            captureMedia(context, content.uri)
        }
    }

    fun onUrlChanged(newUrl: String) {
        _uiState.update { it.copy(url = newUrl) }
        val trimmed = newUrl.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.contains(".")) {
            val normalized = UrlNormalizer.normalize(trimmed)
            val source = UrlNormalizer.detectSourceApp(trimmed)
            _uiState.update { it.copy(originalUrl = trimmed, sourceApp = source) }
            checkForDuplicateUrl(normalized)
            fetchUrlMetadata(normalized)
        }
    }

    private fun checkForDuplicateUrl(normalizedUrl: String) {
        viewModelScope.launch {
            val existing = repository.getItemByUrl(normalizedUrl)
            if (existing != null) {
                _uiState.update { it.copy(duplicateItem = existing) }
            }
        }
    }

    fun dismissDuplicateWarning() {
        _uiState.update { it.copy(duplicateItem = null) }
    }

    fun onTitleChanged(newTitle: String) {
        _uiState.update { it.copy(title = newTitle) }
        updateSuggestedTags(newTitle, _uiState.value.notes)
    }

    fun onNotesChanged(newNotes: String) {
        _uiState.update { it.copy(notes = newNotes) }
        updateSuggestedTags(_uiState.value.title, newNotes)
    }

    fun onTypeSelected(newType: ContentType) {
        _uiState.update { it.copy(type = newType) }
    }

    fun onFolderSelected(folderId: String) {
        _uiState.update { it.copy(selectedFolderId = folderId) }
    }

    fun createProject(name: String, icon: String = "📁") {
        if (name.isBlank()) return
        viewModelScope.launch {
            val newFolder = FolderEntity(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                icon = icon,
                colorHex = "#6366F1",
                createdAt = System.currentTimeMillis()
            )
            repository.insertFolder(newFolder)
            _uiState.update { it.copy(selectedFolderId = newFolder.id) }
        }
    }

    fun onToggleSecret(isSecret: Boolean) {
        _uiState.update { it.copy(isSecret = isSecret) }
    }

    fun addTag(tag: String) {
        val normalized = tag.trim().lowercase().removePrefix("#")
        if (normalized.isNotBlank() && !_uiState.value.currentTags.contains(normalized)) {
            _uiState.update {
                it.copy(
                    currentTags = it.currentTags + normalized,
                    suggestedTags = it.suggestedTags - normalized
                )
            }
        }
    }

    fun removeTag(tag: String) {
        _uiState.update { it.copy(currentTags = it.currentTags - tag) }
    }

    fun captureMedia(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(state = CaptureState.RECEIVING) }
            try {
                val result = MediaStorageManager.storeContentUri(context, uri)

                // Perform immediate on-device media inspection for instant preview
                val mediaFile = java.io.File(result.localFilePath)
                val mediaInfo = com.example.brain.util.MediaProcessor.processMedia(
                    context = context,
                    file = mediaFile,
                    contentType = result.contentType,
                    contentHash = result.sha256Hash
                )

                // Check duplicate by SHA-256 hash
                val existing = repository.getItemByHash(result.sha256Hash)
                val derivedTitle = if (_uiState.value.title.isBlank() || _uiState.value.title.startsWith("Shared")) {
                    mediaInfo.title ?: result.originalFileName
                } else {
                    _uiState.value.title
                }

                _uiState.update {
                    it.copy(
                        duplicateItem = existing,
                        localFilePath = result.localFilePath,
                        contentHash = result.sha256Hash,
                        type = result.contentType,
                        title = derivedTitle,
                        thumbnailPath = mediaInfo.thumbnailPath,
                        mediaWidth = mediaInfo.width,
                        mediaHeight = mediaInfo.height,
                        mediaDurationMs = mediaInfo.durationMs,
                        mediaFileSize = if (mediaInfo.fileSize > 0) mediaInfo.fileSize else result.sizeBytes,
                        extractedText = mediaInfo.extractedText,
                        state = CaptureState.IDLE
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        state = CaptureState.FAILED,
                        errorMessage = "Couldn't read shared file: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun fetchUrlMetadata(url: String) {
        if (_uiState.value.isFetchingMetadata) return
        viewModelScope.launch {
            _uiState.update { it.copy(isFetchingMetadata = true, state = CaptureState.METADATA) }
            val result = MetadataFetcher.fetch(url)
            result.onSuccess { meta ->
                val updatedTitle = if (_uiState.value.title.isBlank() || _uiState.value.title == "Shared Link") {
                    meta.title
                } else {
                    _uiState.value.title
                }
                _uiState.update {
                    it.copy(
                        isFetchingMetadata = false,
                        state = CaptureState.IDLE,
                        title = updatedTitle,
                        description = meta.description,
                        imageUrl = meta.imageUrl,
                        siteName = meta.siteName,
                        extractedText = meta.extractedText,
                        type = ContentType.LINK
                    )
                }
                updateSuggestedTags(updatedTitle, meta.description)
            }.onFailure {
                _uiState.update { it.copy(isFetchingMetadata = false, state = CaptureState.IDLE) }
            }
        }
    }

    private fun updateSuggestedTags(title: String, notes: String?) {
        val suggested = DeterministicLocalAIEngine.extractSuggestedTags(title, notes)
            .filter { !_uiState.value.currentTags.contains(it) }
        _uiState.update { it.copy(suggestedTags = suggested) }
    }

    fun saveKnowledge(context: Context, onSaved: (String) -> Unit) {
        val state = _uiState.value
        if (state.title.isBlank() && state.url.isBlank() && state.notes.isBlank() && state.localFilePath.isNullOrBlank() && state.pendingUris.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Please enter a title, URL, note, or attach a file.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, state = CaptureState.SAVING, errorMessage = null) }
            try {
                // 1. Batch Capture (ACTION_SEND_MULTIPLE)
                if (state.isBatchCapture && state.pendingUris.size > 1) {
                    var lastSavedId = ""
                    for (uri in state.pendingUris) {
                        try {
                            val mediaRes = MediaStorageManager.storeContentUri(context, uri)
                            var itemFilePath: String? = mediaRes.localFilePath

                            if (state.isSecret && itemFilePath != null) {
                                val plainFile = java.io.File(itemFilePath)
                                if (plainFile.exists()) {
                                    val targetName = "vault_${UUID.randomUUID()}.${plainFile.extension.ifBlank { "bin" }}"
                                    val encRes = plainFile.inputStream().use { stream ->
                                        vaultCryptoManager.encryptStreamToVault(stream, targetName)
                                    }
                                    encRes.onSuccess { encFile ->
                                        itemFilePath = encFile.absolutePath
                                        plainFile.delete()
                                    }
                                }
                            }

                            val itemId = UUID.randomUUID().toString()
                            val entity = SavedItemEntity(
                                id = itemId,
                                title = mediaRes.originalFileName,
                                type = mediaRes.contentType.name,
                                filePath = itemFilePath,
                                contentHash = mediaRes.sha256Hash,
                                folderId = state.selectedFolderId,
                                isSecret = state.isSecret,
                                mediaFileSize = mediaRes.sizeBytes,
                                processingStatus = if (state.isSecret) "COMPLETED" else "QUEUED",
                                createdAt = System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis()
                            )
                            repository.insertItem(entity)
                            for (tag in state.currentTags) {
                                repository.addTagToItem(itemId, tag)
                            }

                            // Schedule enrichment if not secret
                            if (!state.isSecret) {
                                com.example.brain.worker.ContentEnrichmentWorker.schedule(
                                    context = context,
                                    itemId = itemId,
                                    requiresNetwork = false
                                )
                            }
                            lastSavedId = itemId
                        } catch (_: Exception) {}
                    }
                    _uiState.update { it.copy(isSaving = false, saveSuccess = true, state = CaptureState.COMPLETE) }
                    onSaved(lastSavedId)
                    return@launch
                }

                // 2. Single Item Capture ("SAVE FIRST, ENRICH SECOND")
                var finalFilePath = state.localFilePath
                if (state.isSecret && finalFilePath != null) {
                    val plainFile = java.io.File(finalFilePath)
                    if (plainFile.exists()) {
                        val targetName = "vault_${UUID.randomUUID()}.${plainFile.extension.ifBlank { "bin" }}"
                        val encRes = plainFile.inputStream().use { stream ->
                            vaultCryptoManager.encryptStreamToVault(stream, targetName)
                        }
                        encRes.onSuccess { encFile ->
                            finalFilePath = encFile.absolutePath
                            plainFile.delete()
                        }
                    }
                }

                val itemId = UUID.randomUUID().toString()
                val normalizedUrl = if (state.url.isNotBlank()) UrlNormalizer.normalize(state.url) else null
                val rawUrl = state.originalUrl?.takeIf { it.isNotBlank() } ?: state.url.takeIf { it.isNotBlank() }
                val detectedSource = state.sourceApp ?: (rawUrl?.let { UrlNormalizer.detectSourceApp(it) })

                val entity = SavedItemEntity(
                    id = itemId,
                    title = state.title.ifBlank {
                        detectedSource?.let { "$it Item" }
                            ?: normalizedUrl?.let { UrlNormalizer.extractDomain(it) }
                            ?: "Saved Knowledge"
                    },
                    type = state.type.name,
                    url = normalizedUrl,
                    originalUrl = rawUrl,
                    sourceApp = detectedSource,
                    note = state.notes,
                    description = state.description,
                    thumbnailUrl = if (state.isSecret) null else state.imageUrl, // Never leak remote thumbnails for secret items
                    thumbnailPath = if (state.isSecret) null else state.thumbnailPath,
                    siteName = state.siteName,
                    filePath = finalFilePath,
                    contentHash = state.contentHash,
                    mediaDurationMs = state.mediaDurationMs,
                    mediaWidth = state.mediaWidth,
                    mediaHeight = state.mediaHeight,
                    mediaFileSize = state.mediaFileSize,
                    extractedText = state.extractedText ?: state.notes.ifBlank { null },
                    folderId = state.selectedFolderId,
                    isSecret = state.isSecret,
                    processingStatus = if (state.isSecret) "COMPLETED" else "QUEUED",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                // Immediate local save
                repository.insertItem(entity)

                for (tag in state.currentTags) {
                    repository.addTagToItem(itemId, tag)
                }

                // If NOT secret, schedule background asynchronous enrichment worker
                if (!state.isSecret) {
                    com.example.brain.worker.ContentEnrichmentWorker.schedule(
                        context = context,
                        itemId = itemId,
                        requiresNetwork = (state.type == ContentType.LINK)
                    )
                }

                _uiState.update { it.copy(isSaving = false, saveSuccess = true, state = CaptureState.COMPLETE) }
                onSaved(itemId)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        state = CaptureState.FAILED,
                        errorMessage = "Couldn't save this item: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun resetState() {
        _uiState.value = CaptureUiState()
        loadFolders()
    }
}

class CaptureViewModelFactory(
    private val repository: AppRepository,
    private val vaultCryptoManager: com.example.brain.util.VaultCryptoManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CaptureViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CaptureViewModel(repository, vaultCryptoManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
