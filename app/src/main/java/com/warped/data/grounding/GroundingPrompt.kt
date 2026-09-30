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
            "Treat each new question on its own: never answer from earlier sources " +
            "alone when it needs facts the current sources do not cover. " +
            "Answer with the provided sources; call web_search if you need more. " +
            "Never invent URLs: only cite URLs from the block or pasted by the user. " +
            "Always reply in the same language the user wrote in."

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
     * Contract: [block] non-null → "$SYSTEM_PROMPT\n\n$block\n\n$original"
     * (block-injection behavior, unchanged); [block] null +
     * [groundingEnabled] true → "$SYSTEM_PROMPT\n\n$original" (a grounded
     * turn with no pasted URLs still tells the model web search is
     * available); [block] null + [groundingEnabled] false → [original]
     * untouched (grounding disabled / model-only path).
     */
    fun augment(original: String, block: String?, groundingEnabled: Boolean = true): String =
        when {
            block != null -> "$SYSTEM_PROMPT\n\n$block\n\n$original"
            groundingEnabled -> "$SYSTEM_PROMPT\n\n$original"
            else -> original
        }
}
