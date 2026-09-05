package com.example.brain.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_searches")
data class SavedSearchEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val query: String,
    val filterType: String? = null,
    val filterFolderId: String? = null,
    val filterTag: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
