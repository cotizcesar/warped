package com.warped.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["conversation_id", "created_at"])]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "conversation_id") val conversationId: Long,
    @ColumnInfo(name = "role") val role: String,
    @ColumnInfo(name = "content") val content: String,
    @ColumnInfo(name = "token_count") val tokenCount: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "images") val images: String? = null,
    @ColumnInfo(name = "stats") val stats: String? = null,
    @ColumnInfo(name = "reasoning") val reasoning: String? = null,
    // Phase 68 (VMSG-06): voice-message metadata. NULL path = non-voice
    // message; duration 0 = unknown; transcript is the Phase 69 (VMSG-07)
    // placeholder — write-never/read-never in Phase 68, NULL = none yet.
    @ColumnInfo(name = "audio_path") val audioPath: String? = null,
    @ColumnInfo(name = "audio_duration_ms") val audioDurationMs: Long = 0,
    @ColumnInfo(name = "transcript") val transcript: String? = null
)
