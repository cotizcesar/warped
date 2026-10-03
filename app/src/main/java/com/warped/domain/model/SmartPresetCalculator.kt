package com.warped.domain.model

import com.warped.data.local.inference.MemoryInfo

data class SmartPresetResult(
    val parameters: GenerationParameters,
    val tier: MemoryTier,
    val availableGb: Float,
    val totalGb: Float,
    val modelSizeGb: Float
)

enum class MemoryTier { LOW, MID, HIGH }

/** Sampling quadruple: temperature, topK, topP, repeatPenalty. */
private data class Sampling(
    val temperature: Float,
    val topK: Int,
    val topP: Float,
    val repeatPenalty: Float
)

object SmartPresetCalculator {

    private const val LOW_THRESHOLD_MB = 4 * 1024L
    private const val HIGH_THRESHOLD_MB = 8 * 1024L

    fun calculate(
        memoryInfo: MemoryInfo,
        modelSizeBytes: Long
    ): SmartPresetResult {
        val freeMb = memoryInfo.availableBytes / (1024 * 1024)
        val totalMb = memoryInfo.totalBytes / (1024 * 1024)
        val modelMb = modelSizeBytes / (1024 * 1024)

        val availableGb = freeMb / 1024f
        val totalGb = totalMb / 1024f
        val modelSizeGb = modelMb / 1024f

        val tier = when {
            freeMb < LOW_THRESHOLD_MB -> MemoryTier.LOW
            freeMb >= HIGH_THRESHOLD_MB -> MemoryTier.HIGH
            else -> MemoryTier.MID
        }

        val headroomFactor = if (modelMb > 0 && freeMb > 0) {
            (freeMb - modelMb).toFloat() / freeMb.toFloat()
        } else 0f

        val (contextSize, maxTokens, threads) = when (tier) {
            MemoryTier.LOW -> Triple(2048, 1024, 2)
            MemoryTier.MID -> Triple(4096, 2048, 4)
            MemoryTier.HIGH -> Triple(8192, 4096, 6)
        }

        val adjustedContext = if (headroomFactor < 0.2f && tier == MemoryTier.LOW) {
            (contextSize * 0.5f).toInt().coerceAtLeast(512)
        } else contextSize

        val adjustedMaxTokens = if (headroomFactor < 0.1f) {
            (maxTokens * 0.5f).toInt().coerceAtLeast(256)
        } else maxTokens

        // Sampling precision tiers by MODEL file size (small models ramble,
        // loop greetings, and hallucinate at default sampling — tighten
        // them down; big models stay expressive with an anti-slop floor).
        // Memory tiers above own efficiency (context/threads/tokens);
        // these own quality. Thresholds in MiB of the model file.
        val (temperature, topK, topP, repeatPenalty) = when {
            modelMb < 1024 -> Sampling(0.3f, 15, 0.85f, 1.15f) // tiny: max precision
            modelMb < 3072 -> Sampling(0.5f, 25, 0.9f, 1.12f) // small: focused
            modelMb < 6144 -> Sampling(0.7f, 40, 0.95f, 1.08f) // mid: balanced
            else -> Sampling(0.8f, 40, 0.95f, 1.05f) // large: expressive
        }

        val parameters = GenerationParameters(
            temperature = temperature,
            topP = topP,
            topK = topK,
            repeatPenalty = repeatPenalty,
            maxTokens = adjustedMaxTokens,
            contextSize = adjustedContext,
            seed = -1,
            threads = threads
        )

        return SmartPresetResult(
            parameters = parameters,
            tier = tier,
            availableGb = availableGb,
            totalGb = totalGb,
            modelSizeGb = modelSizeGb
        )
    }

    fun tierLabel(tier: MemoryTier): String = when (tier) {
        MemoryTier.LOW -> "Conservative"
        MemoryTier.MID -> "Balanced"
        MemoryTier.HIGH -> "Optimal"
    }

    /**
     * Precision guidance appended to the system instruction for small
     * models (the canned-greeting loops and rambling of sub-3GB models
     * respond strongly to explicit direction; big models need none).
     * Null when the model is large enough to behave without coaching.
     * Pure — unit-tested.
     */
    fun precisionHintFor(modelSizeBytes: Long): String? {
        val modelMb = modelSizeBytes / (1024 * 1024)
        return when {
            modelMb < 1024 ->
                "Answer briefly and directly in the user's language. " +
                    "Never repeat greetings, the question, or your own sentences. " +
                    "If unsure, say so in one line."
            modelMb < 3072 ->
                "Be concise and do not repeat yourself."
            else -> null
        }
    }
}
