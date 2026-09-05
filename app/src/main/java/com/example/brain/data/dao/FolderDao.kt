package com.example.brain.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.brain.data.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(folder: FolderEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(folders: List<FolderEntity>): List<Long>

    @Update
    fun update(folder: FolderEntity): Int

    @Delete
    fun delete(folder: FolderEntity): Int

    @Query("SELECT * FROM folders WHERE isSecret = 0 ORDER BY name ASC")
    fun getAllFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE isSecret = 0 ORDER BY name ASC")
    fun getAllFoldersSync(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE id = :id LIMIT 1")
    fun getFolderById(id: String): FolderEntity?

    @Query("SELECT COUNT(*) FROM saved_items WHERE folderId = :folderId AND isSecret = 0")
    fun getItemCountForFolder(folderId: String): Flow<Int>
}
