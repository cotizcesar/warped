package com.warped.data.grounding

/**
 * Phase 50 (WEB-03): hand-rolled HTML→text extractor, zero new dependencies.
 *
 * Strips scripts/styles/comments, maps block tags to newlines, decodes the
 * common HTML entities plus numeric references, prepends the page title, and
 * truncates to a 4000-char line-boundary budget with a "… [truncado]" marker.
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

    fun extract(html: String, url: String): String {
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

        if (text.length > MAX_CHARS) {
            text = truncateAtLineBoundary(text, MAX_CHARS - TRUNCATION_MARKER.length - 1) +
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
