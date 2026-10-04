package com.warped.domain.model

import com.google.common.truth.Truth.assertThat
import com.warped.data.local.inference.MemoryInfo
import org.junit.jupiter.api.Test

/**
 * Vendor family sampling (upstream model cards, 2026-10-03) overrides
 * size tiers field-by-field; unknown families fall back to tiers.
 */
class FamilySamplingTest {

    private fun memInfo() = MemoryInfo(
        availableBytes = 8L * 1024L * 1024L * 1024L,
        totalBytes = 12L * 1024L * 1024L * 1024L,
        usedPercent = 33
    )

    @Test
    fun `qwen3 thinking row matches vendor card`() {
        val f = familySamplingFor("Qwen3 4B Thinking", thinking = true)

        assertThat(f?.temperature).isEqualTo(0.6f)
        assertThat(f?.topK).isEqualTo(20)
        assertThat(f?.topP).isEqualTo(0.95f)
    }

    @Test
    fun `qwen3 non-thinking row matches vendor card`() {
        val f = familySamplingFor("Qwen3 4B", thinking = false)

        assertThat(f?.temperature).isEqualTo(0.7f)
        assertThat(f?.topK).isEqualTo(20)
        assertThat(f?.topP).isEqualTo(0.8f)
    }

    @Test
    fun `name markers imply reasoning without flag`() {
        assertThat(familySamplingFor("DeepSeek R1 Distill", thinking = false)?.temperature)
            .isEqualTo(0.6f)
        assertThat(familySamplingFor("LFM2.5 1.2B Thinking", thinking = false)?.topK)
            .isEqualTo(50)
    }

    @Test
    fun `gemma runs hot per google card despite mid size`() {
        val p = SmartPresetCalculator.calculate(
            memInfo(), 2500L * 1024L * 1024L,
            modelName = "Gemma 4 E2B",
        ).parameters
        assertThat(p.temperature).isEqualTo(1.0f)
        assertThat(p.topK).isEqualTo(64)
        assertThat(p.topP).isEqualTo(0.95f)
        // Unspecified family fields fall back to the size tier.
        assertThat(p.repeatPenalty).isEqualTo(1.12f)
    }

    @Test
    fun `ministral instruct stays near-greedy`() {
        val p = SmartPresetCalculator.calculate(
            memInfo(), 2200L * 1024L * 1024L,
            modelName = "Ministral 3 3B",
        ).parameters
        assertThat(p.temperature).isEqualTo(0.1f)
    }

    @Test
    fun `unknown family keeps size tiers`() {
        assertThat(familySamplingFor("SomeRandom 1B", thinking = false)).isNull()
        val p = SmartPresetCalculator.calculate(
            memInfo(), 300L * 1024L * 1024L,
            modelName = "SomeRandom 1B",
        ).parameters
        assertThat(p.temperature).isEqualTo(0.3f)
        assertThat(p.topK).isEqualTo(15)
    }

    @Test
    fun `lfm thinking row matches vendor card`() {
        val p = SmartPresetCalculator.calculate(
            memInfo(), 1300L * 1024L * 1024L,
            modelName = "LFM2.5 1.2B Thinking", thinking = true,
        ).parameters
        assertThat(p.temperature).isEqualTo(0.6f)
        assertThat(p.topK).isEqualTo(50)
        assertThat(p.repeatPenalty).isEqualTo(1.05f)
    }

    @Test
    fun `reasoning markers match dedicated reasoners only`() {
        assertThat(hasReasoningMarker("deepseek-r1-1.5b")).isTrue()
        assertThat(hasReasoningMarker("Qwen3 0.6B Thinking (int4)")).isTrue()
        assertThat(hasReasoningMarker("Ministral 3 3B Reasoning")).isTrue()
        // Hybrids answer directly when the question is simple.
        assertThat(hasReasoningMarker("smolllm3-3b")).isFalse()
        assertThat(hasReasoningMarker("Gemma 4 E2B")).isFalse()
        assertThat(hasReasoningMarker("qwen3-4b")).isFalse()
    }

    @Test
    fun `params-mode default set — tuned families vs unknown models`() {
        // 2026-10-04 select: the default (PRESET for tuned families,
        // CUSTOM-with-smart-values otherwise) keys on this predicate.
        assertThat(familySamplingFor("gemma-3-1b-it", thinking = false)).isNotNull()
        assertThat(familySamplingFor("Qwen3 4B", thinking = false)).isNotNull()
        assertThat(familySamplingFor("TinyLlama 1.1B Chat", thinking = false)).isNotNull()
        assertThat(familySamplingFor("SomeRandom 1B", thinking = false)).isNull()
    }
}
