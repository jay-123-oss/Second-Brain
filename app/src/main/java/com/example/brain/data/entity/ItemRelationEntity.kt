package com.example.brain.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "item_relations",
    primaryKeys = ["sourceItemId", "targetItemId", "relationType"],
    foreignKeys = [
        ForeignKey(
            entity = SavedItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceItemId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SavedItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["targetItemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["targetItemId"]),
        Index(value = ["sourceItemId"])
    ]
)
data class ItemRelationEntity(
    val sourceItemId: String,
    val targetItemId: String,
    val relationType: String, // RELATED_TO, REFERENCES, INSPIRED_BY, PART_OF, DERIVED_FROM, DUPLICATE_OF, SUPPORTS, CONTRADICTS
    val origin: String = "USER_CREATED", // USER_CREATED, SYSTEM_SUGGESTED
    val explanation: String? = null, // e.g. "Same project • Shared tags: #ai, #research"
    val confidence: Float = 1.0f,
    val status: String = "ACTIVE", // ACTIVE, DISMISSED
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

