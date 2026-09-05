package com.example.brain.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.brain.data.dao.CollectionDao
import com.example.brain.data.dao.ConversationDao
import com.example.brain.data.dao.FolderDao
import com.example.brain.data.dao.ItemEmbeddingDao
import com.example.brain.data.dao.ItemRelationDao
import com.example.brain.data.dao.SavedItemDao
import com.example.brain.data.dao.SavedSearchDao
import com.example.brain.data.dao.TagDao
import com.example.brain.data.entity.SavedSearchEntity
import com.example.brain.data.entity.CollectionEntity
import com.example.brain.data.entity.CollectionItemCrossRef
import com.example.brain.data.entity.ConversationEntity
import com.example.brain.data.entity.FolderEntity
import com.example.brain.data.entity.ItemEmbeddingEntity
import com.example.brain.data.entity.ItemRelationEntity
import com.example.brain.data.entity.ItemTagCrossRef
import com.example.brain.data.entity.SavedItemEntity
import com.example.brain.data.entity.SavedItemFtsEntity
import com.example.brain.data.entity.TagEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        SavedItemEntity::class,
        FolderEntity::class,
        TagEntity::class,
        ItemTagCrossRef::class,
        CollectionEntity::class,
        CollectionItemCrossRef::class,
        ItemRelationEntity::class,
        ItemEmbeddingEntity::class,
        SavedItemFtsEntity::class,
        ConversationEntity::class,
        SavedSearchEntity::class
    ],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun savedItemDao(): SavedItemDao
    abstract fun folderDao(): FolderDao
    abstract fun tagDao(): TagDao
    abstract fun collectionDao(): CollectionDao
    abstract fun itemRelationDao(): ItemRelationDao
    abstract fun conversationDao(): ConversationDao
    abstract fun itemEmbeddingDao(): ItemEmbeddingDao
    abstract fun savedSearchDao(): SavedSearchDao

    companion object {
        const val INBOX_FOLDER_ID = "inbox_default_id"
        private const val DB_NAME = "pkm_second_brain.db"

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saved_items ADD COLUMN originalUrl TEXT")
                db.execSQL("ALTER TABLE saved_items ADD COLUMN extractedText TEXT")
                db.execSQL("ALTER TABLE saved_items ADD COLUMN processingStatus TEXT NOT NULL DEFAULT 'NOT_STARTED'")
                db.execSQL("ALTER TABLE saved_items ADD COLUMN sourceApp TEXT")
                db.execSQL("ALTER TABLE saved_items ADD COLUMN mediaDurationMs INTEGER")
                db.execSQL("ALTER TABLE saved_items ADD COLUMN mediaWidth INTEGER")
                db.execSQL("ALTER TABLE saved_items ADD COLUMN mediaHeight INTEGER")
                db.execSQL("ALTER TABLE saved_items ADD COLUMN mediaFileSize INTEGER")
                db.execSQL("ALTER TABLE saved_items ADD COLUMN thumbnailPath TEXT")
                db.execSQL("DROP TABLE IF EXISTS saved_items_fts")
                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS saved_items_fts USING fts4(title, description, note, extractedText)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE item_relations ADD COLUMN origin TEXT NOT NULL DEFAULT 'USER_CREATED'")
                db.execSQL("ALTER TABLE item_relations ADD COLUMN explanation TEXT")
                db.execSQL("ALTER TABLE item_relations ADD COLUMN confidence REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE item_relations ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'")
                db.execSQL("ALTER TABLE item_relations ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE collections ADD COLUMN isSmart INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE collections ADD COLUMN ruleJson TEXT")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saved_items ADD COLUMN summary TEXT")
                db.execSQL("ALTER TABLE saved_items ADD COLUMN summaryModel TEXT")
                db.execSQL("ALTER TABLE saved_items ADD COLUMN summaryVersion TEXT")
                db.execSQL("ALTER TABLE saved_items ADD COLUMN summaryCreatedAt INTEGER")
                db.execSQL("CREATE TABLE IF NOT EXISTS ai_conversations (id TEXT PRIMARY KEY NOT NULL, question TEXT NOT NULL, answer TEXT NOT NULL, sourceItemIdsJson TEXT NOT NULL, mode TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ai_conversations_createdAt ON ai_conversations(createdAt)")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saved_items ADD COLUMN reviewAt INTEGER")
                db.execSQL("ALTER TABLE saved_items ADD COLUMN reviewStatus TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE saved_items ADD COLUMN lastReviewedAt INTEGER")
                db.execSQL("ALTER TABLE saved_items ADD COLUMN reviewCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE saved_items ADD COLUMN maturity TEXT NOT NULL DEFAULT 'CAPTURED'")
                db.execSQL("ALTER TABLE saved_items ADD COLUMN openCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE saved_items ADD COLUMN lastOpenedAt INTEGER")
                db.execSQL("CREATE TABLE IF NOT EXISTS saved_searches (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, query TEXT NOT NULL, filterType TEXT, filterFolderId TEXT, filterTag TEXT, createdAt INTEGER NOT NULL)")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                    .fallbackToDestructiveMigration()
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Seed default Inbox folder and sample starter collection
                            CoroutineScope(Dispatchers.IO).launch {
                                val database = getInstance(context)
                                database.folderDao().insert(
                                    FolderEntity(
                                        id = INBOX_FOLDER_ID,
                                        name = "📥 Inbox",
                                        isSecret = false,
                                        parentId = null,
                                        createdAt = System.currentTimeMillis()
                                    )
                                )
                                database.collectionDao().insert(
                                    CollectionEntity(
                                        id = "reading_list_collection",
                                        name = "Reading Queue",
                                        description = "Articles and papers to read and internalize",
                                        colorHex = "#6366F1",
                                        createdAt = System.currentTimeMillis()
                                    )
                                )
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
