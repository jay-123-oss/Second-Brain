package com.example.brain.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.brain.data.entity.ItemEmbeddingEntity
import com.example.brain.data.repository.AppRepository
import com.example.brain.util.BackupManager
import com.example.brain.util.DeterministicLocalAIEngine
import com.example.brain.util.LocalAISettings
import com.example.brain.util.OnboardingPreferences
import com.example.brain.util.SecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat

data class StorageMetrics(
    val databaseBytes: Long = 0L,
    val mediaFilesBytes: Long = 0L,
    val cacheBytes: Long = 0L,
    val totalAppBytes: Long = 0L
) {
    val databaseFormatted: String get() = formatBytes(databaseBytes)
    val mediaFilesFormatted: String get() = formatBytes(mediaFilesBytes)
    val cacheFormatted: String get() = formatBytes(cacheBytes)
    val totalAppFormatted: String get() = formatBytes(totalAppBytes)

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 KB"
            val units = arrayOf("B", "KB", "MB", "GB")
            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
            val df = DecimalFormat("#,##0.#")
            return "${df.format(bytes / Math.pow(1024.0, digitGroups.toDouble()))} ${units[digitGroups]}"
        }
    }
}

data class SettingsUiState(
    // Overview
    val totalItems: Int = 0,
    val secretItems: Int = 0,

    // General
    val themeMode: String = "SYSTEM", // SYSTEM, LIGHT, DARK
    val hapticFeedbackEnabled: Boolean = true,
    val startScreen: String = "HOME",

    // Capture
    val autoEnrichMetadata: Boolean = true,
    val autoSaveQuickNotes: Boolean = true,

    // Search
    val searchHistoryEnabled: Boolean = true,
    val isRebuildingIndex: Boolean = false,

    // Intelligence / Local AI
    val isLocalAIEnabled: Boolean = true,
    val isSemanticSearchEnabled: Boolean = true,
    val isAutoSummarizeEnabled: Boolean = false,
    val isBackgroundAIEnabled: Boolean = false,
    val isRebuildingEmbeddings: Boolean = false,
    val embeddingsCount: Int = 0,
    val conversationsCount: Int = 0,

    // Security
    val isAppLockEnabled: Boolean = false,
    val autoLockTimeoutMs: Long = SecurityManager.TIMEOUT_1_MIN,
    val isBiometricEnabled: Boolean = false,
    val isPinSet: Boolean = false,
    val hasDuressPin: Boolean = false,
    val isDecoyMode: Boolean = false,
    val screenshotProtectionEnabled: Boolean = true,

    // Storage
    val storageMetrics: StorageMetrics = StorageMetrics(),

    // Backup & Recovery
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,

    // Feedback
    val operationMessage: String? = null
)

class SettingsViewModel(
    private val repository: AppRepository,
    private val backupManager: BackupManager,
    private val securityManager: SecurityManager,
    private val context: Context
) : ViewModel() {

    private val localAISettings = LocalAISettings(context)
    private val onboardingPrefs = OnboardingPreferences(context)
    private val generalPrefs = context.getSharedPreferences("second_brain_general_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            themeMode = generalPrefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM",
            hapticFeedbackEnabled = generalPrefs.getBoolean("haptic_feedback", true),
            startScreen = generalPrefs.getString("start_screen", "HOME") ?: "HOME",
            autoEnrichMetadata = generalPrefs.getBoolean("auto_enrich", true),
            autoSaveQuickNotes = generalPrefs.getBoolean("auto_save_notes", true),
            searchHistoryEnabled = generalPrefs.getBoolean("search_history", true),
            isLocalAIEnabled = localAISettings.isLocalAIEnabled,
            isSemanticSearchEnabled = localAISettings.isSemanticSearchEnabled,
            isAutoSummarizeEnabled = localAISettings.isAutoSummarizeEnabled,
            isBackgroundAIEnabled = localAISettings.isBackgroundAIEnabled,
            isAppLockEnabled = securityManager.isAppLockEnabled(),
            autoLockTimeoutMs = securityManager.getAutoLockTimeoutMs(),
            isBiometricEnabled = securityManager.isBiometricEnabled(),
            isPinSet = securityManager.isPinSet(),
            hasDuressPin = securityManager.hasDuressPin(),
            isDecoyMode = securityManager.isDecoyMode
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadCounts()
        refreshStorageMetrics()
    }

    private fun loadCounts() {
        viewModelScope.launch {
            combine(
                repository.getItemCount(),
                repository.getSecretItemCount(),
                repository.getEmbeddingCount(),
                repository.getConversationCount()
            ) { total, secret, embeddings, conversations ->
                listOf(total, secret, embeddings, conversations)
            }.collect { counts ->
                _uiState.update {
                    it.copy(
                        totalItems = counts[0],
                        secretItems = counts[1],
                        embeddingsCount = counts[2],
                        conversationsCount = counts[3],
                        isAppLockEnabled = securityManager.isAppLockEnabled(),
                        autoLockTimeoutMs = securityManager.getAutoLockTimeoutMs(),
                        isBiometricEnabled = securityManager.isBiometricEnabled(),
                        isPinSet = securityManager.isPinSet(),
                        hasDuressPin = securityManager.hasDuressPin(),
                        isDecoyMode = securityManager.isDecoyMode
                    )
                }
            }
        }
    }

    // -------------------------------------------------------------
    // General Settings
    // -------------------------------------------------------------

    fun setThemeMode(mode: String) {
        generalPrefs.edit().putString("theme_mode", mode).apply()
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun toggleHapticFeedback(enabled: Boolean) {
        generalPrefs.edit().putBoolean("haptic_feedback", enabled).apply()
        _uiState.update { it.copy(hapticFeedbackEnabled = enabled) }
    }

    fun setStartScreen(screen: String) {
        generalPrefs.edit().putString("start_screen", screen).apply()
        _uiState.update { it.copy(startScreen = screen) }
    }

    // -------------------------------------------------------------
    // Capture & Search Settings
    // -------------------------------------------------------------

    fun toggleAutoEnrichMetadata(enabled: Boolean) {
        generalPrefs.edit().putBoolean("auto_enrich", enabled).apply()
        _uiState.update { it.copy(autoEnrichMetadata = enabled) }
    }

    fun toggleAutoSaveQuickNotes(enabled: Boolean) {
        generalPrefs.edit().putBoolean("auto_save_notes", enabled).apply()
        _uiState.update { it.copy(autoSaveQuickNotes = enabled) }
    }

    fun toggleSearchHistory(enabled: Boolean) {
        generalPrefs.edit().putBoolean("search_history", enabled).apply()
        _uiState.update { it.copy(searchHistoryEnabled = enabled) }
    }

    fun clearSearchHistory() {
        val searchPrefs = context.getSharedPreferences("second_brain_search_history", Context.MODE_PRIVATE)
        searchPrefs.edit().clear().apply()
        _uiState.update { it.copy(operationMessage = "Search history cleared.") }
    }

    fun rebuildSearchIndex() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRebuildingIndex = true, operationMessage = "Rebuilding search index...") }
            try {
                val activeItems = repository.getActiveItems().first()
                _uiState.update {
                    it.copy(
                        isRebuildingIndex = false,
                        operationMessage = "Search index rebuilt successfully. ${activeItems.size} items indexed (Vault strictly excluded)."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isRebuildingIndex = false,
                        operationMessage = "Index rebuild failed: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    // -------------------------------------------------------------
    // Intelligence / Local AI Settings
    // -------------------------------------------------------------

    fun toggleLocalAI(enabled: Boolean) {
        localAISettings.isLocalAIEnabled = enabled
        _uiState.update { it.copy(isLocalAIEnabled = enabled) }
    }

    fun toggleSemanticSearch(enabled: Boolean) {
        localAISettings.isSemanticSearchEnabled = enabled
        _uiState.update { it.copy(isSemanticSearchEnabled = enabled) }
    }

    fun toggleAutoSummarize(enabled: Boolean) {
        localAISettings.isAutoSummarizeEnabled = enabled
        _uiState.update { it.copy(isAutoSummarizeEnabled = enabled) }
    }

    fun toggleBackgroundAI(enabled: Boolean) {
        localAISettings.isBackgroundAIEnabled = enabled
        _uiState.update { it.copy(isBackgroundAIEnabled = enabled) }
    }

    fun rebuildSemanticIndex() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRebuildingEmbeddings = true, operationMessage = "Rebuilding semantic embeddings...") }
            try {
                val activeItems = repository.getActiveItems().first()
                val embeddings = activeItems.map { item ->
                    val text = "${item.title} ${item.description ?: ""} ${item.notes} ${item.extractedText ?: ""}"
                    val vector = DeterministicLocalAIEngine.computeEmbedding(text)
                    val json = DeterministicLocalAIEngine.serializeEmbedding(vector)
                    ItemEmbeddingEntity(
                        itemId = item.id,
                        embeddingJson = json,
                        modelVersion = DeterministicLocalAIEngine.MODEL_VERSION,
                        updatedAt = System.currentTimeMillis()
                    )
                }
                repository.saveEmbeddings(embeddings)
                refreshStorageMetrics()
                _uiState.update {
                    it.copy(
                        isRebuildingEmbeddings = false,
                        operationMessage = "Semantic index rebuilt. ${embeddings.size} vectors computed (Vault strictly excluded)."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isRebuildingEmbeddings = false,
                        operationMessage = "Embeddings rebuild failed: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun resetLocalIntelligence() {
        viewModelScope.launch {
            repository.resetLocalAIData()
            refreshStorageMetrics()
            _uiState.update {
                it.copy(operationMessage = "Local intelligence reset. Derived AI data, summaries, and chat history cleared. Saved knowledge preserved.")
            }
        }
    }

    // -------------------------------------------------------------
    // Security Settings
    // -------------------------------------------------------------

    fun toggleAppLock(enabled: Boolean) {
        securityManager.setAppLockEnabled(enabled)
        _uiState.update { it.copy(isAppLockEnabled = enabled) }
    }

    fun setAutoLockTimeout(timeoutMs: Long) {
        securityManager.setAutoLockTimeoutMs(timeoutMs)
        _uiState.update { it.copy(autoLockTimeoutMs = timeoutMs) }
    }

    fun toggleBiometric(enabled: Boolean) {
        securityManager.setBiometricEnabled(enabled)
        _uiState.update { it.copy(isBiometricEnabled = enabled) }
    }

    fun setMasterPin(pin: String) {
        securityManager.setMasterPin(pin)
        _uiState.update { it.copy(isPinSet = true, operationMessage = "Master PIN updated successfully.") }
    }

    fun setDuressPin(pin: String) {
        securityManager.setDuressPin(pin)
        _uiState.update { it.copy(hasDuressPin = true, operationMessage = "Duress Decoy PIN configured.") }
    }

    fun removeDuressPin() {
        securityManager.clearDuressPin()
        _uiState.update { it.copy(hasDuressPin = false, operationMessage = "Duress Decoy PIN removed.") }
    }

    fun exitDecoyMode(masterPin: String): Boolean {
        val success = securityManager.exitDecoyMode(masterPin)
        if (success) {
            _uiState.update { it.copy(isDecoyMode = false, operationMessage = "Exited Decoy Mode. Vault restored.") }
        } else {
            _uiState.update { it.copy(operationMessage = "Incorrect Master PIN.") }
        }
        return success
    }

    // -------------------------------------------------------------
    // Real Storage Measurement & Safe Cleanup
    // -------------------------------------------------------------

    fun refreshStorageMetrics() {
        viewModelScope.launch(Dispatchers.IO) {
            var dbSize = 0L
            val dbFile = context.getDatabasePath("pkm_second_brain.db")
            if (dbFile.exists()) dbSize += dbFile.length()
            val walFile = File(dbFile.parentFile, "pkm_second_brain.db-wal")
            if (walFile.exists()) dbSize += walFile.length()
            val shmFile = File(dbFile.parentFile, "pkm_second_brain.db-shm")
            if (shmFile.exists()) dbSize += shmFile.length()

            val mediaDir = File(context.filesDir, "media")
            val mediaSize = if (mediaDir.exists()) mediaDir.walkTopDown().sumOf { it.length() } else 0L

            val cacheSize = context.cacheDir.walkTopDown().sumOf { it.length() }
            val totalAppBytes = dbSize + mediaSize + cacheSize

            val metrics = StorageMetrics(
                databaseBytes = dbSize,
                mediaFilesBytes = mediaSize,
                cacheBytes = cacheSize,
                totalAppBytes = totalAppBytes
            )

            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(storageMetrics = metrics) }
            }
        }
    }

    fun clearThumbnailCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val thumbDir = File(context.cacheDir, "image_cache")
            if (thumbDir.exists()) thumbDir.deleteRecursively()
            val coilDir = File(context.cacheDir, "image_manager_disk_cache")
            if (coilDir.exists()) coilDir.deleteRecursively()
            refreshStorageMetrics()
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(operationMessage = "Thumbnail cache cleared.") }
            }
        }
    }

    fun clearTemporaryFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.isDirectory) file.deleteRecursively() else file.delete()
            }
            refreshStorageMetrics()
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(operationMessage = "Temporary processing files cleared.") }
            }
        }
    }

    // -------------------------------------------------------------
    // Backup & Export
    // -------------------------------------------------------------

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, operationMessage = null) }
            val result = backupManager.exportBackup(uri)
            result.onSuccess { count ->
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        operationMessage = "Exported $count knowledge items successfully (Vault items excluded for safety)."
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        operationMessage = "Export failed: ${err.localizedMessage}"
                    )
                }
            }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, operationMessage = null) }
            val result = backupManager.importBackup(uri)
            result.onSuccess { count ->
                refreshStorageMetrics()
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        operationMessage = "Restored $count knowledge items with verified SHA-256 checksum."
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        operationMessage = "Restore failed: ${err.localizedMessage}"
                    )
                }
            }
        }
    }

    fun showIntroductionAgain() {
        onboardingPrefs.resetOnboarding()
        _uiState.update { it.copy(operationMessage = "Introduction will be shown on next app launch.") }
    }

    fun clearMessage() {
        _uiState.update { it.copy(operationMessage = null) }
    }
}

class SettingsViewModelFactory(
    private val repository: AppRepository,
    private val backupManager: BackupManager,
    private val securityManager: SecurityManager,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(repository, backupManager, securityManager, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
