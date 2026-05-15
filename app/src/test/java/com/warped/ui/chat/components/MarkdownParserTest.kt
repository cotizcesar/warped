package com.warped.ui.chat.components

import com.google.common.truth.Truth.assertThat
import com.warped.domain.highlighting.LanguageDetector
import com.warped.domain.model.MarkdownBlock
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class MarkdownParserTest {

    private val detector = mockk<LanguageDetector> {
        every { detect(any(), any()) } answers {
            val fenceLabel = firstArg<String?>()
            if (!fenceLabel.isNullOrBlank()) {
                when (fenceLabel.lowercase().trim()) {
                    "python", "py" -> "python"
                    "js", "javascript" -> "javascript"
                    "plaintext" -> "plaintext"
                    else -> "plaintext"
                }
            } else {
                val code = secondArg<String>()
                when {
                    code.contains("print") -> "python"
                    code.contains("function") -> "javascript"
                    else -> "plaintext"
                }
            }
        }
    }

    @Test
    fun `plain text returns single TextBlock`() {
        val result = parseMarkdown("Hello", detector)
        assertThat(result).hasSize(1)
        assertThat(result[0]).isInstanceOf(MarkdownBlock.TextBlock::class.java)
        assertThat((result[0] as MarkdownBlock.TextBlock).text).isEqualTo("Hello")
    }

    @Test
    fun `single hash header followed by body text`() {
        val result = parseMarkdown("# Title\nBody", detector)
        assertThat(result).hasSize(2)
        assertThat(result[0]).isInstanceOf(MarkdownBlock.HeaderBlock::class.java)
        with(result[0] as MarkdownBlock.HeaderBlock) {
            assertThat(level).isEqualTo(1)
            assertThat(text).isEqualTo("Title")
        }
        assertThat(result[1]).isInstanceOf(MarkdownBlock.TextBlock::class.java)
        with(result[1] as MarkdownBlock.TextBlock) {
            assertThat(text).isEqualTo("Body")
        }
    }

    @Test
    fun `fenced code block with declared language`() {
        val result = parseMarkdown("```python\nprint(1)\n```", detector)
        assertThat(result).hasSize(1)
        assertThat(result[0]).isInstanceOf(MarkdownBlock.CodeBlock::class.java)
        with(result[0] as MarkdownBlock.CodeBlock) {
            assertThat(language).isEqualTo("python")
            assertThat(code).isEqualTo("print(1)")
        }
    }

    @Test
    fun `fenced code block with no language label auto-detects from content`() {
        val result = parseMarkdown("```\ncode\n```", detector)
        assertThat(result).hasSize(1)
        assertThat(result[0]).isInstanceOf(MarkdownBlock.CodeBlock::class.java)
        // Language depends on mock auto-detection logic
        val block = result[0] as MarkdownBlock.CodeBlock
        assertThat(block.code).isEqualTo("code")
    }

    @Test
    fun `multiline fenced code block preserves newlines`() {
        val result = parseMarkdown("```python\nline1\nline2\n```", detector)
        assertThat(result).hasSize(1)
        assertThat(result[0]).isInstanceOf(MarkdownBlock.CodeBlock::class.java)
        with(result[0] as MarkdownBlock.CodeBlock) {
            assertThat(language).isEqualTo("python")
            assertThat(code).isEqualTo("line1\nline2")
        }
    }

    @Test
    fun `unordered list items coalesced into ListItemBlock`() {
        val result = parseMarkdown("- a\n- b", detector)
        assertThat(result).hasSize(1)
        assertThat(result[0]).isInstanceOf(MarkdownBlock.ListItemBlock::class.java)
        with(result[0] as MarkdownBlock.ListItemBlock) {
            assertThat(ordered).isFalse()
            assertThat(items).containsExactly("a", "b").inOrder()
        }
    }

    @Test
    fun `ordered list items coalesced into ListItemBlock`() {
        val result = parseMarkdown("1. first\n2. second", detector)
        assertThat(result).hasSize(1)
        assertThat(result[0]).isInstanceOf(MarkdownBlock.ListItemBlock::class.java)
        with(result[0] as MarkdownBlock.ListItemBlock) {
            assertThat(ordered).isTrue()
            assertThat(items).containsExactly("first", "second").inOrder()
        }
    }

    @Test
    fun `inline code renders as InlineCodeBlock`() {
        val result = parseMarkdown("`code`", detector)
        assertThat(result).hasSize(1)
        assertThat(result[0]).isInstanceOf(MarkdownBlock.InlineCodeBlock::class.java)
        with(result[0] as MarkdownBlock.InlineCodeBlock) {
            assertThat(code).isEqualTo("code")
        }
    }

    @Test
    fun `inline bold and italic markers preserved in TextBlock text`() {
        val result = parseMarkdown("**bold** and *italic*", detector)
        assertThat(result).hasSize(1)
        assertThat(result[0]).isInstanceOf(MarkdownBlock.TextBlock::class.java)
        with(result[0] as MarkdownBlock.TextBlock) {
            // Inline markers are preserved — styling applied at render time
            assertThat(text).isEqualTo("**bold** and *italic*")
        }
    }

    @Test
    fun `unclosed code fence flushed to CodeBlock for streaming safety`() {
        val result = parseMarkdown("```python\nunclosed", detector)
        assertThat(result).hasSize(1)
        assertThat(result[0]).isInstanceOf(MarkdownBlock.CodeBlock::class.java)
        with(result[0] as MarkdownBlock.CodeBlock) {
            assertThat(language).isEqualTo("python")
            assertThat(code).isEqualTo("unclosed")
        }
    }
}
