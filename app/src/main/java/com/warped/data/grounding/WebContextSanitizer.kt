package com.warped.data.grounding

/**
 * Phase 50 (WEB-05): trust-boundary sanitizer for fetched web content.
 *
 * Drops instruction-like lines (prompt-injection hijack patterns, ES + EN)
 * at line granularity and escapes delimiter collisions so fetched content
 * can never break out of the [WEB CONTEXT] block or hijack the model.
 * Pure Kotlin — no Android imports, unit-testable on the JVM.
 */
object WebContextSanitizer {

    private val HIJACK_PATTERNS = listOf(
        Regex("(?i)^(ignore|olvida|disregard).{0,40}(previous|anterior|ant\\/eriores|instructions|instrucciones)"),
        Regex("(?i)you are now|eres ahora|actúa como|actua como"),
        Regex("(?i)^system\\s*:"),
        Regex("(?i)as an ai|como ia|como una ia"),
    )

    fun sanitize(text: String): String {
        val kept = text.split("\n").filter { line ->
            val trimmed = line.trim()
            HIJACK_PATTERNS.none { it.containsMatchIn(trimmed) }
        }
        return kept.joinToString("\n")
            .replace("[WEB CONTEXT", "[WEB-CONTEXT")
            .replace("[FIN WEB CONTEXT", "[FIN-WEB-CONTEXT")
    }
}
