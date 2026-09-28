package com.warped.data.grounding

import org.jsoup.Jsoup

/**
 * Phase 52 (EXTRACT-01): Jsoup parse-only HTML→text extractor with the
 * Phase 50 hand-rolled regex pipeline kept as fallback.
 *
 * Pipeline: Jsoup.parse(html, url) — parse-only, NEVER the connect()
 * network entry-point (which would bypass the stripped client, 64KB cap,
 * and timeouts enforced upstream in WebPageFetcher) — then script/style/noscript + nav/footer/aside strip,
 * title prepend, block-tag newline reconstruction BEFORE normalized text()
 * (bare body.text() collapses everything into a wall of text), line
 * normalization, MULTI_NEWLINES collapse, and line-boundary truncation with
 * the "… [truncado]" marker at the caller-provided budget.
 *
 * When Jsoup output isBlank(), the legacy regex extractor runs; only
 * blank-from-both yields empty output upstream. No network, timeout, or
 * cap parameters here — fetch policy stays frozen in WebPageFetcher.
 * Pure Kotlin — no Android imports, unit-testable on the JVM.
 */
object HtmlToTextExtractor {

    const val MAX_CHARS = 4000
    const val TRUNCATION_MARKER = "… [truncado]"

    private val SCRIPT_STYLE_NOSCRIPT = Regex(
        "(?is)<(script|style|noscript)[^>]*>.*?</\\1\\s*>"
    )
    private val COMMENTS = Regex("(?s)<!--.*?-->")
    private val TITLE = Regex("(?is)<title[^>]*>(.*?)</title\\s*>")
    private val BLOCK_TAGS = Regex("(?i)</?(p|div|h[1-6]|li|tr|br|section|article|header|footer|blockquote|pre)[^>]*>")
    private val ANY_TAG = Regex("<[^>]*>")
    private val MULTI_NEWLINES = Regex("\n{3,}")

    /**
     * Extracts readable text from [html] with the default [MAX_CHARS]
     * budget. Kept for the frozen WebPageFetcher call site.
     */
    fun extract(html: String, url: String): String = extract(html, url, MAX_CHARS)

    /**
     * Extracts readable text from [html] truncated to [budget] chars.
     * Used per page with [GroundingBudget.perPageBudget].
     *
     * [budget] is floored at the crash-preventing minimum
     * ([TRUNCATION_MARKER].length + 1) so a degenerate caller value can
     * never drive `budget - marker - 1` negative into `take(negative)`
     * (`IllegalArgumentException`) and turn a grounding turn into an
     * exception path. Production budgets floor much higher upstream
     * ([GroundingBudget.MIN_PER_PAGE]); this floor only preserves the
     * no-crash contract for direct callers.
     */
    fun extract(html: String, url: String, budget: Int): String {
        val safeBudget = budget.coerceAtLeast(TRUNCATION_MARKER.length + 1)
        val jsoupResult = extractJsoup(html, url, safeBudget)
        if (jsoupResult.isNotBlank()) return jsoupResult
        return extractLegacy(html, safeBudget)
    }

    private fun extractJsoup(html: String, url: String, budget: Int): String {
        val doc = try {
            Jsoup.parse(html, url)
        } catch (_: Exception) {
            return ""
        }
        doc.select("script, style, noscript, nav, footer, aside").remove()
        val title = doc.title().trim()
        // Rebuild block separators BEFORE reading text — Element.text()
        // normalizes whitespace runs (Pitfall 2), so wholeText() preserves
        // the prepended newlines and per-line trimming below normalizes.
        doc.select("p, div, h1, h2, h3, h4, h5, h6, li, tr, br, section, article, header, blockquote, pre")
            .prepend("\n")
        val body = doc.body()?.wholeText().orEmpty()
        val lines = body.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        var text = lines.joinToString("\n")
        if (title.isNotEmpty()) {
            text = "$title\n$text"
        }
        text = MULTI_NEWLINES.replace(text, "\n\n").trim()
        if (text.length > budget) {
            text = truncateAtLineBoundary(text, budget - TRUNCATION_MARKER.length - 1) +
                "\n" + TRUNCATION_MARKER
        }
        return text
    }

    /**
     * Phase 50 (WEB-03) hand-rolled regex extractor, unchanged logic.
     * Fallback when the Jsoup core yields blank output.
     */
    private fun extractLegacy(html: String, budget: Int): String {
        val title = TITLE.find(html)?.groupValues?.getOrNull(1)
            ?.let { decodeEntities(it).trim() }
            .orEmpty()

        var text = SCRIPT_STYLE_NOSCRIPT.replace(html, "\n")
        text = COMMENTS.replace(text, "")
        text = BLOCK_TAGS.replace(text, "\n")
        text = ANY_TAG.replace(text, "")
        text = decodeEntities(text)

        val lines = text.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        text = lines.joinToString("\n")

        if (title.isNotEmpty()) {
            text = "$title\n$text"
        }

        text = MULTI_NEWLINES.replace(text, "\n\n").trim()

        if (text.length > budget) {
            text = truncateAtLineBoundary(text, budget - TRUNCATION_MARKER.length - 1) +
                "\n" + TRUNCATION_MARKER
        }
        return text
    }

    private fun truncateAtLineBoundary(text: String, budget: Int): String {
        if (text.length <= budget) return text
        val lastNewline = text.lastIndexOf('\n', budget)
        return if (lastNewline > 0) {
            text.take(lastNewline)
        } else {
            text.take(budget)
        }
    }

    private val NAMED_ENTITIES = mapOf(
        "&amp;" to "&",
        "&lt;" to "<",
        "&gt;" to ">",
        "&quot;" to "\"",
        "&#39;" to "'",
        "&apos;" to "'",
        "&nbsp;" to " ",
    )
    private val DECIMAL_ENTITY = Regex("&#(\\d+);")
    private val HEX_ENTITY = Regex("&#x([0-9a-fA-F]+);")

    private fun decodeEntities(text: String): String {
        var result = text
        for ((entity, char) in NAMED_ENTITIES) {
            result = result.replace(entity, char)
        }
        result = DECIMAL_ENTITY.replace(result) { m ->
            val code = m.groupValues[1].toIntOrNull() ?: return@replace m.value
            codePointToString(code) ?: m.value
        }
        result = HEX_ENTITY.replace(result) { m ->
            val code = m.groupValues[1].toIntOrNull(16) ?: return@replace m.value
            codePointToString(code) ?: m.value
        }
        return result
    }

    private fun codePointToString(code: Int): String? = try {
        String(Character.toChars(code))
    } catch (_: IllegalArgumentException) {
        null
    }
}
