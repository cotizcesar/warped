package com.warped.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "temperature") val temperature: Float,
    @ColumnInfo(name = "top_p") val topP: Float,
    @ColumnInfo(name = "top_k") val topK: Int,
    @ColumnInfo(name = "repeat_penalty") val repeatPenalty: Float,
    @ColumnInfo(name = "max_tokens") val maxTokens: Int,
    @ColumnInfo(name = "context_size") val contextSize: Int,
    @ColumnInfo(name = "seed") val seed: Int,
    @ColumnInfo(name = "threads") val threads: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
