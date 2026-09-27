package com.warped.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.warped.data.local.db.dao.BenchmarkResultDao
import com.warped.data.local.db.dao.ConversationDao
import com.warped.data.local.db.dao.DownloadCheckpointDao
import com.warped.data.local.db.dao.LocalModelDao
import com.warped.data.local.db.dao.MessageDao
import com.warped.data.local.db.dao.PresetDao
import com.warped.data.local.db.dao.RemoteEndpointDao
import com.warped.data.local.db.entity.BenchmarkResultEntity
import com.warped.data.local.db.entity.ConversationEntity
import com.warped.data.local.db.entity.DownloadCheckpointEntity
import com.warped.data.local.db.entity.LocalModelEntity
import com.warped.data.local.db.entity.MessageEntity
import com.warped.data.local.db.entity.PresetEntity
import com.warped.data.local.db.entity.RemoteEndpointEntity

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        RemoteEndpointEntity::class,
        LocalModelEntity::class,
        PresetEntity::class,
        DownloadCheckpointEntity::class,
        BenchmarkResultEntity::class
    ],
    version = 13,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun remoteEndpointDao(): RemoteEndpointDao
    abstract fun localModelDao(): LocalModelDao
    abstract fun presetDao(): PresetDao
    abstract fun downloadCheckpointDao(): DownloadCheckpointDao
    abstract fun benchmarkResultDao(): BenchmarkResultDao
}
