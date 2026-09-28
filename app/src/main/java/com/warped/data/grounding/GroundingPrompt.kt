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
        "Responde usando el bloque [WEB CONTEXT] cuando sea relevante. " +
            "Cita las fuentes con marcadores [1]/[2]. " +
            "Si necesitas información fresca y no hay bloque de contexto, " +
            "pide al usuario que pegue un enlace. " +
            "Nunca inventes URLs: solo cita las URLs del bloque o las que el usuario pegó."

    fun buildBlock(url: String, text: String): String =
        "[WEB CONTEXT — fuente [1]: $url]\n$text\n[FIN WEB CONTEXT]"

    /**
     * Phase 52 (FETCH-01): numbered fusion of N pages in paste order.
     * [pages] is a list of url-to-text pairs; block i cites URL i so
     * Fuentes order == block order.
     */
    fun buildFusedBlock(pages: List<Pair<String, String>>): String =
        pages.mapIndexed { i, (url, text) ->
            "[WEB CONTEXT ${i + 1} — fuente [${i + 1}]: $url]\n$text\n[FIN WEB CONTEXT ${i + 1}]"
        }.joinToString("\n\n")

    /**
     * Contract: [block] non-null → "$SYSTEM_PROMPT\n\n$block\n\n$original"
     * ([WEB CONTEXT] behavior, unchanged); [block] null +
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
