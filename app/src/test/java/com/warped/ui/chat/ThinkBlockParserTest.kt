package com.warped.ui.chat

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * R1-distill leak (2026-10-03): DeepSeek-R1 emits thought with NO opening
 * `<think>` tag, ending in a lone `</think>` — the pair regex missed it
 * and the whole thought rendered in-band in both toggle modes.
 */
class ThinkBlockParserTest {

    @Test
    fun `orphan close splits thought from answer when thinking on`() {
        val raw = "Okay, the user greeted me in Spanish.\n</think>\n¡Hola! ¿Cómo estás?"

        val (clean, reasoning) = parseThinkBlocks(raw, enabled = true)

        assertThat(clean).isEqualTo("¡Hola! ¿Cómo estás?")
        assertThat(reasoning).contains("greeted me in Spanish")
        assertThat(clean).doesNotContain("</think>")
    }

    @Test
    fun `orphan close drops thought entirely when thinking off`() {
        val raw = "Okay, the user greeted me in Spanish.\n</think>\n¡Hola! ¿Cómo estás?"

        val (clean, reasoning) = parseThinkBlocks(raw, enabled = false)

        assertThat(clean).isEqualTo("¡Hola! ¿Cómo estás?")
        assertThat(reasoning).isEmpty()
    }

    @Test
    fun `paired tags still work when thinking on`() {
        val raw = "<think>Let me think.</think>Final answer."

        val (clean, reasoning) = parseThinkBlocks(raw, enabled = true)

        assertThat(clean).isEqualTo("Final answer.")
        assertThat(reasoning).isEqualTo("Let me think.")
    }

    @Test
    fun `plain answer passes through untouched`() {
        val raw = "¡Hola! ¿Cómo estás?"

        val (cleanOn, reasoningOn) = parseThinkBlocks(raw, enabled = true)
        val (cleanOff, reasoningOff) = parseThinkBlocks(raw, enabled = false)

        assertThat(cleanOn).isEqualTo(raw)
        assertThat(reasoningOn).isEmpty()
        assertThat(cleanOff).isEqualTo(raw)
        assertThat(reasoningOff).isEmpty()
    }

    @Test
    fun `unclosed open tag treats tail as thought when on`() {
        val raw = "Answer so far.<think>Still thinking..."

        val (clean, reasoning) = parseThinkBlocks(raw, enabled = true)

        assertThat(clean).isEqualTo("Answer so far.")
        assertThat(reasoning).isEqualTo("Still thinking...")
    }

    @Test
    fun `case variants split too`() {
        val raw = "Thought here.\n</THINK>\nDone."

        val (clean, reasoning) = parseThinkBlocks(raw, enabled = true)

        assertThat(clean).isEqualTo("Done.")
        assertThat(reasoning).isEqualTo("Thought here.")
    }
}
