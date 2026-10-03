package com.warped.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * File-stem prettifier for model display names (user decision 2026-10-03):
 * "SmolLM3-3B_q4_block32_ekv4096" must render as "SmolLM3 3B".
 */
class ModelNamingTest {

    @Test
    fun `quant suffix cut to family and size`() {
        assertThat(prettyModelName("SmolLM3-3B_q4_block32_ekv4096.litertlm"))
            .isEqualTo("SmolLM3 3B")
    }

    @Test
    fun `distill stem keeps identity, drops packing`() {
        assertThat(prettyModelName("DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm"))
            .isEqualTo("DeepSeek R1 Distill Qwen 1.5B")
    }

    @Test
    fun `underscore convention keeps thinking marker`() {
        assertThat(prettyModelName("Qwen3_4b_thinking_dynamic_wi4b32_afp32.litertlm"))
            .isEqualTo("Qwen3 4b thinking")
    }

    @Test
    fun `plain name just dehyphenates`() {
        assertThat(prettyModelName("gemma-4-12B-it.litertlm"))
            .isEqualTo("gemma 4 12B it")
    }

    @Test
    fun `path input uses basename only`() {
        assertThat(prettyModelName("/data/models/qwen3_0.6b_q4_block32_ekv1280.litertlm"))
            .isEqualTo("qwen3 0.6b")
    }

    @Test
    fun `degenerate input never returns blank`() {
        assertThat(prettyModelName("q4.litertlm")).isEqualTo("q4")
        assertThat(prettyModelName("model.litertlm")).isEqualTo("model")
    }
}
