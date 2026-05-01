package com.warped.data.local.db.entity

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
    importedAt = Instant.ofEpochMilli(importedAt)
)

fun LocalModel.toEntity(): LocalModelEntity = LocalModelEntity(
    id = id,
    name = name,
    filePath = filePath,
    sizeBytes = sizeBytes,
    quantization = quantization,
    parameterCount = parameterCount,
    architecture = architecture,
    importedAt = importedAt.toEpochMilli()
)
