package com.warped.data.grounding

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Quick-task (needs-web-gate): truth table for the social/identity gate.
 * FALSE = social turn, skips the pre-search. TRUE = search as today.
 */
class NeedsWebTest {

    @ParameterizedTest(name = "social (no search): \"{0}\"")
    @ValueSource(
        strings = [
            "hola",
            "HOLA",
            "Hola!",
            "hola, quien Eres",
            "buenos días",
            "qué tal?",
            "¿cómo estás?",
            "gracias",
            "Gracias!",
            "thanks",
            "Thank you",
            "adiós",
            "bye",
            "hasta luego",
            "quien eres",
            "¿quién eres?",
            "who are you",
            "tu nombre",
            "what is your name",
            "que puedes hacer",
            "what can you do",
            "ayuda",
            "help",
            "qué modelo eres",
        ],
    )
    fun `social messages skip the search`(query: String) {
        assertThat(NeedsWeb.needsWeb(query)).isFalse()
    }

    @ParameterizedTest(name = "factual (search): \"{0}\"")
    @ValueSource(
        strings = [
            "qué es X?",
            "qué hora es",
            "quién ganó el partido",
            "explícame la fotosíntesis",
            "what is the capital of France",
            "latest news",
            "holanda",
            "gracioso",
            "helper",
            "",
            "   ",
        ],
    )
    fun `factual and empty queries still search`(query: String) {
        assertThat(NeedsWeb.needsWeb(query)).isTrue()
    }

    @Test
    fun `holanda never matches the hola keyword`() {
        // Word-boundary contract: substring matching would fire on
        // "holanda" — token matching must not.
        assertThat(NeedsWeb.needsWeb("holanda")).isTrue()
        assertThat(NeedsWeb.needsWeb("hola")).isFalse()
    }
}
