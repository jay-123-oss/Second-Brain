package com.example.brain.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)
