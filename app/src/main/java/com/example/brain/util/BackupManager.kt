package com.example.brain.util

import android.content.Context
import android.net.Uri
import com.example.brain.data.AppDatabase
import com.example.brain.data.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.security.MessageDigest

/**
 * Robust, offline backup manager that serializes the Second Brain database to JSON.
 * - Enforces Vault isolation: items with isSecret = true are NEVER exported in standard backup.
 * - Computes and verifies a SHA-256 integrity checksum.
 */
class BackupManager(
    private val context: Context,
    private val database: AppDatabase
) {

    suspend fun exportBackup(destinationUri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject()
            root.put("version", 1)
            root.put("timestamp", System.currentTimeMillis())
            root.put("app", "Second Brain")

            // 1. Folders
            val folders = database.folderDao().getAllFolders().first()
            val foldersArray = JSONArray()
            for (f in folders) {
                val fObj = JSONObject()
                fObj.put("id", f.id)
                fObj.put("name", f.name)
                fObj.put("icon", f.icon)
                fObj.put("colorHex", f.colorHex)
                fObj.put("parentId", f.parentId)
                fObj.put("createdAt", f.createdAt)
                foldersArray.put(fObj)
            }
            root.put("folders", foldersArray)

            // 2. Items (EXCLUDING isSecret = true)
            val items = database.savedItemDao().getActiveItems().first()
            val itemsArray = JSONArray()
            for (item in items) {
                if (item.isSecret) continue // Strict Vault boundary
                val itemObj = JSONObject()
                itemObj.put("id", item.id)
                itemObj.put("title", item.title)
                itemObj.put("type", item.type)
                itemObj.put("url", item.url)
                itemObj.put("notes", item.notes)
                itemObj.put("description", item.description)
                itemObj.put("imageUrl", item.imageUrl)
                itemObj.put("siteName", item.siteName)
                itemObj.put("filePath", item.filePath)
                itemObj.put("folderId", item.folderId)
                itemObj.put("isFavorite", item.isFavorite)
                itemObj.put("isReadLater", item.isReadLater)
                itemObj.put("createdAt", item.createdAt)
                itemObj.put("updatedAt", item.updatedAt)
                itemsArray.put(itemObj)
            }
            root.put("items", itemsArray)

            // 3. Tags
            val tags = database.tagDao().getAllTags().first()
            val tagsArray = JSONArray()
            for (t in tags) {
                val tObj = JSONObject()
                tObj.put("name", t.name)
                tagsArray.put(tObj)
            }
            root.put("tags", tagsArray)

            // 4. Collections
            val collections = database.collectionDao().getAllCollections().first()
            val colArray = JSONArray()
            for (c in collections) {
                val cObj = JSONObject()
                cObj.put("id", c.id)
                cObj.put("title", c.title)
                cObj.put("description", c.description)
                cObj.put("colorHex", c.colorHex)
                cObj.put("icon", c.icon)
                cObj.put("createdAt", c.createdAt)
                colArray.put(cObj)
            }
            root.put("collections", colArray)

            val jsonContent = root.toString(2)
            val checksum = computeSha256(jsonContent)
            root.put("checksum", checksum)

            val finalOutput = root.toString(2)
            context.contentResolver.openOutputStream(destinationUri)?.use { os ->
                os.write(finalOutput.toByteArray(Charsets.UTF_8))
                os.flush()
            } ?: return@withContext Result.failure(Exception("Unable to open output stream for destination URI"))

            Result.success(items.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importBackup(sourceUri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val contentBuilder = StringBuilder()
            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        contentBuilder.append(line).append('\n')
                    }
                }
            } ?: return@withContext Result.failure(Exception("Unable to open input stream for source URI"))

            val root = JSONObject(contentBuilder.toString())
            val declaredChecksum = root.optString("checksum", "")

            // Validate checksum by removing checksum field and recomputing
            root.remove("checksum")
            val rawContent = root.toString(2)
            val computed = computeSha256(rawContent)

            if (declaredChecksum.isNotBlank() && declaredChecksum != computed) {
                return@withContext Result.failure(SecurityException("Backup file checksum mismatch. The file may be corrupted."))
            }

            var importedCount = 0

            // Import Folders
            val foldersArray = root.optJSONArray("folders")
            if (foldersArray != null) {
                for (i in 0 until foldersArray.length()) {
                    val fObj = foldersArray.getJSONObject(i)
                    val folder = FolderEntity(
                        id = fObj.getString("id"),
                        name = fObj.getString("name"),
                        icon = fObj.optString("icon", "📁"),
                        colorHex = fObj.optString("colorHex", "#4F46E5"),
                        parentId = fObj.optString("parentId").takeIf { it != "null" && it.isNotBlank() },
                        createdAt = fObj.optLong("createdAt", System.currentTimeMillis())
                    )
                    database.folderDao().insert(folder)
                }
            }

            // Import Items
            val itemsArray = root.optJSONArray("items")
            if (itemsArray != null) {
                for (i in 0 until itemsArray.length()) {
                    val itemObj = itemsArray.getJSONObject(i)
                    val item = SavedItemEntity(
                        id = itemObj.getString("id"),
                        title = itemObj.getString("title"),
                        type = itemObj.getString("type"),
                        url = itemObj.optString("url").takeIf { it != "null" && it.isNotBlank() },
                        note = itemObj.optString("notes"),
                        description = itemObj.optString("description").takeIf { it != "null" && it.isNotBlank() },
                        thumbnailUrl = itemObj.optString("imageUrl").takeIf { it != "null" && it.isNotBlank() },
                        siteName = itemObj.optString("siteName").takeIf { it != "null" && it.isNotBlank() },
                        filePath = itemObj.optString("filePath").takeIf { it != "null" && it.isNotBlank() },
                        folderId = itemObj.optString("folderId", "inbox_default_id"),
                        isFavorite = itemObj.optBoolean("isFavorite", false),
                        isReadLater = itemObj.optBoolean("isReadLater", false),
                        isSecret = false, // Never mark standard imported items as secret
                        createdAt = itemObj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = itemObj.optLong("updatedAt", System.currentTimeMillis())
                    )
                    database.savedItemDao().insert(item)
                    importedCount++
                }
            }

            // Import Tags
            val tagsArray = root.optJSONArray("tags")
            if (tagsArray != null) {
                for (i in 0 until tagsArray.length()) {
                    val tObj = tagsArray.getJSONObject(i)
                    val tag = TagEntity(name = tObj.getString("name"))
                    database.tagDao().insertTag(tag)
                }
            }

            // Import Collections
            val colArray = root.optJSONArray("collections")
            if (colArray != null) {
                for (i in 0 until colArray.length()) {
                    val cObj = colArray.getJSONObject(i)
                    val col = CollectionEntity(
                        id = cObj.getString("id"),
                        name = cObj.optString("title", cObj.optString("name", "Untitled")),
                        description = cObj.optString("description"),
                        colorHex = cObj.optString("colorHex", "#6366F1"),
                        icon = cObj.optString("icon", "📚"),
                        createdAt = cObj.optLong("createdAt", System.currentTimeMillis())
                    )
                    database.collectionDao().insert(col)
                }
            }

            Result.success(importedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun computeSha256(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(content.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
