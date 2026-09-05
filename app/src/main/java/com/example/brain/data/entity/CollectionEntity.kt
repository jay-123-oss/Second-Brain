package com.example.brain.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String = "",
    val icon: String = "📚",
    val colorHex: String = "#6366F1",
    val isSmart: Boolean = false,
    val ruleJson: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val title: String
        get() = name
}
