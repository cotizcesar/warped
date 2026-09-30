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

    // ── Quick-task (citation taps) ──────────────────────────

    private fun annotatedWithCitations(text: String) =
        parseInlineMarkdownAsAnnotatedString(
            text = text,
            baseStyle = SpanStyle(),
            inlineCodeBgColor = inlineCodeBgColor,
            onCitationClick = {},
        )

    private fun citationsOf(result: androidx.compose.ui.text.AnnotatedString) =
        result.getStringAnnotations(CITATION_TAG, 0, result.length)

    @Test
    fun `single citation marker annotates exactly the digit range`() {
        val result = annotatedWithCitations("Read this [2] now")

        assertThat(result.text).isEqualTo("Read this [2] now")
        val annotations = citationsOf(result)
        assertThat(annotations).hasSize(1)
        assertThat(annotations[0].item).isEqualTo("2")
        assertThat(result.text.substring(annotations[0].start, annotations[0].end))
            .isEqualTo("2")
    }

    @Test
    fun `multi marker annotates each number independently`() {
        val result = annotatedWithCitations("See [1, 5] for details")

        assertThat(result.text).isEqualTo("See [1, 5] for details")
        val annotations = citationsOf(result)
        assertThat(annotations.map { it.item }).containsExactly("1", "5").inOrder()
        // Brackets, comma, and space carry no annotation.
        val annotatedOffsets = annotations.flatMap { a -> a.start until a.end }.toSet()
        assertThat(annotatedOffsets).doesNotContain(result.text.indexOf("["))
        assertThat(annotatedOffsets).doesNotContain(result.text.indexOf(","))
        assertThat(annotatedOffsets).doesNotContain(result.text.indexOf("]"))
    }

    @Test
    fun `compact multi marker without spaces splits`() {
        val result = annotatedWithCitations("[1,5]")

        assertThat(result.text).isEqualTo("[1,5]")
        assertThat(citationsOf(result).map { it.item })
            .containsExactly("1", "5").inOrder()
    }

    @Test
    fun `out-of-range numbers still annotate - caller ignores them`() {
        val result = annotatedWithCitations("Hallucinated [99] marker")

        // The renderer always fires the parsed int; MessageBubble drops
        // out-of-range N via previewForTap null (covered in
        // SourcePreviewMappingTest).
        assertThat(citationsOf(result).map { it.item }).containsExactly("99")
    }

    @Test
    fun `null callback renders plain output with zero annotations`() {
        val result = parseInlineMarkdownAsAnnotatedString(
            text = "Read this [2] and [1, 5] now",
            baseStyle = SpanStyle(),
            inlineCodeBgColor = inlineCodeBgColor,
            onCitationClick = null,
        )

        assertThat(result.text).isEqualTo("Read this [2] and [1, 5] now")
        assertThat(citationsOf(result)).isEmpty()
        assertThat(result.spanStyles).isEmpty()
    }

    @Test
    fun `citations inside inline code stay plain`() {
        val result = annotatedWithCitations("Use `[1]` for arrays")

        assertThat(result.text).isEqualTo("Use [1] for arrays")
        assertThat(citationsOf(result)).isEmpty()
    }

    @Test
    fun `non-marker brackets stay plain`() {
        val result = annotatedWithCitations("An [abc] note and [] empty")

        assertThat(result.text).isEqualTo("An [abc] note and [] empty")
        assertThat(citationsOf(result)).isEmpty()
    }

    @Test
    fun `citation markers keep bold and italic parsing intact`() {
        val result = annotatedWithCitations("**bold** and [3] plus *ital*")

        assertThat(result.text).isEqualTo("bold and [3] plus ital")
        assertThat(citationsOf(result).map { it.item }).containsExactly("3")
        assertThat(
            result.spanStyles.any { it.item.fontWeight == FontWeight.Bold },
        ).isTrue()
        assertThat(
            result.spanStyles.any { it.item.fontStyle == FontStyle.Italic },
        ).isTrue()
    }
}
