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
     * Returns "$SYSTEM_PROMPT\n\n$block\n\n$original" when [block] is
     * non-null, [original] untouched otherwise (toggle OFF / model-only path).
     */
    fun augment(original: String, block: String?): String =
        if (block != null) "$SYSTEM_PROMPT\n\n$block\n\n$original" else original
}
