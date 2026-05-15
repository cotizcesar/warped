package com.warped.ui.chat.components

import com.warped.domain.highlighting.LanguageDetector
import com.warped.domain.model.MarkdownBlock

private val CODE_FENCE_REGEX = Regex("^```(\\w*)\\s*$")
private val HEADER_REGEX = Regex("^(#{1,3})\\s+(.+)")
private val UNORDERED_LIST_REGEX = Regex("^[\\-\\*]\\s+(.+)")
private val ORDERED_LIST_REGEX = Regex("^(\\d+)\\.\\s+(.+)")
private val INLINE_CODE_REGEX = Regex("`([^`]+)`")

fun parseMarkdown(text: String, languageDetector: LanguageDetector): List<MarkdownBlock> {
    if (text.isBlank()) return emptyList()

    val lines = text.lines()
    val result = mutableListOf<MarkdownBlock>()

    var inCodeBlock = false
    var codeLang = ""
    val codeLines = mutableListOf<String>()

    // Collect consecutive list items
    var pendingListItems = mutableListOf<String>()
    var pendingListOrdered = false

    fun flushListItems() {
        if (pendingListItems.isNotEmpty()) {
            result.add(
                MarkdownBlock.ListItemBlock(
                    ordered = pendingListOrdered,
                    items = pendingListItems.toList(),
                ),
            )
            pendingListItems = mutableListOf()
        }
    }

    fun flushCodeBlock() {
        if (codeLines.isNotEmpty()) {
            val code = codeLines.joinToString("\n").trimEnd('\n')
            val resolvedLang = languageDetector.detect(codeLang.ifBlank { null }, code)
            result.add(MarkdownBlock.CodeBlock(language = resolvedLang, code = code))
            codeLines.clear()
        }
        codeLang = ""
    }

    fun processLine(line: String) {
        // Empty lines separating blocks
        if (line.isBlank()) {
            flushListItems()
            return
        }

        // Check for code fence
        val fenceMatch = CODE_FENCE_REGEX.matchEntire(line)
        if (fenceMatch != null) {
            flushListItems()
            if (inCodeBlock) {
                flushCodeBlock()
                inCodeBlock = false
            } else {
                inCodeBlock = true
                codeLang = fenceMatch.groupValues[1]
            }
            return
        }

        if (inCodeBlock) {
            codeLines.add(line)
            return
        }

        // Headers: ###, ##, #
        val headerMatch = HEADER_REGEX.matchEntire(line)
        if (headerMatch != null) {
            flushListItems()
            val level = headerMatch.groupValues[1].length // 1, 2, or 3
            val text = headerMatch.groupValues[2]
            result.add(MarkdownBlock.HeaderBlock(level = level, text = text))
            return
        }

        // Unordered list: - or *
        val unorderedMatch = UNORDERED_LIST_REGEX.matchEntire(line)
        if (unorderedMatch != null) {
            if (pendingListItems.isNotEmpty() && pendingListOrdered) {
                flushListItems()
            }
            pendingListOrdered = false
            pendingListItems.add(unorderedMatch.groupValues[1])
            return
        }

        // Ordered list: 1. 2. etc.
        val orderedMatch = ORDERED_LIST_REGEX.matchEntire(line)
        if (orderedMatch != null) {
            if (pendingListItems.isNotEmpty() && !pendingListOrdered) {
                flushListItems()
            }
            pendingListOrdered = true
            pendingListItems.add(orderedMatch.groupValues[2])
            return
        }

        // If we were collecting list items but this line isn't one, flush them
        flushListItems()

        // Check if the entire line is a single inline code span
        val trimmedLine = line.trim()
        val inlineCodeMatch = INLINE_CODE_REGEX.matchEntire(trimmedLine)
        if (inlineCodeMatch != null) {
            result.add(MarkdownBlock.InlineCodeBlock(code = inlineCodeMatch.groupValues[1]))
            return
        }

        // Default: TextBlock (inline markdown markers preserved for render-time styling)
        result.add(MarkdownBlock.TextBlock(text = line))
    }

    for (line in lines) {
        processLine(line)
    }

    // Flush any remaining content
    flushListItems()
    if (inCodeBlock) {
        flushCodeBlock()
    }

    return result
}
