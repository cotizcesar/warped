package com.warped.data.local.db.entity

import com.warped.domain.model.GenerationParameters
import com.warped.domain.model.LocalModel
import java.time.Instant

fun LocalModelEntity.toDomain(): LocalModel = LocalModel(
    id = id,
    name = name,
    filePath = filePath,
    sizeBytes = sizeBytes,
    quantization = quantization,
    parameterCount = parameterCount,
    architecture = architecture,
    modelFormat = modelFormat,
    importedAt = Instant.ofEpochMilli(importedAt),
    parameters = GenerationParameters(
        temperature = paramTemperature,
        topP = paramTopP,
        topK = paramTopK,
        repeatPenalty = paramRepeatPenalty,
        maxTokens = paramMaxTokens,
        contextSize = paramContextSize,
        seed = paramSeed
    )
)

fun LocalModel.toEntity(): LocalModelEntity = LocalModelEntity(
    id = id,
    name = name,
    filePath = filePath,
    sizeBytes = sizeBytes,
    quantization = quantization,
    parameterCount = parameterCount,
    architecture = architecture,
    modelFormat = modelFormat,
    importedAt = importedAt.toEpochMilli(),
    paramTemperature = parameters.temperature,
    paramTopP = parameters.topP,
    paramTopK = parameters.topK,
    paramRepeatPenalty = parameters.repeatPenalty,
    paramMaxTokens = parameters.maxTokens,
    paramContextSize = parameters.contextSize,
    paramSeed = parameters.seed
)
