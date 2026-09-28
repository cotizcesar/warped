package com.warped.data.grounding

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * WebFetch parity (quick task 20260928): Jsoup parse-only HTML→markdown
 * converter, zero new dependencies (no Turndown on JVM).
 *
 * Mirrors OpenCode `webfetch.ts` converter rules: ATX headings, fenced code,
 * `-` bullets, strips script/style/meta/link (plus a small superset of
 * layout/chrome tags). Text-mode parity: script/style/noscript/iframe/object/
 * embed content never leaks into output.
 *
 * Pipeline: Jsoup.parse(html, url) — parse-only, NEVER connect() — strip
 * chrome tags, walk the body tree emitting markdown blocks, then normalize
 * lines (trim/drop-blank, collapse 3+ newlines) and truncate at a line
 * boundary with the shared "… [truncated]" marker at the caller [budget].
 *
 * Returns "" when nothing usable so the caller falls back to
 * [HtmlToTextExtractor]. Pure Kotlin — no Android imports, JVM unit-testable.
 */
object HtmlToMarkdown {

    fun convert(html: String, url: String, budget: Int): String {
        if (html.isBlank()) return ""
        val safeBudget = budget.coerceAtLeast(HtmlToTextExtractor.TRUNCATION_MARKER.length + 1)
        val doc = try {
            Jsoup.parse(html, url)
        } catch (_: Exception) {
            return ""
        }
        doc.select(
            "script, style, noscript, iframe, object, embed, nav, footer, aside, form, meta, link"
        ).remove()
        val title = doc.title().trim()
        val body = doc.body() ?: return ""
        val out = StringBuilder()
        for (child in body.children()) {
            renderBlock(child, out)
        }
        // Fallback: body with no element children (bare text) — keep it.
        if (out.isBlank()) {
            val bare = body.text().trim()
            if (bare.isNotEmpty()) out.append(bare).append("\n")
        }
        var text = normalize(out.toString())
        if (title.isNotEmpty()) {
            text = normalize("$title\n$text")
        }
        if (text.isBlank()) return ""
        if (text.length > safeBudget) {
            text = truncateAtLineBoundary(
                text,
                safeBudget - HtmlToTextExtractor.TRUNCATION_MARKER.length - 1
            ) + "\n" + HtmlToTextExtractor.TRUNCATION_MARKER
        }
        return text.trim().takeIf { it.isNotEmpty() } ?: ""
    }

    private fun normalize(text: String): String {
        val lines = text.split("\n").map { it.trimEnd() }
        // Keep leading structure but drop blank runs down to single blanks.
        val kept = lines.map { it.trim() }.filter { it.isNotEmpty() }
        return Regex("\n{3,}").replace(kept.joinToString("\n"), "\n\n").trim()
    }

    private fun truncateAtLineBoundary(text: String, budget: Int): String {
        if (text.length <= budget) return text
        val lastNewline = text.lastIndexOf('\n', budget)
        return if (lastNewline > 0) text.take(lastNewline) else text.take(budget)
    }

    private fun renderBlock(el: Element, out: StringBuilder) {
        when (el.tagName().lowercase()) {
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                val level = el.tagName()[1].digitToInt()
                out.append("#".repeat(level)).append(" ").append(inlineText(el).trim()).append("\n\n")
            }
            "p", "div", "section", "article", "header", "main" -> {
                // Recurse so nested lists/tables/headings keep structure;
                // plain inline content falls back to a paragraph.
                val childBlocks = el.children()
                if (childBlocks.isEmpty()) {
                    val t = inlineText(el).trim()
                    if (t.isNotEmpty()) out.append(t).append("\n\n")
                } else {
                    val inline = StringBuilder()
                    for (child in childBlocks) {
                        if (isBlockTag(child.tagName())) {
                            if (inline.isNotBlank()) {
                                out.append(inline.toString().trim()).append("\n\n")
                                inline.clear()
                            }
                            renderBlock(child, out)
                        } else {
                            appendInline(child, inline)
                        }
                    }
                    if (inline.isNotBlank()) out.append(inline.toString().trim()).append("\n\n")
                    // Bare text nodes directly under the container.
                    val own = el.ownText().trim()
                    if (own.isNotEmpty() && out.isEmpty()) out.append(own).append("\n\n")
                }
            }
            "ul" -> {
                for (li in el.children()) {
                    if (li.tagName().equals("li", ignoreCase = true)) {
                        out.append("- ").append(inlineText(li).trim()).append("\n")
                    }
                }
                out.append("\n")
            }
            "ol" -> {
                var n = 1
                for (li in el.children()) {
                    if (li.tagName().equals("li", ignoreCase = true)) {
                        out.append("${n++}. ").append(inlineText(li).trim()).append("\n")
                    }
                }
                out.append("\n")
            }
            "li" -> out.append("- ").append(inlineText(el).trim()).append("\n\n")
            "pre" -> {
                val code = el.selectFirst("code") ?: el
                val lang = languageHint(code)
                val raw = code.wholeText().trim('\n')
                out.append("```").append(lang).append("\n")
                    .append(raw.trim()).append("\n```\n\n")
            }
            "table" -> renderTable(el, out)
            "blockquote" -> {
                val t = inlineText(el).trim()
                if (t.isNotEmpty()) {
                    for (line in t.split("\n")) {
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty()) out.append("> ").append(trimmed).append("\n")
                    }
                    out.append("\n")
                }
            }
            "hr" -> out.append("---\n\n")
            "img" -> {
                val alt = el.attr("alt").trim()
                if (alt.isNotEmpty()) out.append(alt).append("\n\n")
            }
            "br" -> out.append("\n")
            else -> {
                // Unknown/span containers: recurse into block children,
                // otherwise emit inline text.
                val blocks = el.children().filter { isBlockTag(it.tagName()) }
                if (blocks.isEmpty()) {
                    val t = inlineText(el).trim()
                    if (t.isNotEmpty()) out.append(t).append("\n\n")
                } else {
                    for (child in el.children()) renderBlockOrInline(child, out)
                }
            }
        }
    }

    private fun renderBlockOrInline(el: Element, out: StringBuilder) {
        if (isBlockTag(el.tagName())) renderBlock(el, out)
        else {
            val t = inlineText(el).trim()
            if (t.isNotEmpty()) out.append(t).append("\n\n")
        }
    }

    private fun isBlockTag(tag: String): Boolean = when (tag.lowercase()) {
        "h1", "h2", "h3", "h4", "h5", "h6", "p", "div", "ul", "ol", "li",
        "pre", "table", "blockquote", "hr", "section", "article", "header", "main" -> true
        else -> false
    }

    private fun languageHint(code: Element): String {
        for (cls in code.className().split(" ")) {
            val c = cls.trim()
            if (c.startsWith("language-", ignoreCase = true)) {
                return c.substringAfter("-").trim().takeWhile { it.isLetterOrDigit() || it == '-' || it == '+' }
            }
        }
        return ""
    }

    private fun renderTable(table: Element, out: StringBuilder) {
        val rows = table.select("tr")
        if (rows.isEmpty()) return
        val cells = rows.map { row ->
            row.select("th, td").map { inlineText(it).trim().replace("|", "\\|") }
        }.filter { it.isNotEmpty() }
        if (cells.isEmpty()) return
        val width = cells.maxOf { it.size }
        val norm = cells.map { row ->
            if (row.size < width) row + List(width - row.size) { "" } else row
        }
        out.append("| ").append(norm.first().joinToString(" | ")).append(" |\n")
        out.append("| ").append(List(width) { "---" }.joinToString(" | ")).append(" |\n")
        for (row in norm.drop(1)) {
            out.append("| ").append(row.joinToString(" | ")).append(" |\n")
        }
        out.append("\n")
    }

    /** Inline markdown for phrasing content: bold/italic/links/images. */
    private fun inlineText(el: Element): String {
        val sb = StringBuilder()
        for (node in el.childNodes()) appendInline(node, sb)
        return sb.toString().replace(Regex("[ \\t]+"), " ")
    }

    private fun appendInline(node: Node, sb: StringBuilder) {
        when (node) {
            is TextNode -> sb.append(node.wholeText())
            is Element -> when (node.tagName().lowercase()) {
                "b", "strong" -> sb.append("**").append(inlineText(node).trim()).append("**")
                "i", "em" -> sb.append("*").append(inlineText(node).trim()).append("*")
                "code" -> sb.append("`").append(node.text().trim()).append("`")
                "a" -> {
                    val text = inlineText(node).trim()
                    val href = node.attr("href").trim()
                    if (text.isEmpty()) return
                    if (href.startsWith("http://", ignoreCase = true) ||
                        href.startsWith("https://", ignoreCase = true)
                    ) {
                        sb.append("[").append(text).append("](").append(href).append(")")
                    } else {
                        // Non-http(s) scheme (javascript:/data:/...): emit
                        // visible text unwrapped — never a clickable target.
                        sb.append(text)
                    }
                }
                "img" -> {
                    val alt = node.attr("alt").trim()
                    if (alt.isNotEmpty()) sb.append(alt)
                }
                "br" -> sb.append("\n")
                else -> sb.append(inlineText(node))
            }
        }
    }
}
