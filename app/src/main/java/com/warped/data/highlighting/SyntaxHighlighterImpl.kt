package com.warped.data.highlighting

import com.warped.domain.highlighting.LanguageDetector
import com.warped.domain.highlighting.SyntaxHighlighter
import com.warped.domain.model.SyntaxToken
import com.warped.domain.model.TokenType
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.SyntaxThemes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyntaxHighlighterImpl @Inject constructor(
    private val languageDetector: LanguageDetector,
) : SyntaxHighlighter {

    private val cacheMutex = Mutex()
    private val cache = object : LinkedHashMap<String, List<SyntaxToken>>(50, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<SyntaxToken>>?): Boolean {
            return size > 50
        }
    }

    private fun cacheKey(code: String, language: String): String = "$code|$language"

    override suspend fun highlight(code: String, language: String): List<SyntaxToken> {
        if (code.length > 500_000) {
            Timber.w("SyntaxHighlighter: code block too large (%d chars) — returning plain tokens", code.length)
            return listOf(SyntaxToken(0, code.length, TokenType.PLAIN, code))
        }
        val key = cacheKey(code, language)
        val cached = cacheMutex.withLock { cache[key] }
        if (cached != null) return cached
        return withContext(Dispatchers.Default) {
            val syntaxLanguage = languageDetector.resolveSyntaxLanguage(language)
            val highlights = Highlights.Builder()
                .code(code)
                .language(syntaxLanguage)
                .theme(SyntaxThemes.monokai())
                .build()
            val structure = highlights.getCodeStructure()
            val tokens = TypeMapper.map(structure, code)
            cacheMutex.withLock { cache[key] = tokens }
            tokens
        }
    }
}
