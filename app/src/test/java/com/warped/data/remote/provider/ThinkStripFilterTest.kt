package com.warped.data.remote.provider

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * 2026-10-04: thinking-off turns on thinking models (e.g. Gemma hybrids
 * via LM Studio) rendered an empty bubble. The provider fans reasoning
 * out as SPLIT markers — "<think>", thought, "</think>" in separate
 * Deltas — which a single-token pair strip could never remove; the
 * orphan markers then made the ViewModel truncate the whole turn.
 */
class ThinkStripFilterTest {

    @Test
    fun `split markers across deltas drop thought and keep answer`() {
        val filter = ThinkStripFilter()

        val out = listOf("<think>", "Why is the sky blue?", "</think>", "¡Hola! El cielo es azul.")
            .map { filter.filterDelta(it) }
            .joinToString("")

        assertThat(out).isEqualTo("¡Hola! El cielo es azul.")
    }

    @Test
    fun `complete pair inside one token is removed`() {
        val filter = ThinkStripFilter()

        assertThat(filter.filterDelta("<think>hmm</think>Listo.")).isEqualTo("Listo.")
    }

    @Test
    fun `plain text passes through untouched`() {
        val filter = ThinkStripFilter()

        assertThat(filter.filterDelta("¡Hola! ¿Cómo estás?")).isEqualTo("¡Hola! ¿Cómo estás?")
    }

    @Test
    fun `unclosed thought swallows following deltas until close`() {
        val filter = ThinkStripFilter()

        assertThat(filter.filterDelta("Respuesta <think>pensando...")).isEqualTo("Respuesta ")
        assertThat(filter.filterDelta("sigo pensando...")).isEmpty()
        assertThat(filter.filterDelta("listo.</think>¡Aquí está!")).isEqualTo("¡Aquí está!")
    }

    @Test
    fun `orphan close without open keeps following answer`() {
        val filter = ThinkStripFilter()

        assertThat(filter.filterDelta("pensamiento.</think>Respuesta final."))
            .isEqualTo("Respuesta final.")
    }

    @Test
    fun `open and close split with answer on both sides`() {
        val filter = ThinkStripFilter()

        val out = listOf("Antes <think>", "medio", "</think> después.")
            .map { filter.filterDelta(it) }
            .joinToString("")

        assertThat(out).isEqualTo("Antes  después.")
    }
}
