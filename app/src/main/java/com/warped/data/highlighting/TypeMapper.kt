package com.warped.data.highlighting

import com.warped.domain.model.SyntaxToken
import com.warped.domain.model.TokenType
import dev.snipme.highlights.model.CodeStructure
import dev.snipme.highlights.model.PhraseLocation

internal object TypeMapper {

    private val CONSTANT_KEYWORDS = setOf("true", "false", "null", "nil", "none", "undefined")
    private val OPERATOR_REGEX = Regex("^[+\\-*/%=<>!&|^~?:]+$")

    private val TOKEN_TYPE_PRIORITY = mapOf(
        TokenType.STRING to 12,
        TokenType.KEYWORD to 11,
        TokenType.COMMENT to 10,
        TokenType.CONSTANT to 9,
        TokenType.NUMBER to 8,
        TokenType.FUNCTION to 7,
        TokenType.TYPE to 6,
        TokenType.OPERATOR to 5,
        TokenType.PROPERTY to 4,
        TokenType.PUNCTUATION to 3,
        TokenType.TAG to 2,
        TokenType.PLAIN to 1,
    )

    fun map(structure: CodeStructure, code: String): List<SyntaxToken> {
        val rawTokens = mutableListOf<SyntaxToken>()

        structure.keywords.forEach { loc ->
            rawTokens.add(SyntaxToken(loc.start, loc.end, TokenType.KEYWORD, code.substring(loc.start, loc.end)))
        }
        structure.strings.forEach { loc ->
            rawTokens.add(SyntaxToken(loc.start, loc.end, TokenType.STRING, code.substring(loc.start, loc.end)))
        }
        structure.comments.forEach { loc ->
            rawTokens.add(SyntaxToken(loc.start, loc.end, TokenType.COMMENT, code.substring(loc.start, loc.end)))
        }
        structure.multilineComments.forEach { loc ->
            rawTokens.add(SyntaxToken(loc.start, loc.end, TokenType.COMMENT, code.substring(loc.start, loc.end)))
        }
        structure.annotations.forEach { loc ->
            rawTokens.add(SyntaxToken(loc.start, loc.end, TokenType.PROPERTY, code.substring(loc.start, loc.end)))
        }
        structure.marks.forEach { loc ->
            rawTokens.add(SyntaxToken(loc.start, loc.end, TokenType.TAG, code.substring(loc.start, loc.end)))
        }
        structure.literals.forEach { loc ->
            val text = code.substring(loc.start, loc.end)
            val type = if (text.lowercase() in CONSTANT_KEYWORDS) TokenType.CONSTANT else TokenType.NUMBER
            rawTokens.add(SyntaxToken(loc.start, loc.end, type, text))
        }
        structure.punctuations.forEach { loc ->
            val text = code.substring(loc.start, loc.end)
            val type = if (OPERATOR_REGEX.matches(text)) TokenType.OPERATOR else TokenType.PUNCTUATION
            rawTokens.add(SyntaxToken(loc.start, loc.end, type, text))
        }

        // Resolve overlaps: group by start position, keep highest-priority token
        val byStart = LinkedHashMap<Int, MutableList<SyntaxToken>>()
        for (token in rawTokens) {
            byStart.getOrPut(token.start) { mutableListOf() }.add(token)
        }
        val resolved = mutableListOf<SyntaxToken>()
        for ((_, tokens) in byStart) {
            val best = tokens.maxWith(compareBy<SyntaxToken> { TOKEN_TYPE_PRIORITY[it.type] ?: 0 }.thenBy { it.end - it.start })
            resolved.add(best)
        }

        // Sort by start position
        resolved.sortBy { it.start }

        // Fill gaps with PLAIN tokens
        val filled = mutableListOf<SyntaxToken>()
        var pos = 0
        for (token in resolved) {
            if (token.start > pos) {
                filled.add(SyntaxToken(pos, token.start, TokenType.PLAIN, code.substring(pos, token.start)))
            }
            filled.add(token)
            pos = token.end
        }
        if (pos < code.length) {
            filled.add(SyntaxToken(pos, code.length, TokenType.PLAIN, code.substring(pos, code.length)))
        }

        return filled
    }
}
