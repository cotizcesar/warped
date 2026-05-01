package com.warped.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "endpoints")
data class RemoteEndpointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "url") val url: String,
    @ColumnInfo(name = "api_type") val apiType: String,
    @ColumnInfo(name = "model_id") val modelId: String?,
    @ColumnInfo(name = "encrypted_api_key_ref") val encryptedApiKeyRef: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "is_active") val isActive: Boolean = false
)
