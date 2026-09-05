package com.example.brain.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

@Fts4
@Entity(tableName = "saved_items_fts")
data class SavedItemFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowid: Int,
    val title: String?,
    val description: String?,
    val note: String?,
    val extractedText: String?
)
