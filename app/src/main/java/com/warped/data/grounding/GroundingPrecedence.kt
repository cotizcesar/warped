package com.warped.data.grounding

/**
 * Phase 53 (TOGGLE-02): pure-Kotlin grounding skip precedence for the
 * ChatViewModel send hook (threat T-53-05: single decision function).
 *
 * Order: one-off Sin web chip first, then the per-chat tri-state override
 * when non-null, else the global default-ON. Zero Android imports — stays
 * JVM-testable (RESEARCH pattern 4).
 */
object GroundingPrecedence {

    /**
     * @param skipOnce one-shot composer "Sin web" flag for this send only.
     * @param perChat per-conversation override; null means Heredar (inherit).
     * @param global global DataStore default-ON value.
     */
    fun shouldGround(skipOnce: Boolean, perChat: Boolean?, global: Boolean): Boolean {
        if (skipOnce) return false
        if (perChat != null) return perChat
        return global
    }
}
