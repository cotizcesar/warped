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
    val modelFormat: String = "GGUF",
    val importedAt: Instant
) {
    val capabilities: ModelCapabilities by lazy {
        val lower = "${name} ${filePath}".lowercase()
        ModelCapabilities(
            vision = lower.containsAny("vision", "vl", "multimodal", "llava", "qwen-vl",
                "gemini", "pixtral", "qwen2-vl", "llama-vision", "phi-vision", "paligemma"),
            reasoning = lower.containsAny("r1", "reasoning", "qwq", "o1-", "o3-",
                "thinking", "thinker", "deep-thought"),
            tools = lower.containsAny("tool", "function-calling", "hermes", "command-r")
        )
    }
}

data class ModelCapabilities(
    val vision: Boolean = false,
    val reasoning: Boolean = false,
    val tools: Boolean = false
)

private fun String.containsAny(vararg keywords: String): Boolean =
    keywords.any { contains(it, ignoreCase = true) }
