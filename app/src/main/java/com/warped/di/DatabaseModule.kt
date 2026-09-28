package com.warped.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.warped.BuildConfig
import com.warped.data.local.db.AppDatabase
import com.warped.data.local.db.MIGRATION_4_5
import com.warped.data.local.db.MIGRATION_5_6
import com.warped.data.local.db.MIGRATION_6_7
import com.warped.data.local.db.MIGRATION_7_8
import com.warped.data.local.db.MIGRATION_8_9
import com.warped.data.local.db.MIGRATION_9_10
import com.warped.data.local.db.MIGRATION_10_11
import com.warped.data.local.db.MIGRATION_11_12
import com.warped.data.local.db.MIGRATION_12_13
import com.warped.data.local.db.MIGRATION_13_14
import com.warped.data.local.db.dao.BenchmarkResultDao
import com.warped.data.local.db.dao.ConversationDao
import com.warped.data.local.db.dao.DownloadCheckpointDao
import com.warped.data.local.db.dao.LocalModelDao
import com.warped.data.local.db.dao.MessageDao
import com.warped.data.local.db.dao.PresetDao
import com.warped.data.local.db.dao.RemoteEndpointDao
import com.warped.data.local.security.KeystoreManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SupportFactory
import java.security.SecureRandom
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private const val DB_PASSPHRASE_KEY = "db_passphrase"

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        keystoreManager: KeystoreManager
    ): AppDatabase {
        val passphrase = getOrCreateDbPassphrase(keystoreManager)
        val factory = SupportFactory(passphrase)
        return Room.databaseBuilder(context, AppDatabase::class.java, "warped.db")
            .openHelperFactory(factory)
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
            .fallbackToDestructiveMigration(false)
            .build()
    }

    private fun getOrCreateDbPassphrase(keystoreManager: KeystoreManager): ByteArray {
        val existing = keystoreManager.get(DB_PASSPHRASE_KEY)
        return if (existing != null) {
            existing.toByteArray(Charsets.UTF_8)
        } else {
            val newPhrase = generatePassphrase()
            keystoreManager.put(DB_PASSPHRASE_KEY, newPhrase)
            newPhrase.toByteArray(Charsets.UTF_8)
        }
    }

    private fun generatePassphrase(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

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
    fun provideBenchmarkResultDao(db: AppDatabase): BenchmarkResultDao = db.benchmarkResultDao()

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
}
