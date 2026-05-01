package com.warped.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_models")
data class LocalModelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "quantization") val quantization: String,
    @ColumnInfo(name = "parameter_count") val parameterCount: String,
    @ColumnInfo(name = "architecture") val architecture: String,
    @ColumnInfo(name = "imported_at") val importedAt: Long
)
