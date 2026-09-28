package com.warped.data.grounding

/**
 * Phase 50 (WEB-05): trust-boundary sanitizer for fetched web content.
 *
 * Drops instruction-like lines (prompt-injection hijack patterns, ES + EN)
 * at line granularity and escapes delimiter collisions so fetched content
 * can never break out of the [WEB CONTEXT] block or hijack the model.
 * Markdown-aware: `[text](target)` link targets allow only http(s) (and
 * protocol-relative `//` as https); javascript:/data:/vbscript: targets —
 * including case/whitespace/control-char padded variants — are stripped to
 * bare `[text]`. Relative targets are kept as harmless text. Delimiter
 * escaping applies to the full string AFTER link neutralization. No fence
 * exemption: code-fence and quote lines are scanned like every other line.
 * Pure Kotlin — no Android imports, unit-testable on the JVM.
 */
object WebContextSanitizer {

    private val HIJACK_PATTERNS = listOf(
        Regex("(?i)^(ignore|olvida|disregard).{0,40}(previous|anterior|ant\\/eriores|instructions|instrucciones)"),
        Regex("(?i)you are now|eres ahora|actúa como|actua como"),
        Regex("(?i)^system\\s*:"),
        Regex("(?i)as an ai|como ia|como una ia"),
    )

    private val MARKDOWN_LINK = Regex("\\[([^\\[\\]]*)\\]\\(([^()]*)\\)")

    private val DANGEROUS_SCHEMES = listOf(
        Regex("(?i)^[\\s\\u0000-\\u0020]*javascript\\s*:"),
        Regex("(?i)^[\\s\\u0000-\\u0020]*data\\s*:"),
        Regex("(?i)^[\\s\\u0000-\\u0020]*vbscript\\s*:"),
    )

    fun sanitize(text: String): String {
        val neutralized = MARKDOWN_LINK.replace(text) { m ->
            val label = m.groupValues[1]
            val rawTarget = m.groupValues[2].trim()
            val target = rawTarget.trim { it <= ' ' || it.isWhitespace() }.trim('\u0000', '\u200B', '\uFEFF')
            when {
                target.startsWith("//") -> "[$label](https:$target)"
                target.startsWith("http://", ignoreCase = true) ||
                    target.startsWith("https://", ignoreCase = true) -> m.value
                DANGEROUS_SCHEMES.any { it.containsMatchIn(target) } -> "[$label]"
                else -> m.value
            }
        }
        val kept = neutralized.split("\n").filter { line ->
            val trimmed = line.trim()
            HIJACK_PATTERNS.none { it.containsMatchIn(trimmed) }
        }
        return kept.joinToString("\n")
            .replace("[WEB CONTEXT", "[WEB-CONTEXT")
            .replace("[FIN WEB CONTEXT", "[FIN-WEB-CONTEXT")
            .replace("[END WEB CONTEXT", "[END-WEB-CONTEXT")
    }
}
