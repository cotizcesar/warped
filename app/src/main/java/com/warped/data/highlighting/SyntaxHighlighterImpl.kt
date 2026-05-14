package com.warped.data.highlighting

import com.warped.domain.highlighting.LanguageDetector
import com.warped.domain.highlighting.SyntaxHighlighter
import com.warped.domain.model.SyntaxToken
import com.warped.domain.model.TokenType
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.SyntaxThemes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyntaxHighlighterImpl @Inject constructor(
    private val languageDetector: LanguageDetector,
) : SyntaxHighlighter {

    private val cache = object : LinkedHashMap<Int, List<SyntaxToken>>(50, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, List<SyntaxToken>>?): Boolean {
            return size > 50
        }
    }

    private fun cacheKey(code: String, language: String): Int = code.hashCode() xor language.hashCode()

    override suspend fun highlight(code: String, language: String): List<SyntaxToken> {
        if (code.length > 500_000) {
            Timber.w("SyntaxHighlighter: code block too large (%d chars) — returning plain tokens", code.length)
            return listOf(SyntaxToken(0, code.length, TokenType.PLAIN, code))
        }
        val key = cacheKey(code, language)
        cache[key]?.let { return it }
        return withContext(Dispatchers.Default) {
            val syntaxLanguage = languageDetector.resolveSyntaxLanguage(language)
            val highlights = Highlights.Builder()
                .code(code)
                .language(syntaxLanguage)
                .theme(SyntaxThemes.monokai())
                .build()
            val structure = highlights.getCodeStructure()
            val tokens = TypeMapper.map(structure, code)
            cache[key] = tokens
            tokens
        }
    }
}
