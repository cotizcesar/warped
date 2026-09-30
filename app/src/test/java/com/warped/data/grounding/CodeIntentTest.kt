package com.warped.data.grounding

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Quick-task (code-intent-gate): truth table for the code-generation gate.
 * TRUE = code turn, skips the pre-search. FALSE = search as today.
 */
class CodeIntentTest {

    @ParameterizedTest(name = "code (no search): \"{0}\"")
    @ValueSource(
        strings = [
            // Verbatim on-device evidence: doubled-s typo in javascript.
            "Dame un ejemplo de codigo simple en jsavascript",
            "write a quicksort in python",
            "escribe una funcion en javascript",
            "genera una clase en kotlin",
            "corrige este bug",
            "depura mi script python",
            "implementa un programa en java",
            "explica este codigo",
            "create a class in typescript",
            "debug my ruby script",
            // Diacritic-insensitive: código/función/método normalize.
            "escribe una función en javascript",
            "corrige este método",
            "dame un ejemplo de código",
            // Case/punctuation-insensitive.
            "WRITE A QUICKSORT IN PYTHON",
            "Escribe una funcion, por favor!",
            "¿genera una clase en kotlin?",
        ],
    )
    fun `code turns skip the search`(query: String) {
        assertThat(CodeIntent.isCodeTurn(query)).isTrue()
    }

    @ParameterizedTest(name = "non-code (search): \"{0}\"")
    @ValueSource(
        strings = [
            // Language alone, no verb/noun arm — versioned-facts query.
            "Kotlin 2.3 new features",
            // Verb alone, no code noun or language.
            "write an email",
            // Factual — unchanged behavior.
            "que es la fotosintesis",
            "what is the capital of France",
            // Social — NeedsWeb's job; CodeIntent reports no code intent.
            "hola",
            // Fail-open: empty searches as today.
            "",
            "   ",
            // Whole-token contract: plural does not match the singular noun.
            "codigos",
            // Verb-adjacent factual: "explicame" != "explica" (whole-token).
            "explícame la fotosíntesis",
        ],
    )
    fun `factual and empty queries still search`(query: String) {
        assertThat(CodeIntent.isCodeTurn(query)).isFalse()
    }

    @Test
    fun `codigos never matches the codigo keyword`() {
        // Word-boundary contract: substring matching would fire on
        // "codigos" — token matching must not.
        assertThat(CodeIntent.isCodeTurn("codigos")).isFalse()
        assertThat(CodeIntent.isCodeTurn("codigo")).isFalse()
        assertThat(CodeIntent.isCodeTurn("explica este codigo")).isTrue()
    }

    @Test
    fun `language alone searches while verb plus language skips`() {
        // Conjunction guard: bare language names keep searching (the model
        // keeps its loop escape hatch for versioned facts), while
        // verb+language implies code intent even without a listed noun.
        assertThat(CodeIntent.isCodeTurn("Kotlin 2.3 new features")).isFalse()
        assertThat(CodeIntent.isCodeTurn("write a quicksort in python")).isTrue()
        assertThat(CodeIntent.isCodeTurn("write an email")).isFalse()
    }
}
