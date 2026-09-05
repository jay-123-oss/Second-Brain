package com.example.brain.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.brain.data.AppDatabase
import com.example.brain.util.MetadataFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Background WorkManager worker for deferred URL metadata extraction.
 * Guarantees that URL saves happen immediately without blocking on network,
 * while automatically enriching titles, descriptions, and thumbnails when connectivity is restored.
 */
class PendingMetadataWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_ITEM_ID = "item_id"
        const val KEY_URL = "url"

        fun schedule(context: Context, itemId: String, url: String) {
            ContentEnrichmentWorker.schedule(context, itemId, requiresNetwork = true)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val itemId = inputData.getString(KEY_ITEM_ID) ?: return@withContext Result.failure()
        val url = inputData.getString(KEY_URL) ?: return@withContext Result.failure()

        try {
            val database = AppDatabase.getInstance(context)
            val itemDao = database.savedItemDao()
            val existingItem = itemDao.getItemById(itemId) ?: return@withContext Result.failure()

            val metaResult = MetadataFetcher.fetch(url)
            metaResult.onSuccess { meta ->
                val updatedTitle = if (existingItem.title.isBlank() ||
                    existingItem.title == "Shared Link" ||
                    existingItem.title == url
                ) {
                    meta.title
                } else {
                    existingItem.title
                }

                val updatedItem = existingItem.copy(
                    title = updatedTitle,
                    description = meta.description ?: existingItem.description,
                    thumbnailUrl = meta.imageUrl ?: existingItem.thumbnailUrl,
                    siteName = meta.siteName ?: existingItem.siteName,
                    updatedAt = System.currentTimeMillis()
                )
                itemDao.update(updatedItem)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
