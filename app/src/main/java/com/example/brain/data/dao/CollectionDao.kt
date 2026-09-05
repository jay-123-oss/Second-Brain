package com.example.brain.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.brain.data.entity.CollectionEntity
import com.example.brain.data.entity.CollectionItemCrossRef
import com.example.brain.data.entity.SavedItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(collection: CollectionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCrossRef(crossRef: CollectionItemCrossRef): Long

    @Update
    fun update(collection: CollectionEntity): Int

    @Delete
    fun delete(collection: CollectionEntity): Int

    @Delete
    fun deleteCrossRef(crossRef: CollectionItemCrossRef): Int

    @Query("DELETE FROM collection_item_cross_ref WHERE collectionId = :collectionId AND itemId = :itemId")
    fun removeItemFromCollection(collectionId: String, itemId: String): Int

    @Query("SELECT * FROM collections ORDER BY createdAt DESC")
    fun getAllCollections(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections ORDER BY createdAt DESC")
    fun getAllCollectionsSync(): List<CollectionEntity>

    @Query("SELECT * FROM collections WHERE isSmart = 1 ORDER BY createdAt DESC")
    fun getSmartCollections(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections WHERE isSmart = 0 ORDER BY createdAt DESC")
    fun getManualCollections(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections WHERE id = :id LIMIT 1")
    fun getCollectionById(id: String): CollectionEntity?

    @Query("""
        SELECT saved_items.* FROM saved_items
        INNER JOIN collection_item_cross_ref ON saved_items.id = collection_item_cross_ref.itemId
        WHERE collection_item_cross_ref.collectionId = :collectionId AND saved_items.isSecret = 0
        ORDER BY saved_items.createdAt DESC
    """)
    fun getItemsInCollection(collectionId: String): Flow<List<SavedItemEntity>>

    @Query("""
        SELECT COUNT(*) FROM collection_item_cross_ref
        WHERE collectionId = :collectionId
    """)
    fun getItemCountForCollection(collectionId: String): Flow<Int>
}
