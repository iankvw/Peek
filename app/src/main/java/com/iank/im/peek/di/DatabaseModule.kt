package com.iank.im.peek.di

import android.content.Context
import androidx.room.Room
import com.iank.im.peek.data.local.db.PeekDatabase
import com.iank.im.peek.data.local.db.dao.ApiPresetDao
import com.iank.im.peek.data.local.db.dao.ConversationDao
import com.iank.im.peek.data.local.db.dao.FileDeletionQueueDao
import com.iank.im.peek.data.local.db.dao.MessageDao
import com.iank.im.peek.data.local.db.dao.RemoteFileCacheDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PeekDatabase {
        return Room.databaseBuilder(
            context,
            PeekDatabase::class.java,
            PeekDatabase.DATABASE_NAME
        )
            .addMigrations(
                PeekDatabase.MIGRATION_1_2,
                PeekDatabase.MIGRATION_2_3,
                PeekDatabase.MIGRATION_3_4
            )
            .build()
    }

    @Provides
    fun provideApiPresetDao(db: PeekDatabase): ApiPresetDao = db.apiPresetDao()

    @Provides
    fun provideConversationDao(db: PeekDatabase): ConversationDao = db.conversationDao()

    @Provides
    fun provideMessageDao(db: PeekDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideRemoteFileCacheDao(db: PeekDatabase): RemoteFileCacheDao = db.remoteFileCacheDao()

    @Provides
    fun provideFileDeletionQueueDao(db: PeekDatabase): FileDeletionQueueDao = db.fileDeletionQueueDao()
}
