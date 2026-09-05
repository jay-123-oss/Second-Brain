package com.example.brain.data.backup

data class BackupManifest(
    val formatVersion: Int = 1,
    val appVersion: String = "1.0.0",
    val schemaVersion: Int = 1,
    val backupType: String = "STANDARD", // "STANDARD" or "VAULT_ENCRYPTED"
    val createdAt: Long = System.currentTimeMillis(),
    val itemCount: Int = 0,
    val projectCount: Int = 0,
    val tagCount: Int = 0,
    val collectionCount: Int = 0,
    val relationCount: Int = 0,
    val mediaCount: Int = 0,
    val totalSizeBytes: Long = 0L,
    val checksum: String = "",
    val isEncrypted: Boolean = false
)

data class RestorePreview(
    val manifest: BackupManifest,
    val isValid: Boolean,
    val validationError: String? = null
)

data class IntegrityReport(
    val isHealthy: Boolean,
    val databaseStatus: String,
    val mediaStatus: String,
    val searchIndexStatus: String,
    val vaultIsolationStatus: String,
    val issues: List<String> = emptyList()
)

data class StorageBreakdown(
    val databaseSizeBytes: Long = 0L,
    val mediaSizeBytes: Long = 0L,
    val vaultSizeBytes: Long = 0L,
    val cacheSizeBytes: Long = 0L,
    val freeDiskSpaceBytes: Long = 0L
)
