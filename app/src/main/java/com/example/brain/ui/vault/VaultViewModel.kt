package com.example.brain.ui.vault

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.brain.data.entity.SavedItemEntity
import com.example.brain.data.model.ContentType
import com.example.brain.data.repository.AppRepository
import com.example.brain.util.SecurityManager
import com.example.brain.util.VaultCryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*

data class VaultUiState(
    val isUnlocked: Boolean = false,
    val isDecoyMode: Boolean = false,
    val isPinSet: Boolean = false,
    val isSettingUpPin: Boolean = false,
    val pinInput: String = "",
    val searchQuery: String = "",
    val secretItems: List<SavedItemEntity> = emptyList(),
    val filteredItems: List<SavedItemEntity> = emptyList(),
    val decryptedBitmaps: Map<String, Bitmap> = emptyMap(),
    val errorMessage: String? = null,
    val lockoutSeconds: Long = 0L,
    val isBiometricEnabled: Boolean = false,
    val isLoading: Boolean = false
)

class VaultViewModel(
    private val repository: AppRepository,
    private val securityManager: SecurityManager,
    private val vaultCryptoManager: VaultCryptoManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        VaultUiState(
            isPinSet = securityManager.isPinSet(),
            isSettingUpPin = !securityManager.isPinSet(),
            isBiometricEnabled = securityManager.isBiometricEnabled(),
            lockoutSeconds = securityManager.getRemainingLockoutSeconds()
        )
    )
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    init {
        checkSession()
    }

    fun checkSession() {
        if (!securityManager.isPinSet()) {
            _uiState.update { it.copy(isPinSet = false, isSettingUpPin = true) }
            return
        }

        if (securityManager.isSessionExpired()) {
            lockVault()
        }
    }

    fun onPinDigitEntered(digit: String) {
        val remaining = securityManager.getRemainingLockoutSeconds()
        if (remaining > 0) {
            _uiState.update { it.copy(lockoutSeconds = remaining, errorMessage = "Too many attempts. Cooldown active.") }
            return
        }

        val current = _uiState.value.pinInput
        if (current.length < 8) {
            val updated = current + digit
            _uiState.update { it.copy(pinInput = updated, errorMessage = null) }
            if (updated.length >= 4 && !_uiState.value.isSettingUpPin) {
                if (updated.length == 4 || updated.length == 6) {
                    verifyPin(updated)
                }
            }
        }
    }

    fun onPinBackspace() {
        val current = _uiState.value.pinInput
        if (current.isNotEmpty()) {
            _uiState.update { it.copy(pinInput = current.dropLast(1), errorMessage = null) }
        }
    }

    fun setMasterPin(pin: String, duressPin: String? = null) {
        if (pin.length < 4) {
            _uiState.update { it.copy(errorMessage = "PIN must be at least 4 digits") }
            return
        }
        securityManager.setMasterPin(pin)
        if (!duressPin.isNullOrBlank()) {
            securityManager.setDuressPin(duressPin)
        }
        _uiState.update {
            it.copy(
                isPinSet = true,
                isSettingUpPin = false,
                isUnlocked = true,
                pinInput = ""
            )
        }
        loadSecretItems()
    }

    fun unlockWithBiometric() {
        if (securityManager.unlockWithBiometric()) {
            _uiState.update {
                it.copy(
                    isUnlocked = true,
                    isDecoyMode = false,
                    pinInput = "",
                    errorMessage = null
                )
            }
            loadSecretItems()
        }
    }

    fun verifyPin(pin: String) {
        val result = securityManager.verifyPin(pin)
        when (result) {
            SecurityManager.UnlockResult.SUCCESS -> {
                _uiState.update {
                    it.copy(
                        isUnlocked = true,
                        isDecoyMode = false,
                        pinInput = "",
                        errorMessage = null,
                        lockoutSeconds = 0L
                    )
                }
                loadSecretItems()
            }
            SecurityManager.UnlockResult.SUCCESS_DURESS -> {
                // Coercion safety: decoy empty state, zero leak of secrets
                _uiState.update {
                    it.copy(
                        isUnlocked = true,
                        isDecoyMode = true,
                        pinInput = "",
                        secretItems = emptyList(),
                        filteredItems = emptyList(),
                        errorMessage = null,
                        lockoutSeconds = 0L
                    )
                }
            }
            SecurityManager.UnlockResult.INCORRECT_PIN -> {
                val remaining = securityManager.getRemainingLockoutSeconds()
                _uiState.update {
                    it.copy(
                        pinInput = "",
                        lockoutSeconds = remaining,
                        errorMessage = if (remaining > 0) "Too many attempts. Cooldown active." else "Incorrect PIN. Try again."
                    )
                }
            }
            SecurityManager.UnlockResult.RATE_LIMITED -> {
                _uiState.update {
                    it.copy(
                        pinInput = "",
                        lockoutSeconds = securityManager.getRemainingLockoutSeconds(),
                        errorMessage = "Rate limit reached. Try again later."
                    )
                }
            }
            SecurityManager.UnlockResult.NO_PIN_SET -> {
                _uiState.update { it.copy(isSettingUpPin = true) }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        filterItems(query)
    }

    private fun filterItems(query: String) {
        val all = _uiState.value.secretItems
        if (query.isBlank()) {
            _uiState.update { it.copy(filteredItems = all) }
        } else {
            val q = query.trim().lowercase()
            val filtered = all.filter {
                it.title.lowercase().contains(q) ||
                it.note.lowercase().contains(q) ||
                (it.description?.lowercase()?.contains(q) == true)
            }
            _uiState.update { it.copy(filteredItems = filtered) }
        }
    }

    private fun loadSecretItems() {
        viewModelScope.launch {
            repository.getSecretItems().collect { items ->
                _uiState.update {
                    it.copy(
                        secretItems = items,
                        filteredItems = if (it.searchQuery.isBlank()) items else it.filteredItems
                    )
                }
                decryptImagesForItems(items)
            }
        }
    }

    private fun decryptImagesForItems(items: List<SavedItemEntity>) {
        viewModelScope.launch {
            val bitmaps = mutableMapOf<String, Bitmap>()
            for (item in items) {
                val path = item.filePath
                if (path != null && vaultCryptoManager.isVaultFile(path)) {
                    val file = File(path)
                    val result = vaultCryptoManager.decryptVaultImageToBitmap(file)
                    result.onSuccess { bitmap ->
                        bitmaps[item.id] = bitmap
                    }
                }
            }
            if (bitmaps.isNotEmpty()) {
                _uiState.update { it.copy(decryptedBitmaps = it.decryptedBitmaps + bitmaps) }
            }
        }
    }

    fun createPrivateNote(title: String, note: String) {
        if (title.isBlank() && note.isBlank()) return
        viewModelScope.launch {
            val entity = SavedItemEntity(
                id = UUID.randomUUID().toString(),
                title = title.ifBlank { "Private Note" },
                type = ContentType.NOTE.name,
                note = note,
                isSecret = true,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            repository.insertItem(entity)
        }
    }

    fun importPrivateMedia(context: Context, uri: Uri, originalName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val ext = originalName.substringAfterLast(".", "bin")
                val targetFileName = "vault_${UUID.randomUUID()}.$ext"

                val encryptResult = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        vaultCryptoManager.encryptStreamToVault(stream, targetFileName)
                    } ?: Result.failure(Exception("Could not open input stream"))
                }

                encryptResult.onSuccess { encryptedFile ->
                    val isImage = ext.lowercase() in listOf("jpg", "jpeg", "png", "webp", "gif")
                    val isPdf = ext.lowercase() == "pdf"
                    val type = if (isImage) ContentType.IMAGE else if (isPdf) ContentType.PDF else ContentType.DOCUMENT

                    val entity = SavedItemEntity(
                        id = UUID.randomUUID().toString(),
                        title = originalName,
                        type = type.name,
                        filePath = encryptedFile.absolutePath,
                        isSecret = true,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.insertItem(entity)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Encryption failed: ${e.localizedMessage}") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun deleteSecretItem(item: SavedItemEntity) {
        viewModelScope.launch {
            if (item.filePath != null && vaultCryptoManager.isVaultFile(item.filePath)) {
                vaultCryptoManager.deleteVaultFile(item.filePath)
            }
            repository.deleteItem(item)
            _uiState.update {
                it.copy(
                    secretItems = it.secretItems - item,
                    filteredItems = it.filteredItems - item,
                    decryptedBitmaps = it.decryptedBitmaps - item.id
                )
            }
        }
    }

    fun lockVault() {
        securityManager.lock()
        _uiState.update {
            it.copy(
                isUnlocked = false,
                pinInput = "",
                secretItems = emptyList(),
                filteredItems = emptyList(),
                decryptedBitmaps = emptyMap(),
                isDecoyMode = false,
                searchQuery = ""
            )
        }
    }
}

class VaultViewModelFactory(
    private val repository: AppRepository,
    private val securityManager: SecurityManager,
    private val vaultCryptoManager: VaultCryptoManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VaultViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VaultViewModel(repository, securityManager, vaultCryptoManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
