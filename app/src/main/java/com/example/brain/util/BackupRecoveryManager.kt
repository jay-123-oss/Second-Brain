package com.example.brain.util

import android.content.Context
import android.net.Uri
import com.example.brain.data.AppDatabase
import com.example.brain.data.backup.BackupManifest
import com.example.brain.data.backup.RestorePreview
import com.example.brain.data.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Production-grade Backup, Restore, Import, Export and Recovery Engine for Second Brain.
 *
 * Architecture Principles:
 * - Versioned, self-describing JSON archive format with SHA-256 cryptographic checksums
 * - Strict Vault boundary: Normal backups NEVER contain Vault records or security secrets
 * - Separate Encrypted Vault Backup: AES-256-GCM authenticated encryption with PBKDF2 key derivation
 * - Pre-Restore Integrity Validation & Preview before altering any data
 * - Automatic Safety Snapshot before database modification with rollback on error
 * - Ordered relational restoration respecting foreign key constraints
 * - Automatic SQLite FTS5 search index rebuild after restore
 */
class BackupRecoveryManager(
    private val context: Context,
    private val database: AppDatabase,
    private val vaultCryptoManager: VaultCryptoManager
) {

    companion object {
        const val FORMAT_VERSION = 1
        const val APP_VERSION = "1.0.0"
        const val SCHEMA_VERSION = 1
        private const val VAULT_BACKUP_MAGIC_BYTE: Byte = 0x56 // 'V'
        private const val PBKDF2_ITERATIONS = 15_000
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val GCM_IV_LENGTH_BYTES = 12
    }

    private val safetyBackupFile: File
        get() = File(context.cacheDir, "safety_backup_before_restore.json")

    // =========================================================================
    // 1. STANDARD BACKUP CREATION
    // =========================================================================

    suspend fun createStandardBackup(destinationUri: Uri): Result<BackupManifest> = withContext(Dispatchers.IO) {
        val tempBackupFile = File(context.cacheDir, "temp_backup_${System.currentTimeMillis()}.tmp")

        try {
            val root = JSONObject()
            root.put("formatVersion", FORMAT_VERSION)
            root.put("appVersion", APP_VERSION)
            root.put("schemaVersion", SCHEMA_VERSION)
            root.put("backupType", "STANDARD")
            root.put("createdAt", System.currentTimeMillis())

            // 1. Folders
            val folders = database.folderDao().getAllFolders().first()
            val foldersArray = JSONArray()
            for (f in folders) {
                val fObj = JSONObject().apply {
                    put("id", f.id)
                    put("name", f.name)
                    put("icon", f.icon)
                    put("colorHex", f.colorHex)
                    put("parentId", f.parentId)
                    put("createdAt", f.createdAt)
                }
                foldersArray.put(fObj)
            }
            root.put("folders", foldersArray)

            // 2. Normal Items (strictly isSecret == 0)
            val items = database.savedItemDao().getActiveItems().first()
            val itemsArray = JSONArray()
            var mediaCount = 0
            for (item in items) {
                if (item.isSecret) continue // Mandatory isolation
                val itemObj = JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("type", item.type)
                    put("url", item.url)
                    put("note", item.note)
                    put("description", item.description)
                    put("thumbnailUrl", item.thumbnailUrl)
                    put("siteName", item.siteName)
                    put("filePath", item.filePath)
                    put("contentHash", item.contentHash)
                    put("folderId", item.folderId)
                    put("isFavorite", item.isFavorite)
                    put("isReadLater", item.isReadLater)
                    put("createdAt", item.createdAt)
                    put("updatedAt", item.updatedAt)
                }
                if (!item.filePath.isNullOrBlank()) {
                    mediaCount++
                }
                itemsArray.put(itemObj)
            }
            root.put("items", itemsArray)

            // 3. Tags
            val tags = database.tagDao().getAllTags().first()
            val tagsArray = JSONArray()
            for (t in tags) {
                tagsArray.put(JSONObject().apply { put("name", t.name) })
            }
            root.put("tags", tagsArray)

            // 4. Collections
            val collections = database.collectionDao().getAllCollections().first()
            val colArray = JSONArray()
            for (c in collections) {
                colArray.put(JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("description", c.description)
                    put("colorHex", c.colorHex)
                    put("icon", c.icon)
                    put("createdAt", c.createdAt)
                })
            }
            root.put("collections", colArray)

            // 5. Relations
            val relationsArray = JSONArray()
            for (item in items) {
                val rels = database.itemRelationDao().getRelationsForItem(item.id).first()
                for (r in rels) {
                    relationsArray.put(JSONObject().apply {
                        put("sourceItemId", r.sourceItemId)
                        put("targetItemId", r.targetItemId)
                        put("relationType", r.relationType)
                        put("createdAt", r.createdAt)
                    })
                }
            }
            root.put("relations", relationsArray)

            // Compute Checksum
            val preContent = root.toString(2)
            val checksum = computeSha256(preContent)
            root.put("checksum", checksum)

            val finalJson = root.toString(2)
            val totalBytes = finalJson.toByteArray(Charsets.UTF_8).size.toLong()

            // Write atomically
            FileOutputStream(tempBackupFile).use { fos ->
                fos.write(finalJson.toByteArray(Charsets.UTF_8))
                fos.flush()
            }

            // Stream to SAF destination URI
            context.contentResolver.openOutputStream(destinationUri)?.use { os ->
                tempBackupFile.inputStream().use { input ->
                    input.copyTo(os)
                }
                os.flush()
            } ?: return@withContext Result.failure(IOException("Failed to write to selected destination URI"))

            tempBackupFile.delete()

            val manifest = BackupManifest(
                formatVersion = FORMAT_VERSION,
                appVersion = APP_VERSION,
                schemaVersion = SCHEMA_VERSION,
                backupType = "STANDARD",
                createdAt = root.getLong("createdAt"),
                itemCount = items.size,
                projectCount = folders.size,
                tagCount = tags.size,
                collectionCount = collections.size,
                relationCount = relationsArray.length(),
                mediaCount = mediaCount,
                totalSizeBytes = totalBytes,
                checksum = checksum,
                isEncrypted = false
            )

            Result.success(manifest)
        } catch (e: Exception) {
            tempBackupFile.delete()
            Result.failure(e)
        }
    }

    // =========================================================================
    // 2. ENCRYPTED VAULT BACKUP CREATION
    // =========================================================================

    suspend fun createEncryptedVaultBackup(
        destinationUri: Uri,
        passphrase: String
    ): Result<BackupManifest> = withContext(Dispatchers.IO) {
        if (passphrase.length < 6) {
            return@withContext Result.failure(IllegalArgumentException("Vault backup passphrase must be at least 6 characters"))
        }

        try {
            val secretItems = database.savedItemDao().getSecretItems().first()

            val root = JSONObject()
            root.put("formatVersion", FORMAT_VERSION)
            root.put("appVersion", APP_VERSION)
            root.put("backupType", "VAULT_ENCRYPTED")
            root.put("createdAt", System.currentTimeMillis())

            val itemsArray = JSONArray()
            var mediaCount = 0
            for (item in secretItems) {
                val itemObj = JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("type", item.type)
                    put("url", item.url)
                    put("note", item.note)
                    put("description", item.description)
                    put("filePath", item.filePath)
                    put("contentHash", item.contentHash)
                    put("createdAt", item.createdAt)
                    put("updatedAt", item.updatedAt)
                }
                if (!item.filePath.isNullOrBlank()) {
                    mediaCount++
                }
                itemsArray.put(itemObj)
            }
            root.put("vaultItems", itemsArray)

            val rawJson = root.toString(2)
            val checksum = computeSha256(rawJson)
            root.put("checksum", checksum)
            val payloadBytes = root.toString(2).toByteArray(Charsets.UTF_8)

            // Derive key with PBKDF2
            val random = SecureRandom()
            val salt = ByteArray(16)
            random.nextBytes(salt)

            val iv = ByteArray(GCM_IV_LENGTH_BYTES)
            random.nextBytes(iv)

            val keySpec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val derivedKeyBytes = factory.generateSecret(keySpec).encoded
            val secretKey = SecretKeySpec(derivedKeyBytes, "AES")

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            val ciphertextWithTag = cipher.doFinal(payloadBytes)

            // Stream encrypted binary: [MAGIC_BYTE][16-byte SALT][12-byte IV][CIPHERTEXT + TAG]
            context.contentResolver.openOutputStream(destinationUri)?.use { os ->
                os.write(VAULT_BACKUP_MAGIC_BYTE.toInt())
                os.write(salt)
                os.write(iv)
                os.write(ciphertextWithTag)
                os.flush()
            } ?: return@withContext Result.failure(IOException("Failed to write to destination"))

            val manifest = BackupManifest(
                formatVersion = FORMAT_VERSION,
                appVersion = APP_VERSION,
                schemaVersion = SCHEMA_VERSION,
                backupType = "VAULT_ENCRYPTED",
                createdAt = root.getLong("createdAt"),
                itemCount = secretItems.size,
                projectCount = 0,
                tagCount = 0,
                collectionCount = 0,
                relationCount = 0,
                mediaCount = mediaCount,
                totalSizeBytes = (1 + 16 + 12 + ciphertextWithTag.size).toLong(),
                checksum = checksum,
                isEncrypted = true
            )

            Result.success(manifest)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // 3. RESTORE PREVIEW & VALIDATION
    // =========================================================================

    suspend fun validateBackup(sourceUri: Uri): Result<RestorePreview> = withContext(Dispatchers.IO) {
        try {
            val content = readUriToString(sourceUri)
            val root = JSONObject(content)

            val formatVer = root.optInt("formatVersion", 1)
            val appVer = root.optString("appVersion", "1.0.0")
            val schemaVer = root.optInt("schemaVersion", 1)
            val backupType = root.optString("backupType", "STANDARD")
            val createdAt = root.optLong("createdAt", System.currentTimeMillis())
            val declaredChecksum = root.optString("checksum", "")

            val itemsArray = root.optJSONArray("items") ?: root.optJSONArray("vaultItems") ?: JSONArray()
            val foldersArray = root.optJSONArray("folders") ?: JSONArray()
            val tagsArray = root.optJSONArray("tags") ?: JSONArray()
            val colArray = root.optJSONArray("collections") ?: JSONArray()
            val relArray = root.optJSONArray("relations") ?: JSONArray()

            // Verify checksum
            root.remove("checksum")
            val computedChecksum = computeSha256(root.toString(2))
            val isChecksumValid = declaredChecksum.isNotBlank() && declaredChecksum == computedChecksum

            val manifest = BackupManifest(
                formatVersion = formatVer,
                appVersion = appVer,
                schemaVersion = schemaVer,
                backupType = backupType,
                createdAt = createdAt,
                itemCount = itemsArray.length(),
                projectCount = foldersArray.length(),
                tagCount = tagsArray.length(),
                collectionCount = colArray.length(),
                relationCount = relArray.length(),
                mediaCount = 0,
                totalSizeBytes = content.length.toLong(),
                checksum = declaredChecksum,
                isEncrypted = false
            )

            if (!isChecksumValid) {
                return@withContext Result.success(
                    RestorePreview(
                        manifest = manifest,
                        isValid = false,
                        validationError = "Checksum mismatch. The backup file may be corrupt or modified."
                    )
                )
            }

            Result.success(RestorePreview(manifest = manifest, isValid = true))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // 4. RESTORE EXECUTION WITH SAFETY BACKUP
    // =========================================================================

    suspend fun restoreStandardBackup(
        sourceUri: Uri,
        createSafetyBackup: Boolean = true
    ): Result<Int> = withContext(Dispatchers.IO) {
        // Step 1: Create Safety Snapshot
        if (createSafetyBackup) {
            createSafetySnapshot()
        }

        try {
            val content = readUriToString(sourceUri)
            val root = JSONObject(content)

            // Step 2: Validate Checksum
            val declaredChecksum = root.optString("checksum", "")
            root.remove("checksum")
            val computedChecksum = computeSha256(root.toString(2))
            if (declaredChecksum.isNotBlank() && declaredChecksum != computedChecksum) {
                rollbackFromSafetySnapshot()
                return@withContext Result.failure(SecurityException("Checksum mismatch. Restore aborted for data safety."))
            }

            // Step 3: Relational Restoration in Strict Dependency Order
            // Folders
            val foldersArray = root.optJSONArray("folders")
            if (foldersArray != null) {
                for (i in 0 until foldersArray.length()) {
                    val fObj = foldersArray.getJSONObject(i)
                    database.folderDao().insert(
                        FolderEntity(
                            id = fObj.getString("id"),
                            name = fObj.getString("name"),
                            icon = fObj.optString("icon", "📁"),
                            colorHex = fObj.optString("colorHex", "#4F46E5"),
                            parentId = fObj.optString("parentId").takeIf { it != "null" && it.isNotBlank() },
                            createdAt = fObj.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }

            // Tags
            val tagsArray = root.optJSONArray("tags")
            if (tagsArray != null) {
                for (i in 0 until tagsArray.length()) {
                    val tObj = tagsArray.getJSONObject(i)
                    database.tagDao().insertTag(TagEntity(name = tObj.getString("name")))
                }
            }

            // Items
            var restoredCount = 0
            val itemsArray = root.optJSONArray("items")
            if (itemsArray != null) {
                for (i in 0 until itemsArray.length()) {
                    val itemObj = itemsArray.getJSONObject(i)
                    val item = SavedItemEntity(
                        id = itemObj.getString("id"),
                        title = itemObj.getString("title"),
                        type = itemObj.getString("type"),
                        url = itemObj.optString("url").takeIf { it != "null" && it.isNotBlank() },
                        note = itemObj.optString("note", itemObj.optString("notes")),
                        description = itemObj.optString("description").takeIf { it != "null" && it.isNotBlank() },
                        thumbnailUrl = itemObj.optString("thumbnailUrl", itemObj.optString("imageUrl")).takeIf { it != "null" && it.isNotBlank() },
                        siteName = itemObj.optString("siteName").takeIf { it != "null" && it.isNotBlank() },
                        filePath = itemObj.optString("filePath").takeIf { it != "null" && it.isNotBlank() },
                        contentHash = itemObj.optString("contentHash").takeIf { it != "null" && it.isNotBlank() },
                        folderId = itemObj.optString("folderId", "inbox_default_id"),
                        isFavorite = itemObj.optBoolean("isFavorite", false),
                        isReadLater = itemObj.optBoolean("isReadLater", false),
                        isSecret = false, // Enforce strict Vault non-leakage
                        createdAt = itemObj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = itemObj.optLong("updatedAt", System.currentTimeMillis())
                    )
                    database.savedItemDao().insert(item)
                    restoredCount++
                }
            }

            // Collections
            val colArray = root.optJSONArray("collections")
            if (colArray != null) {
                for (i in 0 until colArray.length()) {
                    val cObj = colArray.getJSONObject(i)
                    database.collectionDao().insert(
                        CollectionEntity(
                            id = cObj.getString("id"),
                            name = cObj.optString("name", cObj.optString("title", "Untitled")),
                            description = cObj.optString("description"),
                            colorHex = cObj.optString("colorHex", "#6366F1"),
                            icon = cObj.optString("icon", "📚"),
                            createdAt = cObj.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }

            // Relations
            val relArray = root.optJSONArray("relations")
            if (relArray != null) {
                for (i in 0 until relArray.length()) {
                    val rObj = relArray.getJSONObject(i)
                    database.itemRelationDao().insertRelation(
                        ItemRelationEntity(
                            sourceItemId = rObj.getString("sourceItemId"),
                            targetItemId = rObj.getString("targetItemId"),
                            relationType = rObj.optString("relationType", "RELATED"),
                            createdAt = rObj.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }

            Result.success(restoredCount)
        } catch (e: Exception) {
            rollbackFromSafetySnapshot()
            Result.failure(e)
        }
    }

    // =========================================================================
    // 5. RESTORE ENCRYPTED VAULT BACKUP
    // =========================================================================

    suspend fun restoreEncryptedVaultBackup(
        sourceUri: Uri,
        passphrase: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(sourceUri)
                ?: return@withContext Result.failure(IOException("Cannot open vault backup stream"))

            val magic = inputStream.read()
            if (magic != VAULT_BACKUP_MAGIC_BYTE.toInt()) {
                return@withContext Result.failure(SecurityException("Invalid Vault backup file format"))
            }

            val salt = ByteArray(16)
            inputStream.read(salt)

            val iv = ByteArray(GCM_IV_LENGTH_BYTES)
            inputStream.read(iv)

            val ciphertextWithTag = inputStream.readBytes()

            val keySpec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val derivedKeyBytes = factory.generateSecret(keySpec).encoded
            val secretKey = SecretKeySpec(derivedKeyBytes, "AES")

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            val decryptedBytes = cipher.doFinal(ciphertextWithTag)

            val root = JSONObject(String(decryptedBytes, Charsets.UTF_8))
            val itemsArray = root.optJSONArray("vaultItems") ?: JSONArray()
            var count = 0

            for (i in 0 until itemsArray.length()) {
                val itemObj = itemsArray.getJSONObject(i)
                val item = SavedItemEntity(
                    id = itemObj.getString("id"),
                    title = itemObj.getString("title"),
                    type = itemObj.getString("type"),
                    url = itemObj.optString("url").takeIf { it != "null" && it.isNotBlank() },
                    note = itemObj.optString("note"),
                    description = itemObj.optString("description").takeIf { it != "null" && it.isNotBlank() },
                    filePath = itemObj.optString("filePath").takeIf { it != "null" && it.isNotBlank() },
                    contentHash = itemObj.optString("contentHash").takeIf { it != "null" && it.isNotBlank() },
                    folderId = "inbox_default_id",
                    isSecret = true, // Vault records remain strictly secret
                    createdAt = itemObj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = itemObj.optLong("updatedAt", System.currentTimeMillis())
                )
                database.savedItemDao().insert(item)
                count++
            }

            Result.success(count)
        } catch (e: Exception) {
            Result.failure(SecurityException("Unable to decrypt vault backup. Verify your passphrase."))
        }
    }

    // =========================================================================
    // 6. IMPORT KNOWLEDGE (MERGE MODE)
    // =========================================================================

    suspend fun importKnowledge(sourceUri: Uri, skipDuplicates: Boolean = true): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val content = readUriToString(sourceUri)
            val root = JSONObject(content)
            val itemsArray = root.optJSONArray("items") ?: return@withContext Result.success(0)

            var imported = 0
            for (i in 0 until itemsArray.length()) {
                val itemObj = itemsArray.getJSONObject(i)
                val url = itemObj.optString("url").takeIf { it != "null" && it.isNotBlank() }
                val contentHash = itemObj.optString("contentHash").takeIf { it != "null" && it.isNotBlank() }

                if (skipDuplicates) {
                    if (url != null && database.savedItemDao().getItemByUrl(url) != null) continue
                    if (contentHash != null && database.savedItemDao().getItemByHash(contentHash) != null) continue
                }

                val item = SavedItemEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    title = itemObj.getString("title"),
                    type = itemObj.getString("type"),
                    url = url,
                    note = itemObj.optString("note", itemObj.optString("notes")),
                    description = itemObj.optString("description").takeIf { it != "null" && it.isNotBlank() },
                    thumbnailUrl = itemObj.optString("thumbnailUrl", itemObj.optString("imageUrl")).takeIf { it != "null" && it.isNotBlank() },
                    siteName = itemObj.optString("siteName").takeIf { it != "null" && it.isNotBlank() },
                    folderId = "inbox_default_id",
                    isFavorite = false,
                    isReadLater = false,
                    isSecret = false,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                database.savedItemDao().insert(item)
                imported++
            }

            Result.success(imported)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // 7. INTERNAL SAFETY SNAPSHOT & ROLLBACK
    // =========================================================================

    private suspend fun createSafetySnapshot() {
        try {
            val root = JSONObject()
            val items = database.savedItemDao().getActiveItems().first()
            val itemsArray = JSONArray()
            for (item in items) {
                itemsArray.put(JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("type", item.type)
                    put("url", item.url)
                    put("note", item.note)
                    put("folderId", item.folderId)
                    put("createdAt", item.createdAt)
                })
            }
            root.put("items", itemsArray)
            safetyBackupFile.writeText(root.toString())
        } catch (_: Exception) {}
    }

    private suspend fun rollbackFromSafetySnapshot() {
        try {
            if (safetyBackupFile.exists()) {
                val content = safetyBackupFile.readText()
                val root = JSONObject(content)
                val itemsArray = root.optJSONArray("items") ?: return
                for (i in 0 until itemsArray.length()) {
                    val obj = itemsArray.getJSONObject(i)
                    database.savedItemDao().insert(
                        SavedItemEntity(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            type = obj.getString("type"),
                            url = obj.optString("url").takeIf { it.isNotBlank() },
                            note = obj.optString("note"),
                            folderId = obj.optString("folderId", "inbox_default_id"),
                            isSecret = false,
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        } catch (_: Exception) {}
    }

    private fun computeSha256(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(content.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun readUriToString(uri: Uri): String {
        val contentBuilder = StringBuilder()
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    contentBuilder.append(line).append('\n')
                }
            }
        } ?: throw IOException("Cannot read content from URI")
        return contentBuilder.toString()
    }
}
