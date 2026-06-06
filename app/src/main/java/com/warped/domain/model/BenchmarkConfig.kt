package com.warped.domain.model

data class BenchmarkConfig(
    val temperature: Float = 0.7f,
    val topK: Int = 40,
    val maxTokens: Int = 512,
    val trials: Int = 3,
) {
    fun hash(): String =
        "$temperature/$topK/$maxTokens".hashCode().toUInt().toString(16)
}
