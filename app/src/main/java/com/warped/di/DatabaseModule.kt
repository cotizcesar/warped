package com.warped.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.warped.data.local.db.AppDatabase
import com.warped.data.local.db.MIGRATION_4_5
import com.warped.data.local.db.MIGRATION_5_6
import com.warped.data.local.db.MIGRATION_6_7
import com.warped.data.local.db.MIGRATION_7_8
import com.warped.data.local.db.MIGRATION_8_9
import com.warped.data.local.db.MIGRATION_9_10
import com.warped.data.local.db.MIGRATION_8_9
import com.warped.data.local.db.dao.ConversationDao
import com.warped.data.local.db.dao.DownloadCheckpointDao
import com.warped.data.local.db.dao.LocalModelDao
import com.warped.data.local.db.dao.MessageDao
import com.warped.data.local.db.dao.PresetDao
import com.warped.data.local.db.dao.RemoteEndpointDao
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
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "warped.db")
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
            .fallbackToDestructiveMigration(true)
            .build()

    @Provides
    fun provideConversationDao(db: AppDatabase): ConversationDao = db.conversationDao()

    @Provides
    fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideRemoteEndpointDao(db: AppDatabase): RemoteEndpointDao = db.remoteEndpointDao()

    @Provides
    fun provideLocalModelDao(db: AppDatabase): LocalModelDao = db.localModelDao()

    @Provides
    fun providePresetDao(db: AppDatabase): PresetDao = db.presetDao()

    @Provides
    fun provideDownloadCheckpointDao(db: AppDatabase): DownloadCheckpointDao = db.downloadCheckpointDao()

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
}
