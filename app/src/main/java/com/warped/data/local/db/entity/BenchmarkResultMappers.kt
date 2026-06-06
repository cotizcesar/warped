package com.warped.data.local.db.entity

import com.warped.domain.model.BenchmarkResult

fun BenchmarkResultEntity.toDomain(): BenchmarkResult = BenchmarkResult(
    id = id,
    modelId = modelId,
    configHash = configHash,
    initTimeMs = initTimeMs,
    prefillTokPerSec = prefillTokPerSec,
    decodeTokPerSec = decodeTokPerSec,
    peakMemoryBytes = peakMemoryBytes,
    createdAt = createdAt,
)

fun BenchmarkResult.toEntity(): BenchmarkResultEntity = BenchmarkResultEntity(
    id = id,
    modelId = modelId,
    configHash = configHash,
    initTimeMs = initTimeMs,
    prefillTokPerSec = prefillTokPerSec,
    decodeTokPerSec = decodeTokPerSec,
    peakMemoryBytes = peakMemoryBytes,
    createdAt = createdAt,
)
