package com.example.brain.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.brain.data.entity.SavedSearchEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedSearchDao {
    @Query("SELECT * FROM saved_searches ORDER BY createdAt DESC")
    fun getAllSavedSearches(): Flow<List<SavedSearchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSavedSearch(search: SavedSearchEntity): Long

    @Query("DELETE FROM saved_searches WHERE id = :id")
    fun deleteSavedSearch(id: String): Int
}


