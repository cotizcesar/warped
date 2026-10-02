package com.warped.data.grounding

/**
 * Phase 70 (70-01): bounded plain-text gate + truncation helpers for the
 * document-reader tool (TOOL-01 tracer).
 *
 * Pure Kotlin — no Android imports, JVM-testable. Operates on an
 * already-read [String] plus sizes, so the `ContentResolver` read itself
 * lives in `ChatViewModel.attachDocument` (Plan 02); this object owns the
 * policy (gate, bound, cap) that both the local loop and the remote
 * executors share.
 *
 * Threat coverage (see plan threat model):
 * - T-70-01 (prompt injection): gating here is format-only; content
 *   sanitization lives in [DocumentPrompt.sanitize] (WebContextSanitizer
 *   precedent) and runs before fusion.
 * - T-70-02 (memory exhaustion): [capFor] reuses
 *   [GroundingBudget.perPageBudget] — no new budget constants — and the
 *   caller pre-checks size and reads at most cap + 1 byte.
 */
object DocumentReader {

    /** Bounded read outcome: [Ok] carries the usable text, the rest degrade. */
    sealed interface DocumentRead {
        /** Usable text; [truncatedAt] is the cap N when over-cap, null when whole. */
        data class Ok(val text: String, val truncatedAt: Int?) : DocumentRead
        /** Non-text pick (pdf/docx/octet-stream/…) — graceful unsupported path. */
        data object Unsupported : DocumentRead
        /** Nothing decodable — graceful failed-read path. */
        data object Empty : DocumentRead
    }

    /**
     * Format gate: true only for the plain-text family (`text/plain`,
     * `text/markdown`, any other text-prefixed mime) or — when SAF supplies
     * `.txt`/`.md` extension fallback. Everything else (application/pdf,
     * docx, octet-stream) gates out to [DocumentRead.Unsupported].
     * Mime (when present) wins over the extension; params (`; charset=…`)
     * are stripped before matching.
     */
    fun gate(mime: String?, filename: String): Boolean {
        val clean = mime?.trim()?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        if (clean.isNotEmpty()) {
            return clean == "text/plain" || clean == "text/markdown" || clean.startsWith("text/")
        }
        val lower = filename.lowercase()
        return lower.endsWith(".txt") || lower.endsWith(".md")
    }

    /**
     * Applies the cap: text within [cap] passes through untouched
     * (marker null); over-cap text is cut at exactly [cap] chars with the
     * marker N == cap so the envelope can state the bounds explicitly.
     */
    fun bound(text: String, cap: Int): Pair<String, Int?> =
        if (text.length <= cap) text to null else text.take(cap) to cap

    /**
     * The document size cap IS the per-page grounding budget for a single
     * page (one document per turn, CONTEXT-locked) — no new constants.
     */
    fun capFor(contextSize: Int): Int = GroundingBudget.perPageBudget(contextSize, 1)
}
