package com.warped.data.grounding

/**
 * Grounding precedence for the ChatViewModel send hook (threat T-53-05:
 * single decision function).
 *
 * Order: the per-chat tri-state override when non-null, else the global
 * default-ON. Zero Android imports — stays JVM-testable (RESEARCH
 * pattern 4).
 */
object GroundingPrecedence {

    /**
     * @param perChat per-conversation override; null means Heredar (inherit).
     * @param global global DataStore default-ON value.
     */
    fun shouldGround(perChat: Boolean?, global: Boolean): Boolean {
        if (perChat != null) return perChat
        return global
    }
}
