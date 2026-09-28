package com.warped.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.warped.data.local.db.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getById(id: Long): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conversation: ConversationEntity): Long

    @Query("UPDATE conversations SET updated_at = :timestamp WHERE id = :id")
    suspend fun updateTimestamp(id: Long, timestamp: Long)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: Long)

    // Phase 53 (TOGGLE-01): tri-state override read/write. Room maps nullable
    // INTEGER to Boolean? natively (null/0/1); the migration test proves it.
    // WR-02: the override is a preference, not recency — never bump
    // updated_at here (observeAll orders by updated_at DESC; a toggle on an
    // old conversation must not teleport it to the top of the list).
    @Query("SELECT web_override FROM conversations WHERE id = :id")
    suspend fun getWebOverride(id: Long): Boolean?

    @Query("UPDATE conversations SET web_override = :override WHERE id = :id")
    suspend fun setWebOverride(id: Long, override: Boolean?)

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()
}
