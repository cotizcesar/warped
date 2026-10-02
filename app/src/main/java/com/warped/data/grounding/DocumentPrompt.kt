package com.warped.data.grounding

/**
 * Phase 70 (70-01): `[DOCUMENT CONTEXT]` fusion for the document-reader
 * tool (TOOL-01 tracer).
 *
 * Mirrors the `[WEB CONTEXT]` pipeline conventions: [buildBlock] is the
 * document analog of `GroundingPrompt.buildFusedBlock` (explicit
 * header/footer with filename + truncation bounds so the model always
 * knows the content limits); [augmentWithDocument] follows the exact
 * `GroundingPrompt.augment` order (system prompt → block → original →
 * language directive of the ORIGINAL user text).
 *
 * Document text is untrusted (T-70-01): [sanitize] strips hijack lines
 * with the `WebContextSanitizer` patterns verbatim and escapes document
 * delimiter collisions so pasted content can never break out of the
 * block. Pure Kotlin — no Android imports, JVM-testable.
 */
object DocumentPrompt {

    /**
     * Envelope: `--- Document: {filename} ---` header, text, then
     * `--- End of document ---` footer with ` (truncated at N chars)`
     * appended when [truncatedAt] is non-null — never silent truncation.
     */
    fun buildBlock(filename: String, text: String, truncatedAt: Int?): String {
        val footer = if (truncatedAt != null) {
            "--- End of document --- (truncated at $truncatedAt chars)"
        } else {
            "--- End of document ---"
        }
        return "--- Document: $filename ---\n$text\n$footer"
    }

    // Hijack-line patterns reused VERBATIM from WebContextSanitizer
    // (document content is equally untrusted — ES + EN instruction-like
    // lines are dropped at line granularity, quote-nesting stripped first).
    private val HIJACK_PATTERNS = listOf(
        Regex("(?i)^(ignore|olvida|disregard).{0,40}(previous|anterior|ant\\/eriores|instructions|instrucciones)"),
        Regex("(?i)you are now|eres ahora|actúa como|actua como"),
        Regex("(?i)^system\\s*:"),
        Regex("(?i)as an ai|como ia|como una ia"),
    )

    /**
     * Strips hijack lines, then escapes document delimiter collisions so a
     * pasted `--- Document: [` / `--- End of document ---` /
     * `[DOCUMENT CONTEXT` can never forge block boundaries (mirrors the
     * WebContextSanitizer lines 56-60 escaping shape).
     */
    fun sanitize(text: String): String {
        val kept = text.split("\n").filter { line ->
            val trimmed = line.trim()
            val unquoted = trimmed.replace(Regex("^(>\\s*)+"), "")
            HIJACK_PATTERNS.none { it.containsMatchIn(trimmed) || it.containsMatchIn(unquoted) }
        }
        return kept.joinToString("\n")
            .replace("[DOCUMENT CONTEXT", "[DOCUMENT-CONTEXT")
            .replace("--- Document: [", "--- Document:-[")
            .replace("--- End of document ---", "--- End-of-document ---")
    }

    /**
     * Fuses a sanitized document block into the turn — same order as
     * `GroundingPrompt.augment`: system prompt, block, original, then the
     * language directive of the ORIGINAL text (never the block).
     */
    fun augmentWithDocument(original: String, block: String): String =
        "${GroundingPrompt.SYSTEM_PROMPT}\n\n$block\n\n$original\n\n${GroundingPrompt.languageDirective(original)}"
}
