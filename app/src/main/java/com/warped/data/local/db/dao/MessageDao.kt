package com.warped.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.warped.data.local.db.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY created_at ASC")
    fun observeByConversation(conversationId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY created_at ASC")
    suspend fun getByConversation(conversationId: Long): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity): Long

    @Update
    suspend fun update(message: MessageEntity)

    @Query("DELETE FROM messages WHERE conversation_id = :conversationId")
    suspend fun deleteByConversation(conversationId: Long)

    @Query("DELETE FROM messages")
    suspend fun deleteAll()

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteById(messageId: Long)

    /**
     * Phase 54 (RETRY-01): row-reuse lookup for retry writes. Resolves the
     * assistant row id by (conversation_id, created_at) — `createdAt` millis
     * round-trips exactly (Instant.ofEpochMilli/toEpochMilli) and a covering
     * index exists on (conversation_id, created_at). The role column stores
     * the enum name (`toEntity` writes `role.name`), so the 'ASSISTANT'
     * literal is exact. Returns null when the message was deleted — the
     * caller treats null as a silent no-op and never inserts orphan rows.
     */
    @Query(
        "SELECT id FROM messages WHERE conversation_id = :conversationId " +
            "AND created_at = :createdAt AND role = 'ASSISTANT' LIMIT 1",
    )
    suspend fun findAssistantRowId(conversationId: Long, createdAt: Long): Long?
}
