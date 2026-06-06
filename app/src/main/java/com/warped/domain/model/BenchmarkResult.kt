package com.warped.domain.model

data class BenchmarkResult(
    val id: Long = 0,
    val modelId: String,
    val configHash: String,
    val initTimeMs: Long,
    val prefillTokPerSec: Double,
    val decodeTokPerSec: Double,
    val peakMemoryBytes: Long,
    val createdAt: Long,
)
