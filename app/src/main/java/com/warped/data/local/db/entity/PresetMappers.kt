package com.warped.data.local.db.entity

import com.warped.domain.model.Preset
import java.time.Instant

fun PresetEntity.toDomain(): Preset = Preset(
    id = id,
    name = name,
    temperature = temperature,
    topP = topP,
    topK = topK,
    repeatPenalty = repeatPenalty,
    maxTokens = maxTokens,
    contextSize = contextSize,
    seed = seed,
    threads = threads,
    createdAt = Instant.ofEpochMilli(createdAt)
)

fun Preset.toEntity(): PresetEntity = PresetEntity(
    id = id,
    name = name,
    temperature = temperature,
    topP = topP,
    topK = topK,
    repeatPenalty = repeatPenalty,
    maxTokens = maxTokens,
    contextSize = contextSize,
    seed = seed,
    threads = threads,
    createdAt = createdAt.toEpochMilli()
)
