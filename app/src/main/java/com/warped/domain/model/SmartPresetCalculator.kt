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

        val parameters = GenerationParameters(
            temperature = 0.7f,
            topP = 0.9f,
            topK = 40,
            repeatPenalty = 1.1f,
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
}
