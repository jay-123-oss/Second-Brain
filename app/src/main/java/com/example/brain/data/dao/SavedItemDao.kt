package com.example.brain.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.brain.data.entity.SavedItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(item: SavedItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(items: List<SavedItemEntity>): List<Long>

    @Update
    fun update(item: SavedItemEntity): Int

    @Delete
    fun delete(item: SavedItemEntity): Int

    @Query("DELETE FROM saved_items WHERE id = :id")
    fun deleteById(id: String): Int

    @Query("SELECT * FROM saved_items WHERE id = :id LIMIT 1")
    fun getItemById(id: String): SavedItemEntity?

    @Query("SELECT * FROM saved_items WHERE url = :url AND isSecret = 0 LIMIT 1")
    fun getItemByUrl(url: String): SavedItemEntity?

    @Query("SELECT * FROM saved_items WHERE contentHash = :hash AND isSecret = 0 LIMIT 1")
    fun getItemByHash(hash: String): SavedItemEntity?

    @Query("SELECT * FROM saved_items WHERE isSecret = 0 ORDER BY createdAt DESC")
    fun getActiveItems(): Flow<List<SavedItemEntity>>

    @Query("SELECT * FROM saved_items WHERE isSecret = 1 ORDER BY createdAt DESC")
    fun getSecretItems(): Flow<List<SavedItemEntity>>

    @Query("SELECT * FROM saved_items WHERE isFavorite = 1 AND isSecret = 0 ORDER BY createdAt DESC")
    fun getFavoriteItems(): Flow<List<SavedItemEntity>>

    @Query("SELECT * FROM saved_items WHERE isReadLater = 1 AND isSecret = 0 ORDER BY createdAt DESC")
    fun getReadLaterItems(): Flow<List<SavedItemEntity>>

    @Query("SELECT * FROM saved_items WHERE folderId = :folderId AND isSecret = 0 ORDER BY createdAt DESC")
    fun getItemsInFolder(folderId: String): Flow<List<SavedItemEntity>>

    @Query("SELECT * FROM saved_items WHERE isSecret = 0 ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentItems(limit: Int): Flow<List<SavedItemEntity>>

    @Query("SELECT * FROM saved_items WHERE isSecret = 0 AND createdAt <= :cutoffTimestamp ORDER BY createdAt ASC LIMIT :limit")
    fun getOldForgottenItems(cutoffTimestamp: Long, limit: Int): Flow<List<SavedItemEntity>>

    @Query("SELECT COUNT(*) FROM saved_items WHERE isSecret = 0")
    fun getItemCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM saved_items WHERE isSecret = 1")
    fun getSecretItemCount(): Flow<Int>

    @Query("UPDATE saved_items SET isFavorite = :isFavorite, updatedAt = :timestamp WHERE id = :id")
    fun updateFavoriteStatus(id: String, isFavorite: Boolean, timestamp: Long = System.currentTimeMillis()): Int

    @Query("UPDATE saved_items SET isReadLater = :isReadLater, updatedAt = :timestamp WHERE id = :id")
    fun updateReadLaterStatus(id: String, isReadLater: Boolean, timestamp: Long = System.currentTimeMillis()): Int

    @Query("UPDATE saved_items SET folderId = :folderId, updatedAt = :timestamp WHERE id = :id")
    fun moveItemToFolder(id: String, folderId: String, timestamp: Long = System.currentTimeMillis()): Int

    @Query("UPDATE saved_items SET isSecret = :isSecret, updatedAt = :timestamp WHERE id = :id")
    fun setSecretStatus(id: String, isSecret: Boolean, timestamp: Long = System.currentTimeMillis()): Int

    @Query("UPDATE saved_items SET note = :notes, updatedAt = :timestamp WHERE id = :id")
    fun updateNotes(id: String, notes: String, timestamp: Long): Int

    @Query("UPDATE saved_items SET processingStatus = :status, updatedAt = :timestamp WHERE id = :id")
    fun updateProcessingStatus(id: String, status: String, timestamp: Long = System.currentTimeMillis()): Int

    @Query("""
        UPDATE saved_items SET 
            title = :title,
            description = :description,
            thumbnailUrl = :thumbnailUrl,
            thumbnailPath = :thumbnailPath,
            extractedText = :extractedText,
            sourceApp = :sourceApp,
            mediaDurationMs = :mediaDurationMs,
            mediaWidth = :mediaWidth,
            mediaHeight = :mediaHeight,
            mediaFileSize = :mediaFileSize,
            processingStatus = :status,
            updatedAt = :timestamp
        WHERE id = :id
    """)
    fun updateEnrichmentData(
        id: String,
        title: String,
        description: String?,
        thumbnailUrl: String?,
        thumbnailPath: String?,
        extractedText: String?,
        sourceApp: String?,
        mediaDurationMs: Long?,
        mediaWidth: Int?,
        mediaHeight: Int?,
        mediaFileSize: Long?,
        status: String,
        timestamp: Long = System.currentTimeMillis()
    ): Int

    @Query("SELECT * FROM saved_items WHERE isSecret = 0 AND processingStatus IN ('NOT_STARTED', 'QUEUED', 'PROCESSING') ORDER BY createdAt DESC")
    fun getUnprocessedItems(): List<SavedItemEntity>

    @Query("""
        SELECT * FROM saved_items 
        WHERE isSecret = 0 
        AND (title LIKE '%' || :query || '%' 
             OR note LIKE '%' || :query || '%' 
             OR description LIKE '%' || :query || '%' 
             OR url LIKE '%' || :query || '%'
             OR originalUrl LIKE '%' || :query || '%'
             OR sourceApp LIKE '%' || :query || '%'
             OR extractedText LIKE '%' || :query || '%')
        ORDER BY createdAt DESC
    """)
    fun searchItems(query: String): List<SavedItemEntity>

    @Query("""
        SELECT * FROM saved_items 
        WHERE isSecret = 1 
        AND (title LIKE '%' || :query || '%' 
             OR note LIKE '%' || :query || '%' 
             OR description LIKE '%' || :query || '%' 
             OR url LIKE '%' || :query || '%'
             OR extractedText LIKE '%' || :query || '%')
        ORDER BY createdAt DESC
    """)
    fun searchSecretItems(query: String): List<SavedItemEntity>

    @Query("""
        UPDATE saved_items SET 
            summary = :summary,
            summaryModel = :model,
            summaryVersion = :version,
            summaryCreatedAt = :timestamp,
            updatedAt = :timestamp
        WHERE id = :id
    """)
    fun updateSummary(
        id: String,
        summary: String?,
        model: String?,
        version: String?,
        timestamp: Long = System.currentTimeMillis()
    ): Int

    @Query("UPDATE saved_items SET summary = NULL, summaryModel = NULL, summaryVersion = NULL, summaryCreatedAt = NULL")
    fun clearAllSummaries(): Int

    // Prompt 10: Knowledge OS Queries & Operations
    @Query("SELECT * FROM saved_items WHERE isSecret = 0 AND reviewAt IS NOT NULL AND reviewAt <= :now AND reviewStatus != 'REVIEWED' ORDER BY reviewAt ASC")
    fun getReviewDueItems(now: Long): Flow<List<SavedItemEntity>>

    @Query("SELECT * FROM saved_items WHERE isSecret = 0 AND createdAt <= :threshold AND (lastOpenedAt IS NULL OR lastOpenedAt <= :threshold) ORDER BY (isFavorite * 2) DESC, createdAt ASC LIMIT :limit")
    fun getForgottenItems(threshold: Long, limit: Int): Flow<List<SavedItemEntity>>

    @Query("SELECT * FROM saved_items WHERE isSecret = 0 AND lastOpenedAt IS NOT NULL ORDER BY lastOpenedAt DESC LIMIT :limit")
    fun getRecentlyOpenedItems(limit: Int): Flow<List<SavedItemEntity>>

    @Query("SELECT * FROM saved_items WHERE isSecret = 0 AND (folderId = 'inbox_default_id' OR folderId IS NULL) ORDER BY createdAt DESC")
    fun getInboxItems(): Flow<List<SavedItemEntity>>

    @Query("""
        UPDATE saved_items SET
            reviewAt = :reviewAt,
            reviewStatus = :reviewStatus,
            lastReviewedAt = :lastReviewedAt,
            reviewCount = :reviewCount,
            updatedAt = :timestamp
        WHERE id = :id
    """)
    fun updateReviewState(
        id: String,
        reviewAt: Long?,
        reviewStatus: String,
        lastReviewedAt: Long?,
        reviewCount: Int,
        timestamp: Long
    ): Int

    @Query("UPDATE saved_items SET maturity = :maturity, updatedAt = :timestamp WHERE id = :id")
    fun updateMaturity(
        id: String,
        maturity: String,
        timestamp: Long
    ): Int

    @Query("UPDATE saved_items SET openCount = openCount + 1, lastOpenedAt = :timestamp WHERE id = :id")
    fun recordItemOpened(
        id: String,
        timestamp: Long
    ): Int

    @Query("UPDATE saved_items SET folderId = :folderId, updatedAt = :timestamp WHERE id IN (:itemIds)")
    fun bulkUpdateFolder(
        itemIds: List<String>,
        folderId: String,
        timestamp: Long
    ): Int

    @Query("UPDATE saved_items SET maturity = :maturity, updatedAt = :timestamp WHERE id IN (:itemIds)")
    fun bulkUpdateMaturity(
        itemIds: List<String>,
        maturity: String,
        timestamp: Long
    ): Int

    @Query("DELETE FROM saved_items WHERE id IN (:itemIds) AND isSecret = 0")
    fun bulkDelete(itemIds: List<String>): Int
}


