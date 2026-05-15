package com.warped.domain.model

sealed class MarkdownBlock {
    data class TextBlock(val text: String) : MarkdownBlock()
    data class HeaderBlock(val level: Int, val text: String) : MarkdownBlock()
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock()
    data class InlineCodeBlock(val code: String) : MarkdownBlock()
    data class ListItemBlock(val ordered: Boolean, val items: List<String>) : MarkdownBlock()
}
