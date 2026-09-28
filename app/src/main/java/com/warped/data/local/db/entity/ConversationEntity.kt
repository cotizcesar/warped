package com.warped.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "provider_type") val providerType: String,
    @ColumnInfo(name = "endpoint_id") val endpointId: Long,
    @ColumnInfo(name = "model_id") val modelId: String?,
    @ColumnInfo(name = "system_prompt") val systemPrompt: String?,
    // Phase 53 (TOGGLE-01): tri-state override NULL/0/1, null = inherit global default-ON.
    @ColumnInfo(name = "web_override") val webOverride: Boolean? = null,
)
