package com.warped.data.grounding

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Pure unit tests for [AnaphoraAnchor.buildQuery], mirroring the
 * NeedsWebTest/CodeIntentTest style. Matrix: pronoun x history source →
 * anchored vs raw; anchor selection; truncation; dedupe; diacritics; ES+EN.
 */
class AnaphoraAnchorTest {

    @Test
    fun `anaphoric follow-up with prior user history anchors`() {
        val q = AnaphoraAnchor.buildQuery(
            message = "Quien es su hermanastro?",
            priorUser = listOf("Háblame de la familia real"),
            lastAssistant = null,
        )
        assertThat(q).isEqualTo("Quien es su hermanastro? Háblame de la familia real")
    }

    @Test
    fun `anaphoric follow-up with assistant-only history anchors on assistant`() {
        val q = AnaphoraAnchor.buildQuery(
            message = "Who are they?",
            priorUser = emptyList(),
            lastAssistant = "The British royal family lives in London.",
        )
        assertThat(q).isEqualTo("Who are they? The British royal family lives in London.")
    }

    @Test
    fun `anaphoric follow-up with no history returns raw`() {
        val q = AnaphoraAnchor.buildQuery(
            message = "Quien es su hermanastro?",
            priorUser = emptyList(),
            lastAssistant = null,
        )
        assertThat(q).isEqualTo("Quien es su hermanastro?")
    }

    @Test
    fun `anaphoric first turn with blank assistant returns raw`() {
        val q = AnaphoraAnchor.buildQuery(
            message = "Who is he?",
            priorUser = emptyList(),
            lastAssistant = "   ",
        )
        assertThat(q).isEqualTo("Who is he?")
    }

    @Test
    fun `no anaphora with history returns raw`() {
        val q = AnaphoraAnchor.buildQuery(
            message = "Latest news about Mars rovers",
            priorUser = listOf("Háblame de la familia real"),
            lastAssistant = "Some answer.",
        )
        assertThat(q).isEqualTo("Latest news about Mars rovers")
    }

    @Test
    fun `most recent prior user wins over assistant`() {
        val q = AnaphoraAnchor.buildQuery(
            message = "Tell me more about this",
            priorUser = listOf("Old topic", "New topic: quantum dots"),
            lastAssistant = "Assistant answer about royals.",
        )
        assertThat(q).isEqualTo("Tell me more about this New topic: quantum dots")
    }

    @Test
    fun `blank prior user entries are skipped`() {
        val q = AnaphoraAnchor.buildQuery(
            message = "Tell me more about this",
            priorUser = listOf("First topic", "   "),
            lastAssistant = "Assistant answer.",
        )
        assertThat(q).isEqualTo("Tell me more about this First topic")
    }

    @Test
    fun `long anchor truncates to 200 chars`() {
        val longTopic = "x".repeat(500)
        val q = AnaphoraAnchor.buildQuery(
            message = "Quien es su hermanastro?",
            priorUser = listOf(longTopic),
            lastAssistant = null,
        )
        assertThat(q).isEqualTo("Quien es su hermanastro? ${"x".repeat(200)}")
        assertThat(q.length).isEqualTo("Quien es su hermanastro? ".length + 200)
    }

    @Test
    fun `anchor already contained in message returns raw`() {
        val q = AnaphoraAnchor.buildQuery(
            message = "Háblame de la familia real, su historia?",
            priorUser = listOf("la familia real"),
            lastAssistant = null,
        )
        assertThat(q).isEqualTo("Háblame de la familia real, su historia?")
    }

    @Test
    fun `anchor equals message returns raw`() {
        val msg = "Háblame de esto"
        val q = AnaphoraAnchor.buildQuery(
            message = msg,
            priorUser = listOf(msg),
            lastAssistant = null,
        )
        assertThat(q).isEqualTo(msg)
    }

    @Test
    fun `dedupe is case-insensitive`() {
        val q = AnaphoraAnchor.buildQuery(
            message = "Cuéntame de LA FAMILIA REAL, su origen?",
            priorUser = listOf("la familia real"),
            lastAssistant = null,
        )
        assertThat(q).isEqualTo("Cuéntame de LA FAMILIA REAL, su origen?")
    }

    @Test
    fun `diacritic-insensitive su fires`() {
        val q = AnaphoraAnchor.buildQuery(
            message = "Quién es sú hermanastro?",
            priorUser = listOf("La familia real"),
            lastAssistant = null,
        )
        assertThat(q).isEqualTo("Quién es sú hermanastro? La familia real")
    }

    @Test
    fun `quien with accent is tokenized like quien`() {
        // `quién` alone is not an anaphora token — the anchor fires on `su`.
        val raw = AnaphoraAnchor.buildQuery(
            message = "¿Quién ganó ayer?",
            priorUser = listOf("La familia real"),
            lastAssistant = null,
        )
        assertThat(raw).isEqualTo("¿Quién ganó ayer?")
    }

    @Test
    fun `ES spot checks anchor`() {
        val cases = mapOf(
            "¿Quién es él?" to true,
            "Háblame de eso" to true,
            "¿Qué dijo ella ayer?" to true,
            "¿Qué le dijo ayer?" to true,
        )
        for ((msg, anchored) in cases) {
            val q = AnaphoraAnchor.buildQuery(msg, listOf("La familia real"), null)
            if (anchored) {
                assertThat(q).isEqualTo("$msg La familia real")
            } else {
                assertThat(q).isEqualTo(msg)
            }
        }
    }

    @Test
    fun `EN spot checks anchor`() {
        val anchored = listOf(
            "Tell me more about this",
            "Who are they?",
            "What did she say?",
        )
        for (msg in anchored) {
            assertThat(AnaphoraAnchor.buildQuery(msg, listOf("Quantum dots"), null))
                .isEqualTo("$msg Quantum dots")
        }
    }

    @Test
    fun `substring does not fire`() {
        // `thesis` contains `his`/`he`-like substrings but no whole-token hit.
        val q = AnaphoraAnchor.buildQuery(
            message = "Format my thesis",
            priorUser = listOf("La familia real"),
            lastAssistant = null,
        )
        assertThat(q).isEqualTo("Format my thesis")
    }

    @Test
    fun `empty message returns raw`() {
        assertThat(AnaphoraAnchor.buildQuery("", listOf("Topic"), "Answer")).isEmpty()
    }
}
