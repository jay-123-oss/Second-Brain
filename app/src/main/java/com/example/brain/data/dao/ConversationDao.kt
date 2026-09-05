package com.example.brain.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.brain.data.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(conversation: ConversationEntity): Long

    @Query("SELECT * FROM ai_conversations ORDER BY createdAt DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM ai_conversations WHERE question LIKE '%' || :query || '%' OR answer LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchConversations(query: String): Flow<List<ConversationEntity>>

    @Query("DELETE FROM ai_conversations WHERE id = :id")
    fun deleteById(id: String): Int

    @Query("DELETE FROM ai_conversations")
    fun clearAll(): Int

    @Query("SELECT COUNT(*) FROM ai_conversations")
    fun getConversationCount(): Flow<Int>
}
