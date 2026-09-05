package com.example.brain.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.brain.data.entity.ItemTagCrossRef
import com.example.brain.data.entity.SavedItemEntity
import com.example.brain.data.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertTag(tag: TagEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertItemTagCrossRef(crossRef: ItemTagCrossRef): Long

    @Query("DELETE FROM item_tag_cross_ref WHERE itemId = :itemId AND tagName = :tagName")
    fun removeItemTag(itemId: String, tagName: String): Int

    @Query("DELETE FROM item_tag_cross_ref WHERE itemId = :itemId")
    fun removeAllTagsForItem(itemId: String): Int

    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun getAllTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun getAllTagsSync(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    fun getTagByName(name: String): TagEntity?

    @Query("""
        SELECT tags.* FROM tags
        INNER JOIN item_tag_cross_ref ON tags.name = item_tag_cross_ref.tagName
        WHERE item_tag_cross_ref.itemId = :itemId
        ORDER BY tags.name ASC
    """)
    fun getTagsForItem(itemId: String): Flow<List<TagEntity>>

    @Query("""
        SELECT tags.* FROM tags
        INNER JOIN item_tag_cross_ref ON tags.name = item_tag_cross_ref.tagName
        WHERE item_tag_cross_ref.itemId = :itemId
        ORDER BY tags.name ASC
    """)
    fun getTagsForItemSync(itemId: String): List<TagEntity>

    @Query("""
        SELECT saved_items.* FROM saved_items
        INNER JOIN item_tag_cross_ref ON saved_items.id = item_tag_cross_ref.itemId
        WHERE item_tag_cross_ref.tagName = :tagName AND saved_items.isSecret = 0
        ORDER BY saved_items.createdAt DESC
    """)
    fun getItemsForTag(tagName: String): Flow<List<SavedItemEntity>>

    @Query("""
        SELECT tags.name AS name, COUNT(item_tag_cross_ref.itemId) AS count
        FROM tags
        INNER JOIN item_tag_cross_ref ON tags.name = item_tag_cross_ref.tagName
        INNER JOIN saved_items ON item_tag_cross_ref.itemId = saved_items.id
        WHERE saved_items.isSecret = 0
        GROUP BY tags.name
        ORDER BY count DESC, tags.name ASC
    """)
    fun getTagsWithCount(): Flow<List<TagWithCount>>
}

data class TagWithCount(
    val name: String,
    val count: Int
)
