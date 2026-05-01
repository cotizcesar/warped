package com.warped.domain.model

data class GenerationParameters(
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.1f,
    val maxTokens: Int = 2048,
    val contextSize: Int = 4096,
    val seed: Int = -1,
    val threads: Int = 4,
    val reasoningEnabled: Boolean = true
)
