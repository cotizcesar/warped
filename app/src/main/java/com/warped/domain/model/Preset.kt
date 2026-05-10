package com.warped.domain.model

import java.time.Instant

data class Preset(
    val id: Long = 0,
    val name: String,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.1f,
    val maxTokens: Int = 2048,
    val contextSize: Int = 4096,
    val seed: Int = -1,
    val threads: Int = 4,
    val modelFormat: String = "LITERTLM",
    val createdAt: Instant = Instant.now()
) {
    fun toGenerationParameters(): GenerationParameters = GenerationParameters(
        temperature = temperature,
        topP = topP,
        topK = topK,
        repeatPenalty = repeatPenalty,
        maxTokens = maxTokens,
        contextSize = contextSize,
        seed = seed,
        threads = threads
    )
}
