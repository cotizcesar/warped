package com.warped.data.highlighting

import com.google.common.truth.Truth.assertThat
import com.warped.domain.highlighting.LanguageDetector
import com.warped.domain.model.TokenType
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class SyntaxHighlighterImplTest {

    private val languageDetector = LanguageDetector()
    private val highlighter = SyntaxHighlighterImpl(languageDetector)

    @Test
    fun `highlight should return non-empty List of SyntaxToken for python snippet`() = runTest {
        val tokens = highlighter.highlight("print('hello')", "python")
        assertThat(tokens).isNotEmpty()
    }

    @Test
    fun `highlight should return non-empty List of SyntaxToken for java snippet`() = runTest {
        val tokens = highlighter.highlight("public class Foo {}", "java")
        assertThat(tokens).isNotEmpty()
    }

    @Test
    fun `highlight should return non-empty List of SyntaxToken for bash snippet`() = runTest {
        val tokens = highlighter.highlight("echo hello", "bash")
        assertThat(tokens).isNotEmpty()
    }

    @Test
    fun `highlight should cache results, second call returns same reference`() = runTest {
        val code = "fun foo() = 42"
        val lang = "kotlin"
        val first = highlighter.highlight(code, lang)
        val second = highlighter.highlight(code, lang)
        assertThat(second === first).isTrue()
    }

    @Test
    fun `highlight should handle unsupported languages without crashing`() = runTest {
        val tokens = highlighter.highlight("{\"key\": \"value\"}", "json")
        assertThat(tokens).isNotEmpty()
    }

    @Test
    fun `highlight should wrap oversized code blocks in single PLAIN token`() = runTest {
        val bigCode = "a".repeat(500_001)
        val tokens = highlighter.highlight(bigCode, "plaintext")
        assertThat(tokens).hasSize(1)
        assertThat(tokens[0].type).isEqualTo(TokenType.PLAIN)
        assertThat(tokens[0].text).isEqualTo(bigCode)
    }
}
