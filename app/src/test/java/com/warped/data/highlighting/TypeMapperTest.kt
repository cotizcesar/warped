package com.warped.data.highlighting

import com.google.common.truth.Truth.assertThat
import com.warped.domain.model.TokenType
import dev.snipme.highlights.model.CodeStructure
import dev.snipme.highlights.model.PhraseLocation
import org.junit.jupiter.api.Test

class TypeMapperTest {

    // === Core Mapping Tests ===

    @Test
    fun `maps keywords to KEYWORD type`() {
        val code = "public class Foo {}"
        val structure = CodeStructure(
            marks = setOf(PhraseLocation(16, 17), PhraseLocation(18, 19)),
            punctuations = emptySet(),
            keywords = setOf(PhraseLocation(0, 6), PhraseLocation(7, 12)),
            strings = emptySet(),
            literals = emptySet(),
            comments = emptySet(),
            multilineComments = emptySet(),
            annotations = emptySet(),
            incremental = false,
        )
        val tokens = TypeMapper.map(structure, code)
        val keywordTokens = tokens.filter { it.type == TokenType.KEYWORD }
        assertThat(keywordTokens).hasSize(2)
        assertThat(keywordTokens[0].text).isEqualTo("public")
        assertThat(keywordTokens[1].text).isEqualTo("class")
    }

    @Test
    fun `maps strings to STRING type`() {
        val code = "val x = \"hello world\""
        val structure = CodeStructure(
            marks = emptySet(),
            punctuations = setOf(PhraseLocation(6, 7)),
            keywords = setOf(PhraseLocation(0, 3)),
            strings = setOf(PhraseLocation(8, 21)),
            literals = emptySet(),
            comments = emptySet(),
            multilineComments = emptySet(),
            annotations = emptySet(),
            incremental = false,
        )
        val tokens = TypeMapper.map(structure, code)
        val stringTokens = tokens.filter { it.type == TokenType.STRING }
        assertThat(stringTokens).hasSize(1)
        assertThat(stringTokens[0].text).isEqualTo("\"hello world\"")
    }

    @Test
    fun `maps comments to COMMENT type`() {
        val code = "// this is a comment\nval x = 1"
        val structure = CodeStructure(
            marks = emptySet(),
            punctuations = setOf(PhraseLocation(26, 27)),
            keywords = setOf(PhraseLocation(20, 23)),
            strings = emptySet(),
            literals = setOf(PhraseLocation(28, 29)),
            comments = setOf(PhraseLocation(0, 19)),
            multilineComments = emptySet(),
            annotations = emptySet(),
            incremental = false,
        )
        val tokens = TypeMapper.map(structure, code)
        val commentTokens = tokens.filter { it.type == TokenType.COMMENT }
        assertThat(commentTokens).hasSize(1)
        assertThat(commentTokens[0].text).startsWith("//")
    }

    @Test
    fun `maps multiline comments to COMMENT type`() {
        val code = "/* block comment */\nval x = 1"
        val structure = CodeStructure(
            marks = emptySet(),
            punctuations = setOf(PhraseLocation(26, 27)),
            keywords = setOf(PhraseLocation(20, 23)),
            strings = emptySet(),
            literals = setOf(PhraseLocation(28, 29)),
            comments = emptySet(),
            multilineComments = setOf(PhraseLocation(0, 19)),
            annotations = emptySet(),
            incremental = false,
        )
        val tokens = TypeMapper.map(structure, code)
        val commentTokens = tokens.filter { it.type == TokenType.COMMENT }
        assertThat(commentTokens).hasSize(1)
        assertThat(commentTokens[0].text).isEqualTo("/* block comment */")
    }

    @Test
    fun `maps annotations to PROPERTY type`() {
        val code = "@Override\npublic void foo() {}"
        val structure = CodeStructure(
            marks = setOf(PhraseLocation(25, 26), PhraseLocation(26, 27)),
            punctuations = emptySet(),
            keywords = setOf(PhraseLocation(10, 16), PhraseLocation(17, 21)),
            strings = emptySet(),
            literals = emptySet(),
            comments = emptySet(),
            multilineComments = emptySet(),
            annotations = setOf(PhraseLocation(0, 9)),
            incremental = false,
        )
        val tokens = TypeMapper.map(structure, code)
        val propertyTokens = tokens.filter { it.type == TokenType.PROPERTY }
        assertThat(propertyTokens).hasSize(1)
        assertThat(propertyTokens[0].text).isEqualTo("@Override")
    }

    // === Integration: Full Java snippet ===

    @Test
    fun `map should return tokens with correct types for Java snippet`() {
        val code = "public class Foo { int x = 42; }"
        val structure = CodeStructure(
            marks = setOf(PhraseLocation(17, 18), PhraseLocation(31, 32)),
            punctuations = setOf(PhraseLocation(25, 26), PhraseLocation(29, 30)),
            keywords = setOf(PhraseLocation(0, 6), PhraseLocation(7, 12), PhraseLocation(19, 22)),
            strings = emptySet(),
            literals = setOf(PhraseLocation(27, 29)),
            comments = emptySet(),
            multilineComments = emptySet(),
            annotations = emptySet(),
            incremental = false,
        )

        val tokens = TypeMapper.map(structure, code)

        val keywordTokens = tokens.filter { it.type == TokenType.KEYWORD }
        assertThat(keywordTokens).isNotEmpty()
        assertThat(keywordTokens.map { it.text }).containsAtLeast("public", "class", "int")

        val stringTokens = tokens.filter { it.type == TokenType.STRING }
        assertThat(stringTokens).isEmpty()

        val numberTokens = tokens.filter { it.type == TokenType.NUMBER }
        assertThat(numberTokens.map { it.text }).contains("42")

        val tagTokens = tokens.filter { it.type == TokenType.TAG }
        assertThat(tagTokens.map { it.text }).containsAtLeast("{", "}")

        val operatorTokens = tokens.filter { it.type == TokenType.OPERATOR }
        assertThat(operatorTokens.map { it.text }).contains("=")

        val punctuationTokens = tokens.filter { it.type == TokenType.PUNCTUATION }
        assertThat(punctuationTokens.map { it.text }).contains(";")
    }

    @Test
    fun `map should fill ALL character positions with tokens, no gaps`() {
        val code = "val x = 1"
        val structure = CodeStructure(
            marks = emptySet(),
            punctuations = setOf(PhraseLocation(6, 7)),
            keywords = setOf(PhraseLocation(0, 3)),
            strings = emptySet(),
            literals = setOf(PhraseLocation(8, 9)),
            comments = emptySet(),
            multilineComments = emptySet(),
            annotations = emptySet(),
            incremental = false,
        )

        val tokens = TypeMapper.map(structure, code)

        val sorted = tokens.sortedBy { it.start }
        var expectedPos = 0
        for (token in sorted) {
            assertThat(token.start).isEqualTo(expectedPos)
            expectedPos = token.end
        }
        assertThat(expectedPos).isEqualTo(code.length)
    }

    @Test
    fun `map should split literals into NUMBER and CONSTANT based on text content`() {
        val code = "val a = true; val b = 42; val c = null"
        val structure = CodeStructure(
            marks = emptySet(),
            punctuations = setOf(PhraseLocation(12, 13), PhraseLocation(24, 25)),
            keywords = setOf(PhraseLocation(0, 3), PhraseLocation(14, 17), PhraseLocation(26, 29)),
            strings = emptySet(),
            literals = setOf(
                PhraseLocation(8, 12),
                PhraseLocation(22, 24),
                PhraseLocation(34, 38),
            ),
            comments = emptySet(),
            multilineComments = emptySet(),
            annotations = emptySet(),
            incremental = false,
        )

        val tokens = TypeMapper.map(structure, code)

        val numberTokens = tokens.filter { it.type == TokenType.NUMBER }
        assertThat(numberTokens.map { it.text }).contains("42")

        val constantTokens = tokens.filter { it.type == TokenType.CONSTANT }
        assertThat(constantTokens.map { it.text }).containsAtLeast("true", "null")
    }

    @Test
    fun `map should split punctuations into OPERATOR and PUNCTUATION`() {
        val code = "x = y + z"
        val structure = CodeStructure(
            marks = emptySet(),
            punctuations = setOf(
                PhraseLocation(2, 3),
                PhraseLocation(6, 7),
            ),
            keywords = emptySet(),
            strings = emptySet(),
            literals = emptySet(),
            comments = emptySet(),
            multilineComments = emptySet(),
            annotations = emptySet(),
            incremental = false,
        )

        val tokens = TypeMapper.map(structure, code)

        val operatorTokens = tokens.filter { it.type == TokenType.OPERATOR }
        assertThat(operatorTokens.map { it.text }).containsAtLeast("=", "+")

        val plainTokens = tokens.filter { it.type == TokenType.PLAIN }
        assertThat(plainTokens.map { it.text }.joinToString("")).contains("x")
    }

    // === Edge Cases ===

    @Test
    fun `handles empty code gracefully`() {
        val code = ""
        val structure = CodeStructure(
            marks = emptySet(), punctuations = emptySet(), keywords = emptySet(),
            strings = emptySet(), literals = emptySet(), comments = emptySet(),
            multilineComments = emptySet(), annotations = emptySet(), incremental = false,
        )
        val tokens = TypeMapper.map(structure, code)
        assertThat(tokens).isEmpty()
    }

    @Test
    fun `handles single character code`() {
        val code = "x"
        val structure = CodeStructure(
            marks = emptySet(), punctuations = emptySet(), keywords = emptySet(),
            strings = emptySet(), literals = emptySet(), comments = emptySet(),
            multilineComments = emptySet(), annotations = emptySet(), incremental = false,
        )
        val tokens = TypeMapper.map(structure, code)
        assertThat(tokens).hasSize(1)
        assertThat(tokens[0].type).isEqualTo(TokenType.PLAIN)
        assertThat(tokens[0].text).isEqualTo("x")
    }

    @Test
    fun `splits punctuations into OPERATOR and PUNCTUATION including parens`() {
        val code = "x = (a + b) * c"
        val structure = CodeStructure(
            marks = emptySet(),
            punctuations = setOf(
                PhraseLocation(2, 3),
                PhraseLocation(4, 5),
                PhraseLocation(7, 8),
                PhraseLocation(10, 11),
                PhraseLocation(12, 13),
            ),
            keywords = emptySet(),
            strings = emptySet(),
            literals = emptySet(),
            comments = emptySet(),
            multilineComments = emptySet(),
            annotations = emptySet(),
            incremental = false,
        )
        val tokens = TypeMapper.map(structure, code)
        val operatorTokens = tokens.filter { it.type == TokenType.OPERATOR }
        val punctuationTokens = tokens.filter { it.type == TokenType.PUNCTUATION }
        assertThat(operatorTokens.map { it.text }).containsExactly("=", "+", "*")
        assertThat(punctuationTokens.map { it.text }).containsExactly("(", ")")
    }
}
