package com.warped.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "benchmark_results",
    indices = [Index("modelId"), Index("createdAt")]
)
data class BenchmarkResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "modelId") val modelId: String,
    @ColumnInfo(name = "configHash") val configHash: String,
    @ColumnInfo(name = "initTimeMs") val initTimeMs: Long,
    @ColumnInfo(name = "prefillTokPerSec") val prefillTokPerSec: Double,
    @ColumnInfo(name = "decodeTokPerSec") val decodeTokPerSec: Double,
    @ColumnInfo(name = "peakMemoryBytes") val peakMemoryBytes: Long,
    @ColumnInfo(name = "createdAt") val createdAt: Long,
)
