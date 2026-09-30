package com.warped.data.grounding

/**
 * Phase 50 (WEB-04): current-turn prompt augmentation for web grounding.
 *
 * Grounding is a prefix augmentation (system prompt → block → original)
 * applied to the current user turn only. Pure Kotlin — no Android imports,
 * unit-testable on the JVM.
 */
object GroundingPrompt {

    const val SYSTEM_PROMPT =
        "Answer using the sources below when relevant. " +
            "Cite sources with [1]/[2] markers. " +
            "Resolve pronouns and references (he/she/it/this/that, él/ella/su/eso/este, and names) against the conversation history first, and use the resolved names when searching and answering. " +
            "Cite only sources fetched for the current answer; never reuse citation numbers from earlier turns. " +
            "Answer with the provided sources; call web_search if you need more. " +
            "Never invent URLs: only cite URLs from the block or pasted by the user."

    /** Locked high-precision ES markers: ¿ ¡ á é í ó ú ñ ü (case-insensitive). */
    private val SPANISH_MARKERS = Regex("[¿¡áéíóúñü]", RegexOption.IGNORE_CASE)

    /**
     * Tildeless-Spanish function words, consulted only when the library is
     * NOT confident-Spanish and no markers are present. Every entry must be
     * overwhelmingly Spanish-indicative as a STANDALONE token — deliberately
     * EXCLUDED lookalikes: "dime" (English coin), "favor" ("do me a favor"),
     * "si" (SI units; the library already scores standalone "si" es=0.996
     * anyway). Matching is whole-token, case-insensitive.
     */
    private val SPANISH_FUNCTION_WORDS = setOf(
        "que", "es", "hola", "quien", "eres", "esta", "estas",
        "porque", "como", "donde", "cuando", "gracias",
        "estoy", "tienes", "quieres", "quiero", "puedes",
        "cual", "hora", "biblioteca", "ayuda",
    )
    private val WORD_TOKENS = Regex("\\p{L}+")

    const val SPANISH_DIRECTIVE = "Responde en español, aunque las fuentes estén en inglés."
    const val ENGLISH_DIRECTIVE = "Reply in English, even if the sources are in another language."

    fun isSpanish(text: String): Boolean {
        if (text.isEmpty()) return false
        // Layer 1 — library: a confident TRUE decides Spanish on its own
        // (fixes pure tildeless recall — those strings carry no markers, so
        // TRUE here can only come from the library). A library FALSE/NULL
        // never overrides the layers below: marker presence stays sufficient
        // for Spanish (pinned contract — "What is el niño?" must remain true
        // even though the mostly-English text scores en=1.000).
        if (LanguageDetectorHolder.detectSpanish(text) == true) return true
        // Layer 2 — diacritic/inverted markers (unchanged legacy behavior).
        if (SPANISH_MARKERS.containsMatchIn(text)) return true
        // Layer 3 — tildeless function words. Required by the plan's test
        // table: mixed queries like "que es hollow knight" score en=1.000
        // (probed — "hollow knight" dominates the n-grams), so NO threshold
        // trickery can catch them without also flipping pure-English
        // controls that score an identical en=1.000. The word list catches
        // exactly these; empty/uncertain still falls through to false
        // (fail-open English default preserved).
        return WORD_TOKENS.findAll(text).any { it.value.lowercase() in SPANISH_FUNCTION_WORDS }
    }

    fun languageDirective(text: String): String =
        if (isSpanish(text)) SPANISH_DIRECTIVE else ENGLISH_DIRECTIVE

    fun buildBlock(url: String, text: String): String =
        "--- Source [1]: $url ---\n$text\n--- End of sources ---"

    /**
     * Phase 52 (FETCH-01): numbered fusion of N pages in paste order.
     * [pages] is a list of url-to-text pairs; block i cites URL i so
     * Fuentes order == block order.
     */
    fun buildFusedBlock(pages: List<Pair<String, String>>): String =
        (pages.mapIndexed { i, (url, text) ->
            "--- Source [${i + 1}]: $url ---\n$text"
        } + "--- End of sources ---").joinToString("\n\n")

    /**
     * Contract: [block] non-null → "$SYSTEM_PROMPT\n\n$block\n\n$original\n\n$directive"
     * (block-injection behavior, unchanged); [block] null +
     * [groundingEnabled] true → "$SYSTEM_PROMPT\n\n$original\n\n$directive" (a grounded
     * turn with no pasted URLs still tells the model web search is
     * available); [block] null + [groundingEnabled] false → [original]
     * untouched (grounding disabled / model-only path).
     *
     * The explicit language directive ([languageDirective] of the ORIGINAL
     * user text — never the block) is the last line of every grounded turn.
     */
    fun augment(original: String, block: String?, groundingEnabled: Boolean = true): String =
        when {
            block != null -> "$SYSTEM_PROMPT\n\n$block\n\n$original\n\n${languageDirective(original)}"
            groundingEnabled -> "$SYSTEM_PROMPT\n\n$original\n\n${languageDirective(original)}"
            else -> original
        }
}
