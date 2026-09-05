package com.example.brain.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local conversation history for the "Ask Your Brain" Knowledge Assistant.
 * Strictly excludes sensitive Vault content and temporary internal reasoning.
 */
@Entity(
    tableName = "ai_conversations",
    indices = [Index(value = ["createdAt"])]
)
data class ConversationEntity(
    @PrimaryKey
    val id: String,
    val question: String,
    val answer: String,
    val sourceItemIdsJson: String, // Comma or JSON-separated list of cited SavedItemEntity IDs
    val mode: String = "SEARCH",   // "SEARCH", "SUMMARIZE", "EXPLAIN", "COMPARE"
    val createdAt: Long = System.currentTimeMillis()
)
