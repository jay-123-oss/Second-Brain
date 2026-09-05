package com.example.brain.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.brain.data.backup.BackupManifest
import com.example.brain.data.backup.IntegrityReport
import com.example.brain.data.backup.RestorePreview
import com.example.brain.data.backup.StorageBreakdown
import com.example.brain.util.BackupRecoveryManager
import com.example.brain.util.DataIntegrityManager
import com.example.brain.util.SecurityManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackupRecoveryUiState(
    val isBusy: Boolean = false,
    val operationStep: String? = null,
    val operationMessage: String? = null,
    val lastCreatedManifest: BackupManifest? = null,
    val restorePreview: RestorePreview? = null,
    val pendingRestoreUri: Uri? = null,
    val integrityReport: IntegrityReport? = null,
    val storageBreakdown: StorageBreakdown? = null,
    val isVaultUnlocked: Boolean = false
)

class BackupRecoveryViewModel(
    private val backupRecoveryManager: BackupRecoveryManager,
    private val dataIntegrityManager: DataIntegrityManager,
    private val securityManager: SecurityManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupRecoveryUiState(isVaultUnlocked = !securityManager.isSessionExpired()))
    val uiState: StateFlow<BackupRecoveryUiState> = _uiState.asStateFlow()

    init {
        refreshStorageInfo()
    }

    fun refreshStorageInfo() {
        viewModelScope.launch {
            val breakdown = dataIntegrityManager.getStorageBreakdown()
            _uiState.update { it.copy(storageBreakdown = breakdown) }
        }
    }

    fun runIntegrityScan() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, operationStep = "Auditing database & media...") }
            val report = dataIntegrityManager.checkIntegrity()
            _uiState.update {
                it.copy(
                    isBusy = false,
                    operationStep = null,
                    integrityReport = report,
                    operationMessage = if (report.isHealthy) "Integrity check passed. All data is healthy." else "${report.issues.size} issues identified."
                )
            }
        }
    }

    fun repairIssues() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, operationStep = "Repairing orphaned records...") }
            val result = dataIntegrityManager.repairDatabaseIssues()
            result.onSuccess { count ->
                val report = dataIntegrityManager.checkIntegrity()
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        operationStep = null,
                        integrityReport = report,
                        operationMessage = "Repaired $count issues. Cache cleaned."
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        operationStep = null,
                        operationMessage = "Repair failed: ${err.localizedMessage}"
                    )
                }
            }
        }
    }

    fun createStandardBackup(destinationUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, operationStep = "Creating verified standard backup...") }
            val result = backupRecoveryManager.createStandardBackup(destinationUri)
            result.onSuccess { manifest ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        operationStep = null,
                        lastCreatedManifest = manifest,
                        operationMessage = "Backup created and verified with SHA-256! (${manifest.itemCount} items, ${manifest.projectCount} projects)."
                    )
                }
                refreshStorageInfo()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        operationStep = null,
                        operationMessage = "Backup failed: ${err.localizedMessage}"
                    )
                }
            }
        }
    }

    fun createEncryptedVaultBackup(destinationUri: Uri, passphrase: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, operationStep = "Encrypting private vault with AES-256 GCM...") }
            val result = backupRecoveryManager.createEncryptedVaultBackup(destinationUri, passphrase)
            result.onSuccess { manifest ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        operationStep = null,
                        lastCreatedManifest = manifest,
                        operationMessage = "Encrypted Vault backup created successfully! (${manifest.itemCount} secret items)."
                    )
                }
                refreshStorageInfo()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        operationStep = null,
                        operationMessage = "Vault backup failed: ${err.localizedMessage}"
                    )
                }
            }
        }
    }

    fun selectBackupForRestore(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, operationStep = "Inspecting backup manifest & checksum...") }
            val result = backupRecoveryManager.validateBackup(uri)
            result.onSuccess { preview ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        operationStep = null,
                        restorePreview = preview,
                        pendingRestoreUri = uri
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        operationStep = null,
                        operationMessage = "Invalid backup: ${err.localizedMessage}"
                    )
                }
            }
        }
    }

    fun dismissRestorePreview() {
        _uiState.update { it.copy(restorePreview = null, pendingRestoreUri = null) }
    }

    fun executeRestore(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, operationStep = "Restoring data and rebuilding FTS search index...", restorePreview = null) }
            val result = backupRecoveryManager.restoreStandardBackup(uri, createSafetyBackup = true)
            result.onSuccess { count ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        operationStep = null,
                        pendingRestoreUri = null,
                        operationMessage = "Restore complete! Successfully recovered $count knowledge items with intact links."
                    )
                }
                refreshStorageInfo()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        operationStep = null,
                        pendingRestoreUri = null,
                        operationMessage = "Restore failed: ${err.localizedMessage}. Safety snapshot rollback executed."
                    )
                }
            }
        }
    }

    fun restoreVaultBackup(uri: Uri, passphrase: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, operationStep = "Decrypting and restoring Private Vault records...") }
            val result = backupRecoveryManager.restoreEncryptedVaultBackup(uri, passphrase)
            result.onSuccess { count ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        operationStep = null,
                        operationMessage = "Vault restored ($count secret records). Vault remains securely locked."
                    )
                }
                refreshStorageInfo()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        operationStep = null,
                        operationMessage = err.localizedMessage
                    )
                }
            }
        }
    }

    fun importKnowledge(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, operationStep = "Importing knowledge items...") }
            val result = backupRecoveryManager.importKnowledge(uri, skipDuplicates = true)
            result.onSuccess { count ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        operationStep = null,
                        operationMessage = "Imported $count new items (duplicate items skipped)."
                    )
                }
                refreshStorageInfo()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        operationStep = null,
                        operationMessage = "Import failed: ${err.localizedMessage}"
                    )
                }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(operationMessage = null) }
    }
}

class BackupRecoveryViewModelFactory(
    private val backupRecoveryManager: BackupRecoveryManager,
    private val dataIntegrityManager: DataIntegrityManager,
    private val securityManager: SecurityManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BackupRecoveryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BackupRecoveryViewModel(backupRecoveryManager, dataIntegrityManager, securityManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
