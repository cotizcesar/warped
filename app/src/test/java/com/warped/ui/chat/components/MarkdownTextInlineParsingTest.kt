package com.warped.ui.chat.components

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.google.common.truth.Truth.assertThat
import com.warped.domain.model.SyntaxTheme
import org.junit.jupiter.api.Test

class MarkdownTextInlineParsingTest {

    @Test
    fun `parseInlineMarkdownAsAnnotatedString returns AnnotatedString with bold spans`() {
        val result = parseInlineMarkdownAsAnnotatedString(
            text = "**bold**",
            baseStyle = SpanStyle(),
            codeTheme = SyntaxTheme.MONOKAI,
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
            codeTheme = SyntaxTheme.MONOKAI,
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
            codeTheme = SyntaxTheme.MONOKAI,
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
            codeTheme = SyntaxTheme.MONOKAI,
        )
        assertThat(result.text).isEqualTo("Hello World")
        assertThat(result.spanStyles).isEmpty()
    }
}
