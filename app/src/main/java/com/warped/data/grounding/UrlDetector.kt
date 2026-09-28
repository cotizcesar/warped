package com.warped.data.grounding

/**
 * Phase 50 (WEB-01): deterministic first-URL heuristic.
 *
 * The first http(s) URL in the message triggers exactly one fetch;
 * second and later URLs are ignored (no multi-fetch agent loop).
 * Pure Kotlin — no Android imports, unit-testable on the JVM.
 */
object UrlDetector {

    private val URL_REGEX = Regex("""https?://[^\s<>"')\]]+""")

    /**
     * Returns the first http(s) URL in [text], with trailing sentence
     * punctuation (.,;:!?) trimmed, or null when there is no match.
     */
    fun firstUrl(text: String): String? {
        val match = URL_REGEX.find(text) ?: return null
        val trimmed = match.value.trimEnd('.', ',', ';', ':', '!', '?')
        return trimmed.ifEmpty { null }
    }
}
