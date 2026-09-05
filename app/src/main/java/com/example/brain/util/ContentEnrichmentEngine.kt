package com.example.brain.util

import android.content.Context
import com.example.brain.data.entity.SavedItemEntity
import com.example.brain.data.model.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class EnrichmentResult(
    val title: String,
    val description: String?,
    val thumbnailUrl: String?,
    val thumbnailPath: String?,
    val extractedText: String?,
    val sourceApp: String?,
    val mediaDurationMs: Long?,
    val mediaWidth: Int?,
    val mediaHeight: Int?,
    val mediaFileSize: Long?,
    val status: String // "COMPLETED", "PARTIAL", "FAILED"
)

/**
 * Extension point contract for content enrichment.
 * Currently backed by deterministic on-device scrapers and media analyzers.
 * Ready for future local AI/ML pipelines (embeddings, summarization, entity extraction)
 * without mockups or fake intelligence today.
 */
interface ContentEnrichmentEngine {
    suspend fun enrich(context: Context, item: SavedItemEntity): EnrichmentResult
}

object DeterministicContentEnrichmentEngine : ContentEnrichmentEngine {

    override suspend fun enrich(context: Context, item: SavedItemEntity): EnrichmentResult = withContext(Dispatchers.IO) {
        // Strict Vault boundary check: never enrich secret items in background
        if (item.isSecret) {
            return@withContext EnrichmentResult(
                title = item.title,
                description = item.description,
                thumbnailUrl = null,
                thumbnailPath = null,
                extractedText = null,
                sourceApp = item.sourceApp,
                mediaDurationMs = item.mediaDurationMs,
                mediaWidth = item.mediaWidth,
                mediaHeight = item.mediaHeight,
                mediaFileSize = item.mediaFileSize,
                status = "COMPLETED"
            )
        }

        val contentType = try { ContentType.valueOf(item.type) } catch (_: Exception) { ContentType.NOTE }

        when {
            // 1. URL Enrichment (Web OpenGraph, Meta, and deep body text)
            contentType == ContentType.LINK && !item.url.isNullOrBlank() -> {
                val fetchResult = MetadataFetcher.fetch(item.url)
                val detectedSource = item.sourceApp ?: UrlNormalizer.detectSourceApp(item.url)

                if (fetchResult.isSuccess) {
                    val meta = fetchResult.getOrThrow()
                    val title = if (item.title.isBlank() || item.title == "Shared Link" || item.title == item.url) {
                        meta.title
                    } else {
                        item.title
                    }

                    val isPartial = meta.imageUrl.isNullOrBlank() || meta.description.isNullOrBlank()
                    EnrichmentResult(
                        title = title,
                        description = meta.description ?: item.description,
                        thumbnailUrl = meta.imageUrl ?: item.thumbnailUrl,
                        thumbnailPath = null,
                        extractedText = meta.extractedText ?: item.extractedText,
                        sourceApp = detectedSource,
                        mediaDurationMs = null,
                        mediaWidth = null,
                        mediaHeight = null,
                        mediaFileSize = null,
                        status = if (isPartial) "PARTIAL" else "COMPLETED"
                    )
                } else {
                    // Fallback to human readable title and partial status - never fail the entire item
                    val fallbackTitle = if (item.title.isBlank() || item.title == "Shared Link") {
                        detectedSource?.let { "$it Link" } ?: UrlNormalizer.extractDomain(item.url) ?: "Saved Link"
                    } else {
                        item.title
                    }

                    EnrichmentResult(
                        title = fallbackTitle,
                        description = item.description ?: "Metadata unavailable",
                        thumbnailUrl = item.thumbnailUrl,
                        thumbnailPath = null,
                        extractedText = item.extractedText,
                        sourceApp = detectedSource,
                        mediaDurationMs = null,
                        mediaWidth = null,
                        mediaHeight = null,
                        mediaFileSize = null,
                        status = "PARTIAL"
                    )
                }
            }

            // 2. Local Media Enrichment (Video, Audio, Image, PDF, Document)
            !item.filePath.isNullOrBlank() -> {
                val mediaFile = File(item.filePath)
                val hash = item.contentHash ?: item.id
                val mediaInfo = MediaProcessor.processMedia(context, mediaFile, contentType, hash)

                val derivedTitle = if (item.title.isBlank() || item.title.startsWith("Shared")) {
                    mediaInfo.title ?: mediaFile.nameWithoutExtension.replace('_', ' ').replace('-', ' ')
                } else {
                    item.title
                }

                EnrichmentResult(
                    title = derivedTitle,
                    description = item.description ?: if (mediaInfo.artist != null) "By ${mediaInfo.artist}" else null,
                    thumbnailUrl = null,
                    thumbnailPath = mediaInfo.thumbnailPath ?: item.thumbnailPath,
                    extractedText = mediaInfo.extractedText ?: item.extractedText,
                    sourceApp = item.sourceApp,
                    mediaDurationMs = mediaInfo.durationMs ?: item.mediaDurationMs,
                    mediaWidth = mediaInfo.width ?: item.mediaWidth,
                    mediaHeight = mediaInfo.height ?: item.mediaHeight,
                    mediaFileSize = if (mediaInfo.fileSize > 0) mediaInfo.fileSize else item.mediaFileSize,
                    status = "COMPLETED"
                )
            }

            // 3. Plain Note Enrichment
            else -> {
                EnrichmentResult(
                    title = item.title.ifBlank { item.note.take(40) + if (item.note.length > 40) "..." else "" },
                    description = item.description,
                    thumbnailUrl = null,
                    thumbnailPath = null,
                    extractedText = item.note.ifBlank { null },
                    sourceApp = item.sourceApp,
                    mediaDurationMs = null,
                    mediaWidth = null,
                    mediaHeight = null,
                    mediaFileSize = null,
                    status = "COMPLETED"
                )
            }
        }
    }
}
