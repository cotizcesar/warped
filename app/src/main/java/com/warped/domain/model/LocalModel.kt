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
    val modelFormat: String = "LITERTLM",
    val importedAt: Instant,
    val parameters: GenerationParameters = GenerationParameters()
) {
    val capabilities: ModelCapabilities by lazy {
        ModelCapabilities(
            vision = true,
            reasoning = true,
            tools = true,
            audio = true
        )
    }
}

data class ModelCapabilities(
    val vision: Boolean = false,
    val reasoning: Boolean = false,
    val tools: Boolean = false,
    val audio: Boolean = false
)
