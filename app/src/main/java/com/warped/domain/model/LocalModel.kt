package com.warped.domain.model

import java.time.Instant

data class LocalModel(
    val id: Long = 0,
    val name: String,
    val filePath: String,
    val sizeBytes: Long,
    val quantization: String,
    val parameterCount: String,
    val architecture: String,
    val importedAt: Instant
)
