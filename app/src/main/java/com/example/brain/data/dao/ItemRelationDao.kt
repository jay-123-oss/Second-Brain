package com.example.brain.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.brain.data.entity.ItemRelationEntity
import com.example.brain.data.entity.SavedItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemRelationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertRelation(relation: ItemRelationEntity): Long

    @Delete
    fun deleteRelation(relation: ItemRelationEntity): Int

    @Query("DELETE FROM item_relations WHERE sourceItemId = :sourceId AND targetItemId = :targetId AND relationType = :relationType")
    fun deleteRelation(sourceId: String, targetId: String, relationType: String): Int

    @Query("DELETE FROM item_relations WHERE (sourceItemId = :itemId OR targetItemId = :itemId)")
    fun deleteAllRelationsForItem(itemId: String): Int

    @Query("""
        SELECT * FROM item_relations 
        WHERE (sourceItemId = :itemId OR targetItemId = :itemId) AND status = 'ACTIVE'
        ORDER BY createdAt DESC
    """)
    fun getRelationsForItem(itemId: String): Flow<List<ItemRelationEntity>>

    @Query("""
        SELECT * FROM item_relations 
        WHERE sourceItemId = :itemId AND status = 'ACTIVE'
        ORDER BY createdAt DESC
    """)
    fun getOutgoingRelations(itemId: String): Flow<List<ItemRelationEntity>>

    @Query("""
        SELECT * FROM item_relations 
        WHERE targetItemId = :itemId AND status = 'ACTIVE'
        ORDER BY createdAt DESC
    """)
    fun getIncomingRelations(itemId: String): Flow<List<ItemRelationEntity>>

    @Query("""
        SELECT targetItemId FROM item_relations 
        WHERE sourceItemId = :itemId AND status = 'DISMISSED'
        UNION
        SELECT sourceItemId FROM item_relations
        WHERE targetItemId = :itemId AND status = 'DISMISSED'
    """)
    fun getDismissedRelationItemIds(itemId: String): List<String>

    @Query("""
        UPDATE item_relations 
        SET status = :status, updatedAt = :timestamp 
        WHERE (sourceItemId = :sourceId AND targetItemId = :targetId AND relationType = :relationType)
           OR (sourceItemId = :targetId AND targetItemId = :sourceId AND relationType = :relationType)
    """)
    fun updateRelationStatus(sourceId: String, targetId: String, relationType: String, status: String, timestamp: Long = System.currentTimeMillis()): Int

    @Query("""
        SELECT * FROM item_relations
        WHERE status = 'ACTIVE'
        ORDER BY createdAt DESC
        LIMIT :limit
    """)
    fun getRecentlyConnectedRelations(limit: Int = 10): Flow<List<ItemRelationEntity>>

    @Query("""
        SELECT * FROM item_relations
        WHERE status = 'ACTIVE'
    """)
    fun getAllActiveRelationsSync(): List<ItemRelationEntity>

    @Query("""
        SELECT * FROM item_relations
        WHERE status = 'ACTIVE'
        ORDER BY createdAt DESC
    """)
    fun getAllActiveRelations(): Flow<List<ItemRelationEntity>>


    @Query("""
        SELECT saved_items.* FROM saved_items
        INNER JOIN item_relations ON saved_items.id = item_relations.targetItemId
        WHERE item_relations.sourceItemId = :itemId AND item_relations.status = 'ACTIVE' AND saved_items.isSecret = 0
        UNION
        SELECT saved_items.* FROM saved_items
        INNER JOIN item_relations ON saved_items.id = item_relations.sourceItemId
        WHERE item_relations.targetItemId = :itemId AND item_relations.status = 'ACTIVE' AND saved_items.isSecret = 0
    """)
    fun getRelatedItems(itemId: String): Flow<List<SavedItemEntity>>
}
