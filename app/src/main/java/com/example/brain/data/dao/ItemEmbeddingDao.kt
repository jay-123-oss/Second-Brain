package com.example.brain.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.brain.data.entity.ItemEmbeddingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemEmbeddingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(embedding: ItemEmbeddingEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(embeddings: List<ItemEmbeddingEntity>): List<Long>

    @Query("SELECT * FROM item_embeddings WHERE itemId = :itemId LIMIT 1")
    fun getEmbeddingForItem(itemId: String): ItemEmbeddingEntity?

    @Query("SELECT * FROM item_embeddings")
    fun getAllEmbeddings(): List<ItemEmbeddingEntity>

    @Query("SELECT * FROM item_embeddings")
    fun getAllEmbeddingsFlow(): Flow<List<ItemEmbeddingEntity>>

    @Query("DELETE FROM item_embeddings WHERE itemId = :itemId")
    fun deleteByItemId(itemId: String): Int

    @Query("DELETE FROM item_embeddings")
    fun clearAll(): Int

    @Query("SELECT COUNT(*) FROM item_embeddings")
    fun getEmbeddingCount(): Flow<Int>
}
