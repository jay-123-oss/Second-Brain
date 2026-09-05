package com.example.brain.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "folders",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["parentId"])]
)
data class FolderEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val icon: String = "📁",
    val colorHex: String = "#4F46E5",
    val isSecret: Boolean = false,
    val parentId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
