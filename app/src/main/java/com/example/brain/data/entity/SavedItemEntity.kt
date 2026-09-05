package com.example.brain.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "saved_items",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["folderId"]),
        Index(value = ["url"]),
        Index(value = ["isFavorite"]),
        Index(value = ["isReadLater"]),
        Index(value = ["isSecret"]),
        Index(value = ["createdAt"])
    ]
)
data class SavedItemEntity(
    @PrimaryKey
    val id: String,
    val title: String = "",
    val type: String = "NOTE",
    val url: String? = null,
    val note: String = "",
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val siteName: String? = null,
    val filePath: String? = null,
    val folderId: String = "inbox_default_id",
    val isSecret: Boolean = false,
    val isFavorite: Boolean = false,
    val isReadLater: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val contentHash: String? = null,
    val originalUrl: String? = null,
    val extractedText: String? = null,
    val processingStatus: String = "NOT_STARTED", // NOT_STARTED, QUEUED, PROCESSING, COMPLETED, PARTIAL, FAILED
    val sourceApp: String? = null,
    val mediaDurationMs: Long? = null,
    val mediaWidth: Int? = null,
    val mediaHeight: Int? = null,
    val mediaFileSize: Long? = null,
    val thumbnailPath: String? = null,
    val summary: String? = null,
    val summaryModel: String? = null,
    val summaryVersion: String? = null,
    val summaryCreatedAt: Long? = null,
    // Prompt 10: Knowledge OS Revisit, Maturity & Engagement tracking
    val reviewAt: Long? = null,
    val reviewStatus: String = "NONE", // NONE, PENDING, REVIEWED, SNOOZED
    val lastReviewedAt: Long? = null,
    val reviewCount: Int = 0,
    val maturity: String = "CAPTURED", // CAPTURED, PROCESSING, REVIEWED, UNDERSTOOD, IMPORTANT, ARCHIVED
    val openCount: Int = 0,
    val lastOpenedAt: Long? = null
) {
    val notes: String
        get() = note

    val imageUrl: String?
        get() = thumbnailPath ?: thumbnailUrl
}
