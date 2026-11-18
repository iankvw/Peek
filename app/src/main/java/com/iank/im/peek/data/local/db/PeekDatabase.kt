package com.iank.im.peek.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.iank.im.peek.data.local.db.converter.RoomConverters
import com.iank.im.peek.data.local.db.dao.ApiPresetDao
import com.iank.im.peek.data.local.db.dao.ConversationDao
import com.iank.im.peek.data.local.db.dao.FileDeletionQueueDao
import com.iank.im.peek.data.local.db.dao.MessageDao
import com.iank.im.peek.data.local.db.dao.RemoteFileCacheDao
import com.iank.im.peek.data.local.db.entity.ApiPresetEntity
import com.iank.im.peek.data.local.db.entity.ConversationEntity
import com.iank.im.peek.data.local.db.entity.MessageAttachmentEntity
import com.iank.im.peek.data.local.db.entity.MessageEntity
import com.iank.im.peek.data.local.db.entity.PendingFileDeletionEntity
import com.iank.im.peek.data.local.db.entity.PendingRemoteDeletionEntity
import com.iank.im.peek.data.local.db.entity.RemoteFileCacheEntity

@Database(
    entities = [
        ApiPresetEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        MessageAttachmentEntity::class,
        RemoteFileCacheEntity::class,
        PendingFileDeletionEntity::class,
        PendingRemoteDeletionEntity::class
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(RoomConverters::class)
abstract class PeekDatabase : RoomDatabase() {
    abstract fun apiPresetDao(): ApiPresetDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun remoteFileCacheDao(): RemoteFileCacheDao
    abstract fun fileDeletionQueueDao(): FileDeletionQueueDao

    companion object {
        const val DATABASE_NAME = "peek_database"

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN activeTargetId TEXT")
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN servedGroupName TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN servedPresetName TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN servedModelName TEXT")
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN isReported INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
