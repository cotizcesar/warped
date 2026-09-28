package com.warped.data.grounding

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Phase 50 (WEB-04): prompt augmentation order verification.
 */
class GroundingPromptTest {

    @Test
    fun `system prompt never invents urls`() {
        assertThat(GroundingPrompt.SYSTEM_PROMPT).contains("Nunca inventes URLs")
    }

    @Test
    fun `augment order is prompt then block then original`() {
        val block = GroundingPrompt.buildBlock("https://a.com", "contenido")
        val out = GroundingPrompt.augment("pregunta original", block)

        val promptIdx = out.indexOf(GroundingPrompt.SYSTEM_PROMPT)
        val blockIdx = out.indexOf("[WEB CONTEXT")
        val originalIdx = out.indexOf("pregunta original")
        assertThat(promptIdx).isAtLeast(0)
        assertThat(blockIdx).isGreaterThan(promptIdx)
        assertThat(originalIdx).isGreaterThan(blockIdx)
    }

    @Test
    fun `null block returns original untouched`() {
        assertThat(GroundingPrompt.augment("pregunta", null)).isEqualTo("pregunta")
    }

    @Test
    fun `buildBlock has exact delimiters and source marker`() {
        val block = GroundingPrompt.buildBlock("https://a.com/x", "texto")

        assertThat(block).isEqualTo(
            "[WEB CONTEXT — fuente [1]: https://a.com/x]\ntexto\n[FIN WEB CONTEXT]"
        )
    }
}
