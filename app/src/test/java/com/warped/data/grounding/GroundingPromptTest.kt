package com.warped.data.grounding

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Phase 50 (WEB-04): prompt augmentation order verification.
 */
class GroundingPromptTest {

    @Test
    fun `system prompt never asks the user for links`() {
        // Quick-task (always-search): the no-URL sentence competed with
        // the web_search tool hint — the model asked for links instead of
        // calling tools. The prompt must direct tool-capable turns to the
        // provided sources / web_search instead.
        assertThat(GroundingPrompt.SYSTEM_PROMPT).doesNotContain("paste a link")
    }

    @Test
    fun `system prompt directs armed turns to sources and web_search`() {
        assertThat(GroundingPrompt.SYSTEM_PROMPT)
            .contains("Answer with the provided sources")
        assertThat(GroundingPrompt.SYSTEM_PROMPT).contains("web_search")
    }

    @Test
    fun `system prompt never invents urls`() {
        assertThat(GroundingPrompt.SYSTEM_PROMPT).contains("Never invent URLs")
    }

    @Test
    fun `system prompt carries no generic same-language sentence`() {
        // Explicit per-turn directive (languageDirective, last line of the
        // augmentation) replaces the probabilistic generic rule — the
        // generic sentence must be gone, not duplicated.
        assertThat(GroundingPrompt.SYSTEM_PROMPT).doesNotContain("same language")
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
        // Fixture is English ("hello" — probed en=1.000, no markers, no
        // function words) so the fail-open English directive applies.
        // (Quick-task langdetect-library: the old "pregunta" fixture now —
        // correctly — yields the SPANISH directive, since tildeless
        // "pregunta" scores es=1.000; the assembly contract this test pins
        // is unchanged, only the fixture language needed to stay English
        // for the expectation to hold. "question" was tried and rejected:
        // it scores es=0.999996 — a single-word qu+tion artifact — so it can
        // never serve as an English fixture under library-first.)
        val out = GroundingPrompt.augment("hello", null, groundingEnabled = true)

        assertThat(out).isEqualTo("${GroundingPrompt.SYSTEM_PROMPT}\n\nhello\n\nReply in English, even if the sources are in another language.")
        assertThat(out.indexOf(GroundingPrompt.SYSTEM_PROMPT)).isEqualTo(0)
        assertThat(out.indexOf("hello")).isGreaterThan(0)
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

    // ------------------------------------------------------------------
    // Explicit language directive (EN+ES): detection, selection, assembly.
    // ------------------------------------------------------------------

    @Test
    fun `isSpanish detects inverted marks`() {
        assertThat(GroundingPrompt.isSpanish("¿Cómo estás?")).isTrue()
        assertThat(GroundingPrompt.isSpanish("¡Hola!")).isTrue()
    }

    @Test
    fun `isSpanish detects lowercase accents and ene`() {
        assertThat(GroundingPrompt.isSpanish("está aquí")).isTrue()
        assertThat(GroundingPrompt.isSpanish("niño")).isTrue()
        assertThat(GroundingPrompt.isSpanish("pingüino")).isTrue()
    }

    @Test
    fun `isSpanish detects uppercase markers case-insensitively`() {
        assertThat(GroundingPrompt.isSpanish("ÁRBOL")).isTrue()
        assertThat(GroundingPrompt.isSpanish("NIÑO")).isTrue()
        assertThat(GroundingPrompt.isSpanish("¿QUÉ?")).isTrue()
    }

    @Test
    fun `isSpanish rejects plain english`() {
        assertThat(GroundingPrompt.isSpanish("What is the capital of France?")).isFalse()
        assertThat(GroundingPrompt.isSpanish("hello world")).isFalse()
    }

    @Test
    fun `isSpanish marker presence decides mixed text`() {
        assertThat(GroundingPrompt.isSpanish("What is el niño?")).isTrue()
    }

    @Test
    fun `isSpanish rejects empty and ascii punctuation`() {
        assertThat(GroundingPrompt.isSpanish("")).isFalse()
        assertThat(GroundingPrompt.isSpanish("Really?")).isFalse()
        assertThat(GroundingPrompt.isSpanish("Wow!")).isFalse()
    }

    @Test
    fun `languageDirective selects the exact directive`() {
        assertThat(GroundingPrompt.languageDirective("¿Cómo estás?"))
            .isEqualTo("Responde en español, aunque las fuentes estén en inglés.")
        assertThat(GroundingPrompt.languageDirective("What is the capital of France?"))
            .isEqualTo("Reply in English, even if the sources are in another language.")
        assertThat(GroundingPrompt.languageDirective("")).isEqualTo("Reply in English, even if the sources are in another language.")
    }

    @Test
    fun `augment appends the directive exactly once as the last line - block path`() {
        val block = GroundingPrompt.buildBlock("https://a.com", "some english source text")
        val out = GroundingPrompt.augment("¿Qué dice la fuente?", block, groundingEnabled = true)

        assertThat(countOccurrences(out, "Responde en español, aunque las fuentes estén en inglés.")).isEqualTo(1)
        assertThat(out).doesNotContain("Reply in English, even if the sources are in another language.")
        assertThat(out.endsWith("Responde en español, aunque las fuentes estén en inglés.")).isTrue()
        assertThat(out.indexOf("¿Qué dice la fuente?") < out.lastIndexOf("Responde en español, aunque las fuentes estén en inglés.")).isTrue()
    }

    @Test
    fun `augment appends the directive exactly once as the last line - no-block path`() {
        val out = GroundingPrompt.augment("What is the capital of France?", null, groundingEnabled = true)

        assertThat(countOccurrences(out, "Reply in English, even if the sources are in another language.")).isEqualTo(1)
        assertThat(out).doesNotContain("Responde en español, aunque las fuentes estén en inglés.")
        assertThat(out.endsWith("Reply in English, even if the sources are in another language.")).isTrue()
        assertThat(out.indexOf("What is the capital of France?") < out.lastIndexOf("Reply in English, even if the sources are in another language.")).isTrue()
    }

    @Test
    fun `augment derives the directive from the original text not the block`() {
        val englishBlock = GroundingPrompt.buildBlock("https://a.com", "english source text")
        val spanishBlock = GroundingPrompt.buildBlock("https://a.com", "texto con ñ y acentos está aquí")

        val esQuestionEnBlock = GroundingPrompt.augment("¿Qué dice?", englishBlock, groundingEnabled = true)
        assertThat(esQuestionEnBlock.endsWith("Responde en español, aunque las fuentes estén en inglés.")).isTrue()

        val enQuestionEsBlock = GroundingPrompt.augment("What does it say?", spanishBlock, groundingEnabled = true)
        assertThat(enQuestionEsBlock.endsWith("Reply in English, even if the sources are in another language.")).isTrue()
    }

    @Test
    fun `augment disabled returns original untouched with no directive`() {
        val out = GroundingPrompt.augment("¿Cómo estás?", null, groundingEnabled = false)

        assertThat(out).isEqualTo("¿Cómo estás?")
        assertThat(out).doesNotContain("Responde en español, aunque las fuentes estén en inglés.")
        assertThat(out).doesNotContain("Reply in English, even if the sources are in another language.")
    }

    @Test
    fun `grounded outputs carry no generic same-language sentence`() {
        val block = GroundingPrompt.buildBlock("https://a.com", "text")
        for (out in listOf(
            GroundingPrompt.augment("hello", block, groundingEnabled = true),
            GroundingPrompt.augment("hello", null, groundingEnabled = true),
        )) {
            assertThat(out).doesNotContain("same language")
        }
    }

    @Test
    fun `directives explicitly override source-language mirroring`() {
        assertThat(GroundingPrompt.SPANISH_DIRECTIVE)
            .contains("aunque las fuentes estén en inglés")
        assertThat(GroundingPrompt.ENGLISH_DIRECTIVE)
            .contains("even if the sources are in another language")
    }

    private fun countOccurrences(haystack: String, needle: String): Int {
        var count = 0
        var from = 0
        while (true) {
            val idx = haystack.indexOf(needle, from)
            if (idx < 0) return count
            count++
            from = idx + needle.length
        }
    }

    // ------------------------------------------------------------------
    // Quick-task (langdetect-library): library-first decider with regex
    // fallback. The detector is warmed synchronously so JVM tests pin
    // behavior deterministically (production warms on a daemon thread).
    // ------------------------------------------------------------------

    @BeforeEach
    fun warmDetectorBlocking() {
        LanguageDetectorHolder.resetForTest()
        LanguageDetectorHolder.ensureLoadedBlocking()
    }

    @AfterEach
    fun resetDetector() {
        LanguageDetectorHolder.resetForTest()
    }

    @Test
    fun `library detects tildeless spanish`() {
        // "hola quien eres" / "gracias por tu ayuda" / "donde esta la
        // biblioteca" carry zero markers (verified: no SPANISH_MARKERS match)
        // and score es=1.000 — TRUE comes from the library (layer 1).
        // "que es hollow knight" scores en=1.000 (probed — proper-noun
        // n-grams dominate), so TRUE comes from the function-word layer
        // (layer 3: "que" + "es" as standalone tokens).
        assertThat(GroundingPrompt.isSpanish("hola quien eres")).isTrue()
        assertThat(GroundingPrompt.isSpanish("que es hollow knight")).isTrue()
        assertThat(GroundingPrompt.isSpanish("gracias por tu ayuda")).isTrue()
        assertThat(GroundingPrompt.isSpanish("donde esta la biblioteca")).isTrue()
    }

    @Test
    fun `function-word layer ignores english lookalikes`() {
        // Curated exclusions: "dime" (coin), "favor" ("do me a favor") must
        // NOT trigger Spanish; the library reads both as confident English
        // and neither is in SPANISH_FUNCTION_WORDS.
        assertThat(GroundingPrompt.isSpanish("Flip a dime")).isFalse()
        assertThat(GroundingPrompt.isSpanish("Do me a favor")).isFalse()
        assertThat(GroundingPrompt.isSpanish("Is this the best theme?")).isFalse()
    }

    @Test
    fun `library keeps english english`() {
        assertThat(GroundingPrompt.isSpanish("What is the capital of France?")).isFalse()
        assertThat(GroundingPrompt.isSpanish("hello world")).isFalse()
        assertThat(GroundingPrompt.isSpanish("Can you help me debug this crash?")).isFalse()
    }

    @Test
    fun `short strings pin the fail-open contract`() {
        // Probed against the short-text profiles: "hola" → es=0.994 and
        // "si" → es=0.996, both above the 0.5 threshold → TRUE from the
        // library (layer 1). The contract pinned here is the observable
        // outcome, whatever layer produces it.
        assertThat(GroundingPrompt.isSpanish("hola")).isTrue()
        assertThat(GroundingPrompt.isSpanish("si")).isTrue()
    }

    @Test
    fun `empty and whitespace stay english`() {
        assertThat(GroundingPrompt.isSpanish("")).isFalse()
        assertThat(GroundingPrompt.isSpanish("   ")).isFalse()
    }

    @Test
    fun `library never overrides marker presence`() {
        // Mostly-English text with a Spanish marker keeps today's outcome
        // even if the n-gram model reads it as confident English: markers
        // stay sufficient for Spanish.
        assertThat(GroundingPrompt.isSpanish("What is el niño?")).isTrue()
    }

    @Test
    fun `detector throw falls back to the regex`() {
        LanguageDetectorHolder.detectorFactory = { throw IllegalStateException("boom") }
        LanguageDetectorHolder.ensureLoadedBlocking()

        assertThat(LanguageDetectorHolder.detectSpanish("niño")).isNull()
        // Layer 2 (markers) proves the regex decides with the detector dead…
        assertThat(GroundingPrompt.isSpanish("niño")).isTrue()
        assertThat(GroundingPrompt.isSpanish("hello")).isFalse()
        // …and layer 3 keeps tildeless recall alive without the library:
        // "hola"+"quien" are function-word tokens, so TRUE even on throw.
        assertThat(GroundingPrompt.isSpanish("hola quien eres")).isTrue()
    }

    @Test
    fun `detector not ready falls back to the regex`() {
        // Simulates a cold start where the detector is unavailable (warm
        // thread still loading, or init failed): NULL → regex decides.
        // A null-returning factory is deterministic — no timing dependence.
        LanguageDetectorHolder.detectorFactory = { null }
        LanguageDetectorHolder.ensureLoadedBlocking()

        assertThat(LanguageDetectorHolder.detectSpanish("¿Cómo estás?")).isNull()
        assertThat(GroundingPrompt.isSpanish("¿Cómo estás?")).isTrue()
        assertThat(GroundingPrompt.isSpanish("hello")).isFalse()
    }
}
