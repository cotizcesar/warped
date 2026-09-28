package com.warped.domain.highlighting

import com.warped.domain.model.SyntaxTheme
import com.warped.domain.model.SyntaxToken

interface SyntaxHighlighter {
    suspend fun highlight(code: String, language: String, theme: SyntaxTheme = SyntaxTheme.MONOKAI): List<SyntaxToken>
}
