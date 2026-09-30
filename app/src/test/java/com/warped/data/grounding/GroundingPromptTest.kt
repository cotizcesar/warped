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
    fun `system prompt requires replying in the user's language`() {
        assertThat(GroundingPrompt.SYSTEM_PROMPT)
            .contains("Always reply in the same language the user wrote in.")
    }

    @Test
    fun `system prompt carries the per-turn re-search rule`() {
        // Quick-task (agentic-rows): tool-neutral wording (the pre-search
        // path has no tools — the VM re-searches every turn) that also
        // reaches every armed loop via the augmented user message.
        assertThat(GroundingPrompt.SYSTEM_PROMPT)
            .contains("Treat each new question on its own")
        assertThat(GroundingPrompt.SYSTEM_PROMPT)
            .contains("never answer from earlier sources alone")
    }

    @Test
    fun `augment order is prompt then block then original`() {
        val block = GroundingPrompt.buildBlock("https://a.com", "contenido")
        val out = GroundingPrompt.augment("pregunta original", block, groundingEnabled = true)

        val promptIdx = out.indexOf(GroundingPrompt.SYSTEM_PROMPT)
        val blockIdx = out.indexOf("--- Source [")
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
            "--- Source [1]: https://a.com/x ---\ntexto\n--- End of sources ---"
        )
    }

    @Test
    fun `built blocks never contain the web context label phrase`() {
        val echoTrigger = Regex("WEB CONTEXT\\s*\\d")
        val single = GroundingPrompt.buildBlock("https://a.com/x", "texto")
        val fused = GroundingPrompt.buildFusedBlock(
            listOf("https://a.com/uno" to "uno", "https://b.com/dos" to "dos")
        )
        val augmented = GroundingPrompt.augment("pregunta", single, groundingEnabled = true)

        for (out in listOf(single, fused, augmented)) {
            assertThat(echoTrigger.containsMatchIn(out)).isFalse()
            assertThat(out).contains("--- Source [1]")
            assertThat(out).contains("--- End of sources ---")
            assertThat(out).doesNotContain("[END WEB CONTEXT")
        }
    }
}
