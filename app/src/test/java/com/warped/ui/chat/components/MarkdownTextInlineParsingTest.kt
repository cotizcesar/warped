package com.warped.ui.chat.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class MarkdownTextInlineParsingTest {

    private val inlineCodeBgColor = Color(0x3F272822) // Monokai bg at ~25% alpha

    @Test
    fun `parseInlineMarkdownAsAnnotatedString returns AnnotatedString with bold spans`() {
        val result = parseInlineMarkdownAsAnnotatedString(
            text = "**bold**",
            baseStyle = SpanStyle(),
            inlineCodeBgColor = inlineCodeBgColor,
        )
        val spans = result.spanStyles
        assertThat(spans).isNotEmpty()
        val boldSpan = spans.first { it.item.fontWeight == FontWeight.Bold }
        assertThat(result.text.substring(boldSpan.start, boldSpan.end)).isEqualTo("bold")
    }

    @Test
    fun `parseInlineMarkdownAsAnnotatedString returns AnnotatedString with italic spans`() {
        val result = parseInlineMarkdownAsAnnotatedString(
            text = "*italic*",
            baseStyle = SpanStyle(),
            inlineCodeBgColor = inlineCodeBgColor,
        )
        val spans = result.spanStyles
        val italicSpan = spans.first { it.item.fontStyle == FontStyle.Italic }
        assertThat(result.text.substring(italicSpan.start, italicSpan.end)).isEqualTo("italic")
    }

    @Test
    fun `parseInlineMarkdownAsAnnotatedString returns AnnotatedString with inline code spans`() {
        val result = parseInlineMarkdownAsAnnotatedString(
            text = "`code`",
            baseStyle = SpanStyle(),
            inlineCodeBgColor = inlineCodeBgColor,
        )
        val spans = result.spanStyles
        val codeSpan = spans.first { it.item.fontFamily == FontFamily.Monospace }
        assertThat(result.text.substring(codeSpan.start, codeSpan.end)).isEqualTo("code")
    }

    @Test
    fun `parseInlineMarkdownAsAnnotatedString returns plain text when no markers`() {
        val result = parseInlineMarkdownAsAnnotatedString(
            text = "Hello World",
            baseStyle = SpanStyle(),
            inlineCodeBgColor = inlineCodeBgColor,
        )
        assertThat(result.text).isEqualTo("Hello World")
        assertThat(result.spanStyles).isEmpty()
    }
}
