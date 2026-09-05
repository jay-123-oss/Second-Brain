package com.example.brain.data.repository

import com.example.brain.data.AppDatabase
import com.example.brain.data.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Single source of truth for repository data operations across Second Brain.
 * Encapsulates Room database queries with clean Coroutines suspend / Flow APIs.
 */
class AppRepository(private val database: AppDatabase) {

    private val savedItemDao = database.savedItemDao()
    private val folderDao = database.folderDao()
    private val tagDao = database.tagDao()
    private val collectionDao = database.collectionDao()
    private val relationDao = database.itemRelationDao()
    private val conversationDao = database.conversationDao()
    private val itemEmbeddingDao = database.itemEmbeddingDao()
    private val savedSearchDao = database.savedSearchDao()

    // -------------------------------------------------------------
    // Saved Items (Knowledge)
    // -------------------------------------------------------------

    fun getActiveItems(): Flow<List<SavedItemEntity>> = savedItemDao.getActiveItems()

    fun getFavoriteItems(): Flow<List<SavedItemEntity>> = savedItemDao.getFavoriteItems()

    fun getReadLaterItems(): Flow<List<SavedItemEntity>> = savedItemDao.getReadLaterItems()

    fun getItemsInFolder(folderId: String): Flow<List<SavedItemEntity>> = savedItemDao.getItemsInFolder(folderId)

    fun getSecretItems(): Flow<List<SavedItemEntity>> = savedItemDao.getSecretItems()

    fun getItemCount(): Flow<Int> = savedItemDao.getItemCount()

    fun getSecretItemCount(): Flow<Int> = savedItemDao.getSecretItemCount()

    fun getOldForgottenItems(cutoffTimestamp: Long, limit: Int = 5): Flow<List<SavedItemEntity>> =
        savedItemDao.getOldForgottenItems(cutoffTimestamp, limit)

    fun getRecentItems(limit: Int = 10): Flow<List<SavedItemEntity>> =
        savedItemDao.getRecentItems(limit)

    suspend fun getItemById(id: String): SavedItemEntity? = withContext(Dispatchers.IO) {
        savedItemDao.getItemById(id)
    }

    suspend fun getItemByUrl(url: String): SavedItemEntity? = withContext(Dispatchers.IO) {
        savedItemDao.getItemByUrl(url)
    }

    suspend fun getItemByHash(hash: String): SavedItemEntity? = withContext(Dispatchers.IO) {
        savedItemDao.getItemByHash(hash)
    }

    suspend fun insertItem(item: SavedItemEntity) = withContext(Dispatchers.IO) {
        savedItemDao.insert(item)
    }

    suspend fun updateItem(item: SavedItemEntity) = withContext(Dispatchers.IO) {
        savedItemDao.update(item)
    }

    suspend fun deleteItem(item: SavedItemEntity) = withContext(Dispatchers.IO) {
        savedItemDao.delete(item)
    }

    suspend fun toggleFavorite(id: String, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        savedItemDao.updateFavoriteStatus(id, isFavorite)
    }

    suspend fun toggleReadLater(id: String, isReadLater: Boolean) = withContext(Dispatchers.IO) {
        savedItemDao.updateReadLaterStatus(id, isReadLater)
    }

    suspend fun moveItemToFolder(id: String, folderId: String) = withContext(Dispatchers.IO) {
        savedItemDao.moveItemToFolder(id, folderId)
    }

    suspend fun setSecretStatus(id: String, isSecret: Boolean) = withContext(Dispatchers.IO) {
        savedItemDao.setSecretStatus(id, isSecret)
    }

    suspend fun updateNotes(id: String, notes: String) = withContext(Dispatchers.IO) {
        savedItemDao.updateNotes(id, notes, System.currentTimeMillis())
    }

    suspend fun updateProcessingStatus(id: String, status: String) = withContext(Dispatchers.IO) {
        savedItemDao.updateProcessingStatus(id, status)
    }

    suspend fun updateEnrichmentData(
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
        status: String
    ) = withContext(Dispatchers.IO) {
        savedItemDao.updateEnrichmentData(
            id = id,
            title = title,
            description = description,
            thumbnailUrl = thumbnailUrl,
            thumbnailPath = thumbnailPath,
            extractedText = extractedText,
            sourceApp = sourceApp,
            mediaDurationMs = mediaDurationMs,
            mediaWidth = mediaWidth,
            mediaHeight = mediaHeight,
            mediaFileSize = mediaFileSize,
            status = status
        )
    }

    suspend fun getUnprocessedItems(): List<SavedItemEntity> = withContext(Dispatchers.IO) {
        savedItemDao.getUnprocessedItems()
    }

    // -------------------------------------------------------------
    // Full-Text Search (FTS)
    // -------------------------------------------------------------

    suspend fun searchItems(query: String): List<SavedItemEntity> = withContext(Dispatchers.IO) {
        val sanitizedQuery = query.trim()
        if (sanitizedQuery.isEmpty()) {
            emptyList()
        } else {
            savedItemDao.searchItems(sanitizedQuery)
        }
    }

    suspend fun searchSecretItems(query: String): List<SavedItemEntity> = withContext(Dispatchers.IO) {
        val sanitizedQuery = query.trim()
        if (sanitizedQuery.isEmpty()) {
            emptyList()
        } else {
            savedItemDao.searchSecretItems(sanitizedQuery)
        }
    }

    // -------------------------------------------------------------
    // Folders (Projects / Categories)
    // -------------------------------------------------------------

    fun getAllFolders(): Flow<List<FolderEntity>> = folderDao.getAllFolders()

    suspend fun getFolderById(id: String): FolderEntity? = withContext(Dispatchers.IO) {
        folderDao.getFolderById(id)
    }

    suspend fun insertFolder(folder: FolderEntity) = withContext(Dispatchers.IO) {
        folderDao.insert(folder)
    }

    suspend fun updateFolder(folder: FolderEntity) = withContext(Dispatchers.IO) {
        folderDao.update(folder)
    }

    suspend fun deleteFolder(folder: FolderEntity) = withContext(Dispatchers.IO) {
        folderDao.delete(folder)
    }

    // -------------------------------------------------------------
    // Tags
    // -------------------------------------------------------------

    fun getAllTags(): Flow<List<TagEntity>> = tagDao.getAllTags()

    fun getTagsWithCount(): Flow<List<com.example.brain.data.dao.TagWithCount>> = tagDao.getTagsWithCount()

    fun getTagsForItem(itemId: String): Flow<List<TagEntity>> = tagDao.getTagsForItem(itemId)

    suspend fun bulkDelete(items: List<SavedItemEntity>) = withContext(Dispatchers.IO) {
        items.forEach { savedItemDao.delete(it) }
    }

    suspend fun bulkMove(itemIds: List<String>, folderId: String) = withContext(Dispatchers.IO) {
        itemIds.forEach { savedItemDao.moveItemToFolder(it, folderId) }
    }

    suspend fun bulkFavorite(itemIds: List<String>, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        itemIds.forEach { savedItemDao.updateFavoriteStatus(it, isFavorite) }
    }

    suspend fun bulkReadLater(itemIds: List<String>, isReadLater: Boolean) = withContext(Dispatchers.IO) {
        itemIds.forEach { savedItemDao.updateReadLaterStatus(it, isReadLater) }
    }

    suspend fun getTagsForItemSync(itemId: String): List<TagEntity> = withContext(Dispatchers.IO) {
        tagDao.getTagsForItemSync(itemId)
    }

    suspend fun addTagToItem(itemId: String, tagName: String) = withContext(Dispatchers.IO) {
        val normalizedName = tagName.trim().lowercase().removePrefix("#")
        if (normalizedName.isBlank()) return@withContext

        var tag = tagDao.getTagByName(normalizedName)
        if (tag == null) {
            val newTag = TagEntity(name = normalizedName)
            tagDao.insertTag(newTag)
            tag = tagDao.getTagByName(normalizedName)
        }
        if (tag != null) {
            tagDao.insertItemTagCrossRef(ItemTagCrossRef(itemId, tag.name))
        }
    }

    suspend fun removeTagFromItem(itemId: String, tagName: String) = withContext(Dispatchers.IO) {
        tagDao.removeItemTag(itemId, tagName)
    }

    fun getItemsForTag(tagName: String): Flow<List<SavedItemEntity>> = tagDao.getItemsForTag(tagName)

    // -------------------------------------------------------------
    // Collections
    // -------------------------------------------------------------

    fun getAllCollections(): Flow<List<CollectionEntity>> = collectionDao.getAllCollections()

    fun getSmartCollections(): Flow<List<CollectionEntity>> = collectionDao.getSmartCollections()

    fun getManualCollections(): Flow<List<CollectionEntity>> = collectionDao.getManualCollections()

    suspend fun getCollectionById(id: String): CollectionEntity? = withContext(Dispatchers.IO) {
        collectionDao.getCollectionById(id)
    }

    suspend fun insertCollection(collection: CollectionEntity) = withContext(Dispatchers.IO) {
        collectionDao.insert(collection)
    }

    suspend fun updateCollection(collection: CollectionEntity) = withContext(Dispatchers.IO) {
        collectionDao.update(collection)
    }

    suspend fun deleteCollection(collection: CollectionEntity) = withContext(Dispatchers.IO) {
        collectionDao.delete(collection)
    }

    fun getItemsInCollection(collectionId: String): Flow<List<SavedItemEntity>> =
        collectionDao.getItemsInCollection(collectionId)

    suspend fun addItemToCollection(collectionId: String, itemId: String) = withContext(Dispatchers.IO) {
        collectionDao.insertCrossRef(CollectionItemCrossRef(collectionId, itemId))
    }

    suspend fun removeItemFromCollection(collectionId: String, itemId: String) = withContext(Dispatchers.IO) {
        collectionDao.deleteCrossRef(CollectionItemCrossRef(collectionId, itemId))
    }

    // -------------------------------------------------------------
    // Item Relations (Knowledge Graph)
    // -------------------------------------------------------------

    fun getRelationsForItem(itemId: String): Flow<List<ItemRelationEntity>> =
        relationDao.getRelationsForItem(itemId)

    fun getAllActiveRelations(): Flow<List<ItemRelationEntity>> =
        relationDao.getAllActiveRelations()

    fun getAllActiveRelationsSync(): List<ItemRelationEntity> =
        relationDao.getAllActiveRelationsSync()

    fun getRecentlyConnectedRelations(limit: Int = 10): Flow<List<ItemRelationEntity>> =
        relationDao.getRecentlyConnectedRelations(limit)

    suspend fun getDismissedRelationItemIds(itemId: String): List<String> = withContext(Dispatchers.IO) {
        relationDao.getDismissedRelationItemIds(itemId)
    }


    suspend fun addRelation(
        sourceId: String,
        targetId: String,
        relationType: String,
        origin: String = "USER_CREATED",
        explanation: String? = null,
        confidence: Float = 1.0f
    ) = withContext(Dispatchers.IO) {
        if (sourceId != targetId) {
            val relation = ItemRelationEntity(
                sourceItemId = sourceId,
                targetItemId = targetId,
                relationType = relationType,
                origin = origin,
                explanation = explanation,
                confidence = confidence,
                status = "ACTIVE",
                updatedAt = System.currentTimeMillis()
            )
            relationDao.insertRelation(relation)
        }
    }

    suspend fun dismissSuggestion(sourceId: String, targetId: String, relationType: String) = withContext(Dispatchers.IO) {
        val updatedCount = relationDao.updateRelationStatus(sourceId, targetId, relationType, "DISMISSED")
        if (updatedCount == 0) {
            // Record a dismissed relationship so it won't be suggested again
            val dismissed = ItemRelationEntity(
                sourceItemId = sourceId,
                targetItemId = targetId,
                relationType = relationType,
                origin = "SYSTEM_SUGGESTED",
                explanation = "Dismissed by user",
                confidence = 0f,
                status = "DISMISSED",
                updatedAt = System.currentTimeMillis()
            )
            relationDao.insertRelation(dismissed)
        }
    }

    suspend fun deleteRelation(sourceId: String, targetId: String, relationType: String) = withContext(Dispatchers.IO) {
        relationDao.deleteRelation(sourceId, targetId, relationType)
    }

    // -------------------------------------------------------------
    // Local AI: Summaries, Embeddings & Conversations
    // -------------------------------------------------------------

    suspend fun updateItemSummary(
        itemId: String,
        summary: String?,
        model: String? = "local-extractive-v1",
        version: String? = "1.0"
    ) = withContext(Dispatchers.IO) {
        savedItemDao.updateSummary(itemId, summary, model, version)
    }

    suspend fun saveEmbedding(embedding: ItemEmbeddingEntity) = withContext(Dispatchers.IO) {
        itemEmbeddingDao.insert(embedding)
    }

    suspend fun saveEmbeddings(embeddings: List<ItemEmbeddingEntity>) = withContext(Dispatchers.IO) {
        itemEmbeddingDao.insertAll(embeddings)
    }

    suspend fun getEmbeddingForItem(itemId: String): ItemEmbeddingEntity? = withContext(Dispatchers.IO) {
        itemEmbeddingDao.getEmbeddingForItem(itemId)
    }

    suspend fun getAllEmbeddings(): List<ItemEmbeddingEntity> = withContext(Dispatchers.IO) {
        itemEmbeddingDao.getAllEmbeddings()
    }

    fun getAllEmbeddingsFlow(): Flow<List<ItemEmbeddingEntity>> = itemEmbeddingDao.getAllEmbeddingsFlow()

    fun getEmbeddingCount(): Flow<Int> = itemEmbeddingDao.getEmbeddingCount()

    suspend fun saveConversation(conversation: ConversationEntity) = withContext(Dispatchers.IO) {
        conversationDao.insert(conversation)
    }

    fun getAllConversations(): Flow<List<ConversationEntity>> = conversationDao.getAllConversations()

    fun searchConversations(query: String): Flow<List<ConversationEntity>> = conversationDao.searchConversations(query)

    fun getConversationCount(): Flow<Int> = conversationDao.getConversationCount()

    suspend fun deleteConversation(id: String) = withContext(Dispatchers.IO) {
        conversationDao.deleteById(id)
    }

    suspend fun clearConversations() = withContext(Dispatchers.IO) {
        conversationDao.clearAll()
    }

    /**
     * Resets derived local AI artifacts (summaries, embeddings, and conversation history)
     * WITHOUT deleting user knowledge, notes, tags, projects, or Vault data.
     */
    suspend fun resetLocalAIData() = withContext(Dispatchers.IO) {
        savedItemDao.clearAllSummaries()
        itemEmbeddingDao.clearAll()
        conversationDao.clearAll()
    }

    // -------------------------------------------------------------
    // Prompt 10: Knowledge OS Operations
    // -------------------------------------------------------------

    fun getReviewDueItems(now: Long = System.currentTimeMillis()): Flow<List<SavedItemEntity>> =
        savedItemDao.getReviewDueItems(now)

    fun getForgottenItems(threshold: Long = System.currentTimeMillis() - (30L * 24 * 3600 * 1000), limit: Int = 10): Flow<List<SavedItemEntity>> =
        savedItemDao.getForgottenItems(threshold, limit)

    fun getRecentlyOpenedItems(limit: Int = 10): Flow<List<SavedItemEntity>> =
        savedItemDao.getRecentlyOpenedItems(limit)

    fun getInboxItems(): Flow<List<SavedItemEntity>> = savedItemDao.getInboxItems()

    suspend fun updateReviewState(
        id: String,
        reviewAt: Long?,
        reviewStatus: String,
        lastReviewedAt: Long?,
        reviewCount: Int,
        timestamp: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        savedItemDao.updateReviewState(id, reviewAt, reviewStatus, lastReviewedAt, reviewCount, timestamp)
    }

    suspend fun updateMaturity(id: String, maturity: String, timestamp: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        savedItemDao.updateMaturity(id, maturity, timestamp)
    }

    suspend fun recordItemOpened(id: String, timestamp: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        savedItemDao.recordItemOpened(id, timestamp)
    }

    suspend fun bulkUpdateFolder(itemIds: List<String>, folderId: String, timestamp: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        savedItemDao.bulkUpdateFolder(itemIds, folderId, timestamp)
    }

    suspend fun bulkUpdateMaturity(itemIds: List<String>, maturity: String, timestamp: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        savedItemDao.bulkUpdateMaturity(itemIds, maturity, timestamp)
    }

    suspend fun bulkDeleteByIds(itemIds: List<String>) = withContext(Dispatchers.IO) {
        savedItemDao.bulkDelete(itemIds)
    }



    fun getAllSavedSearches(): Flow<List<com.example.brain.data.entity.SavedSearchEntity>> =
        savedSearchDao.getAllSavedSearches()

    suspend fun insertSavedSearch(search: com.example.brain.data.entity.SavedSearchEntity) = withContext(Dispatchers.IO) {
        savedSearchDao.insertSavedSearch(search)
    }

    suspend fun deleteSavedSearch(id: String) = withContext(Dispatchers.IO) {
        savedSearchDao.deleteSavedSearch(id)
    }
}
