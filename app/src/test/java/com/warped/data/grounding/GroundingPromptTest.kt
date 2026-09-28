package com.warped.data.grounding

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Phase 50 (WEB-04): prompt augmentation order verification.
 */
class GroundingPromptTest {

    @Test
    fun `system prompt never invents urls`() {
        assertThat(GroundingPrompt.SYSTEM_PROMPT).contains("Never invent URLs")
    }

    @Test
    fun `augment order is prompt then block then original`() {
        val block = GroundingPrompt.buildBlock("https://a.com", "contenido")
        val out = GroundingPrompt.augment("pregunta original", block, groundingEnabled = true)

        val promptIdx = out.indexOf(GroundingPrompt.SYSTEM_PROMPT)
        val blockIdx = out.indexOf("[WEB CONTEXT")
        val originalIdx = out.indexOf("pregunta original")
        assertThat(promptIdx).isAtLeast(0)
        assertThat(blockIdx).isGreaterThan(promptIdx)
        assertThat(originalIdx).isGreaterThan(blockIdx)
    }

    @Test
    fun `null block with grounding disabled returns original untouched`() {
        assertThat(GroundingPrompt.augment("pregunta", null, groundingEnabled = false))
            .isEqualTo("pregunta")
    }

    @Test
    fun `null block with grounding enabled prepends system prompt`() {
        val out = GroundingPrompt.augment("pregunta", null, groundingEnabled = true)

        assertThat(out).isEqualTo("${GroundingPrompt.SYSTEM_PROMPT}\n\npregunta")
        assertThat(out.indexOf(GroundingPrompt.SYSTEM_PROMPT)).isEqualTo(0)
        assertThat(out.indexOf("pregunta")).isGreaterThan(0)
    }

    @Test
    fun `buildBlock has exact delimiters and source marker`() {
        val block = GroundingPrompt.buildBlock("https://a.com/x", "texto")

        assertThat(block).isEqualTo(
            "[WEB CONTEXT — source [1]: https://a.com/x]\ntexto\n[END WEB CONTEXT]"
        )
    }
}
