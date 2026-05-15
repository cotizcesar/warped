package com.warped.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_checkpoints")
data class DownloadCheckpointEntity(
    @PrimaryKey
    @ColumnInfo(name = "model_id")
    val modelId: String,       // e.g., "litert-community/ModelName/file.litertlm"
    @ColumnInfo(name = "file_name")
    val fileName: String,
    @ColumnInfo(name = "file_url")
    val fileUrl: String,
    @ColumnInfo(name = "total_bytes")
    val totalBytes: Long,
    @ColumnInfo(name = "downloaded_bytes")
    val downloadedBytes: Long,
    @ColumnInfo(name = "is_gated")
    val isGated: Boolean = false,
)
