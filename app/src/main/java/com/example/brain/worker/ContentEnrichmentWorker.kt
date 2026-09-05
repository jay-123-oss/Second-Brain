package com.example.brain.worker

import android.content.Context
import androidx.work.*
import com.example.brain.data.AppDatabase
import com.example.brain.util.DeterministicContentEnrichmentEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Background WorkManager worker for asynchronous knowledge enrichment.
 * Performs metadata scraping, media metrics extraction, thumbnail generation,
 * and deep text indexing without blocking the user capture experience.
 *
 * Implements "SAVE FIRST, ENRICH SECOND" with idempotency and controlled backoff.
 */
class ContentEnrichmentWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_ITEM_ID = "item_id"
        private const val WORK_TAG_PREFIX = "enrichment_"

        fun schedule(
            context: Context,
            itemId: String,
            requiresNetwork: Boolean = false
        ) {
            val data = Data.Builder()
                .putString(KEY_ITEM_ID, itemId)
                .build()

            val constraintsBuilder = Constraints.Builder()
            if (requiresNetwork) {
                constraintsBuilder.setRequiredNetworkType(NetworkType.CONNECTED)
            }

            val request = OneTimeWorkRequestBuilder<ContentEnrichmentWorker>()
                .setInputData(data)
                .setConstraints(constraintsBuilder.build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .addTag("$WORK_TAG_PREFIX$itemId")
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "$WORK_TAG_PREFIX$itemId",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val itemId = inputData.getString(KEY_ITEM_ID) ?: return@withContext Result.failure()
        val database = AppDatabase.getInstance(context)
        val itemDao = database.savedItemDao()

        val item = itemDao.getItemById(itemId) ?: return@withContext Result.failure()

        // Strict Vault boundary check: never enrich secret items in background
        if (item.isSecret) {
            return@withContext Result.success()
        }

        try {
            // 1. Transition state to PROCESSING
            itemDao.updateProcessingStatus(itemId, "PROCESSING")

            // 2. Perform enrichment
            val result = DeterministicContentEnrichmentEngine.enrich(context, item)

            // 3. Atomically persist enrichment data and update status
            itemDao.updateEnrichmentData(
                id = itemId,
                title = result.title,
                description = result.description,
                thumbnailUrl = result.thumbnailUrl,
                thumbnailPath = result.thumbnailPath,
                extractedText = result.extractedText,
                sourceApp = result.sourceApp,
                mediaDurationMs = result.mediaDurationMs,
                mediaWidth = result.mediaWidth,
                mediaHeight = result.mediaHeight,
                mediaFileSize = result.mediaFileSize,
                status = result.status
            )

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                itemDao.updateProcessingStatus(itemId, "FAILED")
                Result.failure()
            }
        }
    }
}
