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
    @ColumnInfo(name = "imported_at") val importedAt: Long,
    @ColumnInfo(name = "model_format", defaultValue = "LITERTLM") val modelFormat: String = "LITERTLM",
    @ColumnInfo(name = "param_temperature", defaultValue = "0.7") val paramTemperature: Float = 0.7f,
    @ColumnInfo(name = "param_top_p", defaultValue = "0.9") val paramTopP: Float = 0.9f,
    @ColumnInfo(name = "param_top_k", defaultValue = "40") val paramTopK: Int = 40,
    @ColumnInfo(name = "param_repeat_penalty", defaultValue = "1.1") val paramRepeatPenalty: Float = 1.1f,
    @ColumnInfo(name = "param_max_tokens", defaultValue = "2048") val paramMaxTokens: Int = 2048,
    @ColumnInfo(name = "param_context_size", defaultValue = "4096") val paramContextSize: Int = 4096,
    @ColumnInfo(name = "param_seed", defaultValue = "-1") val paramSeed: Int = -1
)
