package com.warped.data.highlighting

import com.google.common.truth.Truth.assertThat
import com.warped.domain.highlighting.LanguageDetector
import com.warped.domain.model.SyntaxTheme
import com.warped.domain.model.TokenType
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class SyntaxHighlighterImplTest {

    private val languageDetector = LanguageDetector()
    private val highlighter = SyntaxHighlighterImpl(languageDetector)

    // === Core Language Tests ===

    @Test
    fun `highlights python code`() = runTest {
        val code = "def hello():\n    return 'world'"
        val tokens = highlighter.highlight(code, "python")
        assertThat(tokens).isNotEmpty()
        assertThat(tokens.any { it.type == TokenType.KEYWORD }).isTrue()
    }

    @Test
    fun `highlights javascript code`() = runTest {
        val code = "function hello() {\n    const x = 42;\n    return x;\n}"
        val tokens = highlighter.highlight(code, "javascript")
        assertThat(tokens).isNotEmpty()
        assertThat(tokens.any { it.type == TokenType.KEYWORD }).isTrue()
    }

    @Test
    fun `highlights kotlin code`() = runTest {
        val code = "fun main() {\n    val name = \"Kotlin\"\n    println(name)\n}"
        val tokens = highlighter.highlight(code, "kotlin")
        assertThat(tokens).isNotEmpty()
    }

    @Test
    fun `highlights bash code`() = runTest {
        val code = "if [ -f file.txt ]; then\n    echo 'found'\nfi"
        val tokens = highlighter.highlight(code, "bash")
        assertThat(tokens).isNotEmpty()
    }

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

    // === Cache Tests ===

    @Test
    fun `caches results, second call returns same reference`() = runTest {
        val code = "val x = 42"
        val tokens1 = highlighter.highlight(code, "kotlin")
        val tokens2 = highlighter.highlight(code, "kotlin")
        assertThat(tokens2).isSameInstanceAs(tokens1)
    }

    @Test
    fun `different languages produce different cache entries`() = runTest {
        val code = "def foo():\n    pass"
        val pythonTokens = highlighter.highlight(code, "python")
        val plainTokens = highlighter.highlight(code, "plaintext")
        assertThat(plainTokens).isNotSameInstanceAs(pythonTokens)
    }

    @Test
    fun `highlight should cache results, second call returns same reference`() = runTest {
        val code = "fun foo() = 42"
        val lang = "kotlin"
        val first = highlighter.highlight(code, lang)
        val second = highlighter.highlight(code, lang)
        assertThat(second === first).isTrue()
    }

    // === Unsupported Language Tests ===

    @Test
    fun `highlight should handle unsupported languages without crashing`() = runTest {
        val tokens = highlighter.highlight("{\"key\": \"value\"}", "json")
        assertThat(tokens).isNotEmpty()
    }

    @Test
    fun `highlights JSON via DEFAULT language`() = runTest {
        val code = """{"name": "Warped", "version": 1}"""
        val tokens = highlighter.highlight(code, "json")
        assertThat(tokens).isNotEmpty()
    }

    @Test
    fun `highlights SQL via DEFAULT language`() = runTest {
        val code = "SELECT * FROM users WHERE id = 1"
        val tokens = highlighter.highlight(code, "sql")
        assertThat(tokens).isNotEmpty()
    }

    // === Size Limit Tests ===

    @Test
    fun `returns single PLAIN token for oversized code`() = runTest {
        val bigCode = "x".repeat(500_001)
        val tokens = highlighter.highlight(bigCode, "python")
        assertThat(tokens).hasSize(1)
        assertThat(tokens[0].type).isEqualTo(TokenType.PLAIN)
        assertThat(tokens[0].text.length).isEqualTo(500_001)
    }

    @Test
    fun `code at 500KB cap is processed normally`() = runTest {
        val code = "val x = 1\n".repeat(25000)
        val tokens = highlighter.highlight(code, "kotlin")
        assertThat(tokens.size).isGreaterThan(1)
    }

    @Test
    fun `highlight should wrap oversized code blocks in single PLAIN token`() = runTest {
        val bigCode = "a".repeat(500_001)
        val tokens = highlighter.highlight(bigCode, "plaintext")
        assertThat(tokens).hasSize(1)
        assertThat(tokens[0].type).isEqualTo(TokenType.PLAIN)
        assertThat(tokens[0].text).isEqualTo(bigCode)
    }

    // === Edge Cases ===

    @Test
    fun `handles empty code`() = runTest {
        val tokens = highlighter.highlight("", "python")
        assertThat(tokens).isEmpty()
    }

    // === Theme Threading Regression (THEME-02) ===

    @Test
    fun `highlights python guide snippet under all 4 presets with KEYWORD each`() = runTest {
        val code = "def hello():\n    return 'world'"
        for (theme in SyntaxTheme.all()) {
            val tokens = highlighter.highlight(code, "python", theme)
            assertThat(tokens).isNotEmpty()
            assertThat(tokens.any { it.type == TokenType.KEYWORD }).isTrue()
        }
    }

    @Test
    fun `cache separates entries per theme`() = runTest {
        val code = "def theme_sep():\n    return 1"
        val monokaiTokens = highlighter.highlight(code, "python", SyntaxTheme.MONOKAI)
        val oneDarkTokens = highlighter.highlight(code, "python", SyntaxTheme.ONE_DARK)
        assertThat(oneDarkTokens).isNotSameInstanceAs(monokaiTokens)
        // Same theme hits the cache again
        val monokaiAgain = highlighter.highlight(code, "python", SyntaxTheme.MONOKAI)
        assertThat(monokaiAgain).isSameInstanceAs(monokaiTokens)
    }
}
