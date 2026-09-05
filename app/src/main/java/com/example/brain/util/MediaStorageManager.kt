package com.example.brain.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.example.brain.data.model.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale

data class StoredMediaResult(
    val localFilePath: String,
    val originalFileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256Hash: String,
    val contentType: ContentType
)

object MediaStorageManager {

    suspend fun storeContentUri(context: Context, uri: Uri): StoredMediaResult = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"

        // Resolve original display name
        var displayName = "file_${System.currentTimeMillis()}"
        var sizeBytes: Long = 0
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex != -1) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrBlank()) displayName = name
                    }
                    if (sizeIndex != -1) {
                        sizeBytes = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (_: Exception) {}

        val extension = resolveExtension(displayName, mimeType)
        val contentType = resolveContentType(mimeType, extension)
        val folderName = when (contentType) {
            ContentType.IMAGE -> "images"
            ContentType.PDF -> "documents"
            ContentType.AUDIO -> "audio"
            ContentType.VIDEO -> "videos"
            else -> "media"
        }

        val targetDir = File(context.filesDir, "media/$folderName").apply {
            if (!exists()) mkdirs()
        }

        // Stream into temporary cache file while calculating SHA-256
        val tempFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}.$extension")
        val digest = MessageDigest.getInstance("SHA-256")
        var totalBytesRead: Long = 0

        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                    output.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                }
                output.flush()
            }
        } ?: throw IllegalStateException("Could not open input stream for $uri")

        val hashBytes = digest.digest()
        val sha256 = hashBytes.joinToString("") { "%02x".format(it) }

        // Deduplicated target file named by hash
        val finalFileName = "$sha256.$extension"
        val finalFile = File(targetDir, finalFileName)

        if (finalFile.exists() && finalFile.length() > 0) {
            // Already stored! Reuse existing file and delete temp
            tempFile.delete()
        } else {
            // Rename atomically
            val renamed = tempFile.renameTo(finalFile)
            if (!renamed) {
                // Fallback copy
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
            }
        }

        StoredMediaResult(
            localFilePath = finalFile.absolutePath,
            originalFileName = displayName,
            mimeType = mimeType,
            sizeBytes = if (sizeBytes > 0) sizeBytes else totalBytesRead,
            sha256Hash = sha256,
            contentType = contentType
        )
    }

    private fun resolveExtension(name: String, mimeType: String): String {
        val dotIdx = name.lastIndexOf('.')
        if (dotIdx != -1 && dotIdx < name.length - 1) {
            return name.substring(dotIdx + 1).lowercase(Locale.ROOT)
        }
        val extensionFromMime = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        return extensionFromMime ?: "dat"
    }

    private fun resolveContentType(mimeType: String, extension: String): ContentType {
        return when {
            mimeType.startsWith("image/") || extension in listOf("jpg", "jpeg", "png", "webp", "gif", "svg") -> ContentType.IMAGE
            mimeType.startsWith("video/") || extension in listOf("mp4", "mkv", "mov", "webm") -> ContentType.VIDEO
            mimeType.startsWith("audio/") || extension in listOf("mp3", "m4a", "wav", "aac", "ogg") -> ContentType.AUDIO
            mimeType == "application/pdf" || extension == "pdf" -> ContentType.PDF
            extension in listOf("doc", "docx", "txt", "md", "csv", "json") -> ContentType.DOCUMENT
            else -> ContentType.DOCUMENT
        }
    }
}
