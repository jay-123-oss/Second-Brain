package com.example.brain.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "item_embeddings",
    foreignKeys = [
        ForeignKey(
            entity = SavedItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["itemId"])]
)
data class ItemEmbeddingEntity(
    @PrimaryKey
    val itemId: String,
    val embeddingJson: String,
    val modelVersion: String = "local-tfidf-v1",
    val updatedAt: Long = System.currentTimeMillis()
)
